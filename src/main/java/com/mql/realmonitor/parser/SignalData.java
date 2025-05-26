package com.mql.realmonitor.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Model-Klasse für Signalprovider-Daten
 * Repräsentiert die extrahierten Daten eines MQL5-Signalproviders
 */
public class SignalData {
    
    // Formatter für Zeitstempel
    public static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = 
    	    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");  // KORRIGIERT: Leerzeichen statt Komma
    
    private final String signalId;
    private final String providerName;  // NEU: Provider-Name
    private final double equity;
    private final double floatingProfit;
    private final String currency;
    private final LocalDateTime timestamp;
    
    /**
     * Konstruktor für SignalData
     * 
     * @param signalId Die eindeutige Signal-ID
     * @param providerName Der Name des Signal-Providers
     * @param equity Der aktuelle Kontostand
     * @param floatingProfit Der aktuelle Floating Profit
     * @param currency Die Währung (z.B. USD, EUR)
     * @param timestamp Der Zeitstempel der Datenerhebung
     */
    public SignalData(String signalId, String providerName, double equity, double floatingProfit, 
                     String currency, LocalDateTime timestamp) {
        this.signalId = signalId;
        this.providerName = providerName != null ? providerName : "Unbekannt";
        this.equity = equity;
        this.floatingProfit = floatingProfit;
        this.currency = currency;
        this.timestamp = timestamp;
    }
    
    /**
     * Konstruktor für Rückwärtskompatibilität (ohne Provider-Name)
     * 
     * @param signalId Die eindeutige Signal-ID
     * @param equity Der aktuelle Kontostand
     * @param floatingProfit Der aktuelle Floating Profit
     * @param currency Die Währung (z.B. USD, EUR)
     * @param timestamp Der Zeitstempel der Datenerhebung
     */
    public SignalData(String signalId, double equity, double floatingProfit, 
                     String currency, LocalDateTime timestamp) {
        this(signalId, null, equity, floatingProfit, currency, timestamp);
    }
    
    /**
     * Copy-Konstruktor mit neuem Zeitstempel
     * 
     * @param other Das zu kopierende SignalData-Objekt
     * @param newTimestamp Der neue Zeitstempel
     */
    public SignalData(SignalData other, LocalDateTime newTimestamp) {
        this.signalId = other.signalId;
        this.providerName = other.providerName;
        this.equity = other.equity;
        this.floatingProfit = other.floatingProfit;
        this.currency = other.currency;
        this.timestamp = newTimestamp;
    }
    
    // Getter-Methoden
    
    public String getSignalId() {
        return signalId;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public double getEquity() {
        return equity;
    }
    
    public double getFloatingProfit() {
        return floatingProfit;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Berechnet den Gesamtwert (Equity + Floating Profit)
     * 
     * @return Der Gesamtwert
     */
    public double getTotalValue() {
        return equity + floatingProfit;
    }
    
    /**
     * Gibt den formatierten Zeitstempel zurück
     * 
     * @return Formatierter Zeitstempel für Anzeige
     */
    public String getFormattedTimestamp() {
        return timestamp.format(TIMESTAMP_FORMATTER);
    }
    
    /**
     * Gibt den Zeitstempel im Datei-Format zurück
     * 
     * @return Formatierter Zeitstempel für Datei-Output
     */
    public String getFileTimestamp() {
        return timestamp.format(FILE_TIMESTAMP_FORMATTER);
    }
    
    /**
     * Formatiert die Equity für Anzeige
     * 
     * @return Formatierte Equity mit Währung
     */
    public String getFormattedEquity() {
        return String.format("%.2f %s", equity, currency);
    }
    
    /**
     * Formatiert den Floating Profit für Anzeige
     * 
     * @return Formatierter Floating Profit mit Währung und Vorzeichen
     */
    public String getFormattedFloatingProfit() {
        String sign = floatingProfit >= 0 ? "+" : "";
        return String.format("%s%.2f %s", sign, floatingProfit, currency);
    }
    
    /**
     * Formatiert den Gesamtwert für Anzeige
     * 
     * @return Formatierter Gesamtwert mit Währung
     */
    public String getFormattedTotalValue() {
        return String.format("%.2f %s", getTotalValue(), currency);
    }
    
    /**
     * Erstellt einen String für die Tick-Datei
     * Format: Datum,Uhrzeit,Equity,FloatingProfit
     * 
     * @return CSV-formatierte Zeile für Tick-Datei
     */
    public String toTickFileEntry() {
        // Verwende Locale.US um sicherzustellen, dass Punkt als Dezimaltrennzeichen verwendet wird
        return String.format(Locale.US, "%.2f,%.2f", equity, floatingProfit);
    }
    
    /**
     * Erstellt einen vollständigen String für die Tick-Datei
     * Format: DD.MM.YYYY,HH:MM:SS,Equity,FloatingProfit
     * 
     * @return Vollständige CSV-Zeile für Tick-Datei
     */
    public String toFullTickFileEntry() {
        return getFileTimestamp() + "," + toTickFileEntry();
    }
    
    /**
     * Prüft ob die Signaldaten gültig sind
     * 
     * @return true wenn alle Daten gültig sind, false sonst
     */
    public boolean isValid() {
        return signalId != null && !signalId.trim().isEmpty() &&
               currency != null && !currency.trim().isEmpty() &&
               currency.length() == 3 && // Standard 3-Buchstaben Währungscode
               !Double.isNaN(equity) && !Double.isInfinite(equity) &&
               !Double.isNaN(floatingProfit) && !Double.isInfinite(floatingProfit) &&
               timestamp != null;
    }
    
    /**
     * Prüft ob sich die Werte seit dem letzten Datensatz geändert haben
     * 
     * @param other Das andere SignalData-Objekt zum Vergleich
     * @return true wenn sich Equity oder Floating Profit geändert haben
     */
    public boolean hasValuesChanged(SignalData other) {
        if (other == null) {
            return true;
        }
        
        return Double.compare(this.equity, other.equity) != 0 ||
               Double.compare(this.floatingProfit, other.floatingProfit) != 0;
    }
    
    /**
     * Berechnet die Änderung der Equity im Vergleich zu anderen Daten
     * 
     * @param other Das andere SignalData-Objekt
     * @return Die Änderung der Equity
     */
    public double getEquityChange(SignalData other) {
        return other != null ? this.equity - other.equity : 0.0;
    }
    
    /**
     * Berechnet die Änderung des Floating Profits im Vergleich zu anderen Daten
     * 
     * @param other Das andere SignalData-Objekt
     * @return Die Änderung des Floating Profits
     */
    public double getFloatingProfitChange(SignalData other) {
        return other != null ? this.floatingProfit - other.floatingProfit : 0.0;
    }
    
    /**
     * Erstellt eine Kopie mit aktualisiertem Zeitstempel
     * 
     * @return Neue SignalData-Instanz mit aktuellem Zeitstempel
     */
    public SignalData withCurrentTimestamp() {
        return new SignalData(this, LocalDateTime.now());
    }
    
    /**
     * Gibt eine zusammenfassende Beschreibung der Signaldaten zurück
     * 
     * @return Textuelle Beschreibung
     */
    public String getSummary() {
        return String.format("Signal %s (%s): %s (Floating: %s) - %s", 
                           signalId, 
                           providerName,
                           getFormattedEquity(), 
                           getFormattedFloatingProfit(),
                           getFormattedTimestamp());
    }
    
    @Override
    public String toString() {
        return String.format("SignalData{id='%s', name='%s', equity=%.2f, floating=%.2f, currency='%s', time='%s'}", 
                           signalId, providerName, equity, floatingProfit, currency, getFormattedTimestamp());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SignalData that = (SignalData) obj;
        return Double.compare(that.equity, equity) == 0 &&
               Double.compare(that.floatingProfit, floatingProfit) == 0 &&
               Objects.equals(signalId, that.signalId) &&
               Objects.equals(providerName, that.providerName) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(signalId, providerName, equity, floatingProfit, currency, timestamp);
    }
    
    /**
     * Erstellt SignalData aus einer Tick-Datei-Zeile
     * Format: DD.MM.YYYY,HH:MM:SS,Equity,FloatingProfit
     * 
     * @param signalId Die Signal-ID
     * @param tickLine Die Zeile aus der Tick-Datei
     * @param defaultCurrency Standard-Währung falls nicht in der Zeile enthalten
     * @return SignalData-Objekt oder null bei Parsing-Fehlern
     */
    public static SignalData fromTickFileLine(String signalId, String tickLine, String defaultCurrency) {
        try {
            if (tickLine == null || tickLine.trim().isEmpty()) {
                return null;
            }
            
            String[] parts = tickLine.split(",");
            if (parts.length < 4) {
                return null;
            }
            
            // Datum und Zeit parsen
            String dateStr = parts[0].trim();
            String timeStr = parts[1].trim();
            LocalDateTime timestamp = LocalDateTime.parse(dateStr + " " + timeStr, 
                                                         FILE_TIMESTAMP_FORMATTER);
            
            // Werte parsen
            double equity = Double.parseDouble(parts[2].trim());
            double floatingProfit = Double.parseDouble(parts[3].trim());
            
            return new SignalData(signalId, equity, floatingProfit, defaultCurrency, timestamp);
            
        } catch (Exception e) {
            return null;
        }
    }
}