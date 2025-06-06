package com.mql.realmonitor.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.data.TickDataLoader.TickData;
import com.mql.realmonitor.data.TickDataLoader.TickDataSet;

/**
 * Berechnet Wochen- und Monatsgewinne basierend auf Tick-Daten
 * 
 * WeeklyProfit: Berechnet vom letzten Sonntag bis jetzt
 * MonthlyProfit: Berechnet vom 1. des aktuellen Monats bis jetzt
 */
public class PeriodProfitCalculator {
    
    private static final Logger LOGGER = Logger.getLogger(PeriodProfitCalculator.class.getName());
    
    /**
     * Ergebnis-Klasse für Profit-Berechnungen
     */
    public static class ProfitResult {
        private final double weeklyProfitPercent;
        private final double monthlyProfitPercent;
        private final boolean hasWeeklyData;
        private final boolean hasMonthlyData;
        private final String diagnosticInfo;
        
        public ProfitResult(double weeklyProfitPercent, double monthlyProfitPercent, 
                           boolean hasWeeklyData, boolean hasMonthlyData, String diagnosticInfo) {
            this.weeklyProfitPercent = weeklyProfitPercent;
            this.monthlyProfitPercent = monthlyProfitPercent;
            this.hasWeeklyData = hasWeeklyData;
            this.hasMonthlyData = hasMonthlyData;
            this.diagnosticInfo = diagnosticInfo;
        }
        
        public double getWeeklyProfitPercent() { return weeklyProfitPercent; }
        public double getMonthlyProfitPercent() { return monthlyProfitPercent; }
        public boolean hasWeeklyData() { return hasWeeklyData; }
        public boolean hasMonthlyData() { return hasMonthlyData; }
        public String getDiagnosticInfo() { return diagnosticInfo; }
        
        /**
         * Formatiert den Wochengewinn für die Anzeige
         */
        public String getFormattedWeeklyProfit() {
            if (!hasWeeklyData) {
                return "N/A";
            }
            String sign = weeklyProfitPercent >= 0 ? "+" : "";
            return String.format("%s%.2f%%", sign, weeklyProfitPercent);
        }
        
        /**
         * Formatiert den Monatsgewinn für die Anzeige
         */
        public String getFormattedMonthlyProfit() {
            if (!hasMonthlyData) {
                return "N/A";
            }
            String sign = monthlyProfitPercent >= 0 ? "+" : "";
            return String.format("%s%.2f%%", sign, monthlyProfitPercent);
        }
        
        @Override
        public String toString() {
            return String.format("ProfitResult{weekly=%s, monthly=%s}", 
                               getFormattedWeeklyProfit(), getFormattedMonthlyProfit());
        }
    }
    
    /**
     * Berechnet Wochen- und Monatsgewinne für einen Signal-Provider
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return ProfitResult mit den berechneten Werten
     */
    public static ProfitResult calculateProfits(String tickFilePath, String signalId) {
        if (tickFilePath == null || signalId == null) {
            LOGGER.warning("PROFIT DEBUG: Ungültige Parameter für Profit-Berechnung: tickFilePath=" + tickFilePath + ", signalId=" + signalId);
            return new ProfitResult(0.0, 0.0, false, false, "Ungültige Parameter");
        }
        
        LOGGER.info("PROFIT DEBUG: Berechne Profits für Signal " + signalId + " - Tick-Datei: " + tickFilePath);
        
        // Tick-Daten laden
        TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
        if (dataSet == null || dataSet.getTickCount() == 0) {
            LOGGER.warning("PROFIT DEBUG: Keine Tick-Daten verfügbar für Signal " + signalId + " - Datei: " + tickFilePath);
            return new ProfitResult(0.0, 0.0, false, false, "Keine Tick-Daten verfügbar: " + tickFilePath);
        }
        
        List<TickData> ticks = dataSet.getTicks();
        LOGGER.info("PROFIT DEBUG: " + ticks.size() + " Ticks geladen für Signal " + signalId);
        
        // Mindestens 2 Ticks erforderlich für sinnvolle Profit-Berechnung
        if (ticks.size() < 2) {
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Nur " + ticks.size() + " Tick(s) verfügbar, brauche mindestens 2 für Profit-Berechnung");
            return new ProfitResult(0.0, 0.0, false, false, "Nicht genügend Daten für Profit-Berechnung");
        }
        
        // Aktuelle Equity (letzter Tick)
        double currentEquity = dataSet.getLatestTick().getEquity();
        LocalDateTime now = LocalDateTime.now();
        
        // Referenzzeitpunkte ermitteln
        LocalDateTime weekStart = getLastSunday(now);
        LocalDateTime monthStart = getFirstOfCurrentMonth(now);
        
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Aktuell: " + now + 
                   ", Wochenstart: " + weekStart + ", Monatsstart: " + monthStart + 
                   ", Aktuelle Equity: " + currentEquity);
        
        // Zeige erste und letzte Ticks für Debugging
        TickData firstTick = ticks.get(0);
        TickData lastTick = ticks.get(ticks.size() - 1);
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Erster Tick: " + firstTick.getTimestamp() + 
                   " (Equity: " + firstTick.getEquity() + "), Letzter Tick: " + lastTick.getTimestamp() + 
                   " (Equity: " + lastTick.getEquity() + ")");
        
        // VERBESSERTE EQUITY-SUCHE: Verwende intelligente Fallback-Strategien
        EquitySearchResult weekEquityResult = findBestEquityForReference(ticks, weekStart, "Wochenstart", signalId);
        EquitySearchResult monthEquityResult = findBestEquityForReference(ticks, monthStart, "Monatsstart", signalId);
        
        // Profit-Berechnungen
        boolean hasWeeklyData = weekEquityResult.hasValidData();
        boolean hasMonthlyData = monthEquityResult.hasValidData();
        
        double weeklyProfit = 0.0;
        double monthlyProfit = 0.0;
        
        if (hasWeeklyData) {
            double weekStartEquity = weekEquityResult.getEquity();
            weeklyProfit = ((currentEquity - weekStartEquity) / weekStartEquity) * 100.0;
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Wochenprofit berechnet: (" + currentEquity + " - " + 
                       weekStartEquity + ") / " + weekStartEquity + " * 100 = " + String.format("%.4f%%", weeklyProfit));
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine gültigen Wochendaten verfügbar");
        }
        
        if (hasMonthlyData) {
            double monthStartEquity = monthEquityResult.getEquity();
            monthlyProfit = ((currentEquity - monthStartEquity) / monthStartEquity) * 100.0;
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Monatsprofit berechnet: (" + currentEquity + " - " + 
                       monthStartEquity + ") / " + monthStartEquity + " * 100 = " + String.format("%.4f%%", monthlyProfit));
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine gültigen Monatsdaten verfügbar");
        }
        
        // Diagnostik-Informationen
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Signal: ").append(signalId).append(", ");
        diagnostic.append("Current Equity: ").append(String.format("%.2f", currentEquity)).append(", ");
        diagnostic.append("Week: ").append(weekEquityResult.getDiagnosticInfo()).append(", ");
        diagnostic.append("Month: ").append(monthEquityResult.getDiagnosticInfo());
        
        LOGGER.info("PROFIT DEBUG: Ergebnis für " + signalId + ": WeeklyProfit=" + String.format("%.4f%%", weeklyProfit) + 
                   ", MonthlyProfit=" + String.format("%.4f%%", monthlyProfit));
        
        return new ProfitResult(weeklyProfit, monthlyProfit, hasWeeklyData, hasMonthlyData, diagnostic.toString());
    }
    
    /**
     * Hilfsobjekt für Equity-Suchergebnisse
     */
    private static class EquitySearchResult {
        private final Double equity;
        private final LocalDateTime timestamp;
        private final String strategy;
        private final boolean hasData;
        
        public EquitySearchResult(Double equity, LocalDateTime timestamp, String strategy) {
            this.equity = equity;
            this.timestamp = timestamp;
            this.strategy = strategy;
            this.hasData = (equity != null && equity > 0);
        }
        
        public static EquitySearchResult noData(String reason) {
            return new EquitySearchResult(null, null, reason);
        }
        
        public boolean hasValidData() { return hasData; }
        public double getEquity() { return equity != null ? equity : 0.0; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public String getDiagnosticInfo() {
            if (!hasData) {
                return strategy + " (keine Daten)";
            }
            return String.format("%s=%.2f am %s (%s)", 
                               strategy.split(" ")[0], equity, 
                               timestamp != null ? timestamp.toLocalDate() : "unknown", strategy);
        }
    }
    
    /**
     * VERBESSERTE METHODE: Findet die beste verfügbare Equity für einen Referenzzeitpunkt
     * 
     * Strategie:
     * 1. Suche exakt am Referenzzeitpunkt
     * 2. Suche den nächsten Tick nach dem Referenzzeitpunkt (für "seit diesem Zeitpunkt")
     * 3. FALLBACK: Suche den letzten verfügbaren Tick VOR dem Referenzzeitpunkt
     * 4. Als letzte Option: Verwende ersten Tick (nur wenn alle anderen fehlschlagen)
     */
    private static EquitySearchResult findBestEquityForReference(List<TickData> ticks, LocalDateTime referenceTime, String purpose, String signalId) {
        if (ticks == null || ticks.isEmpty()) {
            LOGGER.warning("EQUITY DEBUG: Keine Ticks verfügbar für " + purpose + " von Signal " + signalId);
            return EquitySearchResult.noData("keine Ticks");
        }
        
        LOGGER.info("EQUITY DEBUG: Suche " + purpose + " für " + referenceTime + " in " + ticks.size() + " Ticks (Signal " + signalId + ")");
        
        LocalDateTime firstTickTime = ticks.get(0).getTimestamp();
        LocalDateTime lastTickTime = ticks.get(ticks.size() - 1).getTimestamp();
        LOGGER.info("EQUITY DEBUG: Verfügbarer Zeitraum: " + firstTickTime + " bis " + lastTickTime);
        
        // Strategie 1: Suche Tick am oder nach dem Referenzzeitpunkt (bevorzugt)
        for (TickData tick : ticks) {
            if (!tick.getTimestamp().isBefore(referenceTime)) {
                LOGGER.info("EQUITY DEBUG: " + purpose + " - Strategie 1 (ab Referenzzeit): " + tick.getEquity() + 
                           " am " + tick.getTimestamp() + " (Signal " + signalId + ")");
                return new EquitySearchResult(tick.getEquity(), tick.getTimestamp(), 
                                            "Strategie 1 (ab " + referenceTime.toLocalDate() + ")");
            }
        }
        
        // Strategie 2: FALLBACK - Suche letzten verfügbaren Tick VOR dem Referenzzeitpunkt
        LOGGER.info("EQUITY DEBUG: " + purpose + " - Kein Tick ab " + referenceTime + " gefunden, suche letzten Tick davor (Signal " + signalId + ")");
        
        TickData lastTickBefore = null;
        for (TickData tick : ticks) {
            if (tick.getTimestamp().isBefore(referenceTime)) {
                lastTickBefore = tick;  // Überschreibe mit jedem späteren Tick vor der Referenzzeit
            } else {
                break; // Wir haben den Referenzzeitpunkt überschritten
            }
        }
        
        if (lastTickBefore != null) {
            LOGGER.info("EQUITY DEBUG: " + purpose + " - Strategie 2 (letzter vor Referenzzeit): " + lastTickBefore.getEquity() + 
                       " am " + lastTickBefore.getTimestamp() + " (Signal " + signalId + ")");
            return new EquitySearchResult(lastTickBefore.getEquity(), lastTickBefore.getTimestamp(), 
                                        "Strategie 2 (letzter vor " + referenceTime.toLocalDate() + ")");
        }
        
        // Strategie 3: Als absolute letzte Option - verwende ersten verfügbaren Tick
        // (nur wenn alle Ticks nach dem Referenzzeitpunkt liegen)
        LOGGER.warning("EQUITY DEBUG: " + purpose + " - Alle Ticks liegen nach " + referenceTime + 
                      ", verwende ersten verfügbaren als Notlösung (Signal " + signalId + ")");
        TickData firstTick = ticks.get(0);
        return new EquitySearchResult(firstTick.getEquity(), firstTick.getTimestamp(), 
                                    "Strategie 3 (Notlösung - erster Tick)");
    }
    
    /**
     * Ermittelt den letzten Sonntag (Start der aktuellen Woche)
     * 
     * @param referenceDate Das Referenzdatum
     * @return LocalDateTime des letzten Sonntags um 00:00:00
     */
    private static LocalDateTime getLastSunday(LocalDateTime referenceDate) {
        LocalDate date = referenceDate.toLocalDate();
        
        // Wenn heute Sonntag ist, nehme heute
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LOGGER.info("DATUM DEBUG: Heute ist Sonntag (" + date + "), verwende heutigen Tag als Wochenstart");
            return date.atTime(LocalTime.MIN); // 00:00:00
        }
        
        // Sonst gehe zurück bis zum letzten Sonntag
        // Montag=1, Dienstag=2, ..., Samstag=6, Sonntag=7
        int daysToSubtract = date.getDayOfWeek().getValue(); // getValue() gibt 1-7 zurück
        
        // Für Montag (1) → 1 Tag zurück, Dienstag (2) → 2 Tage zurück, usw.
        LocalDate lastSunday = date.minusDays(daysToSubtract);
        
        LOGGER.info("DATUM DEBUG: Heute ist " + date.getDayOfWeek() + " (" + date + 
                   "), letzter Sonntag ist " + lastSunday + " (" + daysToSubtract + " Tage zurück)");
        
        return lastSunday.atTime(LocalTime.MIN);
    }
    
    /**
     * Ermittelt den ersten Tag des aktuellen Monats
     * 
     * @param referenceDate Das Referenzdatum
     * @return LocalDateTime des 1. des Monats um 00:00:00
     */
    private static LocalDateTime getFirstOfCurrentMonth(LocalDateTime referenceDate) {
        LocalDate date = referenceDate.toLocalDate();
        LocalDate firstOfMonth = date.withDayOfMonth(1);
        return firstOfMonth.atTime(LocalTime.MIN);
    }
    
    /**
     * VERALTETE METHODE - wird durch findBestEquityForReference ersetzt
     * Wird für Kompatibilität beibehalten, aber nicht mehr verwendet
     */
    @Deprecated
    private static Double findEquityAtOrAfter(List<TickData> ticks, LocalDateTime targetTime) {
        return findBestEquityForReference(ticks, targetTime, "Legacy", "unknown").getEquity();
    }
    
    /**
     * Test-Methode für die Datums-Berechnungen
     * 
     * @param testDate Das Testdatum
     * @return Diagnostic-String mit berechneten Daten
     */
    public static String testDateCalculations(LocalDateTime testDate) {
        LocalDateTime weekStart = getLastSunday(testDate);
        LocalDateTime monthStart = getFirstOfCurrentMonth(testDate);
        
        return String.format("Test für %s: Wochenstart=%s, Monatsstart=%s", 
                           testDate, weekStart, monthStart);
    }
    
    /**
     * Erstellt eine detaillierte Diagnose für Debugging
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return Detaillierte Diagnose-Informationen
     */
    public static String createDetailedDiagnostic(String tickFilePath, String signalId) {
        StringBuilder diag = new StringBuilder();
        diag.append("=== PERIOD PROFIT DIAGNOSTIC ===\n");
        diag.append("Signal ID: ").append(signalId).append("\n");
        diag.append("Tick File: ").append(tickFilePath).append("\n");
        
        LocalDateTime now = LocalDateTime.now();
        diag.append("Reference Time: ").append(now).append("\n");
        diag.append("Week Start: ").append(getLastSunday(now)).append("\n");
        diag.append("Month Start: ").append(getFirstOfCurrentMonth(now)).append("\n");
        
        TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
        if (dataSet != null) {
            diag.append("Tick Count: ").append(dataSet.getTickCount()).append("\n");
            if (dataSet.getTickCount() > 0) {
                diag.append("First Tick: ").append(dataSet.getFirstTick().getTimestamp()).append("\n");
                diag.append("Last Tick: ").append(dataSet.getLatestTick().getTimestamp()).append("\n");
                diag.append("Current Equity: ").append(dataSet.getLatestTick().getEquity()).append("\n");
            }
        } else {
            diag.append("No tick data available\n");
        }
        
        ProfitResult result = calculateProfits(tickFilePath, signalId);
        diag.append("Calculation Result: ").append(result.toString()).append("\n");
        diag.append("Diagnostic Info: ").append(result.getDiagnosticInfo()).append("\n");
        
        return diag.toString();
    }
}