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
        
        // Aktuelle Equity (letzter Tick)
        double currentEquity = dataSet.getLatestTick().getEquity();
        LocalDateTime now = LocalDateTime.now();
        
        // Referenzzeitpunkte ermitteln
        LocalDateTime weekStart = getLastSunday(now);
        LocalDateTime monthStart = getFirstOfCurrentMonth(now);
        
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Aktuell: " + now + 
                   ", Wochenstart: " + weekStart + ", Monatsstart: " + monthStart + 
                   ", Aktuelle Equity: " + currentEquity);
        
        // ERWEITERT: Zeige erste und letzte Ticks für Debugging
        if (ticks.size() > 0) {
            TickData firstTick = ticks.get(0);
            TickData lastTick = ticks.get(ticks.size() - 1);
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Erster Tick: " + firstTick.getTimestamp() + 
                       " (Equity: " + firstTick.getEquity() + "), Letzter Tick: " + lastTick.getTimestamp() + 
                       " (Equity: " + lastTick.getEquity() + ")");
        }
        
        // Equity-Werte zu den Referenzzeitpunkten finden
        // Suche erste verfügbare Equity AM ODER NACH dem Zeitpunkt (korrekt für "seit Zeitpunkt")
        Double weekStartEquity = findEquityAtOrAfter(ticks, weekStart);
        Double monthStartEquity = findEquityAtOrAfter(ticks, monthStart);
        
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Gefundene Equity-Werte: Wochenstart=" + 
                   weekStartEquity + " (seit " + weekStart.toLocalDate() + "), Monatsstart=" + monthStartEquity + " (seit " + monthStart.toLocalDate() + ")");
        
        // FALLBACK: Wenn keine historischen Daten vorhanden sind, verwende den ersten verfügbaren Tick
        if (weekStartEquity == null && ticks.size() > 1) {
            weekStartEquity = ticks.get(0).getEquity();
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - FALLBACK Wochenstart: Verwende ersten Tick mit Equity " + weekStartEquity);
        }
        
        if (monthStartEquity == null && ticks.size() > 1) {
            monthStartEquity = ticks.get(0).getEquity();
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - FALLBACK Monatsstart: Verwende ersten Tick mit Equity " + monthStartEquity);
        }
        
        // Profit-Berechnungen
        boolean hasWeeklyData = (weekStartEquity != null && weekStartEquity > 0);
        boolean hasMonthlyData = (monthStartEquity != null && monthStartEquity > 0);
        
        double weeklyProfit = 0.0;
        double monthlyProfit = 0.0;
        
        if (hasWeeklyData) {
            weeklyProfit = ((currentEquity - weekStartEquity) / weekStartEquity) * 100.0;
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Wochenprofit berechnet: (" + currentEquity + " - " + 
                       weekStartEquity + ") / " + weekStartEquity + " * 100 = " + String.format("%.4f%%", weeklyProfit));
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine Wochendaten verfügbar");
        }
        
        if (hasMonthlyData) {
            monthlyProfit = ((currentEquity - monthStartEquity) / monthStartEquity) * 100.0;
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Monatsprofit berechnet: (" + currentEquity + " - " + 
                       monthStartEquity + ") / " + monthStartEquity + " * 100 = " + String.format("%.4f%%", monthlyProfit));
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine Monatsdaten verfügbar");
        }
        
        // Diagnostik-Informationen
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Signal: ").append(signalId).append(", ");
        diagnostic.append("Current Equity: ").append(String.format("%.2f", currentEquity)).append(", ");
        diagnostic.append("Week Start (").append(weekStart.toLocalDate()).append("): ");
        diagnostic.append(weekStartEquity != null ? String.format("%.2f", weekStartEquity) : "N/A").append(", ");
        diagnostic.append("Month Start (").append(monthStart.toLocalDate()).append("): ");
        diagnostic.append(monthStartEquity != null ? String.format("%.2f", monthStartEquity) : "N/A");
        
        LOGGER.info("PROFIT DEBUG: Ergebnis für " + signalId + ": WeeklyProfit=" + String.format("%.4f%%", weeklyProfit) + 
                   ", MonthlyProfit=" + String.format("%.4f%%", monthlyProfit));
        
        return new ProfitResult(weeklyProfit, monthlyProfit, hasWeeklyData, hasMonthlyData, diagnostic.toString());
    }
    
    /**
     * Ermittelt den letzten Sonntag (Start der aktuellen Woche)
     * KORRIGIERT: Richtige Datums-Berechnung
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
     * Findet die Equity zum angegebenen Zeitpunkt oder danach
     * ERWEITERT: Bessere Debug-Informationen und Fallback-Strategien
     * 
     * @param ticks Liste der Tick-Daten (sollte chronologisch sortiert sein)
     * @param targetTime Der Zielzeitpunkt
     * @return Die Equity zum oder nach dem Zielzeitpunkt, oder null wenn nicht gefunden
     */
    private static Double findEquityAtOrAfter(List<TickData> ticks, LocalDateTime targetTime) {
        if (ticks == null || ticks.isEmpty()) {
            LOGGER.warning("EQUITY DEBUG: Keine Ticks verfügbar für Suche nach " + targetTime);
            return null;
        }
        
        LOGGER.info("EQUITY DEBUG: Suche erste Equity AM ODER NACH " + targetTime + " in " + ticks.size() + " Ticks");
        
        // Zeige Zeitraum der verfügbaren Daten
        LocalDateTime firstTickTime = ticks.get(0).getTimestamp();
        LocalDateTime lastTickTime = ticks.get(ticks.size() - 1).getTimestamp();
        
        LOGGER.info("EQUITY DEBUG: Verfügbarer Zeitraum: " + firstTickTime + " bis " + lastTickTime);
        
        // Prüfe ob der Zielzeitpunkt vor allen verfügbaren Daten liegt
        if (targetTime.isBefore(firstTickTime)) {
            LOGGER.info("EQUITY DEBUG: Zielzeit " + targetTime + " liegt vor erstem Tick " + firstTickTime + 
                       " - verwende ersten Tick mit Equity " + ticks.get(0).getEquity());
            return ticks.get(0).getEquity();
        }
        
        // Suche den ersten Tick der gleich oder nach dem Zielzeitpunkt liegt
        for (int i = 0; i < ticks.size(); i++) {
            TickData tick = ticks.get(i);
            if (!tick.getTimestamp().isBefore(targetTime)) {
                LOGGER.info("EQUITY DEBUG: Equity gefunden für " + targetTime + ": " + tick.getEquity() + 
                           " am " + tick.getTimestamp() + " (Index " + i + ") - SEIT diesem Zeitpunkt");
                return tick.getEquity();
            }
        }
        
        // Wenn kein Tick nach dem Zielzeitpunkt gefunden wurde,
        // nehme den letzten verfügbaren Tick (falls alle Ticks vor dem Zielzeitpunkt liegen)
        TickData lastTick = ticks.get(ticks.size() - 1);
        LOGGER.info("EQUITY DEBUG: Kein Tick nach " + targetTime + " gefunden, verwende letzten verfügbaren: " + 
                   lastTick.getEquity() + " am " + lastTick.getTimestamp() + " - alle Daten sind VOR dem Zielzeitpunkt");
        return lastTick.getEquity();
    }
    
    /**
     * Erweiterte Methode für Debugging - findet Equity zu einem exakten Zeitpunkt
     * 
     * @param ticks Liste der Tick-Daten
     * @param targetTime Der exakte Zielzeitpunkt
     * @param toleranceMinutes Toleranz in Minuten
     * @return Die Equity innerhalb der Toleranz, oder null
     */
    private static Double findEquityAtExactTime(List<TickData> ticks, LocalDateTime targetTime, int toleranceMinutes) {
        if (ticks == null || ticks.isEmpty()) {
            return null;
        }
        
        LocalDateTime earliestTime = targetTime.minusMinutes(toleranceMinutes);
        LocalDateTime latestTime = targetTime.plusMinutes(toleranceMinutes);
        
        for (TickData tick : ticks) {
            LocalDateTime tickTime = tick.getTimestamp();
            if (!tickTime.isBefore(earliestTime) && !tickTime.isAfter(latestTime)) {
                LOGGER.fine("Exakte Equity gefunden für " + targetTime + " (±" + toleranceMinutes + "min): " + 
                           tick.getEquity() + " am " + tick.getTimestamp());
                return tick.getEquity();
            }
        }
        
        return null;
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