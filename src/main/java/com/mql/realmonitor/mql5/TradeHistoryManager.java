package com.mql.realmonitor.mql5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

/**
 * NEU: Orchestriert das Laden der Trade-Historie für ALLE Signale.
 *
 * Ablauf je Signal (wie im MqlKiScanner):
 *  - Cache: Export jünger als 24 h wird NICHT erneut geladen
 *    (Rate-Limit-Schonung, MQL5-ToS-Risiko)
 *  - Download über Mql5Session (Session-Login, Drosselung eingebaut)
 *  - Roh-CSV speichern: Realtick\trades\{id}_positions.csv
 *  - Equity-Kurve rekonstruieren und speichern: {id}_equity.csv
 *    (Format: Zeitstempel;KumulierterProfit)
 *
 * Läuft bewusst in einem Hintergrund-Thread; die Pause zwischen den
 * Signalen macht Mql5Session über den Rate-Limiter (2-4 s je Request).
 */
public class TradeHistoryManager {

    private static final Logger LOGGER = Logger.getLogger(TradeHistoryManager.class.getName());

    private static final double CACHE_STUNDEN = 24.0;
    private static final DateTimeFormatter EQUITY_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MqlRealMonitorConfig config;
    private final Mql5Session session;

    /** Rückmeldung eines Signal-Downloads für die Zusammenfassung */
    public static class SignalResult {
        public final String signalId;
        public final boolean success;
        public final boolean fromCache;
        public final int tradeCount;
        public final String error;

        public SignalResult(String signalId, boolean success, boolean fromCache,
                            int tradeCount, String error) {
            this.signalId = signalId;
            this.success = success;
            this.fromCache = fromCache;
            this.tradeCount = tradeCount;
            this.error = error;
        }
    }

    /** Gesamt-Ergebnis eines "Trades laden"-Laufs */
    public static class RefreshResult {
        public final List<SignalResult> results = new ArrayList<>();
        public int successCount = 0;
        public int cacheCount = 0;
        public int errorCount = 0;
    }

    public TradeHistoryManager(MqlRealMonitorConfig config) {
        this.config = config;
        this.session = new Mql5Session(config);
    }

    public Mql5Session getSession() {
        return session;
    }

    /**
     * NEU: Lädt die Trade-Historie für alle Signal-IDs.
     *
     * Strategie (Wissen aus der Diagnose vom 21.09.2026): MQL5 akzeptiert
     * geerntete Login-Cookies bei direkten HTTP-Abrufen derzeit NICHT mehr —
     * ist trotzdem eine gültige HTTP-Session vorhanden, wird der schnelle
     * HTTP-Weg genutzt; sonst läuft der Export über EINEN Chrome-Browser
     * (persistentes Profil, Login einmal, Downloads je Signal).
     *
     * @param signalIds Die zu ladenden Signal-IDs
     * @param platformen Optional: Signal-ID → Plattform ("MT4"/"MT5"), null = automatisch erkennen
     * @param progressCallback Optional: Statusmeldung pro Signal (für die GUI-Statuszeile)
     * @return Gesamt-Ergebnis
     */
    public RefreshResult refreshAll(List<String> signalIds, java.util.Map<String, String> platformen,
                                    java.util.function.Consumer<String> progressCallback) {
        RefreshResult result = new RefreshResult();

        try {
            Files.createDirectories(Paths.get(config.getTradesDir()));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Konnte Trades-Verzeichnis nicht erstellen", e);
        }

        // Cache-Prüfung VOR dem Login: Signale mit frischem Cache gar nicht anfassen
        List<String> zuLaden = new ArrayList<>();
        int cacheCount = 0;
        for (String signalId : signalIds) {
            // Selbstheilung: abgeleitete Kurven aus dem Roh-Export neu bauen
            // (kostet kein Netz — heilt bestehende Datenstände nach Format-Änderungen)
            regenerateDerivedCurves(config, signalId);

            double ageHours = Mql5Session.tradesFileAgeHours(config, signalId);
            if (ageHours >= 0 && ageHours < CACHE_STUNDEN) {
                result.results.add(new SignalResult(signalId, true, true, 0, null));
                cacheCount++;
            } else {
                zuLaden.add(signalId);
            }
        }
        if (cacheCount > 0) {
            LOGGER.info(cacheCount + " Signale aus Cache (frisch) — übersprungen");
        }

        if (zuLaden.isEmpty()) {
            result.successCount = cacheCount;
            result.cacheCount = cacheCount;
            LOGGER.info("Alle Trade-Exporte sind frisch im Cache — kein Download nötig");
            return result;
        }

        // Weg 1: HTTP (nur falls gespeicherte Session wirklich gültig ist)
        if (session.hasValidHttpSession()) {
            LOGGER.info("Gültige HTTP-Session vorhanden — nutze schnellen HTTP-Weg");
            for (int i = 0; i < zuLaden.size(); i++) {
                String signalId = zuLaden.get(i);
                if (progressCallback != null) {
                    progressCallback.accept("Trades (HTTP) " + (i + 1) + "/" + zuLaden.size() + ": " + signalId);
                }
                try {
                    SignalResult r = refreshOne(signalId,
                            platformen != null ? platformen.get(signalId) : null);
                    result.results.add(r);
                    if (r.success) {
                        result.successCount++;
                    } else {
                        result.errorCount++;
                    }
                } catch (Exception e) {
                    result.results.add(new SignalResult(signalId, false, false, 0, e.getMessage()));
                    result.errorCount++;
                }
            }
            return finalizeResult(result, cacheCount);
        }

        // Weg 2: Browser-Export (EIN Chrome für alle Signale)
        LOGGER.info("Keine gültige HTTP-Session — nutze Browser-Export (Chrome)");
        try (Mql5BrowserExporter browser = new Mql5BrowserExporter(config)) {
            browser.open();
            if (progressCallback != null) {
                progressCallback.accept("MQL5-Login im Browser...");
            }
            String loginFehler = browser.ensureLogin();
            if (loginFehler != null) {
                LOGGER.severe("Trades laden abgebrochen: " + loginFehler);
                result.results.add(new SignalResult("-", false, false, 0, loginFehler));
                result.errorCount += zuLaden.size();
                return finalizeResult(result, cacheCount);
            }

            for (int i = 0; i < zuLaden.size(); i++) {
                String signalId = zuLaden.get(i);
                if (progressCallback != null) {
                    progressCallback.accept("Trades (Browser) " + (i + 1) + "/" + zuLaden.size() + ": " + signalId);
                }

                try {
                    String[] fehler = new String[1];
                    String csv = browser.exportCsv(signalId,
                            platformen != null ? platformen.get(signalId) : null, fehler);
                    if (csv == null) {
                        result.results.add(new SignalResult(signalId, false, false, 0, fehler[0]));
                        result.errorCount++;
                        continue;
                    }
                    SignalResult r = speichereExport(signalId, csv);
                    result.results.add(r);
                    if (r.success) {
                        result.successCount++;
                    } else {
                        result.errorCount++;
                    }

                    // Pause zwischen den Signalen (MQL5-Schonung, 3-6 s)
                    Thread.sleep(3000 + (long) (Math.random() * 3000));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Fehler beim Browser-Export für " + signalId, e);
                    result.results.add(new SignalResult(signalId, false, false, 0, e.getMessage()));
                    result.errorCount++;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Browser-Export fehlgeschlagen", e);
            result.results.add(new SignalResult("-", false, false, 0,
                    "Browser-Export fehlgeschlagen: " + e.getMessage()));
            result.errorCount += zuLaden.size();
        }

        return finalizeResult(result, cacheCount);
    }

    /** Zählt Ergebnis zusammen und liest Cache-Trade-Zahlen nach */
    private RefreshResult finalizeResult(RefreshResult result, int cacheCount) {
        result.cacheCount = cacheCount;
        // Für Cache-Einträge die tatsächliche Trade-Anzahl nachlesen
        for (int i = 0; i < result.results.size(); i++) {
            SignalResult r = result.results.get(i);
            if (r.fromCache && r.tradeCount == 0) {
                SignalResult neu = new SignalResult(r.signalId, true, true,
                        equityCurveFilePoints(r.signalId), null);
                result.results.set(i, neu);
            }
        }
        LOGGER.info("=== TRADES LADEN ABGESCHLOSSEN: " + result.successCount + " OK, "
                + result.cacheCount + " aus Cache, " + result.errorCount + " Fehler ===");
        return result;
    }

    /**
     * Speichert Roh-CSV + beide Kurven (Gewinnkurve + Trading-Kurve)
     */
    private SignalResult speichereExport(String signalId, String csv) {
        try {
            Path rawPath = Paths.get(config.getTradesFilePath(signalId));
            Files.write(rawPath, csv.getBytes(StandardCharsets.UTF_8));

            List<EquityCurveBuilder.EquityPoint> gewinnKurve = EquityCurveBuilder.buildProfitCurve(csv);
            List<EquityCurveBuilder.EquityPoint> tradingKurve = EquityCurveBuilder.buildTradingCurve(csv);
            writeCurveFile(config.getEquityCurveFilePath(signalId), gewinnKurve,
                    "Gewinnkurve (kumulierte NETTO-Trade-Profits, ohne Kontobewegungen)");
            writeCurveFile(config.getTradingCurveFilePath(signalId), tradingKurve,
                    "Trading-Kurve (Startkapital + Netto-Profits, Ein-/Auszahlungen danach herausgerechnet)");

            LOGGER.info("Trade-Historie gespeichert: " + rawPath + " ("
                    + gewinnKurve.size() + " Trades)");
            return new SignalResult(signalId, true, false, gewinnKurve.size(), null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Trade-Daten für " + signalId + " nicht speichern", e);
            return new SignalResult(signalId, false, false, 0,
                    "Speichern fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * NEU: Baut die abgeleiteten Kurven aus einem bereits gecachten Roh-Export
     * neu (ohne Netz) — selbstheilend für bestehende Datenstände, z. B. nach
     * einer Änderung des Kurven-Formats.
     *
     * @return true wenn die Kurven (neu) geschrieben wurden
     */
    public static boolean regenerateDerivedCurves(MqlRealMonitorConfig config, String signalId) {
        try {
            Path rawPath = Paths.get(config.getTradesFilePath(signalId));
            if (!Files.exists(rawPath)) {
                return false;
            }
            String csv = new String(Files.readAllBytes(rawPath), StandardCharsets.UTF_8);
            List<EquityCurveBuilder.EquityPoint> gewinnKurve = EquityCurveBuilder.buildProfitCurve(csv);
            List<EquityCurveBuilder.EquityPoint> tradingKurve = EquityCurveBuilder.buildTradingCurve(csv);
            writeCurveFile(config.getEquityCurveFilePath(signalId), gewinnKurve,
                    "Gewinnkurve (kumulierte NETTO-Trade-Profits, ohne Kontobewegungen)");
            writeCurveFile(config.getTradingCurveFilePath(signalId), tradingKurve,
                    "Trading-Kurve (Startkapital + Netto-Profits, Ein-/Auszahlungen danach herausgerechnet)");
            // Veraltete Konto-Kurve (Superseded) entfernen
            Files.deleteIfExists(Paths.get(config.getTradesDir(), signalId + "_konto.csv"));
            LOGGER.info("Abgeleitete Kurven neu gebaut für " + signalId + " ("
                    + gewinnKurve.size() + " Trades)");
            return true;
        } catch (Exception e) {
            LOGGER.warning("Konnte abgeleitete Kurven für " + signalId + " nicht neu bauen: " + e.getMessage());
            return false;
        }
    }

    private static void writeCurveFile(String filePath, List<EquityCurveBuilder.EquityPoint> curve,
                                       String beschreibung) throws IOException {
        StringBuilder sb = new StringBuilder("# ").append(beschreibung).append('\n');
        for (EquityCurveBuilder.EquityPoint p : curve) {
            sb.append(p.getTime().format(EQUITY_TIME_FMT)).append(';')
              .append(p.getCumulatedProfit()).append('\n');
        }
        Files.write(Paths.get(filePath), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * NEU: Lädt die Historie eines einzelnen Signals (mit Cache-Prüfung, HTTP-Weg)
     */
    public SignalResult refreshOne(String signalId, String platform) {
        // Cache: Datei jünger als 24 h → nicht erneut laden
        double ageHours = Mql5Session.tradesFileAgeHours(config, signalId);
        if (ageHours >= 0 && ageHours < CACHE_STUNDEN) {
            LOGGER.info("Trade-Export für " + signalId + " ist " + String.format("%.1f", ageHours)
                    + "h alt — überspringe (Cache " + (int) CACHE_STUNDEN + "h)");
            return new SignalResult(signalId, true, true,
                    equityCurveFilePoints(signalId), null);
        }

        Mql5Session.ExportResult export = session.exportPositionsCsv(signalId, platform);
        if (!export.success) {
            return new SignalResult(signalId, false, false, 0, export.error);
        }
        return speichereExport(signalId, export.csv);
    }

    private int equityCurveFilePoints(String signalId) {
        try {
            Path path = Paths.get(config.getEquityCurveFilePath(signalId));
            if (!Files.exists(path)) {
                return 0;
            }
            return (int) Files.readAllLines(path).stream()
                    .filter(l -> !l.isBlank() && !l.startsWith("#")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * NEU: Speichert die Plattform-Zuordnung (Signal-ID → MT4/MT5), die der
     * KiScanner-Import mitbringt. Der Browser-Export probiert dann sofort den
     * richtigen Export-Typ (MT4: history, MT5: positions) — keine 404-Seite
     * mehr im sichtbaren Chrome und schnellere Läufe.
     *
     * Datei: {CONFIG_DIR}\signalplatforms.txt ("2349227=MT4" je Zeile)
     */
    public static void savePlatformMappings(MqlRealMonitorConfig config,
                                            java.util.Map<String, String> platformen) {
        if (platformen == null || platformen.isEmpty()) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("# Signal-ID = Plattform (MT4/MT5) - vom KiScanner-Import\n");
            for (java.util.Map.Entry<String, String> e : platformen.entrySet()) {
                if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    sb.append(e.getKey()).append('=').append(e.getValue().trim().toUpperCase()).append('\n');
                }
            }
            Files.write(Paths.get(config.getConfigDir(), "signalplatforms.txt"),
                    sb.toString().getBytes(StandardCharsets.UTF_8));
            LOGGER.info("Plattform-Zuordnung gespeichert: " + platformen.size() + " Signale");
        } catch (IOException e) {
            LOGGER.warning("Konnte Plattform-Zuordnung nicht speichern: " + e.getMessage());
        }
    }

    /**
     * NEU: Liest die gespeicherte Plattform-Zuordnung (leer, falls keine existiert)
     */
    public static java.util.Map<String, String> readPlatformMappings(MqlRealMonitorConfig config) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        try {
            Path path = Paths.get(config.getConfigDir(), "signalplatforms.txt");
            if (!Files.exists(path)) {
                return map;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                map.put(parts[0].trim(), parts[1].trim());
            }
        } catch (IOException e) {
            LOGGER.warning("Konnte Plattform-Zuordnung nicht lesen: " + e.getMessage());
        }
        return map;
    }

    /**
     * NEU: Liest die gespeicherte Equity-Kurve (Gewinnkurve) für ein Signal.
     *
     * @return Punkte aufsteigend nach Zeit; leere Liste wenn keine Datei existiert
     */
    public static List<EquityCurveBuilder.EquityPoint> readEquityCurve(
            MqlRealMonitorConfig config, String signalId) {
        return readCurveFile(config.getEquityCurveFilePath(signalId));
    }

    /**
     * NEU: Liest die gespeicherte Trading-Kurve (Startkapital + Netto-Profits,
     * Ein-/Auszahlungen danach herausgerechnet) für ein Signal — Grundlage
     * für die Drawdown-Anzeige.
     */
    public static List<EquityCurveBuilder.EquityPoint> readTradingCurve(
            MqlRealMonitorConfig config, String signalId) {
        return readCurveFile(config.getTradingCurveFilePath(signalId));
    }

    private static List<EquityCurveBuilder.EquityPoint> readCurveFile(String filePath) {
        List<EquityCurveBuilder.EquityPoint> curve = new ArrayList<>();
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return curve;
            }
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(";");
                if (parts.length < 2) {
                    continue;
                }
                try {
                    LocalDateTime time = LocalDateTime.parse(parts[0].trim(), EQUITY_TIME_FMT);
                    curve.add(new EquityCurveBuilder.EquityPoint(time, Double.parseDouble(parts[1].trim())));
                } catch (Exception e) {
                    LOGGER.fine("Equity-Kurve: unlesbare Zeile übersprungen: " + line);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Konnte Kurve " + filePath + " nicht lesen: " + e.getMessage());
        }
        return curve;
    }
}
