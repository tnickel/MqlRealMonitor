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
 * PERFORMANCE-BASIERT: Verwendet Profit + FloatingProfit für stabile Berechnungen
 * - Ignoriert Ein-/Auszahlungen (die nur Equity beeinflussen)
 * - Gesamt-Performance = Profit + FloatingProfit (echte Trading-Performance)
 * - Prozentuale Berechnung relativ zum initialen Kapital
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
     * PERFORMANCE-BASIERT: Verwendet Profit + FloatingProfit für Ein-/Auszahlungs-unabhängige Berechnung
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return ProfitResult mit den berechneten Werten
     */
    public static ProfitResult calculateProfits(String tickFilePath, String signalId) {
        if (tickFilePath == null || signalId == null) {
            LOGGER.warning("PERFORMANCE DEBUG: Ungültige Parameter für Profit-Berechnung: tickFilePath=" + tickFilePath + ", signalId=" + signalId);
            return new ProfitResult(0.0, 0.0, false, false, "Ungültige Parameter");
        }
        
        LOGGER.info("PERFORMANCE DEBUG: Berechne Profits für Signal " + signalId + " - Tick-Datei: " + tickFilePath + " (PERFORMANCE-BASIERT)");
        
        // Tick-Daten laden
        TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
        if (dataSet == null || dataSet.getTickCount() == 0) {
            LOGGER.warning("PERFORMANCE DEBUG: Keine Tick-Daten verfügbar für Signal " + signalId + " - Datei: " + tickFilePath);
            return new ProfitResult(0.0, 0.0, false, false, "Keine Tick-Daten verfügbar: " + tickFilePath);
        }
        
        List<TickData> ticks = dataSet.getTicks();
        LOGGER.info("PERFORMANCE DEBUG: " + ticks.size() + " Ticks geladen für Signal " + signalId + " (PERFORMANCE-BASIERT)");
        
        // Mindestens 2 Ticks erforderlich für sinnvolle Profit-Berechnung
        if (ticks.size() < 2) {
            LOGGER.info("PERFORMANCE DEBUG: Signal " + signalId + " - Nur " + ticks.size() + " Tick(s) verfügbar, brauche mindestens 2 für Profit-Berechnung");
            return new ProfitResult(0.0, 0.0, false, false, "Nicht genügend Daten für Profit-Berechnung");
        }
        
        // PERFORMANCE-BASIERT: Aktuelle Gesamt-Performance (Profit + FloatingProfit)
        TickData latestTick = dataSet.getLatestTick();
        double currentPerformance = latestTick.getProfit() + latestTick.getFloatingProfit();
        
        // Initiale Equity als Basis für prozentuale Berechnungen (vom ersten Tick)
        double initialEquity = dataSet.getFirstTick().getEquity();
        
        LocalDateTime now = LocalDateTime.now();
        
        // Referenzzeitpunkte ermitteln
        LocalDateTime weekStart = getLastSunday(now);
        LocalDateTime monthStart = getFirstOfCurrentMonth(now);
        
        LOGGER.info("PERFORMANCE DEBUG: Signal " + signalId + " - Aktuell: " + now + 
                   ", Wochenstart: " + weekStart + ", Monatsstart: " + monthStart + 
                   ", Aktuelle Performance: " + currentPerformance + 
                   " (Profit: " + latestTick.getProfit() + " + Floating: " + latestTick.getFloatingProfit() + ")" +
                   ", Initiale Equity: " + initialEquity + " [PERFORMANCE-BASIERT]");
        
        // Zeige erste und letzte Ticks für Debugging
        TickData firstTick = ticks.get(0);
        TickData lastTick = ticks.get(ticks.size() - 1);
        LOGGER.info("PERFORMANCE DEBUG: Signal " + signalId + " - Erster Tick: " + firstTick.getTimestamp() + 
                   " (Performance: " + (firstTick.getProfit() + firstTick.getFloatingProfit()) + ")" +
                   ", Letzter Tick: " + lastTick.getTimestamp() + 
                   " (Performance: " + (lastTick.getProfit() + lastTick.getFloatingProfit()) + ") [PERFORMANCE-BASIERT]");
        
        // PERFORMANCE-BASIERT: Suche Performance für Referenzzeitpunkte
        PerformanceSearchResult weekPerformanceResult = findBestPerformanceForReference(ticks, weekStart, "Wochenstart", signalId);
        PerformanceSearchResult monthPerformanceResult = findBestPerformanceForReference(ticks, monthStart, "Monatsstart", signalId);
        
        // Performance-Berechnungen
        boolean hasWeeklyData = weekPerformanceResult.hasValidData();
        boolean hasMonthlyData = monthPerformanceResult.hasValidData();
        
        double weeklyProfit = 0.0;
        double monthlyProfit = 0.0;
        
        if (hasWeeklyData) {
            double weekStartPerformance = weekPerformanceResult.getPerformance();
            // Performance-Änderung relativ zur initialen Equity
            double performanceChange = currentPerformance - weekStartPerformance;
            weeklyProfit = (performanceChange / initialEquity) * 100.0;
            
            LOGGER.info("PERFORMANCE DEBUG: Signal " + signalId + " - Wochenprofit berechnet: " +
                       "(" + currentPerformance + " - " + weekStartPerformance + ") / " + initialEquity + " * 100 = " + 
                       String.format("%.4f%%", weeklyProfit) + " [PERFORMANCE-BASIERT]");
        } else {
            LOGGER.warning("PERFORMANCE DEBUG: Signal " + signalId + " - Keine gültigen Wochendaten verfügbar (PERFORMANCE-BASIERT)");
        }
        
        if (hasMonthlyData) {
            double monthStartPerformance = monthPerformanceResult.getPerformance();
            // Performance-Änderung relativ zur initialen Equity
            double performanceChange = currentPerformance - monthStartPerformance;
            monthlyProfit = (performanceChange / initialEquity) * 100.0;
            
            LOGGER.info("PERFORMANCE DEBUG: Signal " + signalId + " - Monatsprofit berechnet: " +
                       "(" + currentPerformance + " - " + monthStartPerformance + ") / " + initialEquity + " * 100 = " + 
                       String.format("%.4f%%", monthlyProfit) + " [PERFORMANCE-BASIERT]");
        } else {
            LOGGER.warning("PERFORMANCE DEBUG: Signal " + signalId + " - Keine gültigen Monatsdaten verfügbar (PERFORMANCE-BASIERT)");
        }
        
        // Diagnostik-Informationen
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Signal: ").append(signalId).append(", ");
        diagnostic.append("Current Performance: ").append(String.format("%.2f", currentPerformance)).append(", ");
        diagnostic.append("Initial Equity: ").append(String.format("%.2f", initialEquity)).append(", ");
        diagnostic.append("Week: ").append(weekPerformanceResult.getDiagnosticInfo()).append(", ");
        diagnostic.append("Month: ").append(monthPerformanceResult.getDiagnosticInfo()).append(" [PERFORMANCE-BASIERT]");
        
        LOGGER.info("PERFORMANCE DEBUG: Ergebnis für " + signalId + ": WeeklyProfit=" + String.format("%.4f%%", weeklyProfit) + 
                   ", MonthlyProfit=" + String.format("%.4f%%", monthlyProfit) + " [PERFORMANCE-BASIERT]");
        
        return new ProfitResult(weeklyProfit, monthlyProfit, hasWeeklyData, hasMonthlyData, diagnostic.toString());
    }
    
    /**
     * PERFORMANCE-BASIERT: Hilfsobjekt für Performance-Suchergebnisse (Profit + FloatingProfit)
     */
    private static class PerformanceSearchResult {
        private final Double performance;
        private final LocalDateTime timestamp;
        private final String strategy;
        private final boolean hasData;
        
        public PerformanceSearchResult(Double performance, LocalDateTime timestamp, String strategy) {
            this.performance = performance;
            this.timestamp = timestamp;
            this.strategy = strategy;
            this.hasData = (performance != null);
        }
        
        public static PerformanceSearchResult noData(String reason) {
            return new PerformanceSearchResult(null, null, reason);
        }
        
        public boolean hasValidData() { return hasData; }
        public double getPerformance() { return performance != null ? performance : 0.0; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public String getDiagnosticInfo() {
            if (!hasData) {
                return strategy + " (keine Daten)";
            }
            return String.format("%s=%.2f am %s (%s)", 
                               strategy.split(" ")[0], performance, 
                               timestamp != null ? timestamp.toLocalDate() : "unknown", strategy);
        }
    }
    
    /**
     * PERFORMANCE-BASIERT: Findet die beste verfügbare Performance für einen Referenzzeitpunkt
     * Performance = Profit + FloatingProfit (unabhängig von Ein-/Auszahlungen)
     * 
     * Strategie:
     * 1. Suche Tick am oder nach dem Referenzzeitpunkt (bevorzugt)
     * 2. FALLBACK: Suche letzten verfügbaren Tick VOR dem Referenzzeitpunkt
     * 3. Als letzte Option: Verwende ersten Tick
     */
    private static PerformanceSearchResult findBestPerformanceForReference(List<TickData> ticks, LocalDateTime referenceTime, String purpose, String signalId) {
        if (ticks == null || ticks.isEmpty()) {
            LOGGER.warning("PERFORMANCE DEBUG: Keine Ticks verfügbar für " + purpose + " von Signal " + signalId + " [PERFORMANCE-BASIERT]");
            return PerformanceSearchResult.noData("keine Ticks");
        }
        
        LOGGER.info("PERFORMANCE DEBUG: Suche " + purpose + " für " + referenceTime + " in " + ticks.size() + " Ticks (Signal " + signalId + ") [PERFORMANCE-BASIERT]");
        
        LocalDateTime firstTickTime = ticks.get(0).getTimestamp();
        LocalDateTime lastTickTime = ticks.get(ticks.size() - 1).getTimestamp();
        LOGGER.info("PERFORMANCE DEBUG: Verfügbarer Zeitraum: " + firstTickTime + " bis " + lastTickTime + " [PERFORMANCE-BASIERT]");
        
        // Strategie 1: Suche Tick am oder nach dem Referenzzeitpunkt (bevorzugt)
        for (TickData tick : ticks) {
            if (!tick.getTimestamp().isBefore(referenceTime)) {
                double performance = tick.getProfit() + tick.getFloatingProfit();
                LOGGER.info("PERFORMANCE DEBUG: " + purpose + " - Strategie 1 (ab Referenzzeit): " + performance + 
                           " (Profit: " + tick.getProfit() + " + Floating: " + tick.getFloatingProfit() + ")" +
                           " am " + tick.getTimestamp() + " (Signal " + signalId + ") [PERFORMANCE-BASIERT]");
                return new PerformanceSearchResult(performance, tick.getTimestamp(), 
                                                 "Strategie 1 (ab " + referenceTime.toLocalDate() + ")");
            }
        }
        
        // Strategie 2: FALLBACK - Suche letzten verfügbaren Tick VOR dem Referenzzeitpunkt
        LOGGER.info("PERFORMANCE DEBUG: " + purpose + " - Kein Tick ab " + referenceTime + " gefunden, suche letzten Tick davor (Signal " + signalId + ") [PERFORMANCE-BASIERT]");
        
        TickData lastTickBefore = null;
        for (TickData tick : ticks) {
            if (tick.getTimestamp().isBefore(referenceTime)) {
                lastTickBefore = tick;  // Überschreibe mit jedem späteren Tick vor der Referenzzeit
            } else {
                break; // Wir haben den Referenzzeitpunkt überschritten
            }
        }
        
        if (lastTickBefore != null) {
            double performance = lastTickBefore.getProfit() + lastTickBefore.getFloatingProfit();
            LOGGER.info("PERFORMANCE DEBUG: " + purpose + " - Strategie 2 (letzter vor Referenzzeit): " + performance + 
                       " (Profit: " + lastTickBefore.getProfit() + " + Floating: " + lastTickBefore.getFloatingProfit() + ")" +
                       " am " + lastTickBefore.getTimestamp() + " (Signal " + signalId + ") [PERFORMANCE-BASIERT]");
            return new PerformanceSearchResult(performance, lastTickBefore.getTimestamp(), 
                                             "Strategie 2 (letzter vor " + referenceTime.toLocalDate() + ")");
        }
        
        // Strategie 3: Als absolute letzte Option - verwende ersten verfügbaren Tick
        LOGGER.warning("PERFORMANCE DEBUG: " + purpose + " - Alle Ticks liegen nach " + referenceTime + 
                      ", verwende ersten verfügbaren als Notlösung (Signal " + signalId + ") [PERFORMANCE-BASIERT]");
        TickData firstTick = ticks.get(0);
        double performance = firstTick.getProfit() + firstTick.getFloatingProfit();
        return new PerformanceSearchResult(performance, firstTick.getTimestamp(), 
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
     * PERFORMANCE-BASIERT: Zeigt Profit + FloatingProfit Werte
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return Detaillierte Diagnose-Informationen
     */
    public static String createDetailedDiagnostic(String tickFilePath, String signalId) {
        StringBuilder diag = new StringBuilder();
        diag.append("=== PERIOD PROFIT DIAGNOSTIC (PERFORMANCE-BASIERT) ===\n");
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
                TickData firstTick = dataSet.getFirstTick();
                TickData lastTick = dataSet.getLatestTick();
                
                diag.append("First Tick: ").append(firstTick.getTimestamp()).append("\n");
                diag.append("Last Tick: ").append(lastTick.getTimestamp()).append("\n");
                diag.append("Initial Equity: ").append(firstTick.getEquity()).append("\n");
                diag.append("Current Performance: ").append(lastTick.getProfit() + lastTick.getFloatingProfit())
                    .append(" (Profit: ").append(lastTick.getProfit())
                    .append(" + Floating: ").append(lastTick.getFloatingProfit()).append(") [PERFORMANCE-BASIERT]\n");
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