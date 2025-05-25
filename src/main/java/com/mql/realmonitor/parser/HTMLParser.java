package com.mql.realmonitor.parser;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * HTML-Parser für MQL5 Signalprovider-Seiten
 * Extrahiert Kontostand, Floating Profit und Provider-Name mit flexiblem Pattern-Matching
 */
public class HTMLParser {
    
    private static final Logger LOGGER = Logger.getLogger(HTMLParser.class.getName());
    
    // Regex-Pattern für das Parsen der Signaldaten
    private static final String KONTOSTAND_PATTERN = 
        "Kontostand:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})";
    
    private static final String FLOATING_PROFIT_PATTERN = 
        "Floating\\s*Profit:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})";
    
    // NEU: Pattern für Provider-Name
    private static final String PROVIDER_NAME_PATTERN = 
        "<div\\s+class=[\"']s-line-card__title[\"']>([^<]+)</div>";
    
    // Pattern für JavaScript description Array Format
    private static final String DESCRIPTION_ARRAY_PATTERN = 
        "description:\\s*\\[\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\]";
    
    // Alternative Pattern für verschiedene Sprach-/Format-Varianten
    private static final String[] ALTERNATIVE_KONTOSTAND_PATTERNS = {
        "Balance:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "Account\\s*Balance:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "Kontostand[^:]*:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "'Kontostand:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})'",  // Quoted version
        "Equity[^:]*:\\s*([\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})"
    };
    
    private static final String[] ALTERNATIVE_FLOATING_PATTERNS = {
        "Floating[^:]*:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "Current\\s*Profit:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "Unrealized\\s*P&L:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})",
        "'Floating\\s*Profit:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})'",  // Quoted version
        "Profit[^:]*:\\s*([-]?[\\d,\\s]+\\.?\\d*)\\s*([A-Z]{3})"
    };
    
    // NEU: Alternative Pattern für Provider-Name
    private static final String[] ALTERNATIVE_PROVIDER_NAME_PATTERNS = {
        "<h1[^>]*class=[\"'][^\"']*title[^\"']*[\"'][^>]*>([^<]+)</h1>",
        "<div[^>]*class=[\"'][^\"']*signal-name[^\"']*[\"'][^>]*>([^<]+)</div>",
        "<span[^>]*class=[\"'][^\"']*provider-name[^\"']*[\"'][^>]*>([^<]+)</span>",
        "<title>([^-<]+)\\s*-\\s*MQL5",
        "class=[\"']s-line-card__title[\"'][^>]*>\\s*([^<]+?)\\s*</[^>]+>"
    };
    
    // Compiled Patterns für bessere Performance
    private final Pattern kontostandardPattern;
    private final Pattern floatingProfitPattern;
    private final Pattern providerNamePattern;  // NEU
    private final Pattern descriptionArrayPattern;
    private final Pattern[] alternativeKontostandPatterns;
    private final Pattern[] alternativeFloatingPatterns;
    private final Pattern[] alternativeProviderNamePatterns;  // NEU
    
    public HTMLParser() {
        // Hauptpattern kompilieren
        kontostandardPattern = Pattern.compile(KONTOSTAND_PATTERN, Pattern.CASE_INSENSITIVE);
        floatingProfitPattern = Pattern.compile(FLOATING_PROFIT_PATTERN, Pattern.CASE_INSENSITIVE);
        providerNamePattern = Pattern.compile(PROVIDER_NAME_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        descriptionArrayPattern = Pattern.compile(DESCRIPTION_ARRAY_PATTERN, Pattern.CASE_INSENSITIVE);
        
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
        
        // NEU: Alternative Provider-Name Pattern kompilieren
        alternativeProviderNamePatterns = new Pattern[ALTERNATIVE_PROVIDER_NAME_PATTERNS.length];
        for (int i = 0; i < ALTERNATIVE_PROVIDER_NAME_PATTERNS.length; i++) {
            alternativeProviderNamePatterns[i] = Pattern.compile(
                ALTERNATIVE_PROVIDER_NAME_PATTERNS[i], Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
            
            // Provider-Name extrahieren
            String providerName = extractProviderName(htmlContent, signalId);
            
            // Zuerst versuchen: description Array Format (JavaScript)
            SignalData descriptionResult = parseDescriptionArray(htmlContent, signalId, providerName);
            if (descriptionResult != null) {
                LOGGER.info("Successfully parsed using description array format for signal: " + signalId);
                return descriptionResult;
            }
            
            // Fallback: Traditionelle Pattern-Matching
            return parseTraditionalFormat(htmlContent, signalId, providerName);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Parsen von Signal: " + signalId, e);
            return null;
        }
    }
    
    /**
     * NEU: Extrahiert den Provider-Namen aus dem HTML
     * 
     * @param htmlContent Der HTML-Inhalt
     * @param signalId Die Signal-ID für Logging
     * @return Der Provider-Name oder "Unbekannt" falls nicht gefunden
     */
    private String extractProviderName(String htmlContent, String signalId) {
        try {
            // Hauptpattern versuchen
            Matcher matcher = providerNamePattern.matcher(htmlContent);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isEmpty()) {
                    LOGGER.info("Provider-Name gefunden (Hauptpattern): " + name + " für Signal: " + signalId);
                    return cleanProviderName(name);
                }
            }
            
            // Alternative Pattern versuchen
            for (int i = 0; i < alternativeProviderNamePatterns.length; i++) {
                matcher = alternativeProviderNamePatterns[i].matcher(htmlContent);
                if (matcher.find()) {
                    String name = matcher.group(1).trim();
                    if (!name.isEmpty()) {
                        LOGGER.info("Provider-Name gefunden (Alt-Pattern " + i + "): " + name + " für Signal: " + signalId);
                        return cleanProviderName(name);
                    }
                }
            }
            
            LOGGER.warning("Provider-Name nicht gefunden für Signal: " + signalId);
            return "Unbekannt";
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Extrahieren des Provider-Namens für Signal: " + signalId, e);
            return "Unbekannt";
        }
    }
    
    /**
     * NEU: Bereinigt den Provider-Namen
     * 
     * @param rawName Der rohe Provider-Name
     * @return Der bereinigte Provider-Name
     */
    private String cleanProviderName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "Unbekannt";
        }
        
        // HTML-Entities dekodieren und bereinigen
        String cleaned = rawName
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"")
            .replaceAll("&#39;", "'")
            .replaceAll("\\s+", " ")  // Mehrfache Leerzeichen zu einem
            .trim();
        
        // Maximale Länge begrenzen
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 47) + "...";
        }
        
        return cleaned;
    }
    
    /**
     * Parst das JavaScript description Array Format
     * Format: description:['Kontostand: 53 745.30 HKD','Floating Profit: 0.00 HKD']
     */
    private SignalData parseDescriptionArray(String htmlContent, String signalId, String providerName) {
        Matcher matcher = descriptionArrayPattern.matcher(htmlContent);
        
        if (matcher.find()) {
            String balanceStr = matcher.group(1);
            String floatingStr = matcher.group(2);
            
            LOGGER.info("Description Array gefunden für Signal " + signalId + ": [" + balanceStr + ", " + floatingStr + "]");
            
            // Equity parsen
            EquityCurrencyPair equityPair = parseEquityFromString(balanceStr);
            if (equityPair == null) {
                LOGGER.warning("Equity konnte nicht geparst werden: " + balanceStr);
                return null;
            }
            
            // Floating Profit parsen
            EquityCurrencyPair floatingPair = parseFloatingFromString(floatingStr);
            if (floatingPair == null) {
                LOGGER.warning("Floating Profit konnte nicht geparst werden: " + floatingStr);
                return null;
            }
            
            // Währung validieren
            if (!equityPair.currency.equals(floatingPair.currency)) {
                LOGGER.warning("Währungen stimmen nicht überein: " + equityPair.currency + " vs " + floatingPair.currency);
                return null;
            }
            
            SignalData result = new SignalData(
                signalId,
                providerName,  // NEU: Provider-Name hinzufügen
                equityPair.value,
                floatingPair.value,
                equityPair.currency,
                LocalDateTime.now()
            );
            
            LOGGER.info("SignalData erfolgreich aus Description Array erstellt: " + result.getSummary());
            return result;
        }
        
        LOGGER.fine("Kein Description Array gefunden für Signal: " + signalId);
        return null;
    }
    
    /**
     * Parst traditionelles HTML-Format
     */
    private SignalData parseTraditionalFormat(String htmlContent, String signalId, String providerName) {
        LOGGER.info("Verwende traditionelles Pattern-Matching für Signal: " + signalId);
        
        // Equity parsen
        EquityCurrencyPair equityPair = parseEquityFromHtml(htmlContent);
        if (equityPair == null) {
            LOGGER.warning("Equity konnte nicht aus HTML geparst werden für Signal: " + signalId);
            return null;
        }
        
        // Floating Profit parsen
        EquityCurrencyPair floatingPair = parseFloatingFromHtml(htmlContent);
        if (floatingPair == null) {
            LOGGER.warning("Floating Profit konnte nicht aus HTML geparst werden für Signal: " + signalId);
            return null;
        }
        
        // Währung validieren
        if (!equityPair.currency.equals(floatingPair.currency)) {
            LOGGER.warning("Währungen stimmen nicht überein: " + equityPair.currency + " vs " + floatingPair.currency + " für Signal: " + signalId);
            return null;
        }
        
        SignalData result = new SignalData(
            signalId,
            providerName,  // NEU: Provider-Name hinzufügen
            equityPair.value,
            floatingPair.value,
            equityPair.currency,
            LocalDateTime.now()
        );
        
        LOGGER.info("SignalData erfolgreich aus traditionellem Format erstellt: " + result.getSummary());
        return result;
    }
    
    /**
     * Parst Equity aus einer String-Zeile
     */
    private EquityCurrencyPair parseEquityFromString(String text) {
        // Hauptpattern versuchen
        Matcher matcher = kontostandardPattern.matcher(text);
        if (matcher.find()) {
            return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
        }
        
        // Alternative Pattern versuchen
        for (Pattern pattern : alternativeKontostandPatterns) {
            matcher = pattern.matcher(text);
            if (matcher.find()) {
                return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
            }
        }
        
        return null;
    }
    
    /**
     * Parst Floating Profit aus einer String-Zeile
     */
    private EquityCurrencyPair parseFloatingFromString(String text) {
        // Hauptpattern versuchen
        Matcher matcher = floatingProfitPattern.matcher(text);
        if (matcher.find()) {
            return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
        }
        
        // Alternative Pattern versuchen
        for (Pattern pattern : alternativeFloatingPatterns) {
            matcher = pattern.matcher(text);
            if (matcher.find()) {
                return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
            }
        }
        
        return null;
    }
    
    /**
     * Parst Equity aus HTML-Inhalt
     */
    private EquityCurrencyPair parseEquityFromHtml(String htmlContent) {
        // Hauptpattern versuchen
        Matcher matcher = kontostandardPattern.matcher(htmlContent);
        if (matcher.find()) {
            return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
        }
        
        // Alternative Pattern versuchen
        for (Pattern pattern : alternativeKontostandPatterns) {
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
            }
        }
        
        return null;
    }
    
    /**
     * Parst Floating Profit aus HTML-Inhalt
     */
    private EquityCurrencyPair parseFloatingFromHtml(String htmlContent) {
        // Hauptpattern versuchen
        Matcher matcher = floatingProfitPattern.matcher(htmlContent);
        if (matcher.find()) {
            return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
        }
        
        // Alternative Pattern versuchen
        for (Pattern pattern : alternativeFloatingPatterns) {
            matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                return createEquityCurrencyPair(matcher.group(1), matcher.group(2));
            }
        }
        
        return null;
    }
    
    /**
     * Erstellt ein EquityCurrencyPair aus Wert und Währung-Strings
     */
    private EquityCurrencyPair createEquityCurrencyPair(String valueStr, String currencyStr) {
        try {
            // Wert bereinigen und parsen
            String cleanValue = valueStr.replaceAll("[^0-9.,-]", "").trim();
            cleanValue = cleanValue.replace(",", "");  // Tausendertrennzeichen entfernen
            
            if (cleanValue.isEmpty()) {
                return null;
            }
            
            double value = Double.parseDouble(cleanValue);
            String currency = currencyStr.trim().toUpperCase();
            
            if (currency.length() != 3) {
                LOGGER.warning("Ungültige Währung: " + currency);
                return null;
            }
            
            return new EquityCurrencyPair(value, currency);
            
        } catch (NumberFormatException e) {
            LOGGER.warning("Fehler beim Parsen des Wertes: " + valueStr + " -> " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Hilfsdatenklasse für Wert-Währung-Paare
     */
    private static class EquityCurrencyPair {
        final double value;
        final String currency;
        
        EquityCurrencyPair(double value, String currency) {
            this.value = value;
            this.currency = currency;
        }
    }
}
