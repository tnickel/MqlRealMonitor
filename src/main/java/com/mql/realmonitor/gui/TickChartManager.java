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
 * KORRIGIERT: Auto-Kalibrierung mit verbessertem Debug-Logging und korrigierter Werteberechnung
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
        LOGGER.info("Beide Charts (Haupt + Drawdown mit korrigierter Auto-Kalibrierung) erstellt für Signal: " + signalId);
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
            "Equity Drawdown (%) - Auto-Kalibriert",
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
        plot.getRangeAxis().setLabel("Drawdown (%) - Auto-Kalibriert");
        plot.getDomainAxis().setLabel("Zeit");
        
        // Y-Achse manuell konfigurieren
        plot.getRangeAxis().setAutoRange(false);  // Deaktiviere Auto-Range für manuelle Kontrolle
        plot.getRangeAxis().setLowerMargin(0.02); // 2% Margin
        plot.getRangeAxis().setUpperMargin(0.02); // 2% Margin
    }
    
    /**
     * Aktualisiert beide Charts mit gefilterten Daten
     */
    public void updateChartsWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks.isEmpty() || mainChart == null || drawdownChart == null) {
            LOGGER.warning("Keine Daten für Chart-Update: filteredTicks.size=" + 
                          (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        try {
            LOGGER.info("=== CHART UPDATE STARTED ===");
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size());
            
            // Chart Serien leeren
            clearAllSeries();
            
            // Debug: Erste Beispiel-Daten loggen
            if (filteredTicks.size() > 0) {
                TickDataLoader.TickData firstTick = filteredTicks.get(0);
                TickDataLoader.TickData lastTick = filteredTicks.get(filteredTicks.size() - 1);
                LOGGER.info("Erster Tick: Equity=" + firstTick.getEquity() + ", Floating=" + firstTick.getFloatingProfit());
                LOGGER.info("Letzter Tick: Equity=" + lastTick.getEquity() + ", Floating=" + lastTick.getFloatingProfit());
            }
            
            // Gefilterte Tick-Daten zu beiden Chart-Serien hinzufügen
            int addedCount = 0;
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
                
                // Debug: Ersten paar Drawdown-Werte loggen
                if (addedCount < 5) {
                    LOGGER.info("Tick " + (addedCount + 1) + ": Equity=" + tick.getEquity() + 
                               ", Floating=" + tick.getFloatingProfit() + 
                               ", Drawdown=" + String.format("%.6f%%", drawdownPercent));
                }
                addedCount++;
            }
            
            LOGGER.info("Hinzugefügte Datenpunkte: " + addedCount);
            
            // Chart-Titel aktualisieren
            updateChartTitles(timeScale, filteredTicks.size());
            
            // Y-Achsen-Bereiche anpassen
            adjustMainChartYAxisRange(filteredTicks);
            adjustDrawdownChartYAxisRangeAutoCalibrated(filteredTicks);
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors(filteredTicks);
            
            LOGGER.info("=== CHART UPDATE COMPLETED ===");
            
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
        LOGGER.fine("Chart-Serien geleert");
    }
    
    /**
     * Aktualisiert die Chart-Titel
     */
    private void updateChartTitles(TimeScale timeScale, int tickCount) {
        mainChart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                          timeScale.getLabel() + " (" + tickCount + " Ticks)");
        
        drawdownChart.setTitle("Equity Drawdown (%) - " + signalId + " (" + providerName + ") - " + 
                              timeScale.getLabel() + " [Auto-Kalibriert]");
    }
    
    /**
     * Berechnet den Drawdown-Prozentsatz
     */
    private double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            LOGGER.warning("Equity ist 0 - kann Drawdown nicht berechnen");
            return 0.0;
        }
        double result = (floatingProfit / equity) * 100.0;
        LOGGER.finest("Drawdown berechnet: " + floatingProfit + " / " + equity + " * 100 = " + result + "%");
        return result;
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
        
        LOGGER.info("Haupt-Chart Y-Achse: " + (minValue - padding) + " bis " + (maxValue + padding));
    }
    
    /**
     * KORRIGIERTE METHODE: Passt den Y-Achsen-Bereich des Drawdown-Charts mit Auto-Kalibrierung an
     * Optimiert für bessere Sichtbarkeit kleiner Bewegungen mit detailliertem Debug-Logging
     */
    private void adjustDrawdownChartYAxisRangeAutoCalibrated(List<TickDataLoader.TickData> filteredTicks) {
        if (drawdownChart == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Kann Drawdown Y-Achse nicht anpassen: Chart=" + (drawdownChart != null) + 
                          ", Ticks=" + (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        LOGGER.info("=== DRAWDOWN AUTO-KALIBRIERUNG START ===");
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // Min/Max Drawdown-Prozentsätze finden mit detailliertem Logging
        double minDrawdown = Double.MAX_VALUE;
        double maxDrawdown = Double.MIN_VALUE;
        
        for (TickDataLoader.TickData tick : filteredTicks) {
            double drawdown = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
            if (drawdown < minDrawdown) minDrawdown = drawdown;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
        }
        
        LOGGER.info("Drawdown-Bereich der Daten: " + String.format("%.6f%% bis %.6f%%", minDrawdown, maxDrawdown));
        
        // Fallback falls keine gültigen Werte
        if (minDrawdown == Double.MAX_VALUE || maxDrawdown == Double.MIN_VALUE) {
            LOGGER.warning("Keine gültigen Drawdown-Werte gefunden - verwende Standardbereich");
            plot.getRangeAxis().setRange(-1.0, 1.0);
            return;
        }
        
        // KORRIGIERTE AUTO-KALIBRIERUNG für bessere Sichtbarkeit
        double dataRange = maxDrawdown - minDrawdown;
        double maxAbsValue = Math.max(Math.abs(minDrawdown), Math.abs(maxDrawdown));
        
        LOGGER.info("Datenspanne: " + String.format("%.6f%%", dataRange) + 
                   ", Max Abs-Wert: " + String.format("%.6f%%", maxAbsValue));
        
        double lowerBound, upperBound;
        String calibrationType;
        
        if (maxAbsValue < 0.001) {
            // Ultra kleine Werte (< 0.001%) - sehr sehr feiner Bereich
            lowerBound = Math.min(minDrawdown - 0.0005, -0.001);
            upperBound = Math.max(maxDrawdown + 0.0005, 0.001);
            calibrationType = "Ultra-fein (< 0.001%)";
            
        } else if (maxAbsValue < 0.01) {
            // Extrem kleine Werte (< 0.01%) - sehr feiner Bereich
            double padding = Math.max(dataRange * 0.3, 0.002);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            calibrationType = "Extrem-fein (< 0.01%)";
            
        } else if (maxAbsValue < 0.1) {
            // Sehr kleine Werte (< 0.1%) - feiner Bereich
            double padding = Math.max(dataRange * 0.25, 0.01);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            calibrationType = "Sehr-fein (< 0.1%)";
            
        } else if (maxAbsValue < 1.0) {
            // Kleine Werte (< 1%) - adaptiver Bereich
            double padding = Math.max(dataRange * 0.2, 0.05);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            calibrationType = "Klein (< 1%)";
            
        } else if (maxAbsValue < 5.0) {
            // Mittlere Werte (< 5%) - normaler Bereich
            double padding = Math.max(dataRange * 0.15, 0.2);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            calibrationType = "Mittel (< 5%)";
            
        } else {
            // Große Werte (>= 5%) - symmetrischer Bereich
            double padding = Math.max(maxAbsValue * 0.1, 0.5);
            lowerBound = -maxAbsValue - padding;
            upperBound = maxAbsValue + padding;
            calibrationType = "Groß (>= 5%) - symmetrisch";
        }
        
        // Sicherstellen, dass der Bereich nicht zu klein wird
        double finalRange = upperBound - lowerBound;
        if (finalRange < 0.002) {  // Minimaler Bereich von 0.002%
            double center = (upperBound + lowerBound) / 2;
            lowerBound = center - 0.001;
            upperBound = center + 0.001;
            calibrationType += " + Min-Bereich-Korrektur";
        }
        
        // Y-Achsen-Bereich setzen
        plot.getRangeAxis().setRange(lowerBound, upperBound);
        plot.getDomainAxis().setAutoRange(true);
        
        LOGGER.info("Auto-Kalibrierung angewendet: " + calibrationType);
        LOGGER.info("Y-Achsen-Bereich gesetzt: " + String.format("%.6f%% bis %.6f%%", lowerBound, upperBound));
        LOGGER.info("Finale Sichtbare Spanne: " + String.format("%.6f%%", finalRange));
        LOGGER.info("=== DRAWDOWN AUTO-KALIBRIERUNG ENDE ===");
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
        
        long zeroCount = filteredTicks.stream().filter(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()) == 0
        ).count();
        
        LOGGER.info("Drawdown Werteverteilung: Positiv=" + positiveCount + 
                   ", Negativ=" + negativeCount + ", Null=" + zeroCount);
        
        // Farbe basierend auf Mehrheit setzen
        if (positiveCount > negativeCount) {
            renderer.setSeriesPaint(0, new Color(0, 150, 0)); // Grün für überwiegend positive Werte
            LOGGER.info("Drawdown-Chart Farbe: Grün (mehr positive Werte)");
        } else if (negativeCount > positiveCount) {
            renderer.setSeriesPaint(0, new Color(200, 0, 0)); // Rot für überwiegend negative Werte
            LOGGER.info("Drawdown-Chart Farbe: Rot (mehr negative Werte)");
        } else {
            renderer.setSeriesPaint(0, new Color(200, 0, 200)); // Magenta für ausgeglichen
            LOGGER.info("Drawdown-Chart Farbe: Magenta (ausgeglichen)");
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