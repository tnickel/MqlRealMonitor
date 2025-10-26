package com.mql.realmonitor.downloader;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.logging.Level;

// NEU: Imports für SSL-Bypass
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;

/**
 * Web-Downloader für MQL5 Signalprovider-Seiten
 * Verwaltet HTTP-Downloads und lokale HTML-Dateien
 * 
 * VERBESSERT: Detaillierte Fehlerdiagnostik mit DownloadResult
 * WARNUNG: SSL-Verifikation kann deaktiviert werden (UNSICHER!)
 */
public class WebDownloader {
    
    private static final Logger LOGGER = Logger.getLogger(WebDownloader.class.getName());
    
    // WARNUNG: Auf true setzen deaktiviert SSL-Zertifikatsprüfung (UNSICHER!)
    private static final boolean DISABLE_SSL_VERIFICATION = true;
    
    private final MqlRealMonitorConfig config;
    private static boolean sslInitialized = false;
    
    public WebDownloader(MqlRealMonitorConfig config) {
        this.config = config;
        
        // SSL-Verifikation deaktivieren falls konfiguriert
        if (DISABLE_SSL_VERIFICATION && !sslInitialized) {
            disableSSLVerification();
            sslInitialized = true;
        }
    }
    
    /**
     * WARNUNG: DEAKTIVIERT SSL-ZERTIFIKATSPRÜFUNG KOMPLETT!
     * NUR FÜR ENTWICKLUNG/TESTS - NICHT FÜR PRODUKTION!
     * 
     * Diese Methode macht die HTTPS-Verbindung UNSICHER!
     */
    private void disableSSLVerification() {
        try {
            LOGGER.warning("===========================================");
            LOGGER.warning("WARNUNG: SSL-ZERTIFIKATSPRÜFUNG DEAKTIVIERT!");
            LOGGER.warning("DIESE VERBINDUNG IST NICHT SICHER!");
            LOGGER.warning("NUR FÜR ENTWICKLUNG - NICHT FÜR PRODUKTION!");
            LOGGER.warning("===========================================");
            
            // Trust Manager der ALLE Zertifikate akzeptiert
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Akzeptiert ALLE Client-Zertifikate
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Akzeptiert ALLE Server-Zertifikate
                    }
                }
            };
            
            // SSL Context mit Trust-All Manager installieren
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            // Hostname Verifier der ALLE Hostnamen akzeptiert
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    // Akzeptiert ALLE Hostnamen
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
            LOGGER.warning("SSL-Verifikation wurde deaktiviert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Deaktivieren der SSL-Verifikation", e);
        }
    }
    
    /**
     * VERBESSERT: Lädt eine Signalprovider-Seite herunter mit detaillierter Fehlerdiagnostik
     * 
     * @param signalId Die ID des Signalproviders
     * @param url Die URL zum Herunterladen
     * @return DownloadResult mit Content oder detaillierten Fehlerinformationen
     */
    public DownloadResult downloadSignalPage(String signalId, String url) {
        // Parameter-Validierung
        if (signalId == null || signalId.trim().isEmpty()) {
            LOGGER.warning("Signal-ID ist leer");
            return DownloadResult.invalidParameter("Signal-ID ist leer");
        }
        
        if (url == null || url.trim().isEmpty()) {
            LOGGER.warning("URL ist leer für Signal-ID: " + signalId);
            return DownloadResult.invalidParameter("URL ist leer");
        }
        
        try {
            LOGGER.info("=== DOWNLOAD START: " + signalId + " ===");
            LOGGER.info("URL: " + url);
            
            // Download durchführen
            DownloadResult result = downloadFromUrl(url);
            
            // Ergebnis auswerten und loggen
            if (result.isSuccess()) {
                LOGGER.info("✓ Download erfolgreich: " + signalId + 
                           " (" + result.getContent().length() + " Zeichen)");
            } else {
                LOGGER.warning("✗ Download fehlgeschlagen: " + signalId);
                LOGGER.warning("  Fehlertyp: " + result.getErrorType());
                LOGGER.warning("  HTTP-Code: " + result.getHttpStatusCode());
                LOGGER.warning("  Details: " + result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "✗ Unerwarteter Fehler beim Download von: " + signalId, e);
            return DownloadResult.exception(e, url);
        }
    }
    
    /**
     * VERBESSERT: Lädt Inhalt von einer URL herunter mit detaillierter Fehlerbehandlung
     * 
     * @param urlString Die URL zum Herunterladen
     * @return DownloadResult mit Content oder Fehlerdetails
     */
    private DownloadResult downloadFromUrl(String urlString) {
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
            
            // Anti-Cache Headers für aktuelle Daten
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Expires", "0");
            
            // Zusätzliche Headers um nicht wie Bot zu wirken
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.setRequestProperty("Sec-Fetch-Dest", "document");
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
            connection.setRequestProperty("Sec-Fetch-Site", "none");
            connection.setRequestProperty("Sec-Fetch-User", "?1");
            
            // Random Delay für Currency-URLs
            if (urlString.contains("mql5.com") && urlString.contains("quotes")) {
                try {
                    Thread.sleep(1000 + (int)(Math.random() * 2000)); // 1-3 Sekunden
                    LOGGER.fine("Anti-Bot Delay angewendet für: " + urlString);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Verbindung herstellen
            LOGGER.fine("Verbinde zu: " + urlString);
            connection.connect();
            
            // HTTP-Response-Code prüfen
            int responseCode = connection.getResponseCode();
            LOGGER.info("HTTP Response Code: " + responseCode + " für " + urlString);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Content lesen
                String content = readResponseContent(connection);
                
                // Content-Validierung
                if (content == null || content.trim().isEmpty()) {
                    LOGGER.warning("Leerer Content trotz HTTP 200 OK");
                    return DownloadResult.emptyContent(urlString);
                }
                
                // Debug-Logging für Currency-URLs
                if (urlString.contains("mql5.com") && urlString.contains("quotes")) {
                    LOGGER.info("Currency-URL erfolgreich geladen");
                    LOGGER.info("Content-Länge: " + content.length() + " Zeichen");
                    LOGGER.info("Response Headers: Cache-Control=" + connection.getHeaderField("Cache-Control"));
                    LOGGER.info("Response Headers: Last-Modified=" + connection.getHeaderField("Last-Modified"));
                }
                
                return DownloadResult.success(content);
                
            } else {
                // HTTP-Fehler mit detailliertem Logging
                String responseMessage = connection.getResponseMessage();
                LOGGER.warning("HTTP-Fehler: " + responseCode + " " + responseMessage);
                LOGGER.warning("URL: " + urlString);
                
                // Versuche Error-Stream zu lesen für mehr Details
                try {
                    InputStream errorStream = connection.getErrorStream();
                    if (errorStream != null) {
                        String errorBody = new BufferedReader(new InputStreamReader(errorStream))
                            .lines().limit(5).reduce("", (a, b) -> a + b + "\n");
                        if (!errorBody.isEmpty()) {
                            LOGGER.warning("Error Response Body (first 5 lines): " + errorBody);
                        }
                    }
                } catch (Exception e) {
                    // Ignoriere Fehler beim Lesen des Error-Streams
                }
                
                return DownloadResult.httpError(responseCode, urlString);
            }
            
        } catch (SocketTimeoutException e) {
            LOGGER.log(Level.WARNING, "Timeout beim Download von: " + urlString, e);
            return DownloadResult.timeout(urlString);
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "IOException beim Download von: " + urlString, e);
            return DownloadResult.exception(e, urlString);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unerwartete Exception beim Download von: " + urlString, e);
            return DownloadResult.exception(e, urlString);
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    // ... (Rest der Klasse bleibt unverändert)
    
    /**
     * Liest den Antwort-Inhalt von der HTTP-Verbindung
     */
    private String readResponseContent(HttpURLConnection connection) throws IOException {
        String encoding = connection.getContentEncoding();
        InputStream inputStream;
        
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
    
    public DownloadResult downloadFromWebUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            LOGGER.warning("URL ist leer");
            return DownloadResult.invalidParameter("URL ist leer");
        }
        
        try {
            LOGGER.info("Lade URL: " + url);
            
            DownloadResult result = downloadFromUrl(url);
            
            if (result.isSuccess()) {
                LOGGER.info("URL erfolgreich geladen: " + result.getContent().length() + " Zeichen");
            } else {
                LOGGER.warning("Download fehlgeschlagen: " + result.getDetailedErrorDescription());
            }
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Download der URL: " + url, e);
            return DownloadResult.exception(e, url);
        }
    }
}