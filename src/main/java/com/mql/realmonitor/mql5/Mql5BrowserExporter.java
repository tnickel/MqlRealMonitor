package com.mql.realmonitor.mql5;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

/**
 * NEU: Browser-basierter Trade-Export (Muster: MqlKiScanner/browser_session.py
 * export_positions_via_browser).
 *
 * Warum: MQL5 akzeptiert geerntete Login-Cookies bei direkten HTTP-Abrufen
 * aktuell nicht mehr (Session-Bindung verschärft) — der einzig zuverlässige
 * Weg ist ein echter Chrome mit persistentem Profil: Er meldet an (Formular
 * nur falls nötig) und lädt die Export-CSV über die Export-URL direkt
 * herunter (Chrome schreibt sie ins Staging-Verzeichnis).
 *
 * EIN Chrome-Fenster für den kompletten Lauf ("Trades laden"): Login einmal,
 * danach je Signal die Export-URL öffnen und den Download abwarten.
 * MT5: /export/positions — MT4: /export/history (positions → 404-Seite,
 * also keine Datei → anderen Typ versuchen).
 */
public class Mql5BrowserExporter implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(Mql5BrowserExporter.class.getName());

    private static final String BASE_URL = "https://www.mql5.com";
    private static final long LOGIN_FORM_WAIT_MS = 1500;
    private static final long DOWNLOAD_TIMEOUT_FIRST_KIND_MS = 20_000;  // 404-Seite kommt sofort, Download braucht Zeit
    private static final long DOWNLOAD_TIMEOUT_SECOND_KIND_MS = 30_000;

    private final MqlRealMonitorConfig config;
    private final Mql5Credentials credentials;
    private final Path stagingDir;

    private WebDriver driver;
    private WebDriverWait wait;

    public Mql5BrowserExporter(MqlRealMonitorConfig config) {
        this.config = config;
        this.credentials = new Mql5Credentials(config.getConfigDir());
        this.stagingDir = Paths.get(config.getTradesDir(), "_download");
    }

    /**
     * Startet Chrome mit persistentem Profil und Download-Staging.
     * Muss vor dem ersten Export aufgerufen werden; close() beendet Chrome.
     */
    public void open() {
        if (!credentials.isConfigured()) {
            throw new IllegalStateException("Keine MQL5-Zugangsdaten konfiguriert "
                    + "(Menü → Einstellungen → Konfiguration)");
        }

        File profileDir = new File(config.getConfigDir(), "chrome_profile_mql5");
        profileDir.mkdirs();
        stagingDir.toFile().mkdirs();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profileDir.getAbsolutePath());
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", java.util.List.of("enable-automation"));
        options.setExperimentalOption("prefs", java.util.Map.of(
                "download.default_directory", stagingDir.toAbsolutePath().toString(),
                "download.prompt_for_download", false,
                "download.directory_upgrade", true,
                "profile.default_content_settings.popups", 0));

        LOGGER.info("Starte Chrome für MQL5-Trade-Export (Profil: " + profileDir + ")");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(45));
    }

    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
            driver = null;
            LOGGER.info("Chrome für MQL5-Trade-Export beendet");
        }
    }

    /**
     * Meldet den Browser an, falls das persistente Profil ausgeloggt ist.
     *
     * @return Fehlermeldung oder null bei Erfolg
     */
    public String ensureLogin() {
        if (driver == null) {
            return "Chrome ist nicht gestartet (open() fehlt)";
        }
        try {
            driver.get(BASE_URL + "/en/auth_login");
            sleep(LOGIN_FORM_WAIT_MS);

            // Entweder Login-Formular, oder Profil ist bereits eingeloggt
            try {
                wait.until(d -> !d.findElements(By.id("Login")).isEmpty()
                        || !d.getCurrentUrl().contains("/auth_login"));
            } catch (Exception ignored) {
            }
            sleep(1000);

            if (!driver.findElements(By.id("Login")).isEmpty()) {
                String fehler = fuelleLoginFormular();
                if (fehler != null) {
                    return fehler;
                }
            }

            // Eingeloggt-Check: Login-Feld darf nicht mehr sichtbar sein
            driver.get(BASE_URL + "/en");
            sleep(LOGIN_FORM_WAIT_MS);
            if (!driver.findElements(By.id("Login")).isEmpty()
                    || driver.getCurrentUrl().contains("/auth_login")) {
                return "MQL5 hat die Anmeldung im Browser nicht akzeptiert — "
                        + "Benutzer/Passwort in den Einstellungen prüfen.";
            }

            LOGGER.info("Chrome ist bei MQL5 eingeloggt");
            return null;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "MQL5-Browser-Login fehlgeschlagen", e);
            return "MQL5-Browser-Login fehlgeschlagen: " + e.getMessage();
        }
    }

    private String fuelleLoginFormular() throws Exception {
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
            submit = driver.findElement(By.cssSelector("input.button.button_yellow.qa-submit"));
        }
        // Bewährt aus MqlDownloader/MqlKiScanner: Submit per JS klicken
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submit);

        try {
            wait.until(d -> !d.getCurrentUrl().contains("/auth_login")
                    && d.findElements(By.id("Login")).isEmpty());
        } catch (Exception ignored) {
        }
        sleep(2000);
        return null;
    }

    /**
     * Lädt den Trade-Export eines Signals im Browser herunter.
     *
     * @param signalId Signal-ID
     * @param platform "MT4"/"MT5" oder null (dann erst positions, dann history)
     * @return CSV-Inhalt (beginnt mit "Time;") oder null mit Fehler in errorHolder[0]
     */
    public String exportCsv(String signalId, String platform, String[] errorHolder) {
        if (driver == null) {
            errorHolder[0] = "Chrome ist nicht gestartet";
            return null;
        }

        String[] kinds = "MT4".equalsIgnoreCase(platform)
                ? new String[]{"history", "positions"}
                : new String[]{"positions", "history"};

        for (int k = 0; k < kinds.length; k++) {
            String kind = kinds[k];
            long timeout = k == 0 ? DOWNLOAD_TIMEOUT_FIRST_KIND_MS : DOWNLOAD_TIMEOUT_SECOND_KIND_MS;

            // Staging leeren, damit die neue Datei eindeutig ist
            cleanStaging();

            String url = BASE_URL + "/en/signals/" + signalId + "/export/" + kind;
            LOGGER.info("Öffne Export-URL (Signal " + signalId + ", " + kind + "): " + url);
            try {
                driver.get(url);

                // Falls die Session gefallen ist: Login-Feld erscheint → neu anmelden
                if (!driver.findElements(By.id("Login")).isEmpty()) {
                    LOGGER.warning("Chrome war ausgeloggt — erneute Anmeldung");
                    String loginFehler = ensureLogin();
                    if (loginFehler != null) {
                        errorHolder[0] = loginFehler;
                        return null;
                    }
                    driver.get(url);
                }

                // Auf die CSV im Staging warten (Chrome schreibt erst .crdownload)
                Path csv = waitForCsv(timeout);
                if (csv == null) {
                    LOGGER.info("Kein " + kind + "-Download für " + signalId
                            + " (vermutlich 404/falscher Export-Typ)");
                    continue; // anderen Export-Typ versuchen
                }

                String inhalt = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
                Files.deleteIfExists(csv);

                String bereinigt = inhalt.startsWith("\ufeff") ? inhalt.substring(1) : inhalt;
                if (!bereinigt.stripLeading().startsWith("Time;")) {
                    errorHolder[0] = "Download für " + signalId + " ist kein Positions-CSV (Anfang: "
                            + bereinigt.substring(0, Math.min(bereinigt.length(), 60)) + ")";
                    return null;
                }
                return bereinigt;

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Browser-Export für " + signalId
                        + " (" + kind + "): " + e.getMessage(), e);
                errorHolder[0] = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        if (errorHolder[0] == null) {
            errorHolder[0] = "Kein Trade-Export für Signal " + signalId + " verfügbar "
                    + "(weder positions noch history)";
        }
        return null;
    }

    // ------------------------------------------------------------ Hilfsmethoden

    private void cleanStaging() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagingDir)) {
            for (Path p : stream) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path waitForCsv(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagingDir, "*.csv")) {
                for (Path p : stream) {
                    if (!p.getFileName().toString().endsWith(".crdownload")) {
                        // Kurz warten, bis Chrome die Datei fertig geschrieben hat
                        long size1 = -1;
                        try {
                            size1 = Files.size(p);
                            Thread.sleep(400);
                            if (Files.size(p) == size1 && size1 > 0) {
                                return p;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(400);
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
