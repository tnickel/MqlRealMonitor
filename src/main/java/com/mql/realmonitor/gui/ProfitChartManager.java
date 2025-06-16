package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * KORRIGIERT: Behält Performance-Optimierungen aber stellt korrekte Chart-Darstellung wieder her
 * BEHEBT: 4-Minuten-Blocking durch Zero-Data-Detection UND korrekte Profit-Darstellung
 * WIEDERHERGESTELLT: Korrekte Y-Achsen-Kalibrierung und Shapes für bessere Sichtbarkeit
 */
public class ProfitChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(ProfitChartManager.class.getName());
    
    // PERFORMANCE: Konstanten für optimale Chart-Performance 
    private static final double MIN_Y_RANGE = 1.0;  // Mindest-Y-Achsen-Spanne
    private static final double DEFAULT_Y_PADDING = 10.0;  // Standard-Y-Padding bei Null-Werten
    private static final int MAX_SERIES_ITEMS = 10000;  // Limit für TimeSeries-Performance
    
    // Chart Komponenten
    private JFreeChart profitChart;
    
    // TimeSeries für Profit-Chart
    private TimeSeries profitSeries;           // Realized Profit (grün)
    private TimeSeries profitPlusOpenSeries;   // Profit + Open Equity (gelb)
    
    private final String signalId;
    private final String providerName;
    
    // Profit-Berechnung
    private Double initialEquity = null;
    
    // DEBUG-COUNTER
    private int updateCounter = 0;
    
    /**
     * Konstruktor
     */
    public ProfitChartManager(String signalId, String providerName) {
        this.signalId = signalId;
        this.providerName = providerName;
        createProfitChart();
    }
    
    /**
     * KORRIGIERT: Erstellt Chart mit Performance UND korrekter Darstellung
     */
    private void createProfitChart() {
        // PERFORMANCE: TimeSeries mit Performance-Optimierungen (aber korrekte Darstellung)
        profitSeries = new TimeSeries("Profit");
        profitSeries.setMaximumItemCount(MAX_SERIES_ITEMS);  // Verhindert Memory-Leaks
        
        profitPlusOpenSeries = new TimeSeries("Profit + Open Equity"); 
        profitPlusOpenSeries.setMaximumItemCount(MAX_SERIES_ITEMS);
        
        // TimeSeriesCollection für Profit-Chart
        TimeSeriesCollection profitDataset = new TimeSeriesCollection();
        profitDataset.addSeries(profitSeries);
        profitDataset.addSeries(profitPlusOpenSeries);
        
        // Chart erstellen (mit korrekten Tooltips für bessere UX)
        profitChart = ChartFactory.createTimeSeriesChart(
            "Profit-Entwicklung - " + signalId + " (" + providerName + ") - DIAGNOSEMODUS",
            "Zeit",
            "Profit (€/$/etc.)",
            profitDataset,
            true,  // Legend
            true,  // Tooltips - REAKTIVIERT für bessere UX
            false  // URLs
        );
        
        // KORRIGIERT: Chart-Konfiguration mit korrekter Darstellung
        configureProfitChart();
        
        LOGGER.info("=== KORRIGIERTER PROFIT-CHART ERSTELLT für Signal: " + signalId + " ===");
    }
    
    /**
     * KORRIGIERT: Konfiguriert Chart für Performance UND korrekte Darstellung
     */
    private void configureProfitChart() {
        XYPlot plot = profitChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // KORRIGIERT: Shapes REAKTIVIERT für korrekte Darstellung (wie in Bild 2)
        renderer.setSeriesLinesVisible(0, true);  // Profit-Linie
        renderer.setSeriesShapesVisible(0, true); // Profit-Punkte REAKTIVIERT
        renderer.setSeriesLinesVisible(1, true);  // Profit+Open-Linie
        renderer.setSeriesShapesVisible(1, true); // Profit+Open-Punkte REAKTIVIERT
        
        // KORREKTE Shape-Größe für bessere Sichtbarkeit (wie Original)
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Profit
        renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Profit+Open (etwas größer)
        
        // KORREKTE Farben (wie Original)
        renderer.setSeriesPaint(0, new Color(0, 150, 0));   // Grün für Profit
        renderer.setSeriesPaint(1, new Color(255, 200, 0)); // Gelb für Profit + Open Equity
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // KORRIGIERT: Ursprüngliche Stärke
        renderer.setSeriesStroke(1, new BasicStroke(3.0f)); // KORRIGIERT: Ursprüngliche Stärke
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben (wie Original)
        profitChart.setBackgroundPaint(new Color(250, 250, 250));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Profit - DIAGNOSEMODUS");
        plot.getDomainAxis().setLabel("Zeit");
        
        // KRITISCH: Y-Achse für manuelle Kontrolle aber KORREKTER Auto-Range
        plot.getRangeAxis().setAutoRange(false);  
        plot.getRangeAxis().setLowerMargin(0.02); // KORRIGIERT: Ursprüngliche Margins
        plot.getRangeAxis().setUpperMargin(0.02);
    }
    
    /**
     * KORRIGIERT: Chart-Update mit Performance-Schutz UND korrekter Darstellung
     */
    public void updateProfitChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        updateCounter++;
        
        LOGGER.info("=== KORRIGIERTER PROFIT CHART UPDATE #" + updateCounter + " GESTARTET für Signal: " + signalId + " ===");
        LOGGER.info("Input-Daten: filteredTicks=" + (filteredTicks != null ? filteredTicks.size() : "NULL") + 
                   ", timeScale=" + (timeScale != null ? timeScale.getLabel() : "NULL"));
        
        if (filteredTicks == null) {
            LOGGER.severe("KRITISCHER FEHLER: filteredTicks ist NULL!");
            return;
        }
        
        if (filteredTicks.isEmpty()) {
            LOGGER.warning("WARNUNG: Keine gefilterten Ticks - Chart bleibt leer!");
            return;
        }
        
        if (profitChart == null) {
            LOGGER.severe("KRITISCHER FEHLER: profitChart ist NULL!");
            return;
        }
        
        try {
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size());
            
            // PERFORMANCE-CHECK: Erkenne Zero-Data-Pattern für Fast-Exit
            if (isZeroDataPattern(filteredTicks)) {
                LOGGER.warning("ZERO-DATA-PATTERN ERKANNT - verwende Fast-Rendering");
                handleZeroDataPatternOptimized(filteredTicks, timeScale);
                return;
            }
            
            // SCHRITT 1: Initial Equity bestimmen für Profit-Berechnung
            TickDataLoader.TickData firstTick = filteredTicks.get(0);
            initialEquity = firstTick.getEquity();
            
            LOGGER.info("=== PROFIT-BERECHNUNG INITIALISIERT ===");
            LOGGER.info("Initial Equity (Startkontostand): " + String.format("%.6f", initialEquity));
            
            // Chart Serien leeren
            LOGGER.info("Leere Profit-Chart-Serien...");
            clearProfitSeries();
            
            // SCHRITT 2: KORREKTE Daten-Hinzufügung (wie Original)
            LOGGER.info("=== BEGINNE PROFIT-DATEN-HINZUFÜGUNG (KORREKTE BERECHNUNG) ===");
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // Zeitstempel konvertieren
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    
                    // KORREKTE Profit-Berechnung (wie Original)
                    double currentEquity = tick.getEquity();
                    double realizedProfit = currentEquity - initialEquity;
                    profitSeries.add(second, realizedProfit);
                    
                    // KORREKTE Profit + Open Equity Berechnung
                    double floatingProfit = tick.getFloatingProfit();
                    double profitPlusOpen = realizedProfit + floatingProfit;
                    profitPlusOpenSeries.add(second, profitPlusOpen);
                    
                    addedCount++;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "FEHLER beim Hinzufügen von Profit-Tick #" + (i+1) + ": " + tick, e);
                }
            }
            
            LOGGER.info("HINZUGEFÜGTE PROFIT-DATENPUNKTE: " + addedCount + " von " + filteredTicks.size());
            
            // Chart-Titel aktualisieren
            updateChartTitle(timeScale, filteredTicks.size());
            
            // KORREKTE Y-Achsen-Kalibrierung (wie Original aber mit Performance-Schutz)
            adjustProfitChartYAxisRangeRobustCorrected(filteredTicks);
            
            // KORRIGIERT: X-Achsen-Bereich (wie Original)
            adjustProfitChartXAxisRange(filteredTicks);
            
            LOGGER.info("=== KORRIGIERTER PROFIT CHART UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im korrigierten Profit Chart Update #" + updateCounter + " für Signal " + signalId, e);
        }
    }
    
    /**
     * PERFORMANCE: Erkenne Zero-Data-Pattern (aber korrekte Schwellenwerte)
     */
    private boolean isZeroDataPattern(List<TickDataLoader.TickData> filteredTicks) {
        if (filteredTicks.size() < 3) return false; // Zu wenige Daten für Pattern-Erkennung
        
        TickDataLoader.TickData firstTick = filteredTicks.get(0);
        double initialEquity = firstTick.getEquity();
        
        int zeroCount = 0;
        for (TickDataLoader.TickData tick : filteredTicks) {
            double realizedProfit = tick.getEquity() - initialEquity;
            double profitPlusOpen = realizedProfit + tick.getFloatingProfit();
            
            // KORRIGIERT: Strengere Schwellenwerte für Zero-Detection
            if (Math.abs(realizedProfit) < 0.01 && Math.abs(profitPlusOpen) < 0.01) {
                zeroCount++;
            }
        }
        
        // KORRIGIERT: 95% statt 80% müssen Zero sein
        boolean isZeroPattern = zeroCount > filteredTicks.size() * 0.95;
        if (isZeroPattern) {
            LOGGER.info("ZERO-PATTERN: " + zeroCount + "/" + filteredTicks.size() + " Null-Werte");
        }
        
        return isZeroPattern;
    }
    
    /**
     * PERFORMANCE: Optimierte Zero-Data-Behandlung (verhindert 4-Minuten-Blocking)
     */
    private void handleZeroDataPatternOptimized(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        LOGGER.info("=== ZERO-DATA FAST-RENDERING START ===");
        
        // Leere Chart-Serien
        clearProfitSeries();
        
        // Initial Equity bestimmen
        TickDataLoader.TickData firstTick = filteredTicks.get(0);
        initialEquity = firstTick.getEquity();
        
        // FAST: Nur erste und letzte Datenpunkte (Null-Werte)
        try {
            Date firstDate = Date.from(firstTick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            profitSeries.add(new Second(firstDate), 0.0);
            profitPlusOpenSeries.add(new Second(firstDate), 0.0);
            
            if (filteredTicks.size() > 1) {
                TickDataLoader.TickData lastTick = filteredTicks.get(filteredTicks.size() - 1);
                Date lastDate = Date.from(lastTick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                profitSeries.add(new Second(lastDate), 0.0);
                profitPlusOpenSeries.add(new Second(lastDate), 0.0);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler bei Zero-Data Fast-Rendering", e);
        }
        
        // Y-Achsen-Bereich für Null-Werte
        XYPlot plot = profitChart.getXYPlot();
        plot.getRangeAxis().setRange(-DEFAULT_Y_PADDING, DEFAULT_Y_PADDING);
        
        // Chart-Titel
        profitChart.setTitle("Profit-Entwicklung - " + signalId + " (" + providerName + ") - ZERO-DATA FAST");
        
        LOGGER.info("=== ZERO-DATA FAST-RENDERING BEENDET ===");
    }
    
    /**
     * KORRIGIERT: Y-Achsen-Kalibrierung wie Original aber mit Performance-Schutz
     */
    private void adjustProfitChartYAxisRangeRobustCorrected(List<TickDataLoader.TickData> filteredTicks) {
        if (profitChart == null || filteredTicks.isEmpty() || initialEquity == null) {
            LOGGER.warning("Kann Profit Y-Achse nicht anpassen");
            return;
        }
        
        LOGGER.info("=== KORRIGIERTE PROFIT Y-ACHSEN KALIBRIERUNG START ===");
        
        XYPlot plot = profitChart.getXYPlot();
        
        // KORREKTE Datenbereich-Ermittlung
        double minProfit = Double.MAX_VALUE;
        double maxProfit = Double.MIN_VALUE;
        double minProfitPlusOpen = Double.MAX_VALUE;
        double maxProfitPlusOpen = Double.MIN_VALUE;
        
        for (TickDataLoader.TickData tick : filteredTicks) {
            double realizedProfit = tick.getEquity() - initialEquity;
            double profitPlusOpen = realizedProfit + tick.getFloatingProfit();
            
            if (realizedProfit < minProfit) minProfit = realizedProfit;
            if (realizedProfit > maxProfit) maxProfit = realizedProfit;
            if (profitPlusOpen < minProfitPlusOpen) minProfitPlusOpen = profitPlusOpen;
            if (profitPlusOpen > maxProfitPlusOpen) maxProfitPlusOpen = profitPlusOpen;
        }
        
        // Globale Min/Max-Werte
        double globalMin = Math.min(minProfit, minProfitPlusOpen);
        double globalMax = Math.max(maxProfit, maxProfitPlusOpen);
        double dataRange = globalMax - globalMin;
        
        LOGGER.info("PROFIT-DATEN-ANALYSE:");
        LOGGER.info("  Realized Profit-Bereich: " + String.format("%.6f bis %.6f", minProfit, maxProfit));
        LOGGER.info("  Profit+Open-Bereich: " + String.format("%.6f bis %.6f", minProfitPlusOpen, maxProfitPlusOpen));
        LOGGER.info("  Globaler Bereich: " + String.format("%.6f bis %.6f", globalMin, globalMax));
        LOGGER.info("  Daten-Spanne: " + String.format("%.6f", dataRange));
        
        // KORRIGIERTE Padding-Berechnung (wie Original)
        double lowerBound, upperBound;
        
        if (dataRange == 0) {
            // PERFORMANCE-SCHUTZ: Identische Werte - sicherer Bereich
            double centerValue = globalMin;
            double minimalRange = Math.max(Math.abs(centerValue) * 0.001, 0.01);
            
            lowerBound = centerValue - minimalRange;
            upperBound = centerValue + minimalRange;
            
        } else {
            // KORREKTE Padding-Berechnung (wie Original)
            double basePadding = dataRange * 0.15;  // 15% der Daten-Spanne
            double relativePadding = Math.abs((globalMin + globalMax) / 2.0) * 0.005; // 0.5%
            double finalPadding = Math.max(basePadding, relativePadding);
            double minPadding = dataRange * 0.05; // Mindestens 5%
            
            if (finalPadding < minPadding) {
                finalPadding = minPadding;
            }
            
            lowerBound = globalMin - finalPadding;
            upperBound = globalMax + finalPadding;
        }
        
        // Y-Achsen-Bereich setzen
        plot.getRangeAxis().setRange(lowerBound, upperBound);
        
        LOGGER.info("=== Y-ACHSEN-BEREICH FINAL GESETZT ===");
        LOGGER.info("Bereich: " + String.format("%.6f bis %.6f", lowerBound, upperBound));
        LOGGER.info("=== KORRIGIERTE PROFIT Y-ACHSEN KALIBRIERUNG ENDE ===");
    }
    
    /**
     * KORRIGIERT: X-Achsen-Kalibrierung (wie Original)
     */
    private void adjustProfitChartXAxisRange(List<TickDataLoader.TickData> filteredTicks) {
        if (profitChart == null || filteredTicks.isEmpty()) {
            return;
        }
        
        XYPlot plot = profitChart.getXYPlot();
        
        // Zeitbereich der Daten ermitteln
        java.time.LocalDateTime earliestTime = filteredTicks.get(0).getTimestamp();
        java.time.LocalDateTime latestTime = filteredTicks.get(filteredTicks.size() - 1).getTimestamp();
        
        // Zeitspanne in Millisekunden
        long timeSpanMillis = java.time.Duration.between(earliestTime, latestTime).toMillis();
        
        // Domain-Achse manuell kalibrieren
        plot.getDomainAxis().setAutoRange(false);
        
        java.time.LocalDateTime displayStart, displayEnd;
        
        if (filteredTicks.size() == 1) {
            displayStart = earliestTime.minusMinutes(30);
            displayEnd = earliestTime.plusMinutes(30);
        } else if (timeSpanMillis < 60000) { // <1 Minute
            java.time.LocalDateTime centerTime = earliestTime.plus(java.time.Duration.ofMillis(timeSpanMillis / 2));
            displayStart = centerTime.minusMinutes(5);
            displayEnd = centerTime.plusMinutes(5);
        } else if (timeSpanMillis < 600000) { // <10 Minuten
            long paddingMillis = timeSpanMillis / 4;
            displayStart = earliestTime.minus(java.time.Duration.ofMillis(paddingMillis));
            displayEnd = latestTime.plus(java.time.Duration.ofMillis(paddingMillis));
        } else {
            // Normale Zeitspanne: 10% Padding
            long paddingMillis = timeSpanMillis / 10;
            displayStart = earliestTime.minus(java.time.Duration.ofMillis(paddingMillis));
            displayEnd = latestTime.plus(java.time.Duration.ofMillis(paddingMillis));
        }
        
        // LocalDateTime zu Date konvertieren
        try {
            java.util.Date startDate = java.util.Date.from(displayStart.atZone(java.time.ZoneId.systemDefault()).toInstant());
            java.util.Date endDate = java.util.Date.from(displayEnd.atZone(java.time.ZoneId.systemDefault()).toInstant());
            
            if (plot.getDomainAxis() instanceof org.jfree.chart.axis.DateAxis) {
                org.jfree.chart.axis.DateAxis dateAxis = (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();
                dateAxis.setMinimumDate(startDate);
                dateAxis.setMaximumDate(endDate);
            } else {
                plot.getDomainAxis().setRange(startDate.getTime(), endDate.getTime());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FEHLER beim Setzen des X-Achsen-Bereichs", e);
            plot.getDomainAxis().setAutoRange(true);
        }
    }
    
    /**
     * Leert die Profit-Chart-Serien
     */
    private void clearProfitSeries() {
        if (profitSeries != null) profitSeries.clear();
        if (profitPlusOpenSeries != null) profitPlusOpenSeries.clear();
    }
    
    /**
     * Aktualisiert den Chart-Titel
     */
    private void updateChartTitle(TimeScale timeScale, int tickCount) {
        if (profitChart != null) {
            profitChart.setTitle("Profit-Entwicklung - " + signalId + " (" + providerName + ") - " + 
                                (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " [DIAGNOSE #" + updateCounter + "]");
        }
        
        LOGGER.info("Profit-Chart-Titel aktualisiert mit " + tickCount + " Ticks");
    }
    
    // Getter-Methoden
    public JFreeChart getProfitChart() {
        return profitChart;
    }
    
    public TimeSeries getProfitSeries() {
        return profitSeries;
    }
    
    public TimeSeries getProfitPlusOpenSeries() {
        return profitPlusOpenSeries;
    }
    
    // Deprecated Getter (für Rückwärtskompatibilität)
    @Deprecated
    public TimeSeries getEquitySeries() {
        LOGGER.warning("getEquitySeries() deprecated - verwende getProfitSeries()");
        return profitSeries;
    }
    
    @Deprecated  
    public TimeSeries getTotalValueSeries() {
        LOGGER.warning("getTotalValueSeries() deprecated - verwende getProfitPlusOpenSeries()");
        return profitPlusOpenSeries;
    }
}