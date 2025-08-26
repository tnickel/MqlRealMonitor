package com.mql.realmonitor.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mql.realmonitor.exception.MqlMonitorException;
import com.mql.realmonitor.exception.MqlMonitorException.ErrorType;

/**
 * Parser für Währungskurse von der MQL5-Website.
 * Extrahiert XAUUSD und BTCUSD Kurse aus dem HTML-Content.
 */
public class CurrencyParser {
    
    private static final Logger logger = Logger.getLogger(CurrencyParser.class.getName());
    
    // Regex-Patterns für verschiedene mögliche HTML-Strukturen von MQL5
    private static final Pattern[] XAUUSD_PATTERNS = {
        Pattern.compile("XAUUSD[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("XAU/USD[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Gold[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\"symbol\":\"XAUUSD\"[^}]*?\"bid\":([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data-symbol=\"XAUUSD\"[^>]*?data-bid=\"([0-9]+\\.[0-9]+)\"", Pattern.CASE_INSENSITIVE)
    };
    
    private static final Pattern[] BTCUSD_PATTERNS = {
        Pattern.compile("BTCUSD[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BTC/USD[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("Bitcoin[^>]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\"symbol\":\"BTCUSD\"[^}]*?\"bid\":([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data-symbol=\"BTCUSD\"[^>]*?data-bid=\"([0-9]+\\.[0-9]+)\"", Pattern.CASE_INSENSITIVE)
    };
    
    /**
     * Parst HTML-Content und extrahiert Währungskurse.
     * 
     * @param htmlContent Der HTML-Content von mql5.com
     * @return Liste der extrahierten CurrencyData Objekte
     * @throws MqlMonitorException bei Parsing-Fehlern
     */
    public List<CurrencyData> parseRates(String htmlContent) throws MqlMonitorException {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            throw new MqlMonitorException(ErrorType.PARSING_ERROR, "HTML-Content ist leer oder null");
        }
        
        List<CurrencyData> rates = new ArrayList<>();
        
        logger.info("Beginne Parsing der Währungskurse von MQL5...");
        
        // XAUUSD parsen
        Double xauusdRate = extractRate(htmlContent, XAUUSD_PATTERNS, "XAUUSD");
        if (xauusdRate != null) {
            rates.add(new CurrencyData("XAUUSD", xauusdRate));
            logger.info("XAUUSD Kurs gefunden: " + xauusdRate);
        } else {
            logger.warning("XAUUSD Kurs konnte nicht extrahiert werden");
        }
        
        // BTCUSD parsen
        Double btcusdRate = extractRate(htmlContent, BTCUSD_PATTERNS, "BTCUSD");
        if (btcusdRate != null) {
            rates.add(new CurrencyData("BTCUSD", btcusdRate));
            logger.info("BTCUSD Kurs gefunden: " + btcusdRate);
        } else {
            logger.warning("BTCUSD Kurs konnte nicht extrahiert werden");
        }
        
        if (rates.isEmpty()) {
            logger.severe("Keine Währungskurse gefunden. HTML-Content Länge: " + htmlContent.length());
            // Debug: Erste 500 Zeichen des HTML loggen
            String debugContent = htmlContent.length() > 500 ? htmlContent.substring(0, 500) : htmlContent;
            logger.info("HTML-Content (erste 500 Zeichen): " + debugContent);
            
            throw new MqlMonitorException(ErrorType.PARSING_ERROR, "Keine Währungskurse gefunden in MQL5 HTML-Content");
        }
        
        logger.info("Insgesamt " + rates.size() + " Währungskurse erfolgreich geparst");
        return rates;
    }
    
    /**
     * Extrahiert einen einzelnen Kurs aus dem HTML-Content.
     * 
     * @param htmlContent Der HTML-Content
     * @param patterns Array von Regex-Patterns zum Testen
     * @param symbol Das Währungssymbol für Logging
     * @return Extrahierter Kurs oder null wenn nicht gefunden
     */
    private Double extractRate(String htmlContent, Pattern[] patterns, String symbol) {
        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = patterns[i];
            Matcher matcher = pattern.matcher(htmlContent);
            
            if (matcher.find()) {
                try {
                    String rateString = matcher.group(1);
                    double rate = Double.parseDouble(rateString);
                    
                    // Plausibilitätsprüfung
                    if (isValidRate(rate, symbol)) {
                        logger.info(symbol + " gefunden mit Pattern " + (i+1) + ": " + rate);
                        return rate;
                    } else {
                        logger.warning(symbol + " Pattern " + (i+1) + " lieferte unplausiblen Wert: " + rate);
                    }
                } catch (NumberFormatException e) {
                    logger.warning(symbol + " Pattern " + (i+1) + " lieferte ungültigen Zahlenwert: " + matcher.group(1));
                }
            }
        }
        
        logger.warning(symbol + " konnte mit keinem der " + patterns.length + " Patterns gefunden werden");
        return null;
    }
    
    /**
     * Validiert ob der extrahierte Kurs plausibel ist.
     * 
     * @param rate Der zu validierende Kurs
     * @param symbol Das Währungssymbol
     * @return true wenn plausibel
     */
    private boolean isValidRate(double rate, String symbol) {
        if (rate <= 0) {
            return false;
        }
        
        switch (symbol.toLowerCase()) {
            case "xauusd":
                // Gold: typisch zwischen 1000-3000 USD
                return rate >= 500 && rate <= 5000;
                
            case "btcusd":
                // Bitcoin: typisch zwischen 10000-100000 USD
                return rate >= 1000 && rate <= 200000;
                
            default:
                // Für andere Symbole: grundlegende Validierung
                return rate > 0 && rate < 1000000;
        }
    }
    
    /**
     * Diagnostik-Methode: Sucht nach möglichen Währungsreferenzen im HTML.
     * 
     * @param htmlContent Der HTML-Content
     * @return Liste gefundener Währungsreferenzen für Debugging
     */
    public List<String> findCurrencyReferences(String htmlContent) {
        List<String> references = new ArrayList<>();
        
        if (htmlContent == null) {
            return references;
        }
        
        // Suche nach häufigen Währungsreferenzen
        String[] searchTerms = {"XAUUSD", "XAU/USD", "Gold", "BTCUSD", "BTC/USD", "Bitcoin", 
                               "symbol", "bid", "ask", "price", "rate"};
        
        for (String term : searchTerms) {
            Pattern pattern = Pattern.compile("(.{0,50})" + term + "(.{0,50})", 
                                            Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(htmlContent);
            
            while (matcher.find() && references.size() < 20) { // Max 20 Referenzen
                references.add(term + ": ..." + matcher.group().trim() + "...");
            }
        }
        
        return references;
    }
}