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
 */
public class MqlRealMonitorConfig {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorConfig.class.getName());
    
    // Standard-Pfade
    private static final String BASE_PATH = "C:\\Forex\\MqlAnalyzer";
    private static final String CONFIG_DIR = BASE_PATH + "\\conf";
    private static final String CONFIG_FILE = CONFIG_DIR + "\\MqlRealMonitorConfig.txt";
    private static final String FAVORITES_FILE = BASE_PATH + "\\config\\favorites.txt";
    private static final String DOWNLOAD_DIR = BASE_PATH + "\\Realtick\\download";
    private static final String TICK_DIR = BASE_PATH + "\\Realtick\\tick";
    
    // Standard-Konfigurationswerte
    private static final int DEFAULT_INTERVAL_HOUR = 1;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String DEFAULT_URL_TEMPLATE = "https://www.mql5.com/de/signals/%s?source=Site+Signals+Subscriptions#!tab=account";
    
    // Konfigurationsvariablen
    private int intervalHour;
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
        this.intervalHour = DEFAULT_INTERVAL_HOUR;
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
     */
    private void parseProperties() {
        intervalHour = getIntProperty("intervalHour", DEFAULT_INTERVAL_HOUR);
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
     */
    private void updateProperties() {
        properties.setProperty("intervalHour", String.valueOf(intervalHour));
        properties.setProperty("timeoutSeconds", String.valueOf(timeoutSeconds));
        properties.setProperty("userAgent", userAgent);
        properties.setProperty("urlTemplate", urlTemplate);
        properties.setProperty("basePath", basePath);
        properties.setProperty("configDir", configDir);
        properties.setProperty("favoritesFile", favoritesFile);
        properties.setProperty("downloadDir", downloadDir);
        properties.setProperty("tickDir", tickDir);
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
     */
    private void logCurrentConfig() {
        LOGGER.info("Aktuelle Konfiguration:");
        LOGGER.info("  Intervall (Stunden): " + intervalHour);
        LOGGER.info("  Timeout (Sekunden): " + timeoutSeconds);
        LOGGER.info("  Favoriten-Datei: " + favoritesFile);
        LOGGER.info("  Download-Verzeichnis: " + downloadDir);
        LOGGER.info("  Tick-Verzeichnis: " + tickDir);
    }
    
    // Getter-Methoden
    
    public int getIntervalHour() {
        return intervalHour;
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
    
    // Setter-Methoden für Konfigurationsänderungen
    
    public void setIntervalHour(int intervalHour) {
        if (intervalHour > 0) {
            this.intervalHour = intervalHour;
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
        return intervalHour > 0 && 
               timeoutSeconds > 0 && 
               userAgent != null && !userAgent.trim().isEmpty() &&
               urlTemplate != null && !urlTemplate.trim().isEmpty();
    }
}