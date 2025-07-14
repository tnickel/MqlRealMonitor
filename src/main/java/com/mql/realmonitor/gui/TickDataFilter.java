package com.mql.realmonitor.gui;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * ERWEITERT: Filtert Tick-Daten basierend auf Zeitintervallen
 * NEU: Unterstützt ALL-Modus für kompletten verfügbaren Zeitraum
 * ALLE PROBLEME BEHOBEN: Robuste Filterung und umfassende Diagnostik
 */
public class TickDataFilter {
    
    private static final Logger LOGGER = Logger.getLogger(TickDataFilter.class.getName());
    
    /**
     * ERWEITERT: Filtert Tick-Daten basierend auf dem Zeitintervall
     * NEU: Unterstützt ALL-Modus für kompletten Zeitraum ohne Filterung
     * ROBUSTE LÖSUNG: Garantiert immer sinnvolle Ergebnisse für alle Signalprovider
     * 
     * @param tickDataSet Das vollständige TickDataSet
     * @param timeScale Das gewünschte Zeitintervall (inkl. ALL)
     * @return Liste der gefilterten Tick-Daten
     */
    public static List<TickDataLoader.TickData> filterTicksForTimeScale(
            TickDataLoader.TickDataSet tickDataSet, TimeScale timeScale) {
        
        LOGGER.info("=== TICK DATA FILTER START (MIT ALL-UNTERSTÜTZUNG) ===");
        LOGGER.info("Input: tickDataSet=" + (tickDataSet != null ? tickDataSet.getSignalId() + " mit " + tickDataSet.getTickCount() + " Ticks" : "NULL") + 
                   ", timeScale=" + (timeScale != null ? timeScale.getLabel() : "NULL"));
        
        if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
            LOGGER.warning("KEINE TICK-DATEN zum Filtern vorhanden!");
            return new ArrayList<>();
        }
        
        if (timeScale == null) {
            LOGGER.warning("timeScale ist NULL - verwende alle verfügbaren Daten");
            List<TickDataLoader.TickData> allTicks = tickDataSet.getTicks();
            LOGGER.info("Gebe alle " + allTicks.size() + " Ticks ohne Zeitfilterung zurück");
            return new ArrayList<>(allTicks);
        }
        
        // NEU: ALL-Modus behandeln - kompletter Zeitraum ohne Filterung
        if (timeScale.isAll()) {
            List<TickDataLoader.TickData> allTicks = tickDataSet.getTicks();
            LOGGER.info("=== ALL-MODUS AKTIVIERT ===");
            LOGGER.info("Gebe kompletten verfügbaren Zeitraum zurück: " + allTicks.size() + " Ticks");
            
            if (!allTicks.isEmpty()) {
                LocalDateTime earliestTime = allTicks.get(0).getTimestamp();
                LocalDateTime latestTime = allTicks.get(allTicks.size() - 1).getTimestamp();
                LOGGER.info("Kompletter Zeitraum: " + earliestTime + " bis " + latestTime);
                
                // Berechne und logge Zeitspanne
                long totalMinutes = java.time.Duration.between(earliestTime, latestTime).toMinutes();
                LOGGER.info("Zeitspanne: " + totalMinutes + " Minuten (" + (totalMinutes / 60.0) + " Stunden, " + (totalMinutes / 1440.0) + " Tage)");
            }
            
            logFilterResult(allTicks, timeScale, false);
            LOGGER.info("=== TICK DATA FILTER ENDE (ALL-MODUS) ===");
            return new ArrayList<>(allTicks);
        }
        
        // NORMALE ZEITINTERVALL-FILTERUNG (bestehende Logik)
        List<TickDataLoader.TickData> allTicks = tickDataSet.getTicks();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = now.minusMinutes(timeScale.getDisplayMinutes());
        
        LOGGER.info("Zeitfilter-Parameter:");
        LOGGER.info("  Aktuelle Zeit: " + now);
        LOGGER.info("  Zeitfenster: " + timeScale.getDisplayMinutes() + " Minuten");
        LOGGER.info("  Cutoff-Zeit: " + cutoffTime);
        LOGGER.info("  Gesamt verfügbare Ticks: " + allTicks.size());
        
        // Zeitraum der verfügbaren Daten analysieren
        if (!allTicks.isEmpty()) {
            LocalDateTime earliestTime = allTicks.get(0).getTimestamp();
            LocalDateTime latestTime = allTicks.get(allTicks.size() - 1).getTimestamp();
            LOGGER.info("Verfügbarer Daten-Zeitraum: " + earliestTime + " bis " + latestTime);
            
            // Prüfe ob überhaupt Daten im gewünschten Zeitfenster liegen
            if (latestTime.isBefore(cutoffTime)) {
                LOGGER.warning("ALLE DATEN ZU ALT! Neueste Daten (" + latestTime + ") sind älter als Cutoff (" + cutoffTime + ")");
                
                // FALLBACK-STRATEGIE: Gebe die neuesten verfügbaren Daten zurück
                int maxRecentTicks = Math.min(10, allTicks.size()); // Maximal 10 neueste Ticks
                List<TickDataLoader.TickData> recentTicks = new ArrayList<>();
                for (int i = allTicks.size() - maxRecentTicks; i < allTicks.size(); i++) {
                    recentTicks.add(allTicks.get(i));
                }
                LOGGER.info("FALLBACK: Gebe " + recentTicks.size() + " neueste verfügbare Ticks zurück");
                LOGGER.info("TIPP: Verwende TimeScale.ALL für kompletten Zeitraum!");
                logFilterResult(recentTicks, timeScale, true);
                return recentTicks;
            }
        }
        
        // NORMALE FILTERUNG: Ticks nach Zeitfenster filtern
        List<TickDataLoader.TickData> filteredTicks = new ArrayList<>();
        int excludedCount = 0;
        
        for (int i = 0; i < allTicks.size(); i++) {
            TickDataLoader.TickData tick = allTicks.get(i);
            
            if (tick.getTimestamp().isAfter(cutoffTime)) {
                filteredTicks.add(tick);
                
                // Log ersten paar und letzten paar gefilterten Ticks
                if (filteredTicks.size() <= 3 || i >= allTicks.size() - 3) {
                    LOGGER.info("GEFILTERT TICK #" + filteredTicks.size() + ": " + 
                               tick.getTimestamp() + " (Equity=" + tick.getEquity() + 
                               ", Floating=" + tick.getFloatingProfit() + ")");
                }
            } else {
                excludedCount++;
                
                // Log ersten paar ausgeschlossenen Ticks
                if (excludedCount <= 3) {
                    LOGGER.info("AUSGESCHLOSSEN TICK #" + (i+1) + ": " + tick.getTimestamp() + 
                               " (zu alt, vor " + cutoffTime + ")");
                }
            }
        }
        
        LOGGER.info("FILTER-ERGEBNIS: " + filteredTicks.size() + " Ticks einbezogen, " + 
                   excludedCount + " Ticks ausgeschlossen");
        
        // ZUSÄTZLICHE FALLBACK-PRÜFUNG: Falls trotz theoretisch vorhandener Daten nichts gefiltert wurde
        if (filteredTicks.isEmpty() && !allTicks.isEmpty()) {
            LOGGER.warning("UNERWARTETER FALL: Filter ergab 0 Ticks obwohl Daten vorhanden sind!");
            LOGGER.warning("Analysiere alle Tick-Zeitstempel:");
            
            for (int i = 0; i < Math.min(allTicks.size(), 5); i++) {
                TickDataLoader.TickData tick = allTicks.get(i);
                boolean isAfterCutoff = tick.getTimestamp().isAfter(cutoffTime);
                LOGGER.warning("  Tick #" + (i+1) + ": " + tick.getTimestamp() + 
                              " -> isAfter(" + cutoffTime + ")=" + isAfterCutoff);
            }
            
            // NOTFALL-FALLBACK: Gebe zumindest den neuesten Tick zurück
            TickDataLoader.TickData latestTick = allTicks.get(allTicks.size() - 1);
            filteredTicks.add(latestTick);
            LOGGER.warning("NOTFALL-FALLBACK: Gebe mindestens neuesten Tick zurück: " + latestTick.getTimestamp());
            LOGGER.warning("TIPP: Verwende TimeScale.ALL für kompletten Zeitraum!");
        }
        
        logFilterResult(filteredTicks, timeScale, false);
        
        LOGGER.info("=== TICK DATA FILTER ENDE ===");
        return filteredTicks;
    }
    
    /**
     * ERWEITERT: Loggt das Filter-Ergebnis detailliert mit ALL-Unterstützung
     */
    private static void logFilterResult(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale, boolean wasFallback) {
        if (filteredTicks.isEmpty()) {
            LOGGER.warning("FILTER-ERGEBNIS: LEER - keine Ticks gefiltert!");
            return;
        }
        
        LOGGER.info("=== FILTER-ERGEBNIS ZUSAMMENFASSUNG ===");
        LOGGER.info("Zeitskala: " + timeScale.getLabel() + (timeScale.isAll() ? " (KOMPLETTER ZEITRAUM)" : ""));
        LOGGER.info("Gefilterte Ticks: " + filteredTicks.size());
        LOGGER.info("Fallback verwendet: " + wasFallback);
        
        TickDataLoader.TickData first = filteredTicks.get(0);
        TickDataLoader.TickData last = filteredTicks.get(filteredTicks.size() - 1);
        
        LOGGER.info("Zeitraum: " + first.getTimestamp() + " bis " + last.getTimestamp());
        
        // Berechne Zeitspanne
        long durationMinutes = java.time.Duration.between(first.getTimestamp(), last.getTimestamp()).toMinutes();
        LOGGER.info("Zeitspanne: " + durationMinutes + " Minuten (" + String.format("%.1f", durationMinutes / 60.0) + " Stunden)");
        
        LOGGER.info("Equity-Bereich: " + 
                   String.format("%.2f bis %.2f",
                   filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getEquity).min().orElse(0),
                   filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getEquity).max().orElse(0)));
        LOGGER.info("Floating-Bereich: " + 
                   String.format("%.2f bis %.2f",
                   filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getFloatingProfit).min().orElse(0),
                   filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getFloatingProfit).max().orElse(0)));
        
        // Berechne und logge Drawdown-Bereich
        double minDrawdown = filteredTicks.stream()
            .mapToDouble(tick -> calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()))
            .min().orElse(0);
        double maxDrawdown = filteredTicks.stream()
            .mapToDouble(tick -> calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()))
            .max().orElse(0);
        
        LOGGER.info("Drawdown-Bereich: " + String.format("%.6f%% bis %.6f%%", minDrawdown, maxDrawdown));
    }
    
    /**
     * NEU: Ermittelt die optimale Chart-Breite basierend auf dem Zeitraum
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     * @return Empfohlene Chart-Breite in Pixeln
     */
    public static int calculateOptimalChartWidth(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            return 800; // Standard-Breite
        }
        
        int tickCount = filteredTicks.size();
        
        if (timeScale.isAll()) {
            // Für ALL: Variable Breite basierend auf Tick-Anzahl
            // 2-4 Pixel pro Tick für gute Sichtbarkeit
            int width = Math.max(800, Math.min(5000, tickCount * 3));
            LOGGER.info("ALL-Modus: Optimale Chart-Breite " + width + "px für " + tickCount + " Ticks");
            return width;
        } else {
            // Für normale Zeitintervalle: Feste Breiten
            switch (timeScale) {
                case M1:
                case M5:
                    return 800;   // Kurze Intervalle - Standard
                case M15:
                case H1:
                    return 1200;  // Mittlere Intervalle - breiter
                case H4:
                case D1:
                    return 1600;  // Lange Intervalle - sehr breit
                default:
                    return 800;
            }
        }
    }
    
    /**
     * VERBESSERT: Berechnet Drawdown-Statistiken für gefilterte Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @return DrawdownStatistics Objekt
     */
    public static DrawdownStatistics calculateDrawdownStatistics(List<TickDataLoader.TickData> filteredTicks) {
        LOGGER.info("=== DRAWDOWN STATISTIKEN BERECHNUNG ===");
        
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("KEINE DATEN für Drawdown-Statistiken");
            return new DrawdownStatistics();
        }
        
        LOGGER.info("Berechne Drawdown-Statistiken für " + filteredTicks.size() + " Ticks");
        
        double[] drawdownValues = new double[filteredTicks.size()];
        for (int i = 0; i < filteredTicks.size(); i++) {
            TickDataLoader.TickData tick = filteredTicks.get(i);
            drawdownValues[i] = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
            
            // Logge erste paar Berechnungen
            if (i < 3) {
                LOGGER.info("Drawdown #" + (i+1) + ": " + 
                           String.format("%.6f%% (Equity=%.2f, Floating=%.2f)", 
                           drawdownValues[i], tick.getEquity(), tick.getFloatingProfit()));
            }
        }
        
        double minDrawdown = java.util.Arrays.stream(drawdownValues).min().orElse(0.0);
        double maxDrawdown = java.util.Arrays.stream(drawdownValues).max().orElse(0.0);
        double avgDrawdown = java.util.Arrays.stream(drawdownValues).average().orElse(0.0);
        
        LOGGER.info("Statistiken: Min=" + String.format("%.6f%%", minDrawdown) + 
                   ", Max=" + String.format("%.6f%%", maxDrawdown) + 
                   ", Avg=" + String.format("%.6f%%", avgDrawdown));
        
        return new DrawdownStatistics(minDrawdown, maxDrawdown, avgDrawdown);
    }
    
    /**
     * NEU: Berechnet den Drawdown-Prozentsatz (lokale Kopie für Konsistenz)
     * 
     * @param equity Der Kontostand
     * @param floatingProfit Der Floating Profit
     * @return Der Drawdown-Prozentsatz
     */
    private static double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            LOGGER.warning("Equity ist 0 - Drawdown kann nicht berechnet werden");
            return 0.0;
        }
        return (floatingProfit / equity) * 100.0;
    }
    
    /**
     * ERWEITERT: Erstellt einen Filter-Bericht für Debugging mit ALL-Unterstützung
     * 
     * @param tickDataSet Das TickDataSet
     * @param timeScale Die Zeitskala
     * @return Detaillierter Bericht als String
     */
    public static String createFilterReport(TickDataLoader.TickDataSet tickDataSet, TimeScale timeScale) {
        StringBuilder report = new StringBuilder();
        report.append("=== TICK DATA FILTER BERICHT (MIT ALL-UNTERSTÜTZUNG) ===\n");
        
        if (tickDataSet == null) {
            report.append("ERROR: tickDataSet ist NULL\n");
            return report.toString();
        }
        
        if (timeScale == null) {
            report.append("ERROR: timeScale ist NULL\n");
            return report.toString();
        }
        
        report.append("Signal ID: ").append(tickDataSet.getSignalId()).append("\n");
        report.append("Gesamt Ticks: ").append(tickDataSet.getTickCount()).append("\n");
        report.append("Zeitskala: ").append(timeScale.getLabel());
        if (timeScale.isAll()) {
            report.append(" (KOMPLETTER ZEITRAUM - KEINE FILTERUNG)");
        } else {
            report.append(" (").append(timeScale.getDisplayMinutes()).append(" Minuten)");
        }
        report.append("\n");
        
        List<TickDataLoader.TickData> filteredTicks = filterTicksForTimeScale(tickDataSet, timeScale);
        report.append("Gefilterte Ticks: ").append(filteredTicks.size()).append("\n");
        
        if (!filteredTicks.isEmpty()) {
            DrawdownStatistics stats = calculateDrawdownStatistics(filteredTicks);
            report.append("Drawdown Statistiken:\n");
            report.append("  Min: ").append(stats.getFormattedMinDrawdown()).append("\n");
            report.append("  Max: ").append(stats.getFormattedMaxDrawdown()).append("\n");
            report.append("  Avg: ").append(stats.getFormattedAvgDrawdown()).append("\n");
            
            // Optimale Chart-Breite anzeigen
            int optimalWidth = calculateOptimalChartWidth(filteredTicks, timeScale);
            report.append("Empfohlene Chart-Breite: ").append(optimalWidth).append("px\n");
        }
        
        return report.toString();
    }
    
    /**
     * ERWEITERT: Datenklasse für Drawdown-Statistiken mit verbesserter Formatierung
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
            return String.format("%.6f%%", minDrawdown);
        }
        
        public String getFormattedMaxDrawdown() {
            return String.format("%.6f%%", maxDrawdown);
        }
        
        public String getFormattedAvgDrawdown() {
            return String.format("%.6f%%", avgDrawdown);
        }
        
        /**
         * NEU: Gibt einen detaillierten String zurück
         */
        @Override
        public String toString() {
            if (!hasData) {
                return "DrawdownStatistics{Keine Daten}";
            }
            return String.format("DrawdownStatistics{Min=%.6f%%, Max=%.6f%%, Avg=%.6f%%}", 
                               minDrawdown, maxDrawdown, avgDrawdown);
        }
    }
}