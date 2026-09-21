package com.mql.realmonitor.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Konfigurationsverwaltung für MqlRealMonitor
 * Verwaltet alle konfigurierbaren Parameter und Pfade
 * GEÄNDERT: Intervall jetzt in Minuten statt Stunden
 * NEU: Konfigurierbarer BASE_PATH über Konstruktor-Parameter
 */
public class MqlRealMonitorConfig {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorConfig.class.getName());
    
    // Standard BASE_PATH (falls nicht anders angegeben)
    private static final String DEFAULT_BASE_PATH = "C:\\Forex\\MqlAnalyzer";
    
    // Standard-Konfigurationswerte - GEÄNDERT: Intervall in Minuten
    private static final int DEFAULT_INTERVAL_MINUTES = 15; // GEÄNDERT: von 1 Stunde zu 15 Minuten
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String DEFAULT_URL_TEMPLATE = "https://www.mql5.com/de/signals/%s?source=Site+Signals+Subscriptions#!tab=account";
    
    // NEU: MqlKiScanner REST-Interface (schreibgeschützt, läuft lokal neben der Streamlit-App)
    private static final String DEFAULT_KISCANNER_URL = "http://127.0.0.1:8611";

    // NEU: Simulator-Defaults (Startdatum und Startkapital je Strategie)
    private static final String DEFAULT_SIMULATOR_START_DATE = "2026-09-01";
    private static final double DEFAULT_SIMULATOR_START_CAPITAL = 10000.0;
    
    // Konfigurationsvariablen - GEÄNDERT: intervalHour → intervalMinutes
    private int intervalMinutes;
    private int timeoutSeconds;
    private String userAgent;
    private String urlTemplate;
    
    // NEU: Base-URL des MqlKiScanner REST-Interface (leer = nicht konfiguriert)
    private String kiScannerBaseUrl;
    
    // NEU: Optionaler Zugriffs-Token für das KiScanner REST-Interface (X-User-Key).
    // Leer = kein Token nötig. Der Wert wird bewusst NIE geloggt.
    private String kiScannerToken;

    // NEU: Simulator — Startdatum (yyyy-MM-dd) und Startkapital je Strategie
    private String simulatorStartDate;
    private double simulatorStartCapital;
    
    // NEU: Dynamische Pfade basierend auf konfigurierbarem BASE_PATH
    private String basePath;
    private String configDir;
    private String configFile;
    private String favoritesFile;
    private String downloadDir;
    private String tickDir;
    private String tradesDir;   // NEU: Trade-Historie von MQL5 (Realtick\trades)
    
    private Properties properties;
    
    /**
     * Standard-Konstruktor mit Default-Pfad
     */
    public MqlRealMonitorConfig() {
        this(DEFAULT_BASE_PATH);
    }
    
    /**
     * NEU: Konstruktor mit konfigurierbarem BASE_PATH
     * 
     * @param basePath Der zu verwendende Basis-Pfad
     */
    public MqlRealMonitorConfig(String basePath) {
        this.properties = new Properties();
        
        // NEU: Pfade dynamisch basierend auf basePath setzen
        setBasePath(basePath);
        setDefaultValues();
        
        LOGGER.info("MqlRealMonitorConfig initialisiert mit BASE_PATH: " + this.basePath);
    }
    
    /**
     * NEU: Setzt den Basis-Pfad und berechnet alle abhängigen Pfade neu
     * 
     * @param basePath Der neue Basis-Pfad
     */
    private void setBasePath(String basePath) {
        // Basis-Pfad validieren und normalisieren
        if (basePath == null || basePath.trim().isEmpty()) {
            LOGGER.warning("Ungültiger BASE_PATH übergeben, verwende Standard: " + DEFAULT_BASE_PATH);
            basePath = DEFAULT_BASE_PATH;
        }
        
        // Pfad normalisieren (Backslashes für Windows)
        this.basePath = basePath.trim().replace("/", "\\");
        
        // Alle abhängigen Pfade berechnen
        this.configDir = this.basePath + "\\config";
        this.configFile = this.configDir + "\\MqlRealMonitorConfig.txt";
        this.favoritesFile = this.configDir + "\\favorites.txt";
        this.downloadDir = this.basePath + "\\Realtick\\download";
        this.tickDir = this.basePath + "\\Realtick\\tick";
        this.tradesDir = this.basePath + "\\Realtick\\trades";
        
        LOGGER.info("Pfade neu berechnet:");
        LOGGER.info("  BASE_PATH: " + this.basePath);
        LOGGER.info("  CONFIG_DIR: " + this.configDir);
        LOGGER.info("  FAVORITES_FILE: " + this.favoritesFile);
        LOGGER.info("  DOWNLOAD_DIR: " + this.downloadDir);
        LOGGER.info("  TICK_DIR: " + this.tickDir);
    }
    
    /**
     * Setzt Standard-Konfigurationswerte
     */
    private void setDefaultValues() {
        this.intervalMinutes = DEFAULT_INTERVAL_MINUTES; // GEÄNDERT
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        this.userAgent = DEFAULT_USER_AGENT;
        this.urlTemplate = DEFAULT_URL_TEMPLATE;
        this.kiScannerBaseUrl = DEFAULT_KISCANNER_URL;
        this.kiScannerToken = "";
        this.simulatorStartDate = DEFAULT_SIMULATOR_START_DATE;
        this.simulatorStartCapital = DEFAULT_SIMULATOR_START_CAPITAL;
    }
    
    /**
     * Lädt die Konfiguration aus der Datei
     */
    public void loadConfig() {
        try {
            // Verzeichnisse erstellen falls nicht vorhanden
            ensureDirectoriesExist();
            
            File configFileObj = new File(configFile);
            
            if (configFileObj.exists()) {
                LOGGER.info("Lade Konfiguration aus: " + configFile);
                
                try (FileInputStream fis = new FileInputStream(configFileObj)) {
                    properties.load(fis);
                    parseProperties();
                    LOGGER.info("Konfiguration erfolgreich geladen");
                }
                
            } else {
                LOGGER.info("Konfigurationsdatei nicht gefunden, erstelle Standard-Konfiguration");
                saveConfig();
            }
            
            logCurrentConfig();
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Laden der Konfiguration, verwende Standardwerte", e);
            setDefaultValues();
        }
    }
    
    /**
     * Speichert die aktuelle Konfiguration in die Datei
     */
    public void saveConfig() {
        try {
            ensureDirectoriesExist();
            
            // Properties aus aktuellen Werten setzen
            updateProperties();
            
            File configFileObj = new File(configFile);
            try (FileOutputStream fos = new FileOutputStream(configFileObj)) {
                properties.store(fos, "MqlRealMonitor Konfiguration - BASE_PATH: " + basePath);
                LOGGER.info("Konfiguration gespeichert: " + configFile);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Speichern der Konfiguration", e);
        }
    }
    
    /**
     * Parst die Properties und setzt die Konfigurationswerte
     * GEÄNDERT: intervalHour → intervalMinutes mit Rückwärtskompatibilität
     */
    private void parseProperties() {
        // Rückwärtskompatibilität: Prüfe erst auf neue intervalMinutes, dann auf alte intervalHour
        if (properties.containsKey("intervalMinutes")) {
            intervalMinutes = getIntProperty("intervalMinutes", DEFAULT_INTERVAL_MINUTES);
        } else if (properties.containsKey("intervalHour")) {
            // Konvertiere alte Stunden-Werte zu Minuten
            int oldHours = getIntProperty("intervalHour", 1);
            intervalMinutes = oldHours * 60;
            LOGGER.info("Konvertiere alten Stunden-Wert (" + oldHours + "h) zu Minuten: " + intervalMinutes + " min");
        } else {
            intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        }
        
        timeoutSeconds = getIntProperty("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        userAgent = properties.getProperty("userAgent", DEFAULT_USER_AGENT);
        urlTemplate = properties.getProperty("urlTemplate", DEFAULT_URL_TEMPLATE);
        
        // NEU: MqlKiScanner REST-Base-URL (Rückwärtskompatibel: Default, wenn nicht gesetzt)
        kiScannerBaseUrl = properties.getProperty("kiscannerBaseUrl", DEFAULT_KISCANNER_URL).trim();
        if (kiScannerBaseUrl.isEmpty()) {
            kiScannerBaseUrl = DEFAULT_KISCANNER_URL;
        }
        
        // NEU: Optionaler Zugriffs-Token (leer = kein Token)
        kiScannerToken = properties.getProperty("kiscannerToken", "").trim();

        // NEU: Simulator-Parameter
        simulatorStartDate = properties.getProperty("simulatorStartDate", DEFAULT_SIMULATOR_START_DATE).trim();
        if (simulatorStartDate.isEmpty()) {
            simulatorStartDate = DEFAULT_SIMULATOR_START_DATE;
        }
        try {
            simulatorStartCapital = Double.parseDouble(
                    properties.getProperty("simulatorStartCapital",
                            String.valueOf(DEFAULT_SIMULATOR_START_CAPITAL)).trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Ungültiger simulatorStartCapital, verwende Standard: " + DEFAULT_SIMULATOR_START_CAPITAL);
            simulatorStartCapital = DEFAULT_SIMULATOR_START_CAPITAL;
        }
        if (simulatorStartCapital <= 0) {
            simulatorStartCapital = DEFAULT_SIMULATOR_START_CAPITAL;
        }
        
        // NEU: BASE_PATH aus Properties laden (falls dort gespeichert)
        String savedBasePath = properties.getProperty("basePath");
        if (savedBasePath != null && !savedBasePath.equals(basePath)) {
            LOGGER.info("BASE_PATH in Properties unterscheidet sich von aktuell verwendetem:");
            LOGGER.info("  Aktuell: " + basePath);
            LOGGER.info("  Properties: " + savedBasePath);
            LOGGER.info("  Verwende aktuellen BASE_PATH (nicht aus Properties)");
        }
    }
    
    /**
     * Aktualisiert Properties aus aktuellen Werten
     * GEÄNDERT: intervalHour → intervalMinutes
     * NEU: Speichert auch BASE_PATH für Referenz
     */
    private void updateProperties() {
        properties.setProperty("intervalMinutes", String.valueOf(intervalMinutes)); // GEÄNDERT
        properties.setProperty("timeoutSeconds", String.valueOf(timeoutSeconds));
        properties.setProperty("userAgent", userAgent);
        properties.setProperty("urlTemplate", urlTemplate);
        
        // NEU: MqlKiScanner REST-Base-URL speichern
        properties.setProperty("kiscannerBaseUrl", kiScannerBaseUrl);
        
        // NEU: Zugriffs-Token speichern (Wert fließt nur in die Config-Datei, nie ins Log)
        properties.setProperty("kiscannerToken", kiScannerToken);

        // NEU: Simulator-Parameter speichern
        properties.setProperty("simulatorStartDate", simulatorStartDate);
        properties.setProperty("simulatorStartCapital", String.valueOf(simulatorStartCapital));
        
        // NEU: BASE_PATH für Referenz speichern (wird aber nicht beim Laden verwendet)
        properties.setProperty("basePath", basePath);
        properties.setProperty("configDir", configDir);
        properties.setProperty("favoritesFile", favoritesFile);
        properties.setProperty("downloadDir", downloadDir);
        properties.setProperty("tickDir", tickDir);
        
        // Alte intervalHour Property entfernen falls vorhanden
        properties.remove("intervalHour");
    }
    
    /**
     * Hilfsmethode zum Lesen von Integer-Properties
     */
    private int getIntProperty(String key, int defaultValue) {
        try {
            String value = properties.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOGGER.warning("Ungültiger Wert für " + key + ", verwende Standard: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Stellt sicher, dass alle benötigten Verzeichnisse existieren
     */
    private void ensureDirectoriesExist() throws IOException {
        createDirectoryIfNotExists(configDir);
        createDirectoryIfNotExists(downloadDir);
        createDirectoryIfNotExists(tickDir);
        createDirectoryIfNotExists(tradesDir);
        createDirectoryIfNotExists(Paths.get(favoritesFile).getParent().toString());
    }
    
    /**
     * Erstellt ein Verzeichnis falls es nicht existiert
     */
    private void createDirectoryIfNotExists(String dirPath) throws IOException {
        Path path = Paths.get(dirPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            LOGGER.info("Verzeichnis erstellt: " + dirPath);
        }
    }
    
    /**
     * Loggt die aktuelle Konfiguration
     * GEÄNDERT: Zeigt Minuten statt Stunden
     * NEU: Zeigt auch alle Pfade
     */
    private void logCurrentConfig() {
        LOGGER.info("=== AKTUELLE KONFIGURATION ===");
        LOGGER.info("  BASE_PATH: " + basePath);
        LOGGER.info("  Intervall (Minuten): " + intervalMinutes); // GEÄNDERT
        LOGGER.info("  Timeout (Sekunden): " + timeoutSeconds);
        LOGGER.info("  Favoriten-Datei: " + favoritesFile);
        LOGGER.info("  Download-Verzeichnis: " + downloadDir);
        LOGGER.info("  Tick-Verzeichnis: " + tickDir);
        LOGGER.info("  Config-Datei: " + configFile);
        LOGGER.info("  KiScanner REST-URL: " + kiScannerBaseUrl);
        LOGGER.info("  KiScanner Token: " + (kiScannerToken.isEmpty() ? "nicht gesetzt" : "gesetzt (Wert wird nicht geloggt)"));
        LOGGER.info("===============================");
    }
    
    // Getter-Methoden - GEÄNDERT: intervalHour → intervalMinutes
    
    /**
     * GEÄNDERT: Gibt das Intervall in Minuten zurück (vorher Stunden)
     */
    public int getIntervalMinutes() {
        return intervalMinutes;
    }
    
    /**
     * DEPRECATED: Für Rückwärtskompatibilität - verwende getIntervalMinutes()
     */
    @Deprecated
    public int getIntervalHour() {
        return Math.max(1, intervalMinutes / 60); // Mindestens 1 Stunde
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public String getUrlTemplate() {
        return urlTemplate;
    }

    /**
     * NEU: Setzt das URL-Template (muss "%s" für die Signal-ID enthalten)
     */
    public void setUrlTemplate(String urlTemplate) {
        if (urlTemplate != null && urlTemplate.trim().contains("%s")) {
            this.urlTemplate = urlTemplate.trim();
        }
    }
    
    /**
     * NEU: Gibt die Base-URL des MqlKiScanner REST-Interface zurück
     * (z. B. http://127.0.0.1:8611)
     */
    public String getKiScannerBaseUrl() {
        return kiScannerBaseUrl;
    }
    
    /**
     * NEU: Setzt die Base-URL des MqlKiScanner REST-Interface
     */
    public void setKiScannerBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            // Abschließenden Slash entfernen für saubere URL-Komposition
            this.kiScannerBaseUrl = url.trim().replaceAll("/+$", "");
        }
    }
    
    /**
     * NEU: Optionaler Zugriffs-Token für das KiScanner REST-Interface.
     * Wird als X-User-Key Header gesendet; leer = kein Token.
     */
    public String getKiScannerToken() {
        return kiScannerToken;
    }
    
    public void setKiScannerToken(String token) {
        this.kiScannerToken = token != null ? token.trim() : "";
    }
    
    /**
     * NEU: Ist ein KiScanner-Zugriffs-Token konfiguriert?
     */
    public boolean hasKiScannerToken() {
        return kiScannerToken != null && !kiScannerToken.isEmpty();
    }

    // ------------------------------------------------------------ Simulator

    /**
     * NEU: Simulator-Startdatum als String (yyyy-MM-dd)
     */
    public String getSimulatorStartDate() {
        return simulatorStartDate;
    }

    /**
     * NEU: Simulator-Startdatum geparst; ungültige/leere Werte fallen auf den
     * ersten des Vormonats zurück (damit startet die GUI immer)
     */
    public java.time.LocalDate getSimulatorStartDateParsed() {
        try {
            return java.time.LocalDate.parse(simulatorStartDate,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return java.time.LocalDate.now().withDayOfMonth(1).minusMonths(1);
        }
    }

    public void setSimulatorStartDate(String date) {
        if (date != null && date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            this.simulatorStartDate = date.trim();
        }
    }

    /**
     * NEU: Startkapital je Strategie für den Simulator
     */
    public double getSimulatorStartCapital() {
        return simulatorStartCapital;
    }

    public void setSimulatorStartCapital(double capital) {
        if (capital > 0) {
            this.simulatorStartCapital = capital;
        }
    }
    
    /**
     * NEU: Baut die REST-URL für die Signalliste des MqlKiScanner.
     * Ampel-Filter gruen/gelb serverseitig — der Client filtert zusätzlich
     * defensiv nochmal selbst.
     * 
     * @return URL z. B. http://127.0.0.1:8611/api/v1/signals?ampel=gruen%2Cgelb
     */
    public String buildKiScannerSignalsUrl() {
        return kiScannerBaseUrl + "/api/v1/signals?ampel=gruen,gelb";
    }
    
    /**
     * NEU: Baut die REST-URL für den Health-Check des MqlKiScanner
     */
    public String buildKiScannerHealthUrl() {
        return kiScannerBaseUrl + "/api/v1/health";
    }
    
    public String getBasePath() {
        return basePath;
    }
    
    public String getConfigDir() {
        return configDir;
    }
    
    public String getFavoritesFile() {
        return favoritesFile;
    }
    
    public String getDownloadDir() {
        return downloadDir;
    }
    
    public String getTickDir() {
        return tickDir;
    }
    
    /**
     * NEU: Gibt den Pfad zur Config-Datei zurück
     */
    public String getConfigFile() {
        return configFile;
    }
    
    /**
     * Erstellt die vollständige URL für eine Signal-ID
     */
    public String buildSignalUrl(String signalId) {
        return String.format(urlTemplate, signalId);
    }
    
    /**
     * Erstellt den Pfad für die HTML-Download-Datei
     */
    public String getDownloadFilePath(String signalId) {
        return downloadDir + "\\" + signalId + ".html";
    }
    
    /**
     * Erstellt den Pfad für die Tick-Datei
     */
    public String getTickFilePath(String signalId) {
        return tickDir + "\\" + signalId + ".txt";
    }

    /**
     * NEU: Gibt das Verzeichnis für die MQL5-Trade-Historie zurück
     */
    public String getTradesDir() {
        return tradesDir;
    }

    /**
     * NEU: Pfad zum gespeicherten Trade-Export (Roh-CSV von MQL5)
     */
    public String getTradesFilePath(String signalId) {
        return tradesDir + "\\" + signalId + "_positions.csv";
    }

    /**
     * NEU: Pfad zur rekonstruierten Equity-Kurve (aus dem Trade-Export)
     */
    public String getEquityCurveFilePath(String signalId) {
        return tradesDir + "\\" + signalId + "_equity.csv";
    }

    /**
     * NEU: Pfad zur Trading-Kurve (Startkapital + Netto-Profits; Ein-/Auszahlungen
     * nach Handelsbeginn sind herausgerechnet — für die Drawdown-Anzeige
     * "wie weit geht es im schlimmsten Fall runter")
     */
    public String getTradingCurveFilePath(String signalId) {
        return tradesDir + "\\" + signalId + "_trading.csv";
    }
    
    // Setter-Methoden für Konfigurationsänderungen - GEÄNDERT
    
    /**
     * GEÄNDERT: Setzt das Intervall in Minuten (vorher Stunden)
     */
    public void setIntervalMinutes(int intervalMinutes) {
        if (intervalMinutes > 0) {
            this.intervalMinutes = intervalMinutes;
        }
    }
    
    /**
     * DEPRECATED: Für Rückwärtskompatibilität - verwende setIntervalMinutes()
     */
    @Deprecated
    public void setIntervalHour(int intervalHour) {
        if (intervalHour > 0) {
            this.intervalMinutes = intervalHour * 60;
        }
    }
    
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds > 0) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
    
    public void setUserAgent(String userAgent) {
        if (userAgent != null && !userAgent.trim().isEmpty()) {
            this.userAgent = userAgent;
        }
    }
    
    /**
     * NEU: Erlaubt nachträgliche Änderung des BASE_PATH
     * ACHTUNG: Bereits geladene Konfiguration wird neu eingelesen!
     * 
     * @param newBasePath Der neue BASIS-Pfad
     */
    public void changeBasePath(String newBasePath) {
        LOGGER.info("=== BASE_PATH WIRD GEÄNDERT ===");
        LOGGER.info("  Alt: " + this.basePath);
        LOGGER.info("  Neu: " + newBasePath);
        
        setBasePath(newBasePath);
        
        // Konfiguration aus neuem Pfad laden
        loadConfig();
        
        LOGGER.info("=== BASE_PATH ERFOLGREICH GEÄNDERT ===");
    }
    
    /**
     * Validiert die aktuelle Konfiguration
     */
    public boolean isValid() {
        boolean valid = intervalMinutes > 0 && 
                       timeoutSeconds > 0 && 
                       userAgent != null && !userAgent.trim().isEmpty() &&
                       urlTemplate != null && !urlTemplate.trim().isEmpty() &&
                       basePath != null && !basePath.trim().isEmpty();
        
        if (!valid) {
            LOGGER.warning("Konfiguration ist ungültig!");
            LOGGER.warning("  intervalMinutes: " + intervalMinutes);
            LOGGER.warning("  timeoutSeconds: " + timeoutSeconds);
            LOGGER.warning("  userAgent: " + (userAgent != null ? "OK" : "NULL"));
            LOGGER.warning("  urlTemplate: " + (urlTemplate != null ? "OK" : "NULL"));
            LOGGER.warning("  basePath: " + (basePath != null ? "OK" : "NULL"));
        }
        
        return valid;
    }
    
    /**
     * NEU: Gibt eine Zusammenfassung der aktuellen Konfiguration zurück
     */
    public String getConfigSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("MqlRealMonitorConfig Summary:\n");
        summary.append("  BASE_PATH: ").append(basePath).append("\n");
        summary.append("  Intervall: ").append(intervalMinutes).append(" Minuten\n");
        summary.append("  Timeout: ").append(timeoutSeconds).append(" Sekunden\n");
        summary.append("  Favoriten-Datei: ").append(favoritesFile).append("\n");
        summary.append("  KiScanner REST-URL: ").append(kiScannerBaseUrl).append("\n");
        summary.append("  Verzeichnisse: config, download, tick alle unter BASE_PATH\n");
        summary.append("  Gültig: ").append(isValid() ? "JA" : "NEIN");
        
        return summary.toString();
    }
}