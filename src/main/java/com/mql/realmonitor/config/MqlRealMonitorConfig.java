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
 */
public class MqlRealMonitorConfig {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorConfig.class.getName());
    
    // Standard-Pfade
    private static final String BASE_PATH = "C:\\Forex\\MqlAnalyzer";
    private static final String CONFIG_DIR = BASE_PATH + "\\config";  // KORRIGIERT: config statt conf
    private static final String CONFIG_FILE = CONFIG_DIR + "\\MqlRealMonitorConfig.txt";
    private static final String FAVORITES_FILE = BASE_PATH + "\\config\\favorites.txt";
    private static final String DOWNLOAD_DIR = BASE_PATH + "\\Realtick\\download";
    private static final String TICK_DIR = BASE_PATH + "\\Realtick\\tick";
    
    // Standard-Konfigurationswerte - GEÄNDERT: Intervall in Minuten
    private static final int DEFAULT_INTERVAL_MINUTES = 15; // GEÄNDERT: von 1 Stunde zu 15 Minuten
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String DEFAULT_URL_TEMPLATE = "https://www.mql5.com/de/signals/%s?source=Site+Signals+Subscriptions#!tab=account";
    
    // Konfigurationsvariablen - GEÄNDERT: intervalHour → intervalMinutes
    private int intervalMinutes;
    private int timeoutSeconds;
    private String userAgent;
    private String urlTemplate;
    private String basePath;
    private String configDir;
    private String favoritesFile;
    private String downloadDir;
    private String tickDir;
    
    private Properties properties;
    
    public MqlRealMonitorConfig() {
        this.properties = new Properties();
        setDefaultValues();
    }
    
    /**
     * Setzt Standard-Konfigurationswerte
     */
    private void setDefaultValues() {
        this.intervalMinutes = DEFAULT_INTERVAL_MINUTES; // GEÄNDERT
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        this.userAgent = DEFAULT_USER_AGENT;
        this.urlTemplate = DEFAULT_URL_TEMPLATE;
        this.basePath = BASE_PATH;
        this.configDir = CONFIG_DIR;
        this.favoritesFile = FAVORITES_FILE;
        this.downloadDir = DOWNLOAD_DIR;
        this.tickDir = TICK_DIR;
    }
    
    /**
     * Lädt die Konfiguration aus der Datei
     */
    public void loadConfig() {
        try {
            // Verzeichnisse erstellen falls nicht vorhanden
            ensureDirectoriesExist();
            
            File configFile = new File(CONFIG_FILE);
            
            if (configFile.exists()) {
                LOGGER.info("Lade Konfiguration aus: " + CONFIG_FILE);
                
                try (FileInputStream fis = new FileInputStream(configFile)) {
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
            
            File configFile = new File(CONFIG_FILE);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                properties.store(fos, "MqlRealMonitor Konfiguration - Automatisch generiert");
                LOGGER.info("Konfiguration gespeichert: " + CONFIG_FILE);
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
        basePath = properties.getProperty("basePath", BASE_PATH);
        configDir = properties.getProperty("configDir", CONFIG_DIR);
        favoritesFile = properties.getProperty("favoritesFile", FAVORITES_FILE);
        downloadDir = properties.getProperty("downloadDir", DOWNLOAD_DIR);
        tickDir = properties.getProperty("tickDir", TICK_DIR);
    }
    
    /**
     * Aktualisiert Properties aus aktuellen Werten
     * GEÄNDERT: intervalHour → intervalMinutes
     */
    private void updateProperties() {
        properties.setProperty("intervalMinutes", String.valueOf(intervalMinutes)); // GEÄNDERT
        properties.setProperty("timeoutSeconds", String.valueOf(timeoutSeconds));
        properties.setProperty("userAgent", userAgent);
        properties.setProperty("urlTemplate", urlTemplate);
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
     */
    private void logCurrentConfig() {
        LOGGER.info("Aktuelle Konfiguration:");
        LOGGER.info("  Intervall (Minuten): " + intervalMinutes); // GEÄNDERT
        LOGGER.info("  Timeout (Sekunden): " + timeoutSeconds);
        LOGGER.info("  Favoriten-Datei: " + favoritesFile);
        LOGGER.info("  Download-Verzeichnis: " + downloadDir);
        LOGGER.info("  Tick-Verzeichnis: " + tickDir);
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
     * Validiert die aktuelle Konfiguration
     */
    public boolean isValid() {
        return intervalMinutes > 0 && 
               timeoutSeconds > 0 && 
               userAgent != null && !userAgent.trim().isEmpty() &&
               urlTemplate != null && !urlTemplate.trim().isEmpty();
    }
}