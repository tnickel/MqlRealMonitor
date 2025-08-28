package com.mql.realmonitor.currency;

import java.time.Duration;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.exception.MqlMonitorException;
import com.mql.realmonitor.exception.MqlMonitorException.ErrorType;

/**
 * Selenium-basierter Currency-Downloader für MQL5 Live-Kurse.
 * Verwendet echten Browser um JavaScript-aktualisierte Kurse zu erfassen.
 */
public class SeleniumCurrencyDownloader {
    
    private static final Logger LOGGER = Logger.getLogger(SeleniumCurrencyDownloader.class.getName());
    
    private static final String MQL5_QUOTES_URL = "https://www.mql5.com/en/quotes/overview";
    private static final int DEFAULT_WAIT_SECONDS = 15;
    private static final int JAVASCRIPT_WAIT_SECONDS = 5;
    
    private final MqlRealMonitorConfig config;
    private WebDriver driver;
    private WebDriverWait wait;
    
    /**
     * Konstruktor für SeleniumCurrencyDownloader.
     * 
     * @param config Konfiguration für Browser-Einstellungen
     */
    public SeleniumCurrencyDownloader(MqlRealMonitorConfig config) {
        this.config = config;
    }
    
    /**
     * Initialisiert den Selenium WebDriver.
     * 
     * @throws MqlMonitorException bei Initialisierungsfehlern
     */
    public void initialize() throws MqlMonitorException {
        try {
            LOGGER.info("Initialisiere Selenium WebDriver...");
            
            ChromeOptions options = new ChromeOptions();
            
            // Headless-Modus (ohne GUI) für Server-Betrieb
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            
            // User-Agent setzen
            options.addArguments("--user-agent=" + getUserAgent());
            
            // Performance-Optimierungen
            options.addArguments("--disable-images");
            options.addArguments("--disable-javascript-harmony-shipping");
            options.addArguments("--disable-extensions");
            
            // Browser starten
            this.driver = new ChromeDriver(options);
            this.wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_SECONDS));
            
            LOGGER.info("Selenium WebDriver erfolgreich initialisiert");
            
        } catch (Exception e) {
            String errorMsg = "Fehler beim Initialisieren des WebDrivers: " + e.getMessage();
            LOGGER.severe(errorMsg);
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, errorMsg, e);
        }
    }
    
    /**
     * Lädt Live-Currency-Kurse von MQL5.
     * 
     * @return XAUUSD und BTCUSD Live-Kurse als CurrencyData Array
     * @throws MqlMonitorException bei Download- oder Parsing-Fehlern
     */
    public CurrencyData[] loadLiveCurrencyRates() throws MqlMonitorException {
        if (driver == null) {
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "WebDriver nicht initialisiert. Rufe initialize() auf.");
        }
        
        try {
            LOGGER.info("Lade MQL5 Quotes-Seite: " + MQL5_QUOTES_URL);
            
            // Seite laden
            driver.get(MQL5_QUOTES_URL);
            
            // Warten bis Seite vollständig geladen
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("navigator-overview-all")));
            
            // Zusätzlich warten, damit JavaScript die Kurse aktualisieren kann
            Thread.sleep(JAVASCRIPT_WAIT_SECONDS * 1000);
            LOGGER.info("JavaScript-Update-Wartezeit abgeschlossen");
            
            // XAUUSD Kurs extrahieren
            CurrencyData xauusdData = extractXauusdRate();
            
            // BTCUSD Kurs extrahieren  
            CurrencyData btcusdData = extractBtcusdRate();
            
            LOGGER.info("Live-Kurse erfolgreich extrahiert");
            LOGGER.info("XAUUSD: " + (xauusdData != null ? xauusdData.getPrice() : "NULL"));
            LOGGER.info("BTCUSD: " + (btcusdData != null ? btcusdData.getPrice() : "NULL"));
            
            // Array zurückgeben (kann null-Elemente enthalten)
            return new CurrencyData[] { xauusdData, btcusdData };
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, "Thread unterbrochen während Currency-Loading", e);
        } catch (Exception e) {
            String errorMsg = "Fehler beim Laden der Live-Currency-Kurse: " + e.getMessage();
            LOGGER.severe(errorMsg);
            throw new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, errorMsg, e);
        }
    }
    
    /**
     * Extrahiert XAUUSD Live-Kurs vom DOM.
     * 
     * @return CurrencyData für XAUUSD oder null bei Fehlern
     */
    private CurrencyData extractXauusdRate() {
        try {
            // Warten bis ticker_bid_375 verfügbar ist
            WebElement xauusdElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("ticker_bid_375")));
            
            String priceText = xauusdElement.getText();
            LOGGER.info("XAUUSD DOM-Text: '" + priceText + "'");
            
            if (priceText != null && !priceText.trim().isEmpty()) {
                double price = parsePrice(priceText.trim());
                if (price > 0) {
                    return new CurrencyData("XAUUSD", price);
                }
            }
            
            LOGGER.warning("Konnte XAUUSD Kurs nicht extrahieren: " + priceText);
            return null;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Extrahieren von XAUUSD: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extrahiert BTCUSD Live-Kurs vom DOM.
     * 
     * @return CurrencyData für BTCUSD oder null bei Fehlern
     */
    private CurrencyData extractBtcusdRate() {
        try {
            // Warten bis ticker_bid_4467 verfügbar ist
            WebElement btcusdElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("ticker_bid_4467")));
            
            String priceText = btcusdElement.getText();
            LOGGER.info("BTCUSD DOM-Text: '" + priceText + "'");
            
            if (priceText != null && !priceText.trim().isEmpty()) {
                double price = parsePrice(priceText.trim());
                if (price > 0) {
                    return new CurrencyData("BTCUSD", price);
                }
            }
            
            LOGGER.warning("Konnte BTCUSD Kurs nicht extrahieren: " + priceText);
            return null;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Extrahieren von BTCUSD: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parst einen Preis-String zu double.
     * 
     * @param priceText Der Preis-Text aus dem DOM
     * @return Geparster Preis oder 0.0 bei Fehlern
     */
    private double parsePrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) {
            return 0.0;
        }
        
        try {
            // Entferne alle Nicht-Ziffern außer Punkt und Komma
            String cleaned = priceText.replaceAll("[^0-9.,]", "");
            
            // Normalisiere Dezimaltrennzeichen
            if (cleaned.contains(",")) {
                cleaned = cleaned.replace(",", ".");
            }
            
            return Double.parseDouble(cleaned);
            
        } catch (NumberFormatException e) {
            LOGGER.warning("Konnte Preis nicht parsen: '" + priceText + "' -> '" + priceText + "'");
            return 0.0;
        }
    }
    
    /**
     * Schließt den WebDriver und gibt Ressourcen frei.
     */
    public void cleanup() {
        try {
            if (driver != null) {
                LOGGER.info("Schließe Selenium WebDriver...");
                driver.quit();
                driver = null;
                wait = null;
                LOGGER.info("WebDriver erfolgreich geschlossen");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Schließen des WebDrivers: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gibt den User-Agent für den Browser zurück.
     * 
     * @return User-Agent String
     */
    private String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }
    
    /**
     * Prüft ob der WebDriver initialisiert ist.
     * 
     * @return true wenn bereit, false sonst
     */
    public boolean isInitialized() {
        return driver != null;
    }
}