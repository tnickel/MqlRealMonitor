package com.mql.realmonitor.currency;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.downloader.DownloadResult;  // NEU: Import für DownloadResult
import com.mql.realmonitor.downloader.WebDownloader;
import com.mql.realmonitor.exception.MqlMonitorException;
import com.mql.realmonitor.exception.MqlMonitorException.ErrorType;
import com.mql.realmonitor.utils.MqlUtils;

/**
 * Hauptklasse für das Laden von Währungskursen von MQL5.
 * ERWEITERT: Unterstützt sowohl statisches HTML-Parsing als auch Selenium Live-Kurse.
 * VERBESSERT: Verwendet jetzt DownloadResult für detaillierte Fehlerdiagnostik
 * Orchestriert den kompletten Prozess: Download → Parse → Save
 */
public class CurrencyDataLoader {
    
    private static final Logger LOGGER = Logger.getLogger(CurrencyDataLoader.class.getName());
    
    // Konfiguration für Selenium vs. HTML-Parsing
    private static final boolean USE_SELENIUM_FOR_CURRENCY = true; // Selenium für Live-Kurse verwenden
    
    private static final String MQL5_URL = "https://www.mql5.com";
    private static final String MQL5_RATES_URL = "https://www.mql5.com/en/quotes";
    
    private final MqlRealMonitorConfig config;
    private final WebDownloader webDownloader;
    private final CurrencyParser currencyParser;
    private final CurrencyDataWriter currencyDataWriter;
    private final String downloadMql5Dir;
    
    // NEU: Selenium-basierter Downloader
    private SeleniumCurrencyDownloader seleniumDownloader;
    
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
        
        // NEU: Selenium-Downloader initialisieren falls gewünscht
        if (USE_SELENIUM_FOR_CURRENCY) {
            this.seleniumDownloader = new SeleniumCurrencyDownloader(config);
        }
    }
    
    /**
     * Lädt aktuelle Währungskurse von MQL5 und speichert sie.
     * ERWEITERT: Verwendet Selenium für Live-Kurse oder HTML-Parsing als Fallback.
     * 
     * @return Anzahl der erfolgreich geladenen und gespeicherten Kurse
     * @throws MqlMonitorException bei Fehlern im Prozess
     */
    public int loadAndSaveCurrencyRates() throws MqlMonitorException {
        LOGGER.info("=== Starte Währungskurs-Loading von MQL5 ===");
        LOGGER.info("Verwendete Methode: " + (USE_SELENIUM_FOR_CURRENCY ? "Selenium Live-Kurse" : "HTML-Parsing"));
        
        try {
            List<CurrencyData> currencyRates;
            
            if (USE_SELENIUM_FOR_CURRENCY) {
                // NEU: Selenium-basiertes Live-Kurs-Loading
                currencyRates = loadCurrencyRatesWithSelenium();
            } else {
                // ALT: HTML-basiertes Parsing
                currencyRates = loadCurrencyRatesWithHtmlParsing();
            }
            
            if (currencyRates.isEmpty()) {
                throw new MqlMonitorException(ErrorType.PARSING_ERROR, "Keine Währungskurse gefunden oder extrahiert");
            }
            
            // Schritt 3: Kurse speichern (bleibt gleich)
            LOGGER.info("Speichere Kursdaten...");
            currencyDataWriter.writeCurrencyData(currencyRates);
            
            LOGGER.info("=== Währungskurs-Loading erfolgreich abgeschlossen ===");
            LOGGER.info("Geladene Kurse: " + currencyRates.size());
            
            // Log der geladenen Kurse
            for (CurrencyData rate : currencyRates) {
                LOGGER.info("  " + rate.getSymbol() + ": " + rate.getPrice());
            }
            
            return currencyRates.size();
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Laden der Währungskurse: " + e.getMessage());
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "Währungskurs-Loading fehlgeschlagen: " + e.getMessage(), e);
        }
    }
    
    /**
     * NEU: Lädt Währungskurse mit Selenium (Live-Kurse).
     * 
     * @return Liste der extrahierten CurrencyData Objekte
     * @throws MqlMonitorException bei Selenium-Fehlern
     */
    private List<CurrencyData> loadCurrencyRatesWithSelenium() throws MqlMonitorException {
        LOGGER.info("Schritt 1/2: Lade Live-Kurse mit Selenium...");
        
        try {
            // Selenium initialisieren
            if (!seleniumDownloader.isInitialized()) {
                seleniumDownloader.initialize();
            }
            
            // Live-Kurse laden
            CurrencyData[] liveRates = seleniumDownloader.loadLiveCurrencyRates();
            
            // Array zu List konvertieren und null-Werte filtern
            List<CurrencyData> validRates = new ArrayList<>();
            for (CurrencyData rate : liveRates) {
                if (rate != null && rate.getPrice() > 0) {
                    validRates.add(rate);
                }
            }
            
            LOGGER.info("Selenium: " + validRates.size() + " gültige Live-Kurse extrahiert");
            return validRates;
            
        } catch (Exception e) {
            LOGGER.severe("Selenium-Fehler: " + e.getMessage());
            // Bei Selenium-Fehlern auf HTML-Parsing fallback
            LOGGER.info("Fallback auf HTML-Parsing...");
            return loadCurrencyRatesWithHtmlParsing();
        }
    }
    
    /**
     * ALT: Lädt Währungskurse mit HTML-Parsing (statische Kurse).
     * 
     * @return Liste der extrahierten CurrencyData Objekte
     * @throws MqlMonitorException bei HTML-Download/Parse-Fehlern
     */
    private List<CurrencyData> loadCurrencyRatesWithHtmlParsing() throws MqlMonitorException {
        LOGGER.info("Schritt 1/3: Lade HTML von MQL5...");
        String htmlContent = downloadMql5Html();
        
        LOGGER.info("Schritt 2/3: Parse Währungskurse aus HTML...");
        List<CurrencyData> currencyRates = currencyParser.parseRates(htmlContent);
        
        LOGGER.info("HTML-Parsing: " + currencyRates.size() + " Kurse extrahiert");
        return currencyRates;
    }
    
    /**
     * VERBESSERT: Lädt HTML-Content von MQL5 herunter mit DownloadResult
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
                LOGGER.info("Rates-URL lieferte wenig Content, versuche Haupt-URL...");
                htmlContent = downloadFromUrl(MQL5_URL);
            }
            
            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                throw new MqlMonitorException(ErrorType.PARSING_ERROR, "MQL5 HTML-Content ist leer");
            }
            
            LOGGER.info("HTML erfolgreich geladen: " + htmlContent.length() + " Zeichen");
            
            // HTML in Datei speichern für Debugging
            saveHtmlToFile(htmlContent);
            
            return htmlContent;
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Download von MQL5: " + e.getMessage());
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "MQL5 Download fehlgeschlagen: " + e.getMessage(), e);
        }
    }
    
    /**
     * VERBESSERT: Lädt HTML von einer spezifischen URL herunter mit DownloadResult
     * 
     * @param url Die URL zum Herunterladen
     * @return HTML-Content oder null bei Fehlern
     */
    private String downloadFromUrl(String url) {
        try {
            LOGGER.info("Lade HTML von: " + url);
            
            // NEU: WebDownloader gibt jetzt DownloadResult zurück
            DownloadResult result = webDownloader.downloadFromWebUrl(url);
            
            // Download-Ergebnis auswerten
            if (result.isSuccess()) {
                String htmlContent = result.getContent();
                LOGGER.info("✓ Download erfolgreich: " + htmlContent.length() + " Zeichen");
                return htmlContent;
            } else {
                // Detaillierte Fehlerinformationen loggen
                LOGGER.warning("✗ Download fehlgeschlagen von: " + url);
                LOGGER.warning("  Fehlertyp: " + result.getErrorType());
                LOGGER.warning("  HTTP-Code: " + result.getHttpStatusCode());
                LOGGER.warning("  Details: " + result.getDetailedErrorDescription());
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Download von " + url + ": " + e.getMessage());
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
            LOGGER.info("HTML gespeichert für Debugging: " + filename);
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Speichern der HTML-Datei: " + e.getMessage());
        }
    }
    
    /**
     * Lädt Währungskurse und gibt Diagnose-Informationen zurück.
     * ERWEITERT: Unterstützt sowohl Selenium als auch HTML-Parsing.
     * 
     * @return Diagnose-String für UI-Anzeige
     */
    public String loadCurrencyRatesWithDiagnosis() {
        try {
            int loadedRates = loadAndSaveCurrencyRates();
            
            StringBuilder diagnosis = new StringBuilder();
            diagnosis.append("Währungskurs-Loading erfolgreich!\n");
            diagnosis.append("Methode: ").append(USE_SELENIUM_FOR_CURRENCY ? "Selenium Live-Kurse" : "HTML-Parsing").append("\n");
            diagnosis.append("Geladene Kurse: ").append(loadedRates).append("\n\n");
            
            // File-Informationen anhängen
            List<String> fileInfo = currencyDataWriter.getCurrencyFileInfo();
            diagnosis.append("Gespeicherte Kursdateien:\n");
            for (String info : fileInfo) {
                diagnosis.append("  ").append(info).append("\n");
            }
            
            return diagnosis.toString();
            
        } catch (MqlMonitorException e) {
            LOGGER.severe("Währungskurs-Loading fehlgeschlagen: " + e.getMessage());
            
            StringBuilder diagnosis = new StringBuilder();
            diagnosis.append("FEHLER beim Währungskurs-Loading!\n");
            diagnosis.append("Methode: ").append(USE_SELENIUM_FOR_CURRENCY ? "Selenium Live-Kurse" : "HTML-Parsing").append("\n");
            diagnosis.append("Fehler: ").append(e.getMessage()).append("\n\n");
            
            // Diagnose-Informationen für Debugging
            if (!USE_SELENIUM_FOR_CURRENCY) {
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
            }
            
            return diagnosis.toString();
        }
    }
    
    /**
     * NEU: Bereinigt Selenium-Ressourcen.
     * Sollte beim Herunterfahren der Anwendung aufgerufen werden.
     */
    public void cleanup() {
        if (seleniumDownloader != null) {
            seleniumDownloader.cleanup();
        }
    }
    
    /**
     * Gibt Statistiken über gespeicherte Kursdaten zurück.
     * 
     * @return Statistik-String
     */
    public String getCurrencyDataStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Währungskurs-Statistiken ===\n");
        stats.append("Aktive Methode: ").append(USE_SELENIUM_FOR_CURRENCY ? "Selenium Live-Kurse" : "HTML-Parsing").append("\n\n");
        
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
                    LOGGER.info("Verzeichnis erstellt: " + dirPath);
                } else {
                    LOGGER.warning("Verzeichnis konnte nicht erstellt werden: " + dirPath);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Erstellen des Verzeichnisses " + dirPath + ": " + e.getMessage());
        }
    }
}