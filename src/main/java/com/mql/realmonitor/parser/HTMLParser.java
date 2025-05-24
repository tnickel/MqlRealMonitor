package com.mql.realmonitor.parser;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * HTML-Parser für MQL5 Signalprovider-Seiten
 * Extrahiert Kontostand und Floating Profit mit flexiblem Pattern-Matching
 */
public class HTMLParser {
    
    private static final Logger LOGGER = Logger.getLogger(HTMLParser.class.getName());
    
    // Regex-Pattern für das Parsen der Signaldaten
    private static final String KONTOSTAND_PATTERN = 
        "Kontostand:\\s*([\\d,]+\\.?\\d*)\\s*([A-Z]{3})";
    
    private static final String FLOATING_PROFIT_PATTERN = 
        "Floating\\s*Profit:\\s*([-]?[\\d,]+\\.?\\d*)\\s*([A-Z]{3})";
    
    // Alternative Pattern für verschiedene Sprach-/Format-Varianten
    private static final String[] ALTERNATIVE_KONTOSTAND_PATTERNS = {
        "Balance:\\s*([\\d,]+\\.?\\d*)\\s*([A-Z]{3})",
        "Account\\s*Balance:\\s*([\\d,]+\\.?\\d*)\\s*([A-Z]{3})",
        "Kontostand[^:]*:\\s*([\\d,]+\\.?\\d*)\\s*([A-Z]{3})"
    };
    
    private static final String[] ALTERNATIVE_FLOATING_PATTERNS = {
        "Floating[^:]*:\\s*([-]?[\\d,]+\\.?\\d*)\\s*([A-Z]{3})",
        "Current\\s*Profit:\\s*([-]?[\\d,]+\\.?\\d*)\\s*([A-Z]{3})",
        "Unrealized\\s*P&L:\\s*([-]?[\\d,]+\\.?\\d*)\\s*([A-Z]{3})"
    };
    
    // Compiled Patterns für bessere Performance
    private final Pattern kontostandardPattern;
    private final Pattern floatingProfitPattern;
    private final Pattern[] alternativeKontostandPatterns;
    private final Pattern[] alternativeFloatingPatterns;
    
    public HTMLParser() {
        // Hauptpattern kompilieren
        kontostandardPattern = Pattern.compile(KONTOSTAND_PATTERN, Pattern.CASE_INSENSITIVE);
        floatingProfitPattern = Pattern.compile(FLOATING_PROFIT_PATTERN, Pattern.CASE_INSENSITIVE);
        
        // Alternative Pattern kompilieren
        alternativeKontostandPatterns = new Pattern[ALTERNATIVE_KONTOSTAND_PATTERNS.length];
        for (int i = 0; i < ALTERNATIVE_KONTOSTAND_PATTERNS.length; i++) {
            alternativeKontostandPatterns[i] = Pattern.compile(
                ALTERNATIVE_KONTOSTAND_PATTERNS[i], Pattern.CASE_INSENSITIVE);
        }
        
        alternativeFloatingPatterns = new Pattern[ALTERNATIVE_FLOATING_PATTERNS.length];
        for (int i = 0; i < ALTERNATIVE_FLOATING_PATTERNS.length; i++) {
            alternativeFloatingPatterns[i] = Pattern.compile(
                ALTERNATIVE_FLOATING_PATTERNS[i], Pattern.CASE_INSENSITIVE);
        }
    }
    
    /**
     * Parst HTML-Inhalt und extrahiert Signaldaten
     * 
     * @param htmlContent Der HTML-Inhalt der Seite
     * @param signalId Die Signal-ID für Logging
     * @return SignalData-Objekt mit extrahierten Daten oder null bei Fehlern
     */
    public SignalData parseSignalData(String htmlContent, String signalId) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            LOGGER.warning("HTML-Inhalt ist leer für Signal: " + signalId);
            return null;
        }
        
        try {
            LOGGER.info("Parse HTML für Signal: " + signalId + " (" + htmlContent.length() + " Zeichen)");
            
            // Kontostand extrahieren
            ParseResult kontostandardResult = extractKontostand(htmlContent);
            if (kontostandardResult == null) {
                LOGGER.warning("Kontostand nicht gefunden für Signal: " + signalId);
                return null;
            }
            
            // Floating Profit extrahieren
            ParseResult floatingResult = extractFloatingProfit(htmlContent);
            if (floatingResult == null) {
                LOGGER.warning("Floating Profit nicht gefunden für Signal: " + signalId);
                // Floating Profit als 0.00 setzen wenn nicht gefunden
                floatingResult = new ParseResult(0.0, kontostandardResult.currency);
            }
            
            // Währungen müssen übereinstimmen
            if (!kontostandardResult.currency.equals(floatingResult.currency)) {
                LOGGER.warning("Währungen stimmen nicht überein für Signal " + signalId + 
                             ": Kontostand=" + kontostandardResult.currency + 
                             ", Floating=" + floatingResult.currency);
                // Verwende Kontostand-Währung als Standard
                floatingResult = new ParseResult(floatingResult.value, kontostandardResult.currency);
            }
            
            // SignalData erstellen
            SignalData signalData = new SignalData(
                signalId,
                kontostandardResult.value,
                floatingResult.value,
                kontostandardResult.currency,
                LocalDateTime.now()
            );
            
            LOGGER.info("Parse erfolgreich für Signal " + signalId + 
                       ": Kontostand=" + kontostandardResult.value + " " + kontostandardResult.currency +
                       ", Floating=" + floatingResult.value + " " + floatingResult.currency);
            
            return signalData;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Parsen von Signal: " + signalId, e);
            return null;
        }
    }
    
    /**
     * Extrahiert den Kontostand aus dem HTML
     * 
     * @param htmlContent Der HTML-Inhalt
     * @return ParseResult mit Wert und Währung oder null
     */
    private ParseResult extractKontostand(String htmlContent) {
        // Zuerst Hauptpattern versuchen
        ParseResult result = tryParseWithPattern(htmlContent, kontostandardPattern, "Kontostand");
        if (result != null) {
            return result;
        }
        
        // Alternative Pattern versuchen
        for (int i = 0; i < alternativeKontostandPatterns.length; i++) {
            result = tryParseWithPattern(htmlContent, alternativeKontostandPatterns[i], 
                                       "Kontostand (Alternative " + (i + 1) + ")");
            if (result != null) {
                return result;
            }
        }
        
        LOGGER.warning("Kontostand Pattern nicht gefunden im HTML");
        logHtmlSample(htmlContent, "Kontostand");
        return null;
    }
    
    /**
     * Extrahiert den Floating Profit aus dem HTML
     * 
     * @param htmlContent Der HTML-Inhalt
     * @return ParseResult mit Wert und Währung oder null
     */
    private ParseResult extractFloatingProfit(String htmlContent) {
        // Zuerst Hauptpattern versuchen
        ParseResult result = tryParseWithPattern(htmlContent, floatingProfitPattern, "Floating Profit");
        if (result != null) {
            return result;
        }
        
        // Alternative Pattern versuchen
        for (int i = 0; i < alternativeFloatingPatterns.length; i++) {
            result = tryParseWithPattern(htmlContent, alternativeFloatingPatterns[i], 
                                       "Floating Profit (Alternative " + (i + 1) + ")");
            if (result != null) {
                return result;
            }
        }
        
        LOGGER.warning("Floating Profit Pattern nicht gefunden im HTML");
        logHtmlSample(htmlContent, "Floating Profit");
        return null;
    }
    
    /**
     * Versucht ein Pattern zu matchen und zu parsen
     * 
     * @param htmlContent Der HTML-Inhalt
     * @param pattern Das Pattern zum Matchen
     * @param patternName Name des Patterns für Logging
     * @return ParseResult oder null wenn nicht gefunden
     */
    private ParseResult tryParseWithPattern(String htmlContent, Pattern pattern, String patternName) {
        Matcher matcher = pattern.matcher(htmlContent);
        
        if (matcher.find()) {
            try {
                String valueStr = matcher.group(1);
                String currency = matcher.group(2);
                
                // Kommas in Zahlen entfernen und parsen
                double value = parseNumericValue(valueStr);
                
                LOGGER.fine(patternName + " gefunden: " + value + " " + currency);
                return new ParseResult(value, currency);
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Parsen von " + patternName + 
                          " Match: " + matcher.group(0), e);
            }
        }
        
        return null;
    }
    
    /**
     * Parst einen numerischen Wert (entfernt Kommas und konvertiert zu double)
     * 
     * @param valueStr Der String mit dem numerischen Wert
     * @return Der geparste double-Wert
     */
    private double parseNumericValue(String valueStr) throws NumberFormatException {
        if (valueStr == null || valueStr.trim().isEmpty()) {
            throw new NumberFormatException("Leerer Wert");
        }
        
        // Whitespace und Kommas entfernen
        String cleanValue = valueStr.trim().replace(",", "");
        
        return Double.parseDouble(cleanValue);
    }
    
    /**
     * Loggt einen HTML-Auszug für Debugging-Zwecke
     * 
     * @param htmlContent Der vollständige HTML-Inhalt
     * @param context Kontext für das Logging
     */
    private void logHtmlSample(String htmlContent, String context) {
        if (LOGGER.isLoggable(Level.FINE)) {
            // Suche nach relevanten Bereichen im HTML
            String[] searchTerms = {"description", "balance", "profit", "equity", "kontostand", "floating"};
            
            for (String term : searchTerms) {
                int index = htmlContent.toLowerCase().indexOf(term.toLowerCase());
                if (index >= 0) {
                    int start = Math.max(0, index - 100);
                    int end = Math.min(htmlContent.length(), index + 200);
                    String sample = htmlContent.substring(start, end);
                    
                    LOGGER.fine(context + " - HTML-Auszug um '" + term + "': " + sample);
                    break;
                }
            }
        }
    }
    
    /**
     * Validiert ob das HTML gültige MQL5-Signalseite zu sein scheint
     * 
     * @param htmlContent Der HTML-Inhalt
     * @return true wenn gültig, false sonst
     */
    public boolean isValidMql5SignalPage(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return false;
        }
        
        // Prüfe auf charakteristische MQL5-Inhalte
        String lowerContent = htmlContent.toLowerCase();
        
        return lowerContent.contains("mql5") &&
               (lowerContent.contains("signal") || lowerContent.contains("account")) &&
               (lowerContent.contains("balance") || lowerContent.contains("kontostand"));
    }
    
    /**
     * Interne Klasse für Parse-Ergebnisse
     */
    private static class ParseResult {
        final double value;
        final String currency;
        
        ParseResult(double value, String currency) {
            this.value = value;
            this.currency = currency;
        }
    }
}