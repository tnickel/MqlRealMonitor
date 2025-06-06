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
 * GEÄNDERT: Verwendet jetzt Profit-Werte statt Equity-Werte für die Berechnung
 * WeeklyProfit: Berechnet vom letzten Sonntag bis jetzt (basierend auf Profit)
 * MonthlyProfit: Berechnet vom 1. des aktuellen Monats bis jetzt (basierend auf Profit)
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
     * GEÄNDERT: Verwendet jetzt Profit-Werte statt Equity-Werte
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
        
        LOGGER.info("PROFIT DEBUG: Berechne Profits für Signal " + signalId + " - Tick-Datei: " + tickFilePath + " (PROFIT-BASIERT)");
        
        // Tick-Daten laden
        TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
        if (dataSet == null || dataSet.getTickCount() == 0) {
            LOGGER.warning("PROFIT DEBUG: Keine Tick-Daten verfügbar für Signal " + signalId + " - Datei: " + tickFilePath);
            return new ProfitResult(0.0, 0.0, false, false, "Keine Tick-Daten verfügbar: " + tickFilePath);
        }
        
        List<TickData> ticks = dataSet.getTicks();
        LOGGER.info("PROFIT DEBUG: " + ticks.size() + " Ticks geladen für Signal " + signalId + " (PROFIT-BASIERT)");
        
        // Mindestens 2 Ticks erforderlich für sinnvolle Profit-Berechnung
        if (ticks.size() < 2) {
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Nur " + ticks.size() + " Tick(s) verfügbar, brauche mindestens 2 für Profit-Berechnung");
            return new ProfitResult(0.0, 0.0, false, false, "Nicht genügend Daten für Profit-Berechnung");
        }
        
        // GEÄNDERT: Aktueller Profit (letzter Tick) statt Equity
        double currentProfit = dataSet.getLatestTick().getProfit();
        LocalDateTime now = LocalDateTime.now();
        
        // Referenzzeitpunkte ermitteln
        LocalDateTime weekStart = getLastSunday(now);
        LocalDateTime monthStart = getFirstOfCurrentMonth(now);
        
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Aktuell: " + now + 
                   ", Wochenstart: " + weekStart + ", Monatsstart: " + monthStart + 
                   ", Aktueller Profit: " + currentProfit + " (PROFIT-BASIERT)");
        
        // Zeige erste und letzte Ticks für Debugging
        TickData firstTick = ticks.get(0);
        TickData lastTick = ticks.get(ticks.size() - 1);
        LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Erster Tick: " + firstTick.getTimestamp() + 
                   " (Profit: " + firstTick.getProfit() + "), Letzter Tick: " + lastTick.getTimestamp() + 
                   " (Profit: " + lastTick.getProfit() + ") [PROFIT-BASIERT]");
        
        // GEÄNDERT: VERBESSERTE PROFIT-SUCHE: Verwendet intelligente Fallback-Strategien
        ProfitSearchResult weekProfitResult = findBestProfitForReference(ticks, weekStart, "Wochenstart", signalId);
        ProfitSearchResult monthProfitResult = findBestProfitForReference(ticks, monthStart, "Monatsstart", signalId);
        
        // Profit-Berechnungen
        boolean hasWeeklyData = weekProfitResult.hasValidData();
        boolean hasMonthlyData = monthProfitResult.hasValidData();
        
        double weeklyProfit = 0.0;
        double monthlyProfit = 0.0;
        
        if (hasWeeklyData) {
            double weekStartProfit = weekProfitResult.getProfit();
            // GEÄNDERT: Profit-Differenz in Prozent der Basis berechnen
            weeklyProfit = calculateProfitChange(currentProfit, weekStartProfit);
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Wochenprofit berechnet: (" + currentProfit + " - " + 
                       weekStartProfit + ") / |" + weekStartProfit + "| * 100 = " + String.format("%.4f%%", weeklyProfit) + " [PROFIT-BASIERT]");
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine gültigen Wochendaten verfügbar (PROFIT-BASIERT)");
        }
        
        if (hasMonthlyData) {
            double monthStartProfit = monthProfitResult.getProfit();
            // GEÄNDERT: Profit-Differenz in Prozent der Basis berechnen
            monthlyProfit = calculateProfitChange(currentProfit, monthStartProfit);
            LOGGER.info("PROFIT DEBUG: Signal " + signalId + " - Monatsprofit berechnet: (" + currentProfit + " - " + 
                       monthStartProfit + ") / |" + monthStartProfit + "| * 100 = " + String.format("%.4f%%", monthlyProfit) + " [PROFIT-BASIERT]");
        } else {
            LOGGER.warning("PROFIT DEBUG: Signal " + signalId + " - Keine gültigen Monatsdaten verfügbar (PROFIT-BASIERT)");
        }
        
        // Diagnostik-Informationen
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Signal: ").append(signalId).append(", ");
        diagnostic.append("Current Profit: ").append(String.format("%.2f", currentProfit)).append(", ");
        diagnostic.append("Week: ").append(weekProfitResult.getDiagnosticInfo()).append(", ");
        diagnostic.append("Month: ").append(monthProfitResult.getDiagnosticInfo()).append(" [PROFIT-BASIERT]");
        
        LOGGER.info("PROFIT DEBUG: Ergebnis für " + signalId + ": WeeklyProfit=" + String.format("%.4f%%", weeklyProfit) + 
                   ", MonthlyProfit=" + String.format("%.4f%%", monthlyProfit) + " [PROFIT-BASIERT]");
        
        return new ProfitResult(weeklyProfit, monthlyProfit, hasWeeklyData, hasMonthlyData, diagnostic.toString());
    }
    
    /**
     * NEUE METHODE: Berechnet die prozentuale Änderung zwischen zwei Profit-Werten
     * Spezielle Behandlung für positive/negative Profit-Werte
     * 
     * @param currentProfit Aktueller Profit-Wert
     * @param referenceProfit Referenz-Profit-Wert
     * @return Prozentuale Änderung
     */
    private static double calculateProfitChange(double currentProfit, double referenceProfit) {
        // Absolute Differenz
        double difference = currentProfit - referenceProfit;
        
        // Wenn Referenz-Profit 0 ist, geben wir die absolute Differenz als Prozent zurück
        if (Math.abs(referenceProfit) < 0.01) {
            return difference * 100.0; // Interpretiere als direkte Prozentpunkte
        }
        
        // Normale prozentuale Berechnung basierend auf Absolutwert der Referenz
        return (difference / Math.abs(referenceProfit)) * 100.0;
    }
    
    /**
     * GEÄNDERT: Hilfsobjekt für Profit-Suchergebnisse (statt Equity)
     */
    private static class ProfitSearchResult {
        private final Double profit;
        private final LocalDateTime timestamp;
        private final String strategy;
        private final boolean hasData;
        
        public ProfitSearchResult(Double profit, LocalDateTime timestamp, String strategy) {
            this.profit = profit;
            this.timestamp = timestamp;
            this.strategy = strategy;
            this.hasData = (profit != null);
        }
        
        public static ProfitSearchResult noData(String reason) {
            return new ProfitSearchResult(null, null, reason);
        }
        
        public boolean hasValidData() { return hasData; }
        public double getProfit() { return profit != null ? profit : 0.0; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public String getDiagnosticInfo() {
            if (!hasData) {
                return strategy + " (keine Daten)";
            }
            return String.format("%s=%.2f am %s (%s)", 
                               strategy.split(" ")[0], profit, 
                               timestamp != null ? timestamp.toLocalDate() : "unknown", strategy);
        }
    }
    
    /**
     * GEÄNDERT: VERBESSERTE METHODE: Findet den besten verfügbaren Profit für einen Referenzzeitpunkt
     * 
     * Strategie:
     * 1. Suche exakt am Referenzzeitpunkt
     * 2. Suche den nächsten Tick nach dem Referenzzeitpunkt (für "seit diesem Zeitpunkt")
     * 3. FALLBACK: Suche den letzten verfügbaren Tick VOR dem Referenzzeitpunkt
     * 4. Als letzte Option: Verwende ersten Tick (nur wenn alle anderen fehlschlagen)
     */
    private static ProfitSearchResult findBestProfitForReference(List<TickData> ticks, LocalDateTime referenceTime, String purpose, String signalId) {
        if (ticks == null || ticks.isEmpty()) {
            LOGGER.warning("PROFIT DEBUG: Keine Ticks verfügbar für " + purpose + " von Signal " + signalId + " [PROFIT-BASIERT]");
            return ProfitSearchResult.noData("keine Ticks");
        }
        
        LOGGER.info("PROFIT DEBUG: Suche " + purpose + " für " + referenceTime + " in " + ticks.size() + " Ticks (Signal " + signalId + ") [PROFIT-BASIERT]");
        
        LocalDateTime firstTickTime = ticks.get(0).getTimestamp();
        LocalDateTime lastTickTime = ticks.get(ticks.size() - 1).getTimestamp();
        LOGGER.info("PROFIT DEBUG: Verfügbarer Zeitraum: " + firstTickTime + " bis " + lastTickTime + " [PROFIT-BASIERT]");
        
        // Strategie 1: Suche Tick am oder nach dem Referenzzeitpunkt (bevorzugt)
        for (TickData tick : ticks) {
            if (!tick.getTimestamp().isBefore(referenceTime)) {
                LOGGER.info("PROFIT DEBUG: " + purpose + " - Strategie 1 (ab Referenzzeit): " + tick.getProfit() + 
                           " am " + tick.getTimestamp() + " (Signal " + signalId + ") [PROFIT-BASIERT]");
                return new ProfitSearchResult(tick.getProfit(), tick.getTimestamp(), 
                                            "Strategie 1 (ab " + referenceTime.toLocalDate() + ")");
            }
        }
        
        // Strategie 2: FALLBACK - Suche letzten verfügbaren Tick VOR dem Referenzzeitpunkt
        LOGGER.info("PROFIT DEBUG: " + purpose + " - Kein Tick ab " + referenceTime + " gefunden, suche letzten Tick davor (Signal " + signalId + ") [PROFIT-BASIERT]");
        
        TickData lastTickBefore = null;
        for (TickData tick : ticks) {
            if (tick.getTimestamp().isBefore(referenceTime)) {
                lastTickBefore = tick;  // Überschreibe mit jedem späteren Tick vor der Referenzzeit
            } else {
                break; // Wir haben den Referenzzeitpunkt überschritten
            }
        }
        
        if (lastTickBefore != null) {
            LOGGER.info("PROFIT DEBUG: " + purpose + " - Strategie 2 (letzter vor Referenzzeit): " + lastTickBefore.getProfit() + 
                       " am " + lastTickBefore.getTimestamp() + " (Signal " + signalId + ") [PROFIT-BASIERT]");
            return new ProfitSearchResult(lastTickBefore.getProfit(), lastTickBefore.getTimestamp(), 
                                        "Strategie 2 (letzter vor " + referenceTime.toLocalDate() + ")");
        }
        
        // Strategie 3: Als absolute letzte Option - verwende ersten verfügbaren Tick
        // (nur wenn alle Ticks nach dem Referenzzeitpunkt liegen)
        LOGGER.warning("PROFIT DEBUG: " + purpose + " - Alle Ticks liegen nach " + referenceTime + 
                      ", verwende ersten verfügbaren als Notlösung (Signal " + signalId + ") [PROFIT-BASIERT]");
        TickData firstTick = ticks.get(0);
        return new ProfitSearchResult(firstTick.getProfit(), firstTick.getTimestamp(), 
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
     * VERALTETE METHODE - wird durch findBestProfitForReference ersetzt
     * Wird für Kompatibilität beibehalten, aber nicht mehr verwendet
     */
    @Deprecated
    private static Double findEquityAtOrAfter(List<TickData> ticks, LocalDateTime targetTime) {
        return findBestProfitForReference(ticks, targetTime, "Legacy", "unknown").getProfit();
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
     * GEÄNDERT: Jetzt mit Profit-Werten statt Equity-Werten
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return Detaillierte Diagnose-Informationen
     */
    public static String createDetailedDiagnostic(String tickFilePath, String signalId) {
        StringBuilder diag = new StringBuilder();
        diag.append("=== PERIOD PROFIT DIAGNOSTIC (PROFIT-BASIERT) ===\n");
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
                diag.append("Current Profit: ").append(dataSet.getLatestTick().getProfit()).append(" [PROFIT-BASIERT]\n");
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