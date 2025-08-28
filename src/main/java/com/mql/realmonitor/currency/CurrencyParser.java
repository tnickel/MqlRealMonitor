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
 * ERWEITERT: Unterstützt sowohl Punkt als auch Komma als Dezimaltrennzeichen.
 */
public class CurrencyParser {
    
    private static final Logger logger = Logger.getLogger(CurrencyParser.class.getName());
    
    // SPEZIFISCHE Regex-Patterns für MQL5 HTML-Struktur
    // Angepasst an die tatsächliche HTML-Struktur von MQL5 Quotes-Seite
    private static final Pattern[] XAUUSD_PATTERNS = {
        // HAUPT-PATTERN: Spezifisch für MQL5 HTML-Struktur mit ticker_bid
        Pattern.compile("ticker_bid_375\">([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ticker_ask_375\">([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        
        // Spezifisch für navigator-overview-all__quote-val mit XAUUSD
        Pattern.compile("XAUUSD.*?navigator-overview-all__quote-val[^>]*>([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("Gold vs US Dollar.*?navigator-overview-all__quote-val[^>]*>([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        
        // Fallback: JSON-Format
        Pattern.compile("\"symbol\":\"XAUUSD\"[^}]*?\"bid\":([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\"375\":\"XAUUSD\"[^}]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        
        // Fallback: data-Attribute
        Pattern.compile("data-symbol=\"XAUUSD\"[^>]*?data-bid=\"([0-9]+\\.[0-9]+)\"", Pattern.CASE_INSENSITIVE),
        
        // NEU: Komma-Versionen für deutsche Locale
        Pattern.compile("ticker_bid_375\">([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ticker_ask_375\">([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("XAUUSD.*?navigator-overview-all__quote-val[^>]*>([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        
        // ALTE Fallback-Patterns (weniger spezifisch)
        Pattern.compile("XAUUSD[^>]{0,100}([0-9]+[.,][0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("XAU/USD[^>]{0,100}([0-9]+[.,][0-9]+)", Pattern.CASE_INSENSITIVE)
    };
    
    private static final Pattern[] BTCUSD_PATTERNS = {
        // HAUPT-PATTERN: Spezifisch für MQL5 HTML-Struktur mit ticker_bid
        Pattern.compile("ticker_bid_4467\">([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ticker_ask_4467\">([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        
        // Spezifisch für navigator-overview-all__quote-val mit BTCUSD
        Pattern.compile("BTCUSD.*?navigator-overview-all__quote-val[^>]*>([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("Bitcoin vs US Dollar.*?navigator-overview-all__quote-val[^>]*>([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        
        // Fallback: JSON-Format
        Pattern.compile("\"symbol\":\"BTCUSD\"[^}]*?\"bid\":([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\"4467\":\"BTCUSD\"[^}]*?([0-9]+\\.[0-9]+)", Pattern.CASE_INSENSITIVE),
        
        // Fallback: data-Attribute
        Pattern.compile("data-symbol=\"BTCUSD\"[^>]*?data-bid=\"([0-9]+\\.[0-9]+)\"", Pattern.CASE_INSENSITIVE),
        
        // NEU: Komma-Versionen für deutsche Locale
        Pattern.compile("ticker_bid_4467\">([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ticker_ask_4467\">([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BTCUSD.*?navigator-overview-all__quote-val[^>]*>([0-9]+,[0-9]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        
        // ALTE Fallback-Patterns (weniger spezifisch)
        Pattern.compile("BTCUSD[^>]{0,100}([0-9]+[.,][0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("BTC/USD[^>]{0,100}([0-9]+[.,][0-9]+)", Pattern.CASE_INSENSITIVE)
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
     * ERWEITERT: Unterstützt sowohl Punkt als auch Komma als Dezimaltrennzeichen.
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
                    
                    // NEU: Normalisiere das Zahlenformat (Komma zu Punkt)
                    String normalizedRateString = normalizeDecimalFormat(rateString);
                    double rate = Double.parseDouble(normalizedRateString);
                    
                    // Plausibilitätsprüfung
                    if (isValidRate(rate, symbol)) {
                        logger.info(symbol + " gefunden mit Pattern " + (i+1) + ": " + rateString + " → " + rate);
                        return rate;
                    } else {
                        logger.warning(symbol + " Pattern " + (i+1) + " lieferte unplausiblen Wert: " + rateString + " → " + rate);
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
     * NEU: Normalisiert Dezimalformat von deutschem (Komma) zu englischem (Punkt) Format.
     * Beispiel: "3392,96000" → "3392.96000"
     * 
     * @param numberString Der zu normalisierende Zahlen-String
     * @return Normalisierter String mit Punkt als Dezimaltrennzeichen
     */
    private String normalizeDecimalFormat(String numberString) {
        if (numberString == null) {
            return null;
        }
        
        // Entferne Whitespace
        String cleaned = numberString.trim();
        
        // Ersetze Komma durch Punkt für Dezimaltrennzeichen
        // Aber nur wenn es EIN Komma gibt (nicht bei Tausender-Trennzeichen)
        if (cleaned.contains(",")) {
            int commaIndex = cleaned.lastIndexOf(",");
            String beforeComma = cleaned.substring(0, commaIndex);
            String afterComma = cleaned.substring(commaIndex + 1);
            
            // Prüfe ob es ein Dezimaltrennzeichen ist (2-5 Ziffern nach Komma)
            if (afterComma.matches("[0-9]{2,5}") && !beforeComma.contains(",")) {
                cleaned = beforeComma + "." + afterComma;
                logger.fine("Dezimalformat normalisiert: " + numberString + " → " + cleaned);
            }
        }
        
        return cleaned;
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
                // Gold: typisch zwischen 1000-5000 USD (erweitert für aktuelle Preise)
                return rate >= 500 && rate <= 6000;
                
            case "btcusd":
                // Bitcoin: typisch zwischen 10000-200000 USD
                return rate >= 1000 && rate <= 300000;
                
            default:
                // Für andere Symbole: grundlegende Validierung
                return rate > 0 && rate < 1000000;
        }
    }
    
    /**
     * ERWEITERTE Diagnostik-Methode: Sucht nach möglichen Währungsreferenzen im HTML.
     * Unterstützt jetzt auch Komma-getrennte Zahlen.
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
        
        // NEU: Suche speziell nach MQL5 ticker IDs für bessere Diagnostik
        Pattern tickerIdPattern = Pattern.compile("ticker_(?:bid|ask)_([0-9]+)\">([0-9]+[.,][0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher tickerIdMatcher = tickerIdPattern.matcher(htmlContent);
        
        int tickerCount = 0;
        while (tickerIdMatcher.find() && tickerCount < 15) {
            String tickerId = tickerIdMatcher.group(1);
            String tickerValue = tickerIdMatcher.group(2);
            references.add("Ticker ID " + tickerId + ": " + tickerValue);
            tickerCount++;
        }
        
        return references;
    }
    
    /**
     * NEU: Test-Methode für Zahlenformat-Normalisierung.
     * Kann für Unit-Tests verwendet werden.
     * 
     * @param testString Test-String zum Normalisieren
     * @return Normalisierter String
     */
    public String testNormalizeDecimalFormat(String testString) {
        return normalizeDecimalFormat(testString);
    }
}