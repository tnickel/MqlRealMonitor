package com.mql.realmonitor.gui;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * Filtert Tick-Daten basierend auf Zeitintervallen
 */
public class TickDataFilter {
    
    private static final Logger LOGGER = Logger.getLogger(TickDataFilter.class.getName());
    
    /**
     * Filtert Tick-Daten basierend auf dem Zeitintervall
     * 
     * @param tickDataSet Das vollständige TickDataSet
     * @param timeScale Das gewünschte Zeitintervall
     * @return Liste der gefilterten Tick-Daten
     */
    public static List<TickDataLoader.TickData> filterTicksForTimeScale(
            TickDataLoader.TickDataSet tickDataSet, TimeScale timeScale) {
        
        if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
            LOGGER.warning("Keine Tick-Daten zum Filtern vorhanden");
            return new ArrayList<>();
        }
        
        List<TickDataLoader.TickData> allTicks = tickDataSet.getTicks();
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeScale.getDisplayMinutes());
        
        List<TickDataLoader.TickData> filteredTicks = new ArrayList<>();
        
        for (TickDataLoader.TickData tick : allTicks) {
            if (tick.getTimestamp().isAfter(cutoffTime)) {
                filteredTicks.add(tick);
            }
        }
        
        LOGGER.info("Gefilterte Ticks für " + timeScale.getLabel() + ": " + 
                   filteredTicks.size() + " von " + allTicks.size() + " Ticks");
        
        return filteredTicks;
    }
    
    /**
     * Berechnet Drawdown-Statistiken für gefilterte Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @return DrawdownStatistics Objekt
     */
    public static DrawdownStatistics calculateDrawdownStatistics(List<TickDataLoader.TickData> filteredTicks) {
        if (filteredTicks.isEmpty()) {
            return new DrawdownStatistics();
        }
        
        double minDrawdown = filteredTicks.stream().mapToDouble(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())
        ).min().orElse(0.0);
        
        double maxDrawdown = filteredTicks.stream().mapToDouble(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())
        ).max().orElse(0.0);
        
        double avgDrawdown = filteredTicks.stream().mapToDouble(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())
        ).average().orElse(0.0);
        
        return new DrawdownStatistics(minDrawdown, maxDrawdown, avgDrawdown);
    }
    
    /**
     * Berechnet den Drawdown-Prozentsatz
     * 
     * @param equity Der Kontostand
     * @param floatingProfit Der Floating Profit
     * @return Der Drawdown-Prozentsatz
     */
    private static double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            return 0.0;
        }
        return (floatingProfit / equity) * 100.0;
    }
    
    /**
     * Datenklasse für Drawdown-Statistiken
     */
    public static class DrawdownStatistics {
        private final double minDrawdown;
        private final double maxDrawdown;
        private final double avgDrawdown;
        private final boolean hasData;
        
        public DrawdownStatistics() {
            this.minDrawdown = 0.0;
            this.maxDrawdown = 0.0;
            this.avgDrawdown = 0.0;
            this.hasData = false;
        }
        
        public DrawdownStatistics(double minDrawdown, double maxDrawdown, double avgDrawdown) {
            this.minDrawdown = minDrawdown;
            this.maxDrawdown = maxDrawdown;
            this.avgDrawdown = avgDrawdown;
            this.hasData = true;
        }
        
        public double getMinDrawdown() { return minDrawdown; }
        public double getMaxDrawdown() { return maxDrawdown; }
        public double getAvgDrawdown() { return avgDrawdown; }
        public boolean hasData() { return hasData; }
        
        public String getFormattedMinDrawdown() {
            return String.format("%.2f%%", minDrawdown);
        }
        
        public String getFormattedMaxDrawdown() {
            return String.format("%.2f%%", maxDrawdown);
        }
        
        public String getFormattedAvgDrawdown() {
            return String.format("%.2f%%", avgDrawdown);
        }
    }
}