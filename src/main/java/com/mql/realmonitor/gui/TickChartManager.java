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
 * Verwaltet die JFreeChart Objekte für Tick-Charts
 * Erstellt und konfiguriert Haupt-Chart und Drawdown-Chart
 */
public class TickChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartManager.class.getName());
    
    // Chart Komponenten
    private JFreeChart mainChart;
    private JFreeChart drawdownChart;
    
    // TimeSeries für Haupt-Chart
    private TimeSeries equitySeries;
    private TimeSeries floatingProfitSeries;
    private TimeSeries totalValueSeries;
    
    // TimeSeries für Drawdown-Chart
    private TimeSeries drawdownPercentSeries;
    
    private final String signalId;
    private final String providerName;
    
    /**
     * Konstruktor
     */
    public TickChartManager(String signalId, String providerName) {
        this.signalId = signalId;
        this.providerName = providerName;
        createBothCharts();
    }
    
    /**
     * Erstellt beide JFreeCharts
     */
    private void createBothCharts() {
        createMainChart();
        createDrawdownChart();
        LOGGER.info("Beide Charts (Haupt + Drawdown) erstellt für Signal: " + signalId);
    }
    
    /**
     * Erstellt den Haupt-Chart (Equity, Floating Profit, Gesamtwert)
     */
    private void createMainChart() {
        // TimeSeries für die verschiedenen Datenreihen
        equitySeries = new TimeSeries("Equity (Kontostand)");
        floatingProfitSeries = new TimeSeries("Floating Profit");
        totalValueSeries = new TimeSeries("Gesamtwert");
        
        // TimeSeriesCollection erstellen
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(equitySeries);
        dataset.addSeries(floatingProfitSeries);
        dataset.addSeries(totalValueSeries);
        
        // Haupt-Chart erstellen
        mainChart = ChartFactory.createTimeSeriesChart(
            "Tick Daten - " + signalId + " (" + providerName + ")",
            "Zeit",
            "Wert",
            dataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Haupt-Chart konfigurieren
        configureMainChart();
    }
    
    /**
     * Erstellt den Drawdown-Chart (Floating Profit in Prozent)
     */
    private void createDrawdownChart() {
        // TimeSeries für Drawdown-Prozentsatz
        drawdownPercentSeries = new TimeSeries("Equity Drawdown (%)");
        
        // TimeSeriesCollection für Drawdown
        TimeSeriesCollection drawdownDataset = new TimeSeriesCollection();
        drawdownDataset.addSeries(drawdownPercentSeries);
        
        // Drawdown-Chart erstellen
        drawdownChart = ChartFactory.createTimeSeriesChart(
            "Equity Drawdown (%)",
            "Zeit",
            "Prozent (%)",
            drawdownDataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Drawdown-Chart konfigurieren
        configureDrawdownChart();
    }
    
    /**
     * Konfiguriert den Haupt-Chart (Farben, Renderer etc.)
     */
    private void configureMainChart() {
        XYPlot plot = mainChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);  // Equity
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesLinesVisible(1, true);  // Floating Profit
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesLinesVisible(2, true);  // Total Value
        renderer.setSeriesShapesVisible(2, false);
        
        // Farben setzen
        renderer.setSeriesPaint(0, new Color(255, 200, 0));  // Equity in Gelb
        renderer.setSeriesPaint(1, Color.RED);               // Floating Profit in Rot
        renderer.setSeriesPaint(2, new Color(0, 200, 0));    // Gesamtwert in Grün
        
        // Linienstärke
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        renderer.setSeriesStroke(2, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 
                                                    1.0f, new float[]{5.0f, 5.0f}, 0.0f)); // Gestrichelt
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        mainChart.setBackgroundPaint(new Color(240, 240, 240));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Wert (USD)");
        plot.getDomainAxis().setLabel("Zeit");
    }
    
    /**
     * Konfiguriert den Drawdown-Chart
     */
    private void configureDrawdownChart() {
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, false);
        
        // Farbe für Drawdown
        renderer.setSeriesPaint(0, new Color(200, 0, 200)); // Magenta für Drawdown
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        drawdownChart.setBackgroundPaint(new Color(250, 250, 250));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Nulllinie hervorheben
        plot.setRangeZeroBaselineVisible(true);
        plot.setRangeZeroBaselinePaint(Color.BLACK);
        plot.setRangeZeroBaselineStroke(new BasicStroke(1.0f));
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Drawdown (%)");
        plot.getDomainAxis().setLabel("Zeit");
        
        // Y-Achse manuell konfigurieren
        plot.getRangeAxis().setAutoRange(true);
        plot.getRangeAxis().setLowerMargin(0.1); // 10% Margin unten
        plot.getRangeAxis().setUpperMargin(0.1); // 10% Margin oben
    }
    
    /**
     * Aktualisiert beide Charts mit gefilterten Daten
     */
    public void updateChartsWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks.isEmpty() || mainChart == null || drawdownChart == null) {
            return;
        }
        
        try {
            // Chart Serien leeren
            clearAllSeries();
            
            // Gefilterte Tick-Daten zu beiden Chart-Serien hinzufügen
            for (TickDataLoader.TickData tick : filteredTicks) {
                Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                Second second = new Second(javaDate);
                
                // Haupt-Chart Daten
                equitySeries.add(second, tick.getEquity());
                floatingProfitSeries.add(second, tick.getFloatingProfit());
                totalValueSeries.add(second, tick.getTotalValue());
                
                // Drawdown-Prozentsatz berechnen und hinzufügen
                double drawdownPercent = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
                drawdownPercentSeries.add(second, drawdownPercent);
            }
            
            // Chart-Titel aktualisieren
            updateChartTitles(timeScale, filteredTicks.size());
            
            // Y-Achsen-Bereiche anpassen
            adjustMainChartYAxisRange(filteredTicks);
            adjustDrawdownChartYAxisRange(filteredTicks);
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors(filteredTicks);
            
            LOGGER.info("Beide Charts aktualisiert mit " + filteredTicks.size() + " gefilterten Ticks für " + timeScale.getLabel());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren der Chart-Daten", e);
        }
    }
    
    /**
     * Leert alle Chart-Serien
     */
    private void clearAllSeries() {
        equitySeries.clear();
        floatingProfitSeries.clear();
        totalValueSeries.clear();
        drawdownPercentSeries.clear();
    }
    
    /**
     * Aktualisiert die Chart-Titel
     */
    private void updateChartTitles(TimeScale timeScale, int tickCount) {
        mainChart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                          timeScale.getLabel() + " (" + tickCount + " Ticks)");
        
        drawdownChart.setTitle("Equity Drawdown (%) - " + signalId + " (" + providerName + ") - " + timeScale.getLabel());
    }
    
    /**
     * Berechnet den Drawdown-Prozentsatz
     */
    private double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            return 0.0;
        }
        return (floatingProfit / equity) * 100.0;
    }
    
    /**
     * Passt den Y-Achsen-Bereich des Haupt-Charts an
     */
    private void adjustMainChartYAxisRange(List<TickDataLoader.TickData> filteredTicks) {
        if (mainChart == null || filteredTicks.isEmpty()) {
            return;
        }
        
        XYPlot plot = mainChart.getXYPlot();
        
        double minValue = filteredTicks.stream().mapToDouble(tick -> 
            Math.min(Math.min(tick.getEquity(), tick.getFloatingProfit()), tick.getTotalValue())
        ).min().orElse(0.0);
        
        double maxValue = filteredTicks.stream().mapToDouble(tick -> 
            Math.max(Math.max(tick.getEquity(), tick.getFloatingProfit()), tick.getTotalValue())
        ).max().orElse(0.0);
        
        if (minValue > 0) {
            minValue = 0;
        }
        
        double range = maxValue - minValue;
        double padding = Math.max(range * 0.05, 100);
        
        plot.getRangeAxis().setRange(minValue - padding, maxValue + padding);
        plot.getDomainAxis().setAutoRange(true);
    }
    
    /**
     * Passt den Y-Achsen-Bereich des Drawdown-Charts an
     */
    private void adjustDrawdownChartYAxisRange(List<TickDataLoader.TickData> filteredTicks) {
        if (drawdownChart == null || filteredTicks.isEmpty()) {
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // Min/Max Drawdown-Prozentsätze finden
        double minDrawdown = filteredTicks.stream().mapToDouble(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())
        ).min().orElse(0.0);
        
        double maxDrawdown = filteredTicks.stream().mapToDouble(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())
        ).max().orElse(0.0);
        
        // Symmetrischer Bereich um 0
        double maxAbsValue = Math.max(Math.abs(minDrawdown), Math.abs(maxDrawdown));
        double padding = Math.max(maxAbsValue * 0.1, 1.0); // Mindestens 1% Padding
        
        plot.getRangeAxis().setRange(-maxAbsValue - padding, maxAbsValue + padding);
        plot.getDomainAxis().setAutoRange(true);
        
        LOGGER.fine("Drawdown Y-Achse angepasst: " + (-maxAbsValue - padding) + " bis " + (maxAbsValue + padding));
    }
    
    /**
     * Aktualisiert die Farben des Drawdown-Charts
     */
    private void updateDrawdownChartColors(List<TickDataLoader.TickData> filteredTicks) {
        if (drawdownChart == null || filteredTicks.isEmpty()) {
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        // Prüfe ob mehr positive oder negative Werte vorhanden sind
        long positiveCount = filteredTicks.stream().filter(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()) > 0
        ).count();
        
        long negativeCount = filteredTicks.stream().filter(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()) < 0
        ).count();
        
        // Farbe basierend auf Mehrheit setzen
        if (positiveCount > negativeCount) {
            renderer.setSeriesPaint(0, new Color(0, 150, 0)); // Grün für überwiegend positive Werte
            LOGGER.fine("Drawdown-Chart Farbe: Grün (mehr positive Werte)");
        } else if (negativeCount > positiveCount) {
            renderer.setSeriesPaint(0, new Color(200, 0, 0)); // Rot für überwiegend negative Werte
            LOGGER.fine("Drawdown-Chart Farbe: Rot (mehr negative Werte)");
        } else {
            renderer.setSeriesPaint(0, new Color(200, 0, 200)); // Magenta für ausgeglichen
            LOGGER.fine("Drawdown-Chart Farbe: Magenta (ausgeglichen)");
        }
    }
    
    // Getter-Methoden
    public JFreeChart getMainChart() {
        return mainChart;
    }
    
    public JFreeChart getDrawdownChart() {
        return drawdownChart;
    }
}