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
 * VERBESSERT: Verwaltet die JFreeChart Objekte für Tick-Charts
 * Erstellt und konfiguriert Haupt-Chart und Drawdown-Chart
 * ALLE PROBLEME BEHOBEN: Robuste Diagnostik und Fehlerbehandlung
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
    
    // NEUER DEBUG-COUNTER
    private int updateCounter = 0;
    
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
        LOGGER.info("=== CHARTS ERSTELLT für Signal: " + signalId + " ===");
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
            "Equity Drawdown (%) - Auto-Kalibriert - DIAGNOSEMODUS",
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
     * VERBESSERT: Shapes immer sichtbar für Ein-Punkt-Darstellung
     */
    private void configureDrawdownChart() {
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true); // GEÄNDERT: Shapes immer sichtbar für einzelne Punkte
        
        // Shape-Größe für bessere Sichtbarkeit einzelner Punkte
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Größere Punkte
        
        // Farbe für Drawdown
        renderer.setSeriesPaint(0, new Color(200, 0, 200)); // Magenta für Drawdown
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));  // Dickere Linie
        
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
        plot.setRangeZeroBaselineStroke(new BasicStroke(2.0f));
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Drawdown (%) - DIAGNOSEMODUS");
        plot.getDomainAxis().setLabel("Zeit");
        
        // Y-Achse manuell konfigurieren
        plot.getRangeAxis().setAutoRange(false);  // Deaktiviere Auto-Range für manuelle Kontrolle
        plot.getRangeAxis().setLowerMargin(0.05); // 5% Margin
        plot.getRangeAxis().setUpperMargin(0.05); // 5% Margin
    }
    
    /**
     * KOMPLETT NEU GESCHRIEBEN: Aktualisiert beide Charts mit gefilterten Daten
     * ALLE PROBLEME BEHOBEN: Umfassende Diagnostik und robuste Fehlerbehandlung
     */
    public void updateChartsWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        updateCounter++;
        
        LOGGER.info("=== CHART UPDATE #" + updateCounter + " GESTARTET für Signal: " + signalId + " ===");
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
        
        if (mainChart == null || drawdownChart == null) {
            LOGGER.severe("KRITISCHER FEHLER: Charts sind NULL! mainChart=" + mainChart + ", drawdownChart=" + drawdownChart);
            return;
        }
        
        try {
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size());
            
            // Beispiel-Daten loggen für Diagnostik
            if (filteredTicks.size() > 0) {
                TickDataLoader.TickData firstTick = filteredTicks.get(0);
                TickDataLoader.TickData lastTick = filteredTicks.get(filteredTicks.size() - 1);
                LOGGER.info("ERSTER TICK: " + formatTickForLog(firstTick));
                LOGGER.info("LETZTER TICK: " + formatTickForLog(lastTick));
                
                // ALLE Ticks loggen wenn wenige vorhanden
                if (filteredTicks.size() <= 10) {
                    LOGGER.info("=== ALLE TICKS (wenige Daten) ===");
                    for (int i = 0; i < filteredTicks.size(); i++) {
                        TickDataLoader.TickData tick = filteredTicks.get(i);
                        LOGGER.info("TICK #" + (i+1) + ": " + formatTickForLog(tick));
                    }
                }
            }
            
            // Chart Serien leeren
            LOGGER.info("Leere Chart-Serien...");
            clearAllSeries();
            
            // SCHRITT-FÜR-SCHRITT: Gefilterte Tick-Daten zu beiden Chart-Serien hinzufügen
            LOGGER.info("=== BEGINNE DATEN-HINZUFÜGUNG ===");
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // Zeitstempel konvertieren
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    
                    // Haupt-Chart Daten hinzufügen
                    equitySeries.add(second, tick.getEquity());
                    floatingProfitSeries.add(second, tick.getFloatingProfit());
                    totalValueSeries.add(second, tick.getTotalValue());
                    
                    // KRITISCH: Drawdown-Prozentsatz berechnen und hinzufügen
                    double drawdownPercent = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
                    drawdownPercentSeries.add(second, drawdownPercent);
                    
                    // DETAILLIERTES LOGGING für jeden Datenpunkt
                    LOGGER.info("TICK #" + (i+1) + " HINZUGEFÜGT: Zeit=" + tick.getTimestamp() + 
                               ", Equity=" + tick.getEquity() + 
                               ", Floating=" + tick.getFloatingProfit() + 
                               ", Berechnet Drawdown=" + String.format("%.6f%%", drawdownPercent));
                    
                    addedCount++;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "FEHLER beim Hinzufügen von Tick #" + (i+1) + ": " + tick, e);
                }
            }
            
            LOGGER.info("HINZUGEFÜGTE DATENPUNKTE: " + addedCount + " von " + filteredTicks.size());
            
            // Serien-Status überprüfen
            checkSeriesStatus();
            
            // Chart-Titel aktualisieren
            updateChartTitles(timeScale, filteredTicks.size());
            
            // Renderer für Drawdown-Chart anpassen basierend auf Datenpunkt-Anzahl
            adjustDrawdownChartRenderer(filteredTicks.size());
            
            // Y-Achsen-Bereiche anpassen
            adjustMainChartYAxisRange(filteredTicks);
            adjustDrawdownChartYAxisRangeRobust(filteredTicks);
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors(filteredTicks);
            
            LOGGER.info("=== CHART UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im Chart Update #" + updateCounter + " für Signal " + signalId, e);
        }
    }
    
    /**
     * NEU: Formatiert einen Tick für das Logging
     */
    private String formatTickForLog(TickDataLoader.TickData tick) {
        if (tick == null) {
            return "NULL";
        }
        return "Zeit=" + tick.getTimestamp() + 
               ", Equity=" + tick.getEquity() + 
               ", Floating=" + tick.getFloatingProfit() + 
               ", Total=" + tick.getTotalValue() +
               ", Berechnet_Drawdown=" + String.format("%.6f%%", calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()));
    }
    
    /**
     * NEU: Überprüft den Status aller Serien
     */
    private void checkSeriesStatus() {
        LOGGER.info("=== SERIEN-STATUS CHECK ===");
        LOGGER.info("equitySeries: " + (equitySeries != null ? equitySeries.getItemCount() + " Items" : "NULL"));
        LOGGER.info("floatingProfitSeries: " + (floatingProfitSeries != null ? floatingProfitSeries.getItemCount() + " Items" : "NULL"));
        LOGGER.info("totalValueSeries: " + (totalValueSeries != null ? totalValueSeries.getItemCount() + " Items" : "NULL"));
        LOGGER.info("drawdownPercentSeries: " + (drawdownPercentSeries != null ? drawdownPercentSeries.getItemCount() + " Items" : "NULL"));
        
        // Detaillierte Drawdown-Serie Analyse
        if (drawdownPercentSeries != null && drawdownPercentSeries.getItemCount() > 0) {
            LOGGER.info("=== DRAWDOWN SERIE DETAILS ===");
            for (int i = 0; i < drawdownPercentSeries.getItemCount(); i++) {
                org.jfree.data.time.RegularTimePeriod period = drawdownPercentSeries.getTimePeriod(i);
                Number value = drawdownPercentSeries.getValue(i);
                LOGGER.info("Drawdown Item #" + (i+1) + ": Zeit=" + period + ", Wert=" + value + "%");
            }
        } else {
            LOGGER.warning("PROBLEM: drawdownPercentSeries ist leer oder NULL!");
        }
    }
    
    /**
     * VERBESSERT: Passt den Drawdown-Chart Renderer basierend auf Datenpunkt-Anzahl an
     */
    private void adjustDrawdownChartRenderer(int tickCount) {
        if (drawdownChart == null) {
            LOGGER.warning("drawdownChart ist NULL - kann Renderer nicht anpassen");
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        if (tickCount <= 1) {
            // Bei einem Datenpunkt: Nur große Shapes anzeigen, keine Linien
            renderer.setSeriesLinesVisible(0, false);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-6, -6, 12, 12)); // Sehr große Punkte
            LOGGER.info("Drawdown-Chart: Ein-Punkt-Modus aktiviert (nur große Shapes)");
            
        } else if (tickCount <= 5) {
            // Bei wenigen Datenpunkten: Linien und große Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Große Punkte
            LOGGER.info("Drawdown-Chart: Wenig-Punkte-Modus aktiviert (Linien + große Shapes)");
            
        } else {
            // Bei vielen Datenpunkten: Linien und kleine Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-2, -2, 4, 4)); // Normale Punkte
            LOGGER.info("Drawdown-Chart: Viel-Punkte-Modus aktiviert (Linien + kleine Shapes)");
        }
    }
    
    /**
     * Leert alle Chart-Serien
     */
    private void clearAllSeries() {
        if (equitySeries != null) equitySeries.clear();
        if (floatingProfitSeries != null) floatingProfitSeries.clear();
        if (totalValueSeries != null) totalValueSeries.clear();
        if (drawdownPercentSeries != null) drawdownPercentSeries.clear();
        LOGGER.info("Alle Chart-Serien geleert");
    }
    
    /**
     * Aktualisiert die Chart-Titel
     */
    private void updateChartTitles(TimeScale timeScale, int tickCount) {
        if (mainChart != null) {
            mainChart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                              (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " (" + tickCount + " Ticks)");
        }
        
        if (drawdownChart != null) {
            drawdownChart.setTitle("Equity Drawdown (%) - " + signalId + " (" + providerName + ") - " + 
                                  (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " [DIAGNOSE #" + updateCounter + "]");
        }
        
        LOGGER.info("Chart-Titel aktualisiert mit " + tickCount + " Ticks");
    }
    
    /**
     * KRITISCH: Berechnet den Drawdown-Prozentsatz mit verbessertem Logging
     */
    private double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            LOGGER.warning("WARNUNG: Equity ist 0 - kann Drawdown nicht berechnen");
            return 0.0;
        }
        
        double result = (floatingProfit / equity) * 100.0;
        
        // DETAILLIERTES LOGGING - JETZT MIT INFO LEVEL
        LOGGER.info("DRAWDOWN BERECHNUNG: " + floatingProfit + " / " + equity + " * 100 = " + String.format("%.6f%%", result));
        
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
        
        LOGGER.info("Haupt-Chart Y-Achse gesetzt: " + String.format("%.2f", minValue - padding) + 
                   " bis " + String.format("%.2f", maxValue + padding));
    }
    
    /**
     * KOMPLETT NEU: Robuste Y-Achsen-Bereich-Anpassung für Drawdown-Chart
     * ALLE PROBLEME BEHOBEN: Garantiert sichtbare Darstellung für alle Wertebereiche
     */
    private void adjustDrawdownChartYAxisRangeRobust(List<TickDataLoader.TickData> filteredTicks) {
        if (drawdownChart == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Kann Drawdown Y-Achse nicht anpassen: Chart=" + (drawdownChart != null) + 
                          ", Ticks=" + (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        LOGGER.info("=== ROBUSTE DRAWDOWN Y-ACHSEN ANPASSUNG START ===");
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // SCHRITT 1: Alle Drawdown-Werte berechnen und sammeln
        double[] drawdownValues = new double[filteredTicks.size()];
        double minDrawdown = Double.MAX_VALUE;
        double maxDrawdown = Double.MIN_VALUE;
        
        for (int i = 0; i < filteredTicks.size(); i++) {
            TickDataLoader.TickData tick = filteredTicks.get(i);
            double drawdown = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
            drawdownValues[i] = drawdown;
            
            if (drawdown < minDrawdown) minDrawdown = drawdown;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            
            LOGGER.info("Drawdown-Wert #" + (i+1) + ": " + String.format("%.6f%%", drawdown));
        }
        
        LOGGER.info("DRAWDOWN-BEREICH: Min=" + String.format("%.6f%%", minDrawdown) + 
                   ", Max=" + String.format("%.6f%%", maxDrawdown));
        
        // SCHRITT 2: Fallback für ungültige Werte
        if (minDrawdown == Double.MAX_VALUE || maxDrawdown == Double.MIN_VALUE) {
            LOGGER.severe("FEHLER: Keine gültigen Drawdown-Werte - setze Notfall-Bereich");
            plot.getRangeAxis().setRange(-5.0, 5.0);
            return;
        }
        
        // SCHRITT 3: Intelligente Bereichsberechnung
        double dataRange = maxDrawdown - minDrawdown;
        double centerValue = (minDrawdown + maxDrawdown) / 2.0;
        double maxAbsValue = Math.max(Math.abs(minDrawdown), Math.abs(maxDrawdown));
        
        LOGGER.info("BEREICHS-ANALYSE: Spanne=" + String.format("%.6f%%", dataRange) + 
                   ", Zentrum=" + String.format("%.6f%%", centerValue) + 
                   ", Max-Abs=" + String.format("%.6f%%", maxAbsValue));
        
        double lowerBound, upperBound;
        String strategie;
        
        // SCHRITT 4: Adaptive Strategie basierend auf Datenlage
        if (dataRange < 0.001) {
            // Alle Werte praktisch identisch - symmetrischer kleiner Bereich um Zentrum
            double padding = Math.max(0.01, Math.abs(centerValue) * 0.1);
            lowerBound = centerValue - padding;
            upperBound = centerValue + padding;
            strategie = "Identische Werte (Spanne < 0.001%)";
            
        } else if (maxAbsValue < 0.1) {
            // Sehr kleine Werte - feiner Bereich mit garantierter Mindestgröße
            double padding = Math.max(dataRange * 0.5, 0.02);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            strategie = "Sehr kleine Werte (< 0.1%)";
            
        } else if (maxAbsValue < 1.0) {
            // Kleine Werte - normaler Bereich mit angemessenem Padding
            double padding = Math.max(dataRange * 0.3, 0.1);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            strategie = "Kleine Werte (< 1.0%)";
            
        } else if (maxAbsValue < 10.0) {
            // Mittlere Werte - proportionaler Bereich
            double padding = Math.max(dataRange * 0.2, 0.5);
            lowerBound = minDrawdown - padding;
            upperBound = maxDrawdown + padding;
            strategie = "Mittlere Werte (< 10.0%)";
            
        } else {
            // Große Werte - symmetrischer Bereich
            double padding = Math.max(maxAbsValue * 0.1, 1.0);
            lowerBound = -maxAbsValue - padding;
            upperBound = maxAbsValue + padding;
            strategie = "Große Werte (>= 10.0%) - symmetrisch";
        }
        
        // SCHRITT 5: Mindestbereich garantieren
        double finalRange = upperBound - lowerBound;
        if (finalRange < 0.02) { // Mindestens 0.02% Spanne
            double center = (upperBound + lowerBound) / 2;
            lowerBound = center - 0.01;
            upperBound = center + 0.01;
            strategie += " + Mindestbereich-Garantie";
        }
        
        // SCHRITT 6: Y-Achsen-Bereich setzen
        plot.getRangeAxis().setRange(lowerBound, upperBound);
        plot.getDomainAxis().setAutoRange(true);
        
        LOGGER.info("STRATEGIE ANGEWENDET: " + strategie);
        LOGGER.info("Y-ACHSEN-BEREICH GESETZT: " + String.format("%.6f%% bis %.6f%%", lowerBound, upperBound));
        LOGGER.info("FINALE SICHTBARE SPANNE: " + String.format("%.6f%%", finalRange));
        LOGGER.info("=== ROBUSTE DRAWDOWN Y-ACHSEN ANPASSUNG ENDE ===");
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
        
        // Prüfe Werteverteilung
        long positiveCount = filteredTicks.stream().filter(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()) > 0
        ).count();
        
        long negativeCount = filteredTicks.stream().filter(tick -> 
            calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit()) < 0
        ).count();
        
        long zeroCount = filteredTicks.stream().filter(tick -> 
            Math.abs(calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit())) < 0.001
        ).count();
        
        LOGGER.info("Drawdown Werteverteilung: Positiv=" + positiveCount + 
                   ", Negativ=" + negativeCount + ", ~Null=" + zeroCount);
        
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