package com.mql.realmonitor.downloader;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Web-Downloader für MQL5 Signalprovider-Seiten
 * Verwaltet HTTP-Downloads und lokale HTML-Dateien
 */
public class WebDownloader {
    
    private static final Logger LOGGER = Logger.getLogger(WebDownloader.class.getName());
    
    private final MqlRealMonitorConfig config;
    
    public WebDownloader(MqlRealMonitorConfig config) {
        this.config = config;
    }
    
    /**
     * Lädt eine Signalprovider-Seite herunter und speichert sie lokal
     * 
     * @param signalId Die ID des Signalproviders
     * @return Der HTML-Inhalt oder null bei Fehlern
     */
    public String downloadSignalPage(String signalId, String url) {
        if (signalId == null || signalId.trim().isEmpty()) {
            LOGGER.warning("Signal-ID ist leer");
            return null;
        }
        
        if (url == null || url.trim().isEmpty()) {
            LOGGER.warning("URL ist leer");
            return null;
        }
        
        try {
            LOGGER.info("Lade Seite: " + signalId + " von " + url);
            
            // HTML-Inhalt herunterladen
            String htmlContent = downloadFromUrl(url);
            
            if (htmlContent != null && !htmlContent.trim().isEmpty()) {
                LOGGER.info("Download erfolgreich: " + signalId + " (" + htmlContent.length() + " Zeichen)");
                return htmlContent;
            } else {
                LOGGER.warning("Leerer HTML-Inhalt für: " + signalId);
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Download von: " + signalId, e);
            return null;
        }
    }
    
    /**
     * Lädt Inhalt von einer URL herunter
     * ERWEITERT: Mit Anti-Cache Headers für aktuelle Kursdaten
     * 
     * @param urlString Die URL zum Herunterladen
     * @return Der Inhalt als String oder null bei Fehlern
     */
    private String downloadFromUrl(String urlString) throws IOException {
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // Request-Parameter setzen
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(config.getTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getTimeoutSeconds() * 1000);
            
            // Standard Headers
            connection.setRequestProperty("User-Agent", config.getUserAgent());
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Connection", "keep-alive");
            
            // NEU: Anti-Cache Headers für aktuelle Kursdaten
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Expires", "0");
            
            // NEU: Zusätzliche Headers um nicht wie Bot zu wirken
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.setRequestProperty("Sec-Fetch-Dest", "document");
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
            connection.setRequestProperty("Sec-Fetch-Site", "none");
            connection.setRequestProperty("Sec-Fetch-User", "?1");
            
            // NEU: Random Delay um nicht wie Bot zu wirken (nur für Currency-URLs)
            if (urlString.contains("mql5.com") && urlString.contains("quotes")) {
                try {
                    Thread.sleep(1000 + (int)(Math.random() * 2000)); // 1-3 Sekunden
                    LOGGER.fine("Anti-Bot Delay angewendet für: " + urlString);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Verbindung herstellen
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String content = readResponseContent(connection);
                
                // Debug-Logging für Currency-URLs
                if (urlString.contains("mql5.com") && urlString.contains("quotes")) {
                    LOGGER.info("Currency-URL erfolgreich geladen: " + urlString);
                    LOGGER.info("Content-Länge: " + (content != null ? content.length() : 0) + " Zeichen");
                    LOGGER.info("Response Headers: Cache-Control=" + connection.getHeaderField("Cache-Control"));
                    LOGGER.info("Response Headers: Last-Modified=" + connection.getHeaderField("Last-Modified"));
                }
                
                return content;
            } else {
                LOGGER.warning("HTTP-Fehler " + responseCode + " für URL: " + urlString);
                return null;
            }
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Liest den Antwort-Inhalt von der HTTP-Verbindung
     * 
     * @param connection Die HTTP-Verbindung
     * @return Der Inhalt als String
     */
    private String readResponseContent(HttpURLConnection connection) throws IOException {
        String encoding = connection.getContentEncoding();
        InputStream inputStream;
        
        // GZIP-Dekomprimierung falls nötig
        if ("gzip".equalsIgnoreCase(encoding)) {
            inputStream = new java.util.zip.GZIPInputStream(connection.getInputStream());
        } else {
            inputStream = connection.getInputStream();
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            return content.toString();
        }
    }
    
    /**
     * Speichert HTML-Inhalt in eine lokale Datei
     * 
     * @param htmlContent Der HTML-Inhalt
     * @param filePath Der Dateipfad
     */
    private void saveHtmlToFile(String htmlContent, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        // Verzeichnis erstellen falls nicht vorhanden
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        // HTML-Datei schreiben
        try (FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(htmlContent);
        }
        
        LOGGER.fine("HTML-Datei gespeichert: " + filePath);
    }
    
    /**
     * Löscht eine alte HTML-Datei falls vorhanden
     * 
     * @param filePath Der Dateipfad
     */
    private void deleteOldHtmlFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                LOGGER.fine("Alte HTML-Datei gelöscht: " + filePath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte alte HTML-Datei nicht löschen: " + filePath, e);
        }
    }
    
    /**
     * Lädt eine bereits heruntergeladene HTML-Datei
     * 
     * @param signalId Die Signal-ID
     * @return Der HTML-Inhalt oder null falls Datei nicht existiert
     */
    public String loadLocalHtmlFile(String signalId) {
        String filePath = config.getDownloadFilePath(signalId);
        
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                LOGGER.fine("Lokale HTML-Datei geladen: " + filePath);
                return content;
            } else {
                LOGGER.fine("Lokale HTML-Datei nicht gefunden: " + filePath);
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Fehler beim Laden der lokalen HTML-Datei: " + filePath, e);
            return null;
        }
    }
    
    /**
     * Bereinigt alte HTML-Dateien aus dem Download-Verzeichnis
     * 
     * @param olderThanHours Dateien älter als X Stunden löschen
     */
    public void cleanupOldHtmlFiles(int olderThanHours) {
        try {
            Path downloadDir = Paths.get(config.getDownloadDir());
            
            if (!Files.exists(downloadDir)) {
                return;
            }
            
            long cutoffTime = System.currentTimeMillis() - (olderThanHours * 60 * 60 * 1000L);
            int deletedCount = 0;
            
            try (var stream = Files.list(downloadDir)) {
                var htmlFiles = stream
                    .filter(path -> path.toString().toLowerCase().endsWith(".html"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
                
                for (Path file : htmlFiles) {
                    try {
                        Files.delete(file);
                        deletedCount++;
                        LOGGER.fine("Alte HTML-Datei gelöscht: " + file.getFileName());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Konnte Datei nicht löschen: " + file, e);
                    }
                }
            }
            
            if (deletedCount > 0) {
                LOGGER.info("Bereinigung abgeschlossen: " + deletedCount + " alte HTML-Dateien gelöscht");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler bei der Bereinigung alter HTML-Dateien", e);
        }
    }
    
    /**
     * Prüft ob eine URL erreichbar ist
     * 
     * @param urlString Die zu prüfende URL
     * @return true wenn erreichbar, false sonst
     */
    public boolean isUrlReachable(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", config.getUserAgent());
            
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "URL nicht erreichbar: " + urlString, e);
            return false;
        }
    }
    
    /**
     * Lädt Inhalt von einer beliebigen URL herunter (öffentliche Methode)
     * 
     * @param url Die URL zum Herunterladen
     * @return HTML-Content oder null bei Fehlern
     */
    public String downloadFromWebUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            LOGGER.warning("URL ist leer");
            return null;
        }
        
        try {
            LOGGER.info("Lade URL: " + url);
            
            String htmlContent = downloadFromUrl(url);
            
            if (htmlContent != null && !htmlContent.trim().isEmpty()) {
                LOGGER.info("URL erfolgreich geladen: " + htmlContent.length() + " Zeichen");
                return htmlContent;
            } else {
                LOGGER.warning("Leerer HTML-Inhalt von URL: " + url);
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Download der URL: " + url, e);
            return null;
        }
    }
}