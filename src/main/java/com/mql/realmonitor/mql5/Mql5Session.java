package com.mql.realmonitor.mql5;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

/**
 * NEU: MQL5-Session mit Browser-Login und gedrosselten Trade-Exporten.
 *
 * Wichtiges Wissen aus dem MqlKiScanner (AGENTS.md, browser_session.py):
 * MQL5 schützt den Login mit einem per JavaScript berechneten Cookie und
 * lehnt reine HTTP-Formular-Logins pauschal mit "Incorrect login" ab —
 * unabhängig von den Zugangsdaten. Bewährte Lösung: echter Chrome
 * (Selenium) meldet an, die Cookies werden geerntet und für schnelle
 * HTTP-Abrufe wiederverwendet (persistente Cookie-Datei).
 *
 * Endpunkte (AGENTS.md doc/02):
 *  - MT5: /en/signals/{id}/export/positions
 *  - MT4: /en/signals/{id}/export/history  (positions → HTTP 404)
 *  Erfolg: Antwort beginnt mit "Time;". Login-HTML → Session erneuern.
 *
 * Rate-Limit: Mindestabstand zwischen Requests (2 s) plus zufällige Pause;
 * bei HTTP 429/503 Backoff, bei 403 Abbruch. Automatisiertes Abrufen kann
 * gegen MQL5-Nutzungsbedingungen verstoßen — bewusst langsam fahren.
 */
public class Mql5Session {

    private static final Logger LOGGER = Logger.getLogger(Mql5Session.class.getName());

    private static final String BASE_URL = "https://www.mql5.com";
    private static final String LOGIN_URL = BASE_URL + "/en/auth_login";
    private static final long MIN_REQUEST_INTERVAL_MS = 2000;
    private static final long THROTTLE_BACKOFF_MS = 45_000;
    private static final int MAX_THROTTLE_RETRIES = 3;

    private final MqlRealMonitorConfig config;
    private final Mql5Credentials credentials;
    private final Path cookieFile;
    private final Random random = new Random();

    private final Map<String, String> cookies = new LinkedHashMap<>();
    private long lastRequestTime = 0;

    /** Ergebnis eines Export-Downloads */
    public static class ExportResult {
        public final boolean success;
        public final String csv;
        public final String error;

        private ExportResult(boolean success, String csv, String error) {
            this.success = success;
            this.csv = csv;
            this.error = error;
        }

        static ExportResult ok(String csv) {
            return new ExportResult(true, csv, null);
        }

        static ExportResult fail(String error) {
            return new ExportResult(false, null, error);
        }
    }

    public Mql5Session(MqlRealMonitorConfig config) {
        this.config = config;
        this.credentials = new Mql5Credentials(config.getConfigDir());
        this.cookieFile = Paths.get(config.getConfigDir(), "mql5_cookies.properties");
        loadCookies();
    }

    public Mql5Credentials getCredentials() {
        return credentials;
    }

    // ------------------------------------------------------------ Cookies

    private void loadCookies() {
        File file = cookieFile.toFile();
        if (!file.exists()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            synchronized (cookies) {
                cookies.clear();
                for (String name : props.stringPropertyNames()) {
                    cookies.put(name, props.getProperty(name));
                }
            }
            LOGGER.info("MQL5-Cookies geladen: " + cookies.size() + " Stück");
        } catch (Exception e) {
            LOGGER.warning("Konnte MQL5-Cookies nicht laden: " + e.getMessage());
        }
    }

    private void saveCookies() {
        try (FileOutputStream fos = new FileOutputStream(cookieFile.toFile())) {
            Properties props = new Properties();
            synchronized (cookies) {
                for (Map.Entry<String, String> e : cookies.entrySet()) {
                    props.setProperty(e.getKey(), e.getValue());
                }
            }
            props.store(fos, "MQL5-Session-Cookies - NIE committen (Session-Rechte!)");
        } catch (Exception e) {
            LOGGER.warning("Konnte MQL5-Cookies nicht speichern: " + e.getMessage());
        }
    }

    private String cookieHeader() {
        synchronized (cookies) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cookies.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------ Login

    /**
     * NEU: Ist eine gespeicherte HTTP-Session nachweislich gültig?
     * (Cookies vorhanden UND Login-Check erfolgreich — OHNE Browser-Login.
     * Diagnose 21.09.2026: MQL5 akzeptiert geerntete Cookies per HTTP
     * derzeit oft nicht; nur bei echter Gültigkeit lohnt sich der HTTP-Weg.)
     */
    public boolean hasValidHttpSession() {
        if (!credentials.isConfigured()) {
            return false;
        }
        synchronized (cookies) {
            if (cookies.isEmpty()) {
                return false;
            }
        }
        return isLoggedIn();
    }

    /**
     * NEU: Ist die HTTP-Session aktuell eingeloggt? (MQL5 zeigt den
     * Abmelde-Link /en/auth_logout nur eingeloggt)
     */
    public boolean isLoggedIn() {
        if (!credentials.isConfigured()) {
            return false;
        }
        try {
            String html = httpGet(BASE_URL + "/en");
            return html.contains("/en/auth_logout") || html.contains(">Logout<")
                    || html.contains("Log out");
        } catch (Exception e) {
            LOGGER.warning("Login-Check fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    /**
     * NEU: Stellt sicher, dass eine gültige Session existiert.
     * Reihenfolge wie im KiScanner: gespeicherte Cookies prüfen → falls
     * ungültig: Selenium-Chrome-Login (JS-Cookie-Schutz!) → Cookies ernten.
     *
     * @return Fehlermeldung oder null bei Erfolg
     */
    public String ensureLoggedIn(boolean forceBrowserLogin) {
        if (!credentials.isConfigured()) {
            return "Keine MQL5-Zugangsdaten konfiguriert.\n\n"
                + "Bitte unter Menü → Einstellungen → Konfiguration MQL5-Login und "
                + "Passwort hinterlegen (Datei: " + credentials.getCredentialsFile() + ").";
        }

        if (!forceBrowserLogin && !cookies.isEmpty() && isLoggedIn()) {
            LOGGER.info("MQL5-Session aus gespeicherten Cookies gültig");
            return null;
        }

        return loginViaBrowser();
    }

    /**
     * NEU: Selenium-Chrome-Login (gleicher Ablauf wie MqlKiScanner/
     * browser_session.py): auth_login öffnen, Formular ausfüllen,
     * Submit per JS klicken, Cookies ernten.
     */
    private String loginViaBrowser() {
        LOGGER.info("=== MQL5 BROWSER LOGIN (Selenium-Chrome) ===");

        File profileDir = new File(config.getConfigDir(), "chrome_profile_mql5");
        profileDir.mkdirs();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profileDir.getAbsolutePath());
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-blink-features=AutomationControlled");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(45));

            driver.get(LOGIN_URL);
            Thread.sleep(1500);

            // Entweder Login-Formular erscheint, oder persistentes Profil ist
            // bereits eingeloggt und leitet weiter — beides abwarten
            try {
                wait.until(d -> !d.findElements(By.id("Login")).isEmpty()
                        || !d.getCurrentUrl().contains("/auth_login"));
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);

            java.util.List<WebElement> loginFields = driver.findElements(By.id("Login"));
            if (!loginFields.isEmpty()) {
                WebElement userField = driver.findElement(By.id("Login"));
                userField.clear();
                userField.sendKeys(credentials.getUser());

                WebElement pwField = driver.findElement(By.id("Password"));
                pwField.clear();
                pwField.sendKeys(credentials.getPassword());

                WebElement submit;
                try {
                    submit = driver.findElement(By.id("loginSubmit"));
                } catch (Exception e) {
                    submit = driver.findElement(
                            By.cssSelector("input.button.button_yellow.qa-submit"));
                }
                // Bewährt aus MqlDownloader: gelben Submit-Button per JS klicken
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                        "arguments[0].click();", submit);

                try {
                    wait.until(d -> !d.getCurrentUrl().contains("/auth_login")
                            && d.findElements(By.id("Login")).isEmpty());
                } catch (Exception ignored) {
                }
                Thread.sleep(2000);
            }

            // Eingeloggt-Check im Browser
            driver.get(BASE_URL + "/en");
            Thread.sleep(1500);
            if (!driver.findElements(By.id("Login")).isEmpty()
                    || driver.getCurrentUrl().contains("/auth_login")) {
                return "MQL5 hat die Anmeldung im Browser nicht akzeptiert (weiterhin "
                    + "ausgeloggt). Benutzer/Passwort in den Einstellungen prüfen.";
            }

            // Cookies ernten und übernehmen
            synchronized (cookies) {
                cookies.clear();
                for (org.openqa.selenium.Cookie c : driver.manage().getCookies()) {
                    if (c.getDomain() != null && c.getDomain().endsWith("mql5.com")) {
                        cookies.put(c.getName(), c.getValue());
                    }
                }
            }
            saveCookies();

            if (isLoggedIn()) {
                LOGGER.info("MQL5-Anmeldung erfolgreich — Cookies übernommen und gespeichert");
                return null;
            }
            return "Browser meldet Login, aber Cookie-Check schlug fehl — erneut versuchen.";

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "MQL5-Browser-Login fehlgeschlagen", e);
            return "MQL5-Browser-Login fehlgeschlagen: " + e.getMessage()
                + "\n\nLäuft Chrome? Ist ChromeDriver verfügbar?";
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------ Export

    /**
     * NEU: Lädt den Trade-Export eines Signals (MT5: positions, MT4: history;
     * unbekannte Plattform: erst positions, dann history).
     *
     * @return ExportResult mit CSV (beginnt mit "Time;") oder Fehlermeldung
     */
    public ExportResult exportPositionsCsv(String signalId, String platform) {
        String fehler = ensureLoggedIn(false);
        if (fehler != null) {
            return ExportResult.fail(fehler);
        }

        // Reihenfolge wie im Scanner: MT4 → history zuerst, sonst positions zuerst
        String[] kinds = "MT4".equalsIgnoreCase(platform)
                ? new String[]{"history", "positions"}
                : new String[]{"positions", "history"};

        String lastError = null;
        for (String kind : kinds) {
            String url = BASE_URL + "/en/signals/" + signalId + "/export/" + kind;
            for (int attempt = 1; attempt <= 3; attempt++) {
                HttpResult result = getWithRetry(url);
                if (result.status == 404) {
                    LOGGER.info("Export " + kind + " für " + signalId + " → 404 (falscher Export-Typ), versuche nächsten");
                    lastError = "HTTP 404";
                    break; // nächster kind
                }
                if (result.error != null) {
                    lastError = result.error;
                    break;
                }
                String body = stripBom(result.body);
                if (body.stripLeading().startsWith("Time;")) {
                    return ExportResult.ok(body);
                }
                if (body.stripLeading().startsWith("<!DOCTYPE") && body.contains("auth_login")) {
                    LOGGER.warning("Session abgelaufen (" + signalId + ") — erneuter Browser-Login");
                    fehler = ensureLoggedIn(true);
                    if (fehler != null) {
                        return ExportResult.fail(fehler);
                    }
                    continue; // Retry mit neuer Session
                }
                lastError = "Antwort ist kein CSV (Anfang: "
                        + body.substring(0, Math.min(body.length(), 80)) + ")";
                sleepQuietly(10_000L * attempt);
            }
        }

        return ExportResult.fail("Trade-Export für Signal " + signalId + " nicht möglich ("
                + lastError + "). Mögliche Ursache: temporäre Drosselung durch MQL5 — "
                + "in ein paar Minuten erneut versuchen.");
    }

    private static String stripBom(String s) {
        return s != null && s.startsWith("\ufeff") ? s.substring(1) : s;
    }

    // ------------------------------------------------------------ HTTP

    private static class HttpResult {
        int status;
        String body;
        String error;
    }

    /**
     * NEU: Rate-limitierter GET mit Backoff bei 429/503 und Abbruch bei 403
     */
    private HttpResult getWithRetry(String urlString) {
        HttpResult result = new HttpResult();
        for (int attempt = 1; attempt <= MAX_THROTTLE_RETRIES; attempt++) {
            rateLimit();
            try {
                HttpURLConnection connection = openConnection(urlString);
                int status = connection.getResponseCode();

                if (status == 429 || status == 503) {
                    LOGGER.warning("MQL5 drosselt (HTTP " + status + ") — Backoff "
                            + (THROTTLE_BACKOFF_MS / 1000) + "s");
                    sleepQuietly(THROTTLE_BACKOFF_MS);
                    continue;
                }
                if (status == 403) {
                    result.status = 403;
                    result.error = "MQL5 antwortet mit HTTP 403 (Sperre/Verbot) — Abbruch";
                    LOGGER.severe(result.error);
                    return result;
                }

                result.status = status;
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                result.body = readAll(stream);
                connection.disconnect();
                return result;

            } catch (Exception e) {
                result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                LOGGER.warning("HTTP-Fehler bei " + urlString + ": " + result.error);
                return result;
            }
        }
        result.error = "MQL5 drosselt weiter nach " + MAX_THROTTLE_RETRIES + " Backoff-Versuchen";
        return result;
    }

    private HttpURLConnection openConnection(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(config.getTimeoutSeconds() * 1000);
        connection.setReadTimeout(config.getTimeoutSeconds() * 1000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36");
        connection.setRequestProperty("Accept-Language", "en");
        connection.setRequestProperty("Accept", "text/csv, text/html, */*");
        String cookie = cookieHeader();
        if (!cookie.isEmpty()) {
            connection.setRequestProperty("Cookie", cookie);
        }
        return connection;
    }

    /**
     * NEU: Mindestabstand zwischen Requests plus zufällige Pause (ToS-Schonung)
     */
    private void rateLimit() {
        long now = System.currentTimeMillis();
        long sinceLast = now - lastRequestTime;
        long minInterval = MIN_REQUEST_INTERVAL_MS + random.nextInt(2000); // 2-4 s
        if (sinceLast < minInterval) {
            sleepQuietly(minInterval - sinceLast);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String httpGet(String urlString) throws Exception {
        rateLimit();
        HttpURLConnection connection = openConnection(urlString);
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();
        if (status != 200) {
            throw new IllegalStateException("HTTP " + status + " für " + urlString);
        }
        return body;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * NEU: Alter der gespeicherten Export-Datei in Stunden; -1 wenn keine existiert
     */
    public static double tradesFileAgeHours(MqlRealMonitorConfig config, String signalId) {
        try {
            Path path = Paths.get(config.getTradesFilePath(signalId));
            if (!Files.exists(path)) {
                return -1;
            }
            return (System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis()) / 3_600_000.0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * NEU: Zeitstempel der Cookie-Datei (für Diagnose); null wenn keine existiert
     */
    public Instant cookieFileTime() {
        try {
            if (Files.exists(cookieFile)) {
                return Files.getLastModifiedTime(cookieFile).toInstant();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
