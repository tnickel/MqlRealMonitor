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
 * - Prozentuale Berechnung relativ zum bereinigten Kapital
 * 
 * NEU: Ein-/Auszahlungserkennung für bereinigte Gewinn-Prozente
 * - Drawdown % → verwendet echte Equity (bleibt in SignalData)
 * - Gewinn % → verwendet bereinigte Equity (ohne Ein-/Auszahlungen)
 */
public class PeriodProfitCalculator {
    
    private static final Logger LOGGER = Logger.getLogger(PeriodProfitCalculator.class.getName());
    
    /**
     * Ein-/Auszahlungserkennung für bereinigte Gewinn-Prozent-Berechnungen
     * KONZEPT:
     * - Drawdown % → verwendet echte Equity (mit Ein-/Auszahlungen) - bleibt in SignalData
     * - Gewinn % → verwendet bereinigte Equity (ohne Ein-/Auszahlungen)
     */
    public static class DepositWithdrawalDetector {
        
        private static final Logger LOGGER = Logger.getLogger(DepositWithdrawalDetector.class.getName());
        
        // Schwellwerte für Ein-/Auszahlungserkennung
        private static final double MINIMUM_AMOUNT_THRESHOLD = 100.0;     // Mindest-Betrag
        private static final double PERCENTAGE_THRESHOLD = 0.03;          // 3% Equity-Sprung
        private static final double TIME_THRESHOLD_MINUTES = 30.0;        // Max 30 Minuten zwischen Ticks
        
        /**
         * Erkennt Ein-/Auszahlungen zwischen zwei aufeinanderfolgenden Ticks
         * 
         * Logik: Wenn Equity-Änderung deutlich von Performance-Änderung abweicht
         */
        public static boolean isDepositOrWithdrawal(TickData previousTick, TickData currentTick) {
            if (previousTick == null || currentTick == null) return false;
            
            // Zeitdifferenz prüfen (nur bei aufeinanderfolgenden Ticks sinnvoll)
            long timeDiffMinutes = java.time.Duration.between(
                previousTick.getTimestamp(), currentTick.getTimestamp()).toMinutes();
            
            if (timeDiffMinutes > TIME_THRESHOLD_MINUTES) {
                // Bei großen Zeitabständen keine Ein-/Auszahlungserkennung
                return false;
            }
            
            // Berechne Änderungen
            double equityChange = currentTick.getEquity() - previousTick.getEquity();
            double profitChange = currentTick.getProfit() - previousTick.getProfit();
            double floatingChange = currentTick.getFloatingProfit() - previousTick.getFloatingProfit();
            double performanceChange = profitChange + floatingChange;
            
            // Unerwartete Equity-Änderung (potentielle Ein-/Auszahlung)
            double unexpectedChange = equityChange - performanceChange;
            
            // Schwellwert-Prüfungen
            boolean absoluteThreshold = Math.abs(unexpectedChange) > MINIMUM_AMOUNT_THRESHOLD;
            boolean percentageThreshold = Math.abs(unexpectedChange / previousTick.getEquity()) > PERCENTAGE_THRESHOLD;
            
            boolean isDepositWithdrawal = absoluteThreshold && percentageThreshold;
            
            if (isDepositWithdrawal) {
                String type = unexpectedChange > 0 ? "EINZAHLUNG" : "AUSZAHLUNG";
                LOGGER.info("DEPOSIT/WITHDRAWAL DETECTED (" + type + "): " +
                           "Equity: " + String.format("%.2f → %.2f (Δ %.2f)", 
                                       previousTick.getEquity(), currentTick.getEquity(), equityChange) + 
                           ", Performance: Δ " + String.format("%.2f", performanceChange) + 
                           ", Unexpected: " + String.format("%.2f", unexpectedChange) + 
                           " (Timestamp: " + currentTick.getTimestamp() + ")");
            }
            
            return isDepositWithdrawal;
        }
        
        /**
         * Berechnet bereinigte Equity-Basis für Gewinn-Prozent-Berechnungen
         * 
         * Entfernt Ein-/Auszahlungseffekte aus der Equity-Entwicklung
         */
        public static double calculateCleanEquityBasis(List<TickData> allTicks, TickData referenceTick) {
            if (allTicks == null || allTicks.isEmpty() || referenceTick == null) {
                return 0.0;
            }
            
            // Finde Referenz-Tick in der Liste
            int referenceIndex = findTickIndex(allTicks, referenceTick);
            if (referenceIndex == -1) {
                // Referenz-Tick nicht gefunden, verwende echte Equity
                LOGGER.warning("CLEAN EQUITY: Referenz-Tick nicht gefunden, verwende echte Equity: " + 
                              referenceTick.getEquity());
                return referenceTick.getEquity();
            }
            
            // Starte mit der Equity vom Referenz-Tick
            double cleanEquity = referenceTick.getEquity();
            double totalDepositsAfterReference = 0.0;
            
            LOGGER.info("CLEAN EQUITY BASIS: Start mit Referenz-Equity: " + cleanEquity + 
                       " (Referenz-Index: " + referenceIndex + ")");
            
            // Analysiere alle Ticks nach dem Referenz-Tick für Ein-/Auszahlungen
            for (int i = referenceIndex + 1; i < allTicks.size(); i++) {
                TickData prevTick = allTicks.get(i - 1);
                TickData currTick = allTicks.get(i);
                
                if (isDepositOrWithdrawal(prevTick, currTick)) {
                    double equityChange = currTick.getEquity() - prevTick.getEquity();
                    double profitChange = currTick.getProfit() - prevTick.getProfit();
                    double floatingChange = currTick.getFloatingProfit() - prevTick.getFloatingProfit();
                    double performanceChange = profitChange + floatingChange;
                    
                    // Der Ein-/Auszahlungsbetrag
                    double depositAmount = equityChange - performanceChange;
                    totalDepositsAfterReference += depositAmount;
                    
                    LOGGER.info("CLEAN EQUITY ADJUSTMENT: " + 
                               (depositAmount > 0 ? "Einzahlung" : "Auszahlung") + " von " + 
                               String.format("%.2f", Math.abs(depositAmount)) + 
                               " bei Tick " + i + " (" + currTick.getTimestamp() + ")");
                }
            }
            
            // Bereinigte Equity = Original Equity am Referenzpunkt
            // (Ein-/Auszahlungen NACH dem Referenzpunkt beeinflussen die Basis nicht)
            double finalCleanEquity = cleanEquity;
            
            LOGGER.info("CLEAN EQUITY BERECHNUNG: " +
                       "Referenz-Equity: " + cleanEquity + 
                       ", Deposits nach Referenz: " + String.format("%.2f", totalDepositsAfterReference) + 
                       ", Finale bereinigte Equity-Basis: " + finalCleanEquity);
            
            return finalCleanEquity;
        }
        
        /**
         * Hilfsmethode: Findet Index eines Ticks in der Liste
         */
        private static int findTickIndex(List<TickData> ticks, TickData targetTick) {
            for (int i = 0; i < ticks.size(); i++) {
                if (ticks.get(i).getTimestamp().equals(targetTick.getTimestamp())) {
                    return i;
                }
            }
            return -1;
        }
        
        /**
         * Berechnet bereinigte Gewinn-Prozente mit Ein-/Auszahlungskorrektur
         * 
         * WICHTIG: Verwendet bereinigte Equity-Basis statt echter Equity
         */
        public static double calculateCleanPercentage(double performanceChange, 
                                                     List<TickData> allTicks, 
                                                     TickData referenceTick) {
            
            // Berechne bereinigte Equity-Basis zum Referenzzeitpunkt
            double cleanEquityBasis = calculateCleanEquityBasis(allTicks, referenceTick);
            
            // Fallback: Wenn bereinigte Equity zu klein oder ungültig
            if (cleanEquityBasis <= 0) {
                cleanEquityBasis = referenceTick.getEquity();
                LOGGER.warning("CLEAN PERCENTAGE: Fallback zu echter Equity: " + cleanEquityBasis);
            }
            
            // Berechne Prozent basierend auf bereinigter Equity
            double percentage = (performanceChange / cleanEquityBasis) * 100.0;
            
            LOGGER.info("CLEAN PERCENTAGE CALCULATION: " +
                       "Performance Change: " + String.format("%.2f", performanceChange) + 
                       ", Clean Equity Basis: " + String.format("%.2f", cleanEquityBasis) + 
                       ", Result: " + String.format("%.2f%%", percentage));
            
            return percentage;
        }
    }
    
    /**
     * Ergebnis-Klasse für Profit-Berechnungen
     * ERWEITERT: Jetzt mit Currency-Werten und Tooltip-Details
     */
    public static class ProfitResult {
        private final double weeklyProfitPercent;
        private final double monthlyProfitPercent;
        private final double weeklyProfitCurrency;    // NEU: Currency-Betrag
        private final double monthlyProfitCurrency;   // NEU: Currency-Betrag
        private final String currency;                // NEU: Währung
        private final boolean hasWeeklyData;
        private final boolean hasMonthlyData;
        private final String diagnosticInfo;
        private final String weeklyTooltip;          // NEU: Tooltip für Weekly Profit
        private final String monthlyTooltip;         // NEU: Tooltip für Monthly Profit
        
        public ProfitResult(double weeklyProfitPercent, double monthlyProfitPercent, 
                           double weeklyProfitCurrency, double monthlyProfitCurrency, String currency,
                           boolean hasWeeklyData, boolean hasMonthlyData, String diagnosticInfo,
                           String weeklyTooltip, String monthlyTooltip) {
            this.weeklyProfitPercent = weeklyProfitPercent;
            this.monthlyProfitPercent = monthlyProfitPercent;
            this.weeklyProfitCurrency = weeklyProfitCurrency;
            this.monthlyProfitCurrency = monthlyProfitCurrency;
            this.currency = currency;
            this.hasWeeklyData = hasWeeklyData;
            this.hasMonthlyData = hasMonthlyData;
            this.diagnosticInfo = diagnosticInfo;
            this.weeklyTooltip = weeklyTooltip;
            this.monthlyTooltip = monthlyTooltip;
        }
        
        // Bestehende Methoden
        public double getWeeklyProfitPercent() { return weeklyProfitPercent; }
        public double getMonthlyProfitPercent() { return monthlyProfitPercent; }
        public boolean hasWeeklyData() { return hasWeeklyData; }
        public boolean hasMonthlyData() { return hasMonthlyData; }
        public String getDiagnosticInfo() { return diagnosticInfo; }
        
        // NEU: Currency-Methoden
        public double getWeeklyProfitCurrency() { return weeklyProfitCurrency; }
        public double getMonthlyProfitCurrency() { return monthlyProfitCurrency; }
        public String getCurrency() { return currency; }
        public String getWeeklyTooltip() { return weeklyTooltip; }
        public String getMonthlyTooltip() { return monthlyTooltip; }
        
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
        
        /**
         * NEU: Formatiert den Wochengewinn in Währung für die Anzeige
         */
        public String getFormattedWeeklyProfitCurrency() {
            if (!hasWeeklyData) {
                return "N/A";
            }
            String sign = weeklyProfitCurrency >= 0 ? "+" : "";
            return String.format("%s%.2f %s", sign, weeklyProfitCurrency, currency != null ? currency : "");
        }
        
        /**
         * NEU: Formatiert den Monatsgewinn in Währung für die Anzeige
         */
        public String getFormattedMonthlyProfitCurrency() {
            if (!hasMonthlyData) {
                return "N/A";
            }
            String sign = monthlyProfitCurrency >= 0 ? "+" : "";
            return String.format("%s%.2f %s", sign, monthlyProfitCurrency, currency != null ? currency : "");
        }
        
        @Override
        public String toString() {
            return String.format("ProfitResult{weekly=%s (%s), monthly=%s (%s)}", 
                               getFormattedWeeklyProfit(), getFormattedWeeklyProfitCurrency(),
                               getFormattedMonthlyProfit(), getFormattedMonthlyProfitCurrency());
        }
    }
    
    /**
     * Berechnet Wochen- und Monatsgewinne für einen Signal-Provider
     * PERFORMANCE-BASIERT: Verwendet Profit + FloatingProfit für Ein-/Auszahlungs-unabhängige Berechnung
     * ERWEITERT: Jetzt mit Currency-Berechnungen und detaillierten Tooltip-Informationen
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return ProfitResult mit den berechneten Werten
     */
    public static ProfitResult calculateProfits(String tickFilePath, String signalId) {
        return calculateProfitsWithCurrency(tickFilePath, signalId, null);
    }
    
    /**
     * ALTE VERSION: Berechnet Wochen- und Monatsgewinne mit Currency-Information
     * PERFORMANCE-BASIERT: Verwendet Profit + FloatingProfit für Ein-/Auszahlungs-unabhängige Berechnung
     * OHNE Ein-/Auszahlungskorrektur (für Rückwärtskompatibilität)
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @param currency Die Währung für Currency-Berechnungen (optional)
     * @return ProfitResult mit den berechneten Werten
     */
    public static ProfitResult calculateProfitsWithCurrency(String tickFilePath, String signalId, String currency) {
        // Für Rückwärtskompatibilität: Verwende die neue bereinigte Methode
        return calculateProfitsWithCleanPercentages(tickFilePath, signalId, currency);
    }
    
    /**
     * NEUE HAUPTMETHODE: Berechnet Wochen- und Monatsgewinne mit Ein-/Auszahlungskorrektur
     * PERFORMANCE-BASIERT: Verwendet Profit + FloatingProfit für Ein-/Auszahlungs-unabhängige Berechnung
     * MIT Ein-/Auszahlungserkennung für bereinigte Gewinn-Prozente
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @param currency Die Währung für Currency-Berechnungen (optional)
     * @return ProfitResult mit den berechneten Werten
     */
    public static ProfitResult calculateProfitsWithCleanPercentages(String tickFilePath, String signalId, String currency) {
        if (tickFilePath == null || signalId == null) {
            LOGGER.warning("CLEAN PROFITS: Ungültige Parameter für Profit-Berechnung: tickFilePath=" + tickFilePath + ", signalId=" + signalId);
            return new ProfitResult(0.0, 0.0, 0.0, 0.0, currency, false, false, "Ungültige Parameter", "", "");
        }
        
        LOGGER.info("CLEAN PROFITS: Berechne Profits mit Ein-/Auszahlungskorrektur für Signal " + signalId + " - Tick-Datei: " + tickFilePath);
        
        // Tick-Daten laden
        TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
        if (dataSet == null || dataSet.getTickCount() == 0) {
            LOGGER.warning("CLEAN PROFITS: Keine Tick-Daten verfügbar für Signal " + signalId + " - Datei: " + tickFilePath);
            return new ProfitResult(0.0, 0.0, 0.0, 0.0, currency, false, false, "Keine Tick-Daten verfügbar: " + tickFilePath, "", "");
        }
        
        List<TickData> ticks = dataSet.getTicks();
        LOGGER.info("CLEAN PROFITS: " + ticks.size() + " Ticks geladen für Signal " + signalId);
        
        // Mindestens 2 Ticks erforderlich für sinnvolle Profit-Berechnung
        if (ticks.size() < 2) {
            LOGGER.info("CLEAN PROFITS: Signal " + signalId + " - Nur " + ticks.size() + " Tick(s) verfügbar, brauche mindestens 2 für Profit-Berechnung");
            return new ProfitResult(0.0, 0.0, 0.0, 0.0, currency, false, false, "Nicht genügend Daten für Profit-Berechnung", "", "");
        }
        
        // PERFORMANCE-BASIERT: Aktuelle Gesamt-Performance (Profit + FloatingProfit)
        TickData latestTick = dataSet.getLatestTick();
        double currentPerformance = latestTick.getProfit() + latestTick.getFloatingProfit();
        
        // Initiale Equity als Fallback-Basis für prozentuale Berechnungen (vom ersten Tick)
        double initialEquity = dataSet.getFirstTick().getEquity();
        
        LocalDateTime now = LocalDateTime.now();
        
        // Referenzzeitpunkte ermitteln
        LocalDateTime weekStart = getLastSunday(now);
        LocalDateTime monthStart = getFirstOfCurrentMonth(now);
        
        LOGGER.info("CLEAN PROFITS: Signal " + signalId + " - Aktuell: " + now + 
                   ", Wochenstart: " + weekStart + ", Monatsstart: " + monthStart + 
                   ", Aktuelle Performance: " + currentPerformance + 
                   " (Profit: " + latestTick.getProfit() + " + Floating: " + latestTick.getFloatingProfit() + ")" +
                   ", Initiale Equity: " + initialEquity);
        
        // Zeige erste und letzte Ticks für Debugging
        TickData firstTick = ticks.get(0);
        TickData lastTick = ticks.get(ticks.size() - 1);
        LOGGER.info("CLEAN PROFITS: Signal " + signalId + " - Erster Tick: " + firstTick.getTimestamp() + 
                   " (Performance: " + (firstTick.getProfit() + firstTick.getFloatingProfit()) + ")" +
                   ", Letzter Tick: " + lastTick.getTimestamp() + 
                   " (Performance: " + (lastTick.getProfit() + lastTick.getFloatingProfit()) + ")");
        
        // PERFORMANCE-BASIERT: Suche Performance für Referenzzeitpunkte
        PerformanceSearchResult weekPerformanceResult = findBestPerformanceForReference(ticks, weekStart, "Wochenstart", signalId);
        PerformanceSearchResult monthPerformanceResult = findBestPerformanceForReference(ticks, monthStart, "Monatsstart", signalId);
        
        // Performance-Berechnungen
        boolean hasWeeklyData = weekPerformanceResult.hasValidData();
        boolean hasMonthlyData = monthPerformanceResult.hasValidData();
        
        double weeklyProfit = 0.0;
        double monthlyProfit = 0.0;
        double weeklyProfitCurrency = 0.0;    // NEU
        double monthlyProfitCurrency = 0.0;   // NEU
        
        // NEU: Tooltip-Informationen aufbauen
        String weeklyTooltip = "";
        String monthlyTooltip = "";
        
        // WOCHENGEWINN mit bereinigter Equity-Basis
        if (hasWeeklyData) {
            double weekStartPerformance = weekPerformanceResult.getPerformance();
            double performanceChange = currentPerformance - weekStartPerformance;
            weeklyProfitCurrency = performanceChange;
            
            // Finde Referenz-Tick für Wochenstart
            TickData weekReferenceTick = findTickForPerformanceResult(ticks, weekPerformanceResult);
            
            if (weekReferenceTick != null) {
                // NEUE BERECHNUNG: Mit Ein-/Auszahlungskorrektur
                weeklyProfit = DepositWithdrawalDetector.calculateCleanPercentage(
                    performanceChange, ticks, weekReferenceTick);
                
                double cleanEquity = DepositWithdrawalDetector.calculateCleanEquityBasis(ticks, weekReferenceTick);
                
                weeklyTooltip = String.format("Weekly Profit (BEREINIGT):\n\n" +
                                            "Aktuelle Performance: %.2f %s\n" +
                                            "Performance am %s: %.2f %s\n" +
                                            "Differenz: %.2f %s\n\n" +
                                            "Bereinigte Equity-Basis: %.2f %s\n" +
                                            "Prozentual: %.2f%%\n\n" +
                                            "Ein-/Auszahlungen werden erkannt und bereinigt",
                                            currentPerformance, currency != null ? currency : "",
                                            weekStart.toLocalDate(), weekStartPerformance, currency != null ? currency : "",
                                            weeklyProfitCurrency, currency != null ? currency : "",
                                            cleanEquity, currency != null ? currency : "",
                                            weeklyProfit);
            } else {
                // Fallback: Ursprüngliche Berechnung mit initialer Equity
                weeklyProfit = (performanceChange / initialEquity) * 100.0;
                weeklyTooltip = "Weekly Profit (Fallback): Bereinigte Berechnung nicht möglich, verwende initiale Equity als Basis";
                
                LOGGER.warning("CLEAN PROFITS: Wochengewinn Fallback für Signal " + signalId + 
                              " - Verwende initiale Equity: " + initialEquity);
            }
            
            LOGGER.info("CLEAN PROFITS: Wochengewinn berechnet für Signal " + signalId + ": " + 
                       String.format("%.4f%%", weeklyProfit) + 
                       " (Currency: " + String.format("%.2f", weeklyProfitCurrency) + " " + (currency != null ? currency : "") + ")");
        } else {
            LOGGER.warning("CLEAN PROFITS: Signal " + signalId + " - Keine gültigen Wochendaten verfügbar");
            weeklyTooltip = "Keine Daten für Wochengewinn verfügbar\n\n" +
                          "Benötigt mindestens einen Tick seit Wochenstart\n" +
                          "oder historische Daten vor dem Wochenstart";
        }
        
        // MONATSGEWINN mit bereinigter Equity-Basis
        if (hasMonthlyData) {
            double monthStartPerformance = monthPerformanceResult.getPerformance();
            double performanceChange = currentPerformance - monthStartPerformance;
            monthlyProfitCurrency = performanceChange;
            
            // Finde Referenz-Tick für Monatsstart
            TickData monthReferenceTick = findTickForPerformanceResult(ticks, monthPerformanceResult);
            
            if (monthReferenceTick != null) {
                // NEUE BERECHNUNG: Mit Ein-/Auszahlungskorrektur
                monthlyProfit = DepositWithdrawalDetector.calculateCleanPercentage(
                    performanceChange, ticks, monthReferenceTick);
                
                double cleanEquity = DepositWithdrawalDetector.calculateCleanEquityBasis(ticks, monthReferenceTick);
                
                monthlyTooltip = String.format("Monthly Profit (BEREINIGT):\n\n" +
                                             "Aktuelle Performance: %.2f %s\n" +
                                             "Performance am %s: %.2f %s\n" +
                                             "Differenz: %.2f %s\n\n" +
                                             "Bereinigte Equity-Basis: %.2f %s\n" +
                                             "Prozentual: %.2f%%\n\n" +
                                             "Ein-/Auszahlungen werden erkannt und bereinigt",
                                             currentPerformance, currency != null ? currency : "",
                                             monthStart.toLocalDate(), monthStartPerformance, currency != null ? currency : "",
                                             monthlyProfitCurrency, currency != null ? currency : "",
                                             cleanEquity, currency != null ? currency : "",
                                             monthlyProfit);
            } else {
                // Fallback: Ursprüngliche Berechnung mit initialer Equity
                monthlyProfit = (performanceChange / initialEquity) * 100.0;
                monthlyTooltip = "Monthly Profit (Fallback): Bereinigte Berechnung nicht möglich, verwende initiale Equity als Basis";
                
                LOGGER.warning("CLEAN PROFITS: Monatsgewinn Fallback für Signal " + signalId + 
                              " - Verwende initiale Equity: " + initialEquity);
            }
            
            LOGGER.info("CLEAN PROFITS: Monatsgewinn berechnet für Signal " + signalId + ": " + 
                       String.format("%.4f%%", monthlyProfit) + 
                       " (Currency: " + String.format("%.2f", monthlyProfitCurrency) + " " + (currency != null ? currency : "") + ")");
        } else {
            LOGGER.warning("CLEAN PROFITS: Signal " + signalId + " - Keine gültigen Monatsdaten verfügbar");
            monthlyTooltip = "Keine Daten für Monatsgewinn verfügbar\n\n" +
                           "Benötigt mindestens einen Tick seit Monatsstart\n" +
                           "oder historische Daten vor dem Monatsstart";
        }
        
        // Diagnostik-Informationen
        StringBuilder diagnostic = new StringBuilder();
        diagnostic.append("Signal: ").append(signalId).append(" [CLEAN PERCENTAGES WITH DEPOSIT/WITHDRAWAL DETECTION], ");
        diagnostic.append("Current Performance: ").append(String.format("%.2f", currentPerformance)).append(", ");
        diagnostic.append("Initial Equity: ").append(String.format("%.2f", initialEquity)).append(", ");
        diagnostic.append("Week: ").append(weekPerformanceResult.getDiagnosticInfo()).append(", ");
        diagnostic.append("Month: ").append(monthPerformanceResult.getDiagnosticInfo());
        
        LOGGER.info("CLEAN PROFITS: Ergebnis für " + signalId + ": WeeklyProfit=" + String.format("%.4f%%", weeklyProfit) + 
                   " (" + String.format("%.2f", weeklyProfitCurrency) + " " + (currency != null ? currency : "") + ")" +
                   ", MonthlyProfit=" + String.format("%.4f%%", monthlyProfit) + 
                   " (" + String.format("%.2f", monthlyProfitCurrency) + " " + (currency != null ? currency : "") + ")");
        
        return new ProfitResult(weeklyProfit, monthlyProfit, weeklyProfitCurrency, monthlyProfitCurrency, currency,
                               hasWeeklyData, hasMonthlyData, diagnostic.toString(), weeklyTooltip, monthlyTooltip);
    }
    
    /**
     * Hilfsmethode: Findet den TickData für ein PerformanceSearchResult
     */
    private static TickData findTickForPerformanceResult(List<TickData> ticks, PerformanceSearchResult result) {
        if (result == null || !result.hasValidData() || result.getTimestamp() == null) {
            return null;
        }
        
        for (TickData tick : ticks) {
            if (tick.getTimestamp().equals(result.getTimestamp())) {
                return tick;
            }
        }
        return null;
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
     * ERWEITERT: Jetzt mit Currency-Informationen und Ein-/Auszahlungsdiagnose
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return Detaillierte Diagnose-Informationen
     */
    public static String createDetailedDiagnostic(String tickFilePath, String signalId) {
        StringBuilder diag = new StringBuilder();
        diag.append("=== PERIOD PROFIT DIAGNOSTIC (PERFORMANCE-BASIERT + CLEAN PERCENTAGES) ===\n");
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
                
                // Ein-/Auszahlungsdiagnose
                List<TickData> ticks = dataSet.getTicks();
                int depositCount = 0;
                for (int i = 1; i < ticks.size(); i++) {
                    if (DepositWithdrawalDetector.isDepositOrWithdrawal(ticks.get(i-1), ticks.get(i))) {
                        depositCount++;
                    }
                }
                diag.append("Detected Deposits/Withdrawals: ").append(depositCount).append(" transactions\n");
            }
        } else {
            diag.append("No tick data available\n");
        }
        
        ProfitResult result = calculateProfitsWithCleanPercentages(tickFilePath, signalId, null);
        diag.append("Calculation Result: ").append(result.toString()).append("\n");
        diag.append("Diagnostic Info: ").append(result.getDiagnosticInfo()).append("\n");
        diag.append("Weekly Tooltip: ").append(result.getWeeklyTooltip().replace("\n", " ")).append("\n");
        diag.append("Monthly Tooltip: ").append(result.getMonthlyTooltip().replace("\n", " ")).append("\n");
        
        return diag.toString();
    }
}