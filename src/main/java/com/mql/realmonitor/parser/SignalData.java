package com.mql.realmonitor.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * ERWEITERT: Model-Klasse für Signalprovider-Daten mit Profit
 * Repräsentiert die extrahierten Daten eines MQL5-Signalproviders
 * NEU: Profit-Feld für Gesamtgewinn des Signals
 * KORRIGIERT: Echter Equity Drawdown anstatt Floating Profit %
 * NEU: Total Value Drawdown für Konsistenz zwischen Chart und Tabelle
 */
public class SignalData {
    
    private static final Logger LOGGER = Logger.getLogger(SignalData.class.getName());
    
    // Formatter für Zeitstempel
    public static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER = 
    	    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    
    private final String signalId;
    private final String providerName;
    private final double equity;
    private final double floatingProfit;
    private final double profit; // NEU: Gesamtprofit des Signals
    private final String currency;
    private final LocalDateTime timestamp;
    
    /**
     * Konstruktor für SignalData mit Profit
     * 
     * @param signalId Die eindeutige Signal-ID
     * @param providerName Der Name des Signal-Providers
     * @param equity Der aktuelle Kontostand
     * @param floatingProfit Der aktuelle Floating Profit
     * @param profit Der Gesamtprofit des Signals
     * @param currency Die Währung (z.B. USD, EUR)
     * @param timestamp Der Zeitstempel der Datenerhebung
     */
    public SignalData(String signalId, String providerName, double equity, double floatingProfit, 
                     double profit, String currency, LocalDateTime timestamp) {
        this.signalId = signalId;
        this.providerName = providerName != null ? providerName : "Unbekannt";
        this.equity = equity;
        this.floatingProfit = floatingProfit;
        this.profit = profit;
        this.currency = currency;
        this.timestamp = timestamp;
        
        // DIAGNOSTIK: Logge Erstellung (ohne falschen Drawdown)
        if (LOGGER.isLoggable(java.util.logging.Level.FINE)) {
            LOGGER.fine("SignalData erstellt: Signal=" + signalId + 
                       ", Equity=" + equity + 
                       ", Floating=" + floatingProfit + 
                       ", Profit=" + profit +
                       " (Echter Drawdown benötigt Peak-Wert)");
        }
    }
    
    /**
     * Konstruktor für Rückwärtskompatibilität (ohne Profit)
     */
    public SignalData(String signalId, String providerName, double equity, double floatingProfit, 
                     String currency, LocalDateTime timestamp) {
        this(signalId, providerName, equity, floatingProfit, 0.0, currency, timestamp);
    }
    
    /**
     * Konstruktor für Rückwärtskompatibilität (ohne Provider-Name)
     */
    public SignalData(String signalId, double equity, double floatingProfit, 
                     String currency, LocalDateTime timestamp) {
        this(signalId, null, equity, floatingProfit, 0.0, currency, timestamp);
    }
    
    /**
     * Copy-Konstruktor mit neuem Zeitstempel
     */
    public SignalData(SignalData other, LocalDateTime newTimestamp) {
        this.signalId = other.signalId;
        this.providerName = other.providerName;
        this.equity = other.equity;
        this.floatingProfit = other.floatingProfit;
        this.profit = other.profit;
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
    
    public double getProfit() { // NEU
        return profit;
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
     * NEU: Berechnet den TOTAL VALUE Drawdown in Prozent (KONSISTENT MIT CHART)
     * 
     * @param peakTotalValue Der historische Peak-Total-Value
     * @return Der Total Value Drawdown in Prozent
     */
    public double getTotalValueDrawdownPercent(double peakTotalValue) {
        if (peakTotalValue == 0.0) {
            LOGGER.warning("TOTAL VALUE DRAWDOWN BERECHNUNG: Peak Total Value ist 0 für Signal " + signalId + " - kann Drawdown nicht berechnen");
            return 0.0;
        }
        
        double currentTotalValue = getTotalValue();
        
        if (Double.isNaN(currentTotalValue) || Double.isInfinite(currentTotalValue)) {
            LOGGER.warning("TOTAL VALUE DRAWDOWN BERECHNUNG: Total Value ist ungültig (" + currentTotalValue + ") für Signal " + signalId);
            return 0.0;
        }
        
        if (Double.isNaN(peakTotalValue) || Double.isInfinite(peakTotalValue)) {
            LOGGER.warning("TOTAL VALUE DRAWDOWN BERECHNUNG: Peak Total Value ist ungültig (" + peakTotalValue + ") für Signal " + signalId);
            return 0.0;
        }
        
        // TOTAL VALUE DRAWDOWN: (Current Total - Peak Total) / Peak Total * 100
        // IDENTISCH MIT CHART-BERECHNUNG!
        double result = ((currentTotalValue - peakTotalValue) / peakTotalValue) * 100.0;
        
        // Zusätzliche Validierung
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            LOGGER.warning("TOTAL VALUE DRAWDOWN BERECHNUNG: Ergebnis ist ungültig (" + result + ") für Signal " + signalId + 
                          " - Current Total=" + currentTotalValue + ", Peak=" + peakTotalValue);
            return 0.0;
        }
        
        // DIAGNOSTIK: Detailliertes Logging für Total Value Drawdown
        LOGGER.fine("TOTAL VALUE DRAWDOWN für Signal " + signalId + ": " + 
                   String.format("(%.6f - %.6f) / %.6f * 100 = %.6f%%", currentTotalValue, peakTotalValue, peakTotalValue, result));
        
        return result;
    }
    
    /**
     * NEU: Formatiert den TOTAL VALUE Drawdown für Anzeige (KONSISTENT MIT CHART)
     * 
     * @param peakTotalValue Der historische Peak-Total-Value
     * @return Formatierter Total Value Drawdown mit Prozentzeichen und Vorzeichen
     */
    public String getFormattedTotalValueDrawdown(double peakTotalValue) {
        double drawdownPercent = getTotalValueDrawdownPercent(peakTotalValue);
        
        // Fallback für ungültige Werte
        if (Double.isNaN(drawdownPercent) || Double.isInfinite(drawdownPercent)) {
            return "N/A";
        }
        
        String sign = drawdownPercent >= 0 ? "+" : "";
        
        // Dynamische Präzision basierend auf Wertegröße
        if (Math.abs(drawdownPercent) < 0.01) {
            // Sehr kleine Werte: 6 Dezimalstellen
            return String.format("%s%.6f%%", sign, drawdownPercent);
        } else if (Math.abs(drawdownPercent) < 1.0) {
            // Kleine Werte: 4 Dezimalstellen
            return String.format("%s%.4f%%", sign, drawdownPercent);
        } else {
            // Normale Werte: 2 Dezimalstellen
            return String.format("%s%.2f%%", sign, drawdownPercent);
        }
    }
    
    /**
     * NEU: Berechnet den ECHTEN Equity Drawdown in Prozent
     * 
     * @param peakEquity Der historische Peak-Equity-Wert
     * @return Der echte Equity Drawdown in Prozent
     */
    public double getTrueEquityDrawdownPercent(double peakEquity) {
        if (peakEquity == 0.0) {
            LOGGER.warning("DRAWDOWN BERECHNUNG: Peak Equity ist 0 für Signal " + signalId + " - kann Drawdown nicht berechnen");
            return 0.0;
        }
        
        if (Double.isNaN(equity) || Double.isInfinite(equity)) {
            LOGGER.warning("DRAWDOWN BERECHNUNG: Equity ist ungültig (" + equity + ") für Signal " + signalId);
            return 0.0;
        }
        
        if (Double.isNaN(peakEquity) || Double.isInfinite(peakEquity)) {
            LOGGER.warning("DRAWDOWN BERECHNUNG: Peak Equity ist ungültig (" + peakEquity + ") für Signal " + signalId);
            return 0.0;
        }
        
        // ECHTER DRAWDOWN: (Current Equity - Peak Equity) / Peak Equity * 100
        double result = ((equity - peakEquity) / peakEquity) * 100.0;
        
        // Zusätzliche Validierung
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            LOGGER.warning("DRAWDOWN BERECHNUNG: Ergebnis ist ungültig (" + result + ") für Signal " + signalId + 
                          " - Equity=" + equity + ", Peak=" + peakEquity);
            return 0.0;
        }
        
        // DIAGNOSTIK: Detailliertes Logging für Drawdown-Berechnung
        LOGGER.fine("ECHTER DRAWDOWN für Signal " + signalId + ": " + 
                   String.format("(%.6f - %.6f) / %.6f * 100 = %.6f%%", equity, peakEquity, peakEquity, result));
        
        return result;
    }
    
    /**
     * @deprecated Diese Methode berechnet NICHT den echten Equity Drawdown, sondern Floating Profit %.
     * Verwende getTrueEquityDrawdownPercent(peakEquity) für echten Drawdown oder
     * getTotalValueDrawdownPercent(peakTotalValue) für Total Value Drawdown.
     */
    @Deprecated
    public double getEquityDrawdownPercent() {
        LOGGER.warning("DEPRECATED: getEquityDrawdownPercent() berechnet nur Floating Profit %. Verwende getTotalValueDrawdownPercent(peakTotalValue) für Konsistenz mit Chart");
        
        if (equity == 0.0) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Equity ist 0 für Signal " + signalId + " - kann Prozentsatz nicht berechnen");
            return 0.0;
        }
        
        if (Double.isNaN(equity) || Double.isInfinite(equity)) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Equity ist ungültig (" + equity + ") für Signal " + signalId);
            return 0.0;
        }
        
        if (Double.isNaN(floatingProfit) || Double.isInfinite(floatingProfit)) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: FloatingProfit ist ungültig (" + floatingProfit + ") für Signal " + signalId);
            return 0.0;
        }
        
        double result = (floatingProfit / equity) * 100.0;
        
        // Zusätzliche Validierung
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Ergebnis ist ungültig (" + result + ") für Signal " + signalId + 
                          " - Equity=" + equity + ", FloatingProfit=" + floatingProfit);
            return 0.0;
        }
        
        // DIAGNOSTIK: Detailliertes Logging für Floating Profit %
        LOGGER.fine("FLOATING PROFIT % für Signal " + signalId + ": " + 
                   String.format("%.2f / %.2f * 100 = %.6f%%", floatingProfit, equity, result));
        
        return result;
    }
    
    /**
     * NEU: Berechnet den Floating Profit als Prozentsatz der Equity
     * 
     * @return Der Floating Profit in Prozent
     */
    public double getFloatingProfitPercent() {
        if (equity == 0.0) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Equity ist 0 für Signal " + signalId + " - kann Prozentsatz nicht berechnen");
            return 0.0;
        }
        
        if (Double.isNaN(equity) || Double.isInfinite(equity) || 
            Double.isNaN(floatingProfit) || Double.isInfinite(floatingProfit)) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Ungültige Werte für Signal " + signalId + 
                          " - Equity=" + equity + ", FloatingProfit=" + floatingProfit);
            return 0.0;
        }
        
        double result = (floatingProfit / equity) * 100.0;
        
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            LOGGER.warning("FLOATING PROFIT % BERECHNUNG: Ungültiges Ergebnis (" + result + ") für Signal " + signalId);
            return 0.0;
        }
        
        return result;
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
     * NEU: Formatiert den Profit für Anzeige
     * 
     * @return Formatierter Profit mit Währung und Vorzeichen
     */
    public String getFormattedProfit() {
        String sign = profit >= 0 ? "+" : "";
        return String.format("%s%.2f %s", sign, profit, currency);
    }
    
    /**
     * KORRIGIERT: Formatiert den ECHTEN Equity Drawdown für Anzeige
     * 
     * @param peakEquity Der historische Peak-Equity-Wert
     * @return Formatierter echter Equity Drawdown mit Prozentzeichen und Vorzeichen
     */
    public String getFormattedTrueEquityDrawdown(double peakEquity) {
        double drawdownPercent = getTrueEquityDrawdownPercent(peakEquity);
        
        // Fallback für ungültige Werte
        if (Double.isNaN(drawdownPercent) || Double.isInfinite(drawdownPercent)) {
            return "N/A";
        }
        
        String sign = drawdownPercent >= 0 ? "+" : "";
        
        // Dynamische Präzision basierend auf Wertegröße
        if (Math.abs(drawdownPercent) < 0.01) {
            // Sehr kleine Werte: 6 Dezimalstellen
            return String.format("%s%.6f%%", sign, drawdownPercent);
        } else if (Math.abs(drawdownPercent) < 1.0) {
            // Kleine Werte: 4 Dezimalstellen
            return String.format("%s%.4f%%", sign, drawdownPercent);
        } else {
            // Normale Werte: 2 Dezimalstellen
            return String.format("%s%.2f%%", sign, drawdownPercent);
        }
    }
    
    /**
     * @deprecated Diese Methode verwendet die veraltete Drawdown-Berechnung. 
     * Verwende getFormattedTotalValueDrawdown(peakTotalValue) für Konsistenz mit Chart.
     */
    @Deprecated
    public String getFormattedEquityDrawdown() {
        LOGGER.warning("DEPRECATED: getFormattedEquityDrawdown() verwendet veraltete Berechnung. Verwende getFormattedTotalValueDrawdown(peakTotalValue) für Konsistenz mit Chart");
        
        double drawdownPercent = getEquityDrawdownPercent(); // Verwendet deprecated Methode
        
        // Fallback für ungültige Werte
        if (Double.isNaN(drawdownPercent) || Double.isInfinite(drawdownPercent)) {
            return "N/A";
        }
        
        String sign = drawdownPercent >= 0 ? "+" : "";
        
        // Dynamische Präzision basierend auf Wertegröße
        if (Math.abs(drawdownPercent) < 0.01) {
            // Sehr kleine Werte: 6 Dezimalstellen
            return String.format("%s%.6f%%", sign, drawdownPercent);
        } else if (Math.abs(drawdownPercent) < 1.0) {
            // Kleine Werte: 4 Dezimalstellen
            return String.format("%s%.4f%%", sign, drawdownPercent);
        } else {
            // Normale Werte: 2 Dezimalstellen
            return String.format("%s%.2f%%", sign, drawdownPercent);
        }
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
     * Format: Equity,FloatingProfit,Profit
     * 
     * @return CSV-formatierte Zeile für Tick-Datei
     */
    public String toTickFileEntry() {
        // Verwende Locale.US um sicherzustellen, dass Punkt als Dezimaltrennzeichen verwendet wird
        return String.format(Locale.US, "%.2f,%.2f,%.2f", equity, floatingProfit, profit);
    }
    
    /**
     * Erstellt einen vollständigen String für die Tick-Datei
     * Format: DD.MM.YYYY,HH:MM:SS,Equity,FloatingProfit,Profit
     * 
     * @return Vollständige CSV-Zeile für Tick-Datei
     */
    public String toFullTickFileEntry() {
        // Datum und Zeit separat formatieren für korrektes CSV-Format
        String dateStr = timestamp.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String timeStr = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return dateStr + "," + timeStr + "," + toTickFileEntry();
    }
    
    /**
     * VERBESSERT: Prüft ob die Signaldaten gültig sind (ohne deprecated Drawdown-Validierung)
     * 
     * @return true wenn alle Daten gültig sind, false sonst
     */
    public boolean isValid() {
        boolean basicValidation = signalId != null && !signalId.trim().isEmpty() &&
                                 currency != null && !currency.trim().isEmpty() &&
                                 currency.length() == 3 && // Standard 3-Buchstaben Währungscode
                                 !Double.isNaN(equity) && !Double.isInfinite(equity) &&
                                 !Double.isNaN(floatingProfit) && !Double.isInfinite(floatingProfit) &&
                                 !Double.isNaN(profit) && !Double.isInfinite(profit) &&
                                 timestamp != null;
        
        if (!basicValidation) {
            LOGGER.warning("SignalData Basis-Validierung fehlgeschlagen für Signal: " + signalId);
            return false;
        }
        
        LOGGER.fine("SignalData Validierung erfolgreich für Signal " + signalId);
        
        return true;
    }
    
    /**
     * Prüft ob sich die Werte seit dem letzten Datensatz geändert haben
     * 
     * @param other Das andere SignalData-Objekt zum Vergleich
     * @return true wenn sich Equity, Floating Profit oder Profit geändert haben
     */
    public boolean hasValuesChanged(SignalData other) {
        if (other == null) {
            return true;
        }
        
        boolean equityChanged = Double.compare(this.equity, other.equity) != 0;
        boolean floatingChanged = Double.compare(this.floatingProfit, other.floatingProfit) != 0;
        boolean profitChanged = Double.compare(this.profit, other.profit) != 0;
        
        if (equityChanged || floatingChanged || profitChanged) {
            LOGGER.fine("Werte geändert für Signal " + signalId + 
                       ": Equity " + other.equity + " -> " + this.equity + 
                       ", Floating " + other.floatingProfit + " -> " + this.floatingProfit +
                       ", Profit " + other.profit + " -> " + this.profit);
        }
        
        return equityChanged || floatingChanged || profitChanged;
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
     * NEU: Berechnet die Änderung des Profits im Vergleich zu anderen Daten
     * 
     * @param other Das andere SignalData-Objekt
     * @return Die Änderung des Profits
     */
    public double getProfitChange(SignalData other) {
        return other != null ? this.profit - other.profit : 0.0;
    }
    
    /**
     * NEU: Berechnet die Änderung des TOTAL VALUE Drawdowns im Vergleich zu anderen Daten
     * 
     * @param other Das andere SignalData-Objekt
     * @param thisPeakTotalValue Der Peak-Total-Value für dieses Signal
     * @param otherPeakTotalValue Der Peak-Total-Value für das andere Signal
     * @return Die Änderung des Total Value Drawdowns in Prozentpunkten
     */
    public double getTotalValueDrawdownChange(SignalData other, double thisPeakTotalValue, double otherPeakTotalValue) {
        if (other == null) {
            return getTotalValueDrawdownPercent(thisPeakTotalValue);
        }
        return this.getTotalValueDrawdownPercent(thisPeakTotalValue) - other.getTotalValueDrawdownPercent(otherPeakTotalValue);
    }
    
    /**
     * KORRIGIERT: Berechnet die Änderung des ECHTEN Drawdowns im Vergleich zu anderen Daten
     * 
     * @param other Das andere SignalData-Objekt
     * @param thisPeakEquity Der Peak-Equity-Wert für dieses Signal
     * @param otherPeakEquity Der Peak-Equity-Wert für das andere Signal
     * @return Die Änderung des echten Drawdowns in Prozentpunkten
     */
    public double getTrueDrawdownChange(SignalData other, double thisPeakEquity, double otherPeakEquity) {
        if (other == null) {
            return getTrueEquityDrawdownPercent(thisPeakEquity);
        }
        return this.getTrueEquityDrawdownPercent(thisPeakEquity) - other.getTrueEquityDrawdownPercent(otherPeakEquity);
    }
    
    /**
     * @deprecated Verwendet veraltete Drawdown-Berechnung. Verwende getTotalValueDrawdownChange().
     */
    @Deprecated
    public double getDrawdownChange(SignalData other) {
        LOGGER.warning("DEPRECATED: getDrawdownChange() verwendet veraltete Berechnung. Verwende getTotalValueDrawdownChange()");
        if (other == null) {
            return getEquityDrawdownPercent();
        }
        return this.getEquityDrawdownPercent() - other.getEquityDrawdownPercent();
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
     * VERBESSERT: Gibt eine zusammenfassende Beschreibung der Signaldaten zurück (mit Profit, ohne deprecated Drawdown)
     * 
     * @return Textuelle Beschreibung
     */
    public String getSummary() {
        return String.format("Signal %s (%s): %s (Floating: %s, Profit: %s) - %s", 
                           signalId, 
                           providerName,
                           getFormattedEquity(), 
                           getFormattedFloatingProfit(),
                           getFormattedProfit(),
                           getFormattedTimestamp());
    }
    
    /**
     * NEU: Gibt eine zusammenfassende Beschreibung mit Total Value Drawdown zurück
     * 
     * @param peakTotalValue Der historische Peak-Total-Value
     * @return Textuelle Beschreibung mit Total Value Drawdown
     */
    public String getSummaryWithTotalValueDrawdown(double peakTotalValue) {
        return String.format("Signal %s (%s): %s (Floating: %s, Profit: %s, Total Value Drawdown: %s) - %s", 
                           signalId, 
                           providerName,
                           getFormattedEquity(), 
                           getFormattedFloatingProfit(),
                           getFormattedProfit(),
                           getFormattedTotalValueDrawdown(peakTotalValue),
                           getFormattedTimestamp());
    }
    
    /**
     * NEU: Gibt eine zusammenfassende Beschreibung mit echtem Drawdown zurück
     * 
     * @param peakEquity Der historische Peak-Equity-Wert
     * @return Textuelle Beschreibung mit echtem Drawdown
     */
    public String getSummaryWithTrueDrawdown(double peakEquity) {
        return String.format("Signal %s (%s): %s (Floating: %s, Profit: %s, Drawdown: %s) - %s", 
                           signalId, 
                           providerName,
                           getFormattedEquity(), 
                           getFormattedFloatingProfit(),
                           getFormattedProfit(),
                           getFormattedTrueEquityDrawdown(peakEquity),
                           getFormattedTimestamp());
    }
    
    /**
     * NEU: Erstellt eine detaillierte Diagnose-Beschreibung
     * 
     * @return Detaillierte Diagnostik-Informationen
     */
    public String getDiagnosticSummary() {
        StringBuilder diag = new StringBuilder();
        diag.append("=== SIGNALDATA DIAGNOSTIK ===\n");
        diag.append("Signal ID: ").append(signalId).append("\n");
        diag.append("Provider: ").append(providerName).append("\n");
        diag.append("Equity: ").append(equity).append(" (valid: ").append(!Double.isNaN(equity) && !Double.isInfinite(equity)).append(")\n");
        diag.append("Floating Profit: ").append(floatingProfit).append(" (valid: ").append(!Double.isNaN(floatingProfit) && !Double.isInfinite(floatingProfit)).append(")\n");
        diag.append("Profit: ").append(profit).append(" (valid: ").append(!Double.isNaN(profit) && !Double.isInfinite(profit)).append(")\n");
        diag.append("Currency: ").append(currency).append(" (valid: ").append(currency != null && currency.length() == 3).append(")\n");
        diag.append("Timestamp: ").append(timestamp).append("\n");
        
        double floatingPercent = getFloatingProfitPercent();
        diag.append("Floating Profit %: ").append(String.format("%.6f%%", floatingPercent))
            .append(" (valid: ").append(!Double.isNaN(floatingPercent) && !Double.isInfinite(floatingPercent)).append(")\n");
        
        diag.append("Total Value: ").append(getTotalValue()).append("\n");
        diag.append("Is Valid: ").append(isValid()).append("\n");
        diag.append("HINWEIS: Echter Drawdown benötigt Peak-Werte\n");
        diag.append("HINWEIS: Total Value Drawdown für Chart-Konsistenz verwenden\n");
        
        return diag.toString();
    }
    
    @Override
    public String toString() {
        return String.format("SignalData{id='%s', name='%s', equity=%.2f, floating=%.2f, profit=%.2f, floatingPercent=%.6f%%, currency='%s', time='%s'}", 
                           signalId, providerName, equity, floatingProfit, profit, getFloatingProfitPercent(), currency, getFormattedTimestamp());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SignalData that = (SignalData) obj;
        return Double.compare(that.equity, equity) == 0 &&
               Double.compare(that.floatingProfit, floatingProfit) == 0 &&
               Double.compare(that.profit, profit) == 0 &&
               Objects.equals(signalId, that.signalId) &&
               Objects.equals(providerName, that.providerName) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(signalId, providerName, equity, floatingProfit, profit, currency, timestamp);
    }
    
    /**
     * Erstellt SignalData aus einer Tick-Datei-Zeile
     * Format: DD.MM.YYYY,HH:MM:SS,Equity,FloatingProfit[,Profit]
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
                LOGGER.warning("Tick-Datei-Zeile hat zu wenige Teile (" + parts.length + "): " + tickLine);
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
            
            // Profit parsen (falls vorhanden)
            double profit = 0.0;
            if (parts.length >= 5) {
                profit = Double.parseDouble(parts[4].trim());
            }
            
            SignalData result = new SignalData(signalId, null, equity, floatingProfit, profit, defaultCurrency, timestamp);
            
            LOGGER.fine("SignalData erfolgreich aus Tick-Zeile erstellt: " + result.getSummary());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Parsen der Tick-Datei-Zeile: " + tickLine + " -> " + e.getMessage());
            return null;
        }
    }
}