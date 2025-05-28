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
 * KORRIGIERT: Verwaltet jetzt sowohl Drawdown-Chart als auch Profit-Chart
 * Kombiniert beide Chart-Manager für eine einheitliche Verwaltung
 * ALLE X-ACHSEN PROBLEME BEHOBEN: Korrekte Domain-Achsen-Kalibrierung
 */
public class TickChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartManager.class.getName());
    
    // Chart Komponenten - BEIDE CHARTS
    private JFreeChart drawdownChart;
    private JFreeChart profitChart;
    
    // TimeSeries für Drawdown-Chart
    private TimeSeries drawdownPercentSeries;
    
    // TimeSeries für Profit-Chart
    private TimeSeries equitySeries;      // Kontostand (grün)
    private TimeSeries totalValueSeries;  // Gesamtwert (gelb)
    
    // NEU: Separater Profit-Chart-Manager
    private ProfitChartManager profitChartManager;
    
    private final String signalId;
    private final String providerName;
    
    // DEBUG-COUNTER
    private int updateCounter = 0;
    
    /**
     * Konstruktor
     */
    public TickChartManager(String signalId, String providerName) {
        this.signalId = signalId;
        this.providerName = providerName;
        
        // Beide Charts erstellen
        createDrawdownChart();
        createProfitChart();
    }
    
    /**
     * Erstellt nur den Drawdown-Chart
     */
    private void createDrawdownChart() {
        // TimeSeries für Drawdown-Prozentsatz
        drawdownPercentSeries = new TimeSeries("Equity Drawdown (%)");
        
        // TimeSeriesCollection für Drawdown
        TimeSeriesCollection drawdownDataset = new TimeSeriesCollection();
        drawdownDataset.addSeries(drawdownPercentSeries);
        
        // Drawdown-Chart erstellen
        drawdownChart = ChartFactory.createTimeSeriesChart(
            "Equity Drawdown (%) - " + signalId + " (" + providerName + ") - DIAGNOSEMODUS",
            "Zeit",
            "Prozent (%)",
            drawdownDataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Drawdown-Chart konfigurieren
        configureDrawdownChart();
        
        LOGGER.info("=== DRAWDOWN-CHART ERSTELLT für Signal: " + signalId + " ===");
    }
    
    /**
     * NEU: Erstellt den Profit-Chart über den separaten Manager
     */
    private void createProfitChart() {
        profitChartManager = new ProfitChartManager(signalId, providerName);
        profitChart = profitChartManager.getProfitChart();
        
        // Direkte Referenzen für Konsistenz
        equitySeries = profitChartManager.getEquitySeries();
        totalValueSeries = profitChartManager.getTotalValueSeries();
        
        LOGGER.info("=== PROFIT-CHART ERSTELLT für Signal: " + signalId + " ===");
    }
    
    /**
     * Konfiguriert den Drawdown-Chart
     */
    private void configureDrawdownChart() {
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true); // Shapes immer sichtbar für einzelne Punkte
        
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
        
        // Y-Achse manuell konfigurieren für intelligente Skalierung
        plot.getRangeAxis().setAutoRange(false);  // Deaktiviere Auto-Range für manuelle Kontrolle
        plot.getRangeAxis().setLowerMargin(0.05); // 5% Margin
        plot.getRangeAxis().setUpperMargin(0.05); // 5% Margin
    }
    
    /**
     * ERWEITERT: Aktualisiert BEIDE Charts mit gefilterten Daten
     */
    public void updateChartsWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        updateCounter++;
        
        LOGGER.info("=== BEIDE CHARTS UPDATE #" + updateCounter + " GESTARTET für Signal: " + signalId + " ===");
        LOGGER.info("Input-Daten: filteredTicks=" + (filteredTicks != null ? filteredTicks.size() : "NULL") + 
                   ", timeScale=" + (timeScale != null ? timeScale.getLabel() : "NULL"));
        
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Keine Daten für Chart-Updates");
            return;
        }
        
        // 1. Drawdown-Chart aktualisieren
        updateDrawdownChartWithData(filteredTicks, timeScale);
        
        // 2. Profit-Chart über Manager aktualisieren
        if (profitChartManager != null) {
            profitChartManager.updateProfitChartWithData(filteredTicks, timeScale);
        }
        
        LOGGER.info("=== BEIDE CHARTS UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN ===");
    }
    
    /**
     * KORRIGIERT: Aktualisiert nur den Drawdown-Chart (für Rückwärtskompatibilität)
     */
    public void updateDrawdownChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        LOGGER.info("=== DRAWDOWN CHART UPDATE GESTARTET ===");
        
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("WARNUNG: Keine gefilterten Ticks für Drawdown-Chart!");
            return;
        }
        
        if (drawdownChart == null) {
            LOGGER.severe("KRITISCHER FEHLER: drawdownChart ist NULL!");
            return;
        }
        
        try {
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size());
            
            // Chart Serie leeren
            LOGGER.info("Leere Drawdown-Chart-Serie...");
            clearDrawdownSeries();
            
            // SCHRITT-FÜR-SCHRITT: Gefilterte Tick-Daten zur Drawdown-Serie hinzufügen
            LOGGER.info("=== BEGINNE DRAWDOWN-DATEN-HINZUFÜGUNG ===");
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // Zeitstempel konvertieren
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    
                    // KRITISCH: Drawdown-Prozentsatz berechnen und hinzufügen
                    double drawdownPercent = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
                    drawdownPercentSeries.add(second, drawdownPercent);
                    
                    // DETAILLIERTES LOGGING für jeden Datenpunkt
                    if (i < 3 || i >= filteredTicks.size() - 3) {
                        LOGGER.info("DRAWDOWN TICK #" + (i+1) + " HINZUGEFÜGT: Zeit=" + tick.getTimestamp() + 
                                   ", Equity=" + tick.getEquity() + 
                                   ", Floating=" + tick.getFloatingProfit() + 
                                   ", Berechnet Drawdown=" + String.format("%.6f%%", drawdownPercent));
                    }
                    
                    addedCount++;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "FEHLER beim Hinzufügen von Drawdown-Tick #" + (i+1) + ": " + tick, e);
                }
            }
            
            LOGGER.info("HINZUGEFÜGTE DRAWDOWN-DATENPUNKTE: " + addedCount + " von " + filteredTicks.size());
            
            // Serie-Status überprüfen
            checkDrawdownSeriesStatus();
            
            // Chart-Titel aktualisieren
            updateDrawdownChartTitle(timeScale, filteredTicks.size());
            
            // Renderer für Drawdown-Chart anpassen basierend auf Datenpunkt-Anzahl
            adjustDrawdownChartRenderer(filteredTicks.size());
            
            // Y-Achsen-Bereich anpassen
            adjustDrawdownChartYAxisRangeRobust(filteredTicks);
            
            // KORRIGIERT: X-Achsen-Bereich anpassen
            adjustDrawdownChartXAxisRange(filteredTicks);
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors(filteredTicks);
            
            LOGGER.info("=== DRAWDOWN CHART UPDATE ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im Drawdown Chart Update für Signal " + signalId, e);
        }
    }
    
    /**
     * Überprüft den Status der Drawdown-Serie
     */
    private void checkDrawdownSeriesStatus() {
        LOGGER.info("=== DRAWDOWN-SERIES-STATUS CHECK ===");
        LOGGER.info("drawdownPercentSeries: " + (drawdownPercentSeries != null ? drawdownPercentSeries.getItemCount() + " Items" : "NULL"));
        
        // Detaillierte Drawdown-Serie Analyse
        if (drawdownPercentSeries != null && drawdownPercentSeries.getItemCount() > 0) {
            LOGGER.info("=== DRAWDOWN SERIE DETAILS ===");
            for (int i = 0; i < Math.min(3, drawdownPercentSeries.getItemCount()); i++) {
                org.jfree.data.time.RegularTimePeriod period = drawdownPercentSeries.getTimePeriod(i);
                Number value = drawdownPercentSeries.getValue(i);
                LOGGER.info("Drawdown Item #" + (i+1) + ": Zeit=" + period + ", Wert=" + value + "%");
            }
        } else {
            LOGGER.warning("PROBLEM: drawdownPercentSeries ist leer oder NULL!");
        }
    }
    
    /**
     * Leert die Drawdown-Chart-Serie
     */
    private void clearDrawdownSeries() {
        if (drawdownPercentSeries != null) drawdownPercentSeries.clear();
        LOGGER.info("Drawdown-Chart-Serie geleert");
    }
    
    /**
     * Aktualisiert den Drawdown-Chart-Titel
     */
    private void updateDrawdownChartTitle(TimeScale timeScale, int tickCount) {
        if (drawdownChart != null) {
            drawdownChart.setTitle("Equity Drawdown (%) - " + signalId + " (" + providerName + ") - " + 
                                  (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " [DIAGNOSE #" + updateCounter + "]");
        }
        
        LOGGER.info("Drawdown-Chart-Titel aktualisiert mit " + tickCount + " Ticks");
    }
    
    /**
     * Passt den Drawdown-Chart Renderer basierend auf Datenpunkt-Anzahl an
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
     * ROBUSTE Y-Achsen-Bereich-Anpassung für Drawdown-Chart
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
            
            if (i < 3) { // Nur erste 3 loggen
                LOGGER.info("Drawdown-Wert #" + (i+1) + ": " + String.format("%.6f%%", drawdown));
            }
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
        
        LOGGER.info("STRATEGIE ANGEWENDET: " + strategie);
        LOGGER.info("Y-ACHSEN-BEREICH GESETZT: " + String.format("%.6f%% bis %.6f%%", lowerBound, upperBound));
        LOGGER.info("=== ROBUSTE DRAWDOWN Y-ACHSEN ANPASSUNG ENDE ===");
    }
    
    /**
     * KORRIGIERT: INTELLIGENTE X-Achsen (Zeit) Kalibrierung für Drawdown-Chart
     * Verhindert zu enge Zeitbereiche bei wenigen Datenpunkten
     */
    private void adjustDrawdownChartXAxisRange(List<TickDataLoader.TickData> filteredTicks) {
        if (drawdownChart == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Kann Drawdown X-Achse nicht anpassen: Chart=" + (drawdownChart != null) + 
                          ", Ticks=" + (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        LOGGER.info("=== INTELLIGENTE DRAWDOWN X-ACHSEN KALIBRIERUNG START ===");
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // Zeitbereich der Daten ermitteln
        java.time.LocalDateTime earliestTime = filteredTicks.get(0).getTimestamp();
        java.time.LocalDateTime latestTime = filteredTicks.get(filteredTicks.size() - 1).getTimestamp();
        
        LOGGER.info("ZEIT-DATEN:");
        LOGGER.info("  Anzahl Datenpunkte: " + filteredTicks.size());
        LOGGER.info("  Früheste Zeit: " + earliestTime);
        LOGGER.info("  Späteste Zeit: " + latestTime);
        
        // Zeitspanne in Millisekunden
        long timeSpanMillis = java.time.Duration.between(earliestTime, latestTime).toMillis();
        LOGGER.info("  Zeitspanne: " + timeSpanMillis + " ms");
        
        // Domain-Achse manuell kalibrieren
        plot.getDomainAxis().setAutoRange(false);
        
        java.time.LocalDateTime displayStart, displayEnd;
        
        if (filteredTicks.size() == 1) {
            // Ein Datenpunkt: Erstelle sinnvollen Zeitbereich um den Punkt
            java.time.LocalDateTime centerTime = earliestTime;
            
            // Standardbereich: ±30 Minuten um den Datenpunkt
            displayStart = centerTime.minusMinutes(30);
            displayEnd = centerTime.plusMinutes(30);
            
            LOGGER.info("EIN DATENPUNKT - Erstelle 1-Stunden-Fenster um " + centerTime);
            
        } else if (timeSpanMillis < 60000) { // Weniger als 1 Minute
            // Sehr kurze Zeitspanne: Erweitere auf mindestens 10 Minuten
            java.time.LocalDateTime centerTime = earliestTime.plus(java.time.Duration.ofMillis(timeSpanMillis / 2));
            displayStart = centerTime.minusMinutes(5);
            displayEnd = centerTime.plusMinutes(5);
            
            LOGGER.info("KURZE ZEITSPANNE (" + timeSpanMillis + " ms) - Erweitere auf 10 Minuten");
            
        } else if (timeSpanMillis < 600000) { // Weniger als 10 Minuten
            // Kurze Zeitspanne: Füge 25% Padding hinzu
            long paddingMillis = timeSpanMillis / 4;
            displayStart = earliestTime.minus(java.time.Duration.ofMillis(paddingMillis));
            displayEnd = latestTime.plus(java.time.Duration.ofMillis(paddingMillis));
            
            LOGGER.info("MITTLERE ZEITSPANNE - 25% Padding hinzugefügt");
            
        } else {
            // Normale Zeitspanne: Füge 10% Padding hinzu
            long paddingMillis = timeSpanMillis / 10;
            displayStart = earliestTime.minus(java.time.Duration.ofMillis(paddingMillis));
            displayEnd = latestTime.plus(java.time.Duration.ofMillis(paddingMillis));
            
            LOGGER.info("NORMALE ZEITSPANNE - 10% Padding hinzugefügt");
        }
        
        // KORRIGIERT: LocalDateTime zu Date konvertieren für JFreeChart
        try {
            java.util.Date startDate = java.util.Date.from(displayStart.atZone(java.time.ZoneId.systemDefault()).toInstant());
            java.util.Date endDate = java.util.Date.from(displayEnd.atZone(java.time.ZoneId.systemDefault()).toInstant());
            
            // KORRIGIERT: Domain-Achse als DateAxis casten für setRange mit Dates
            if (plot.getDomainAxis() instanceof org.jfree.chart.axis.DateAxis) {
                org.jfree.chart.axis.DateAxis dateAxis = (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();
                dateAxis.setMinimumDate(startDate);
                dateAxis.setMaximumDate(endDate);
                LOGGER.info("=== DRAWDOWN X-ACHSEN-BEREICH MIT DATEAXIS ERFOLGREICH GESETZT ===");
            } else {
                // Fallback: Verwende setRange mit numerischen Werten
                plot.getDomainAxis().setRange(startDate.getTime(), endDate.getTime());
                LOGGER.info("=== DRAWDOWN X-ACHSEN-BEREICH MIT NUMERISCHEN WERTEN GESETZT ===");
            }
            
            LOGGER.info("Anzeige von: " + displayStart);
            LOGGER.info("Anzeige bis: " + displayEnd);
            LOGGER.info("Sichtbare Zeitspanne: " + java.time.Duration.between(displayStart, displayEnd).toMinutes() + " Minuten");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FEHLER beim Setzen des Drawdown X-Achsen-Bereichs - verwende Auto-Range", e);
            plot.getDomainAxis().setAutoRange(true);
        }
        
        LOGGER.info("=== INTELLIGENTE DRAWDOWN X-ACHSEN KALIBRIERUNG ENDE ===");
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
        
        LOGGER.info("Drawdown Werteverteilung: Positiv=" + positiveCount + ", Negativ=" + negativeCount);
        
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
    
    // Getter-Methoden - BEIDE CHARTS
    public JFreeChart getDrawdownChart() {
        return drawdownChart;
    }
    
    /**
     * NEU: Gibt den Profit-Chart zurück
     */
    public JFreeChart getProfitChart() {
        return profitChart;
    }
    
    /**
     * NEU: Gibt den Profit-Chart-Manager zurück
     */
    public ProfitChartManager getProfitChartManager() {
        return profitChartManager;
    }
    
    /**
     * @deprecated Der Haupt-Chart wurde entfernt - verwende getDrawdownChart() oder getProfitChart()
     */
    @Deprecated
    public JFreeChart getMainChart() {
        LOGGER.warning("getMainChart() aufgerufen - Haupt-Chart wurde entfernt! Verwende getDrawdownChart() oder getProfitChart()");
        return null;
    }
}