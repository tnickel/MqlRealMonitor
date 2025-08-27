package com.mql.realmonitor.currency;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.downloader.WebDownloader;
import com.mql.realmonitor.exception.MqlMonitorException;
import com.mql.realmonitor.exception.MqlMonitorException.ErrorType;
import com.mql.realmonitor.utils.MqlUtils;

/**
 * Hauptklasse für das Laden von Währungskursen von MQL5.
 * Orchestriert den kompletten Prozess: Download → Parse → Save
 */
public class CurrencyDataLoader {
    
    private static final Logger logger = Logger.getLogger(CurrencyDataLoader.class.getName());
    
    private static final String MQL5_URL = "https://www.mql5.com";
    private static final String MQL5_RATES_URL = "https://www.mql5.com/en/quotes";
    
    private final MqlRealMonitorConfig config;
    private final WebDownloader webDownloader;
    private final CurrencyParser currencyParser;
    private final CurrencyDataWriter currencyDataWriter;
    private final String downloadMql5Dir;
    
    /**
     * Konstruktor für CurrencyDataLoader.
     * 
     * @param config Konfiguration für Pfade und Einstellungen
     */
    public CurrencyDataLoader(MqlRealMonitorConfig config) {
        this.config = config;
        this.webDownloader = new WebDownloader(config);
        this.currencyParser = new CurrencyParser();
        this.currencyDataWriter = new CurrencyDataWriter(config);
        this.downloadMql5Dir = config.getBasePath() + "/realtick/download_mql5";
        
        // Verzeichnis erstellen falls nicht vorhanden
        createDirectoryIfNotExists(downloadMql5Dir);
    }
    
    /**
     * Lädt aktuelle Währungskurse von MQL5 und speichert sie.
     * 
     * @return Anzahl der erfolgreich geladenen und gespeicherten Kurse
     * @throws MqlMonitorException bei Fehlern im Prozess
     */
    public int loadAndSaveCurrencyRates() throws MqlMonitorException {
        logger.info("=== Starte Währungskurs-Loading von MQL5 ===");
        
        try {
            // Schritt 1: HTML von MQL5 herunterladen
            logger.info("Schritt 1/3: Lade HTML von MQL5...");
            String htmlContent = downloadMql5Html();
            
            // Schritt 2: Kurse aus HTML extrahieren
            logger.info("Schritt 2/3: Parse Währungskurse...");
            List<CurrencyData> currencyRates = currencyParser.parseRates(htmlContent);
            
            // Schritt 3: Kurse speichern
            logger.info("Schritt 3/3: Speichere Kursdaten...");
            currencyDataWriter.writeCurrencyData(currencyRates);
            
            logger.info("=== Währungskurs-Loading erfolgreich abgeschlossen ===");
            logger.info("Geladene Kurse: " + currencyRates.size());
            
            // Log der geladenen Kurse
            for (CurrencyData rate : currencyRates) {
                logger.info("  " + rate.getSymbol() + ": " + rate.getPrice());
            }
            
            return currencyRates.size();
            
        } catch (Exception e) {
            logger.severe("Fehler beim Laden der Währungskurse: " + e.getMessage());
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "Währungskurs-Loading fehlgeschlagen: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lädt HTML-Content von MQL5 herunter.
     * 
     * @return HTML-Content als String
     * @throws MqlMonitorException bei Download-Fehlern
     */
    private String downloadMql5Html() throws MqlMonitorException {
        try {
            // Zuerst versuchen wir die Rates-Seite
            String htmlContent = downloadFromUrl(MQL5_RATES_URL);
            
            // Falls Rates-Seite nicht funktioniert, Haupt-URL versuchen
            if (htmlContent == null || htmlContent.trim().isEmpty() || 
                htmlContent.length() < 1000) {
                logger.info("Rates-URL lieferte wenig Content, versuche Haupt-URL...");
                htmlContent = downloadFromUrl(MQL5_URL);
            }
            
            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                throw new MqlMonitorException(ErrorType.PARSING_ERROR, "MQL5 HTML-Content ist leer");
            }
            
            logger.info("HTML erfolgreich geladen: " + htmlContent.length() + " Zeichen");
            
            // HTML in Datei speichern für Debugging
            saveHtmlToFile(htmlContent);
            
            return htmlContent;
            
        } catch (Exception e) {
            logger.severe("Fehler beim Download von MQL5: " + e.getMessage());
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "MQL5 Download fehlgeschlagen: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lädt HTML von einer spezifischen URL herunter.
     * 
     * @param url Die URL zum Herunterladen
     * @return HTML-Content oder null bei Fehlern
     */
    private String downloadFromUrl(String url) {
        try {
        	logger.info("Lade HTML von: " + url);
            
            // WebDownloader gibt direkt HTML-Content zurück, NICHT einen Dateipfad!
            String htmlContent = webDownloader.downloadFromWebUrl(url);
            
            if (htmlContent != null && !htmlContent.trim().isEmpty()) {
            	logger.info("Download erfolgreich: " + htmlContent.length() + " Zeichen");
                return htmlContent;
            } else {
            	logger.warning("Download von " + url + " lieferte keinen Content");
                return null;
            }
            
        } catch (Exception e) {
        	logger.warning("Fehler beim Download von " + url + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Speichert HTML-Content in Datei für Debugging.
     * 
     * @param htmlContent Der zu speichernde HTML-Content
     */
    private void saveHtmlToFile(String htmlContent) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = downloadMql5Dir + "/mql5_rates_" + timestamp + ".html";
            
            MqlUtils.writeTextFile(filename, htmlContent, false);
            logger.info("HTML gespeichert für Debugging: " + filename);
            
        } catch (Exception e) {
            logger.warning("Fehler beim Speichern der HTML-Datei: " + e.getMessage());
        }
    }
    /**
     * Lädt Inhalt von einer URL herunter
     * 
     * @param urlString Die URL zum Herunterladen
     * @return Der Inhalt als String oder null bei Fehlern
     */
   
    /**
     * Lädt Währungskurse und gibt Diagnose-Informationen zurück.
     * 
     * @return Diagnose-String für UI-Anzeige
     */
    public String loadCurrencyRatesWithDiagnosis() {
        try {
            int loadedRates = loadAndSaveCurrencyRates();
            
            StringBuilder diagnosis = new StringBuilder();
            diagnosis.append("Währungskurs-Loading erfolgreich!\n");
            diagnosis.append("Geladene Kurse: ").append(loadedRates).append("\n\n");
            
            // File-Informationen anhängen
            List<String> fileInfo = currencyDataWriter.getCurrencyFileInfo();
            diagnosis.append("Gespeicherte Kursdateien:\n");
            for (String info : fileInfo) {
                diagnosis.append("  ").append(info).append("\n");
            }
            
            return diagnosis.toString();
            
        } catch (MqlMonitorException e) {
            logger.severe("Währungskurs-Loading fehlgeschlagen: " + e.getMessage());
            
            StringBuilder diagnosis = new StringBuilder();
            diagnosis.append("FEHLER beim Währungskurs-Loading!\n");
            diagnosis.append("Fehler: ").append(e.getMessage()).append("\n\n");
            
            // Diagnose-Informationen für Debugging
            try {
                String htmlContent = downloadMql5Html();
                if (htmlContent != null) {
                    List<String> references = currencyParser.findCurrencyReferences(htmlContent);
                    diagnosis.append("Gefundene Währungsreferenzen im HTML:\n");
                    for (String ref : references) {
                        diagnosis.append("  ").append(ref).append("\n");
                    }
                }
            } catch (Exception debugEx) {
                diagnosis.append("Fehler auch bei Diagnose: ").append(debugEx.getMessage());
            }
            
            return diagnosis.toString();
        }
    }
    
    /**
     * Gibt Statistiken über gespeicherte Kursdaten zurück.
     * 
     * @return Statistik-String
     */
    public String getCurrencyDataStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Währungskurs-Statistiken ===\n\n");
        
        List<String> fileInfo = currencyDataWriter.getCurrencyFileInfo();
        if (fileInfo.isEmpty()) {
            stats.append("Keine Kursdateien gefunden.\n");
        } else {
            for (String info : fileInfo) {
                stats.append(info).append("\n");
            }
        }
        
        return stats.toString();
    }
    
    /**
     * Erstellt ein Verzeichnis falls es nicht existiert.
     * 
     * @param dirPath Pfad zum Verzeichnis
     */
    private void createDirectoryIfNotExists(String dirPath) {
        try {
            File directory = new File(dirPath);
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (created) {
                    logger.info("Verzeichnis erstellt: " + dirPath);
                } else {
                    logger.warning("Verzeichnis konnte nicht erstellt werden: " + dirPath);
                }
            }
        } catch (Exception e) {
            logger.severe("Fehler beim Erstellen des Verzeichnisses " + dirPath + ": " + e.getMessage());
        }
    }
}