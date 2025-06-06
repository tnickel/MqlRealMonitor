package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
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
 * KORRIGIERT: ECHTER TOTAL VALUE DRAWDOWN mit Peak-Tracking (nicht mehr Equity-basiert)
 */
public class TickChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartManager.class.getName());
    
    // Chart Komponenten - BEIDE CHARTS
    private JFreeChart drawdownChart;
    private JFreeChart profitChart;
    
    // TimeSeries für Drawdown-Chart (KORRIGIERT: Total Value statt Equity)
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
     * Erstellt nur den Drawdown-Chart (KORRIGIERT: Total Value Drawdown)
     */
    private void createDrawdownChart() {
        // TimeSeries für Total Value Drawdown-Prozentsatz
        drawdownPercentSeries = new TimeSeries("Total Value Drawdown (%)");
        
        // TimeSeriesCollection für Drawdown
        TimeSeriesCollection drawdownDataset = new TimeSeriesCollection();
        drawdownDataset.addSeries(drawdownPercentSeries);
        
        // Drawdown-Chart erstellen (KORRIGIERT: Title zeigt Total Value)
        drawdownChart = ChartFactory.createTimeSeriesChart(
            "Total Value Drawdown (%) - " + signalId + " (" + providerName + ") - DIAGNOSEMODUS",
            "Zeit",
            "Total Value Drawdown (%)",
            drawdownDataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Drawdown-Chart konfigurieren
        configureDrawdownChart();
        
        LOGGER.info("=== TOTAL VALUE DRAWDOWN-CHART ERSTELLT für Signal: " + signalId + " ===");
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
     * Konfiguriert den Drawdown-Chart (KORRIGIERT: Total Value Drawdown Labels)
     */
    private void configureDrawdownChart() {
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true); // Shapes immer sichtbar für einzelne Punkte
        
        // Shape-Größe für bessere Sichtbarkeit einzelner Punkte
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Größere Punkte
        
        // Farbe für Drawdown (Rot für negative Werte macht mehr Sinn)
        renderer.setSeriesPaint(0, new Color(200, 0, 0)); // Rot für Drawdown
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
        
        // Nulllinie hervorheben (wichtig für Drawdown)
        plot.setRangeZeroBaselineVisible(true);
        plot.setRangeZeroBaselinePaint(Color.BLACK);
        plot.setRangeZeroBaselineStroke(new BasicStroke(2.0f));
        
        // Achsen-Labels (KORRIGIERT: Total Value Labels)
        plot.getRangeAxis().setLabel("Total Value Drawdown (%) - EQUITY+FLOATING");
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
        
        // 1. Drawdown-Chart aktualisieren (KORRIGIERT mit Total Value Drawdown)
        updateDrawdownChartWithData(filteredTicks, timeScale);
        
        // 2. Profit-Chart über Manager aktualisieren
        if (profitChartManager != null) {
            profitChartManager.updateProfitChartWithData(filteredTicks, timeScale);
        }
        
        LOGGER.info("=== BEIDE CHARTS UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN ===");
    }
    
    /**
     * KOMPLETT KORRIGIERT: Aktualisiert den Drawdown-Chart mit TOTAL VALUE DRAWDOWN
     * Implementiert Peak-Tracking für historisch korrekte Drawdown-Berechnung
     * BUGFIX: Verwendet jetzt Total Value statt Equity für korrekte Drawdown-Anzeige
     */
    public void updateDrawdownChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        LOGGER.info("=== TOTAL VALUE DRAWDOWN CHART UPDATE GESTARTET ===");
        
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("WARNUNG: Keine gefilterten Ticks für Total Value Drawdown-Chart!");
            return;
        }
        
        if (drawdownChart == null) {
            LOGGER.severe("KRITISCHER FEHLER: drawdownChart ist NULL!");
            return;
        }
        
        try {
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size());
            
            // Chart Serie leeren
            LOGGER.info("Leere Total Value Drawdown-Chart-Serie...");
            clearDrawdownSeries();
            
            // SCHRITT 1: Peak-Tracking und Total Value Drawdown-Berechnung
            LOGGER.info("=== BEGINNE PEAK-TRACKING UND TOTAL VALUE DRAWDOWN-BERECHNUNG ===");
            
            double peakTotalValue = 0.0;  // KORRIGIERT: Peak Total Value statt Peak Equity
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // KORRIGIERT: Verwende Total Value statt Equity
                    double currentTotalValue = tick.getTotalValue();
                    
                    // SCHRITT 2: Peak-Total-Value verfolgen
                    if (i == 0) {
                        // Erster Datenpunkt: Peak initialisieren
                        peakTotalValue = currentTotalValue;
                        LOGGER.info("PEAK TOTAL VALUE INITIALISIERT mit erstem Wert: " + String.format("%.6f", peakTotalValue));
                    } else if (currentTotalValue > peakTotalValue) {
                        // Neuer Peak erreicht
                        double oldPeak = peakTotalValue;
                        peakTotalValue = currentTotalValue;
                        LOGGER.fine("NEUER TOTAL VALUE PEAK ERREICHT: " + String.format("%.6f -> %.6f", oldPeak, peakTotalValue));
                    }
                    
                    // SCHRITT 3: Total Value Drawdown berechnen
                    double totalValueDrawdownPercent = calculateTotalValueDrawdownPercent(currentTotalValue, peakTotalValue);
                    
                    // SCHRITT 4: Zeitstempel konvertieren und zu Serie hinzufügen
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    drawdownPercentSeries.add(second, totalValueDrawdownPercent);
                    
                    // DETAILLIERTES LOGGING für jeden Datenpunkt
                    if (i < 5 || i >= filteredTicks.size() - 5) { // Erste und letzte 5
                        LOGGER.info("TOTAL VALUE DRAWDOWN TICK #" + (i+1) + " HINZUGEFÜGT: Zeit=" + tick.getTimestamp() + 
                                   ", Current Total Value=" + String.format("%.6f", currentTotalValue) + 
                                   ", Peak Total Value=" + String.format("%.6f", peakTotalValue) + 
                                   ", Total Value Drawdown=" + String.format("%.6f%%", totalValueDrawdownPercent));
                    }
                    
                    addedCount++;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "FEHLER beim Hinzufügen von Total Value Drawdown-Tick #" + (i+1) + ": " + tick, e);
                }
            }
            
            LOGGER.info("HINZUGEFÜGTE TOTAL VALUE DRAWDOWN-DATENPUNKTE: " + addedCount + " von " + filteredTicks.size());
            LOGGER.info("FINALER PEAK-TOTAL-VALUE: " + String.format("%.6f", peakTotalValue));
            
            // Serie-Status überprüfen
            checkDrawdownSeriesStatus();
            
            // Chart-Titel aktualisieren
            updateTotalValueDrawdownChartTitle(timeScale, filteredTicks.size());
            
            // Renderer für Drawdown-Chart anpassen basierend auf Datenpunkt-Anzahl
            adjustDrawdownChartRenderer(filteredTicks.size());
            
            // Y-Achsen-Bereich anpassen für Total Value Drawdown
            adjustTotalValueDrawdownChartYAxisRange(filteredTicks);
            
            // X-Achsen-Bereich anpassen
            adjustDrawdownChartXAxisRange(filteredTicks);
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors(filteredTicks);
            
            LOGGER.info("=== TOTAL VALUE DRAWDOWN CHART UPDATE ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im Total Value Drawdown Chart Update für Signal " + signalId, e);
        }
    }
    
    /**
     * KORRIGIERT: Berechnet den TOTAL VALUE Drawdown in Prozent
     * Für Fälle mit konstanter Equity aber schwankendem Floating Profit
     * 
     * @param currentTotalValue Der aktuelle Total Value (Equity + Floating)
     * @param peakTotalValue Der historische Peak-Total-Value
     * @return Der Total Value Drawdown in Prozent
     */
    private double calculateTotalValueDrawdownPercent(double currentTotalValue, double peakTotalValue) {
        if (peakTotalValue == 0) {
            LOGGER.warning("WARNUNG: Peak Total Value ist 0 - kann Total Value Drawdown nicht berechnen");
            return 0.0;
        }
        
        if (Double.isNaN(currentTotalValue) || Double.isInfinite(currentTotalValue) ||
            Double.isNaN(peakTotalValue) || Double.isInfinite(peakTotalValue)) {
            LOGGER.warning("WARNUNG: Ungültige Total Value-Werte - Current: " + currentTotalValue + ", Peak: " + peakTotalValue);
            return 0.0;
        }
        
        // TOTAL VALUE DRAWDOWN: (Current Total - Peak Total) / Peak Total * 100
        double result = ((currentTotalValue - peakTotalValue) / peakTotalValue) * 100.0;
        
        // DETAILLIERTES LOGGING für Total Value Drawdown
        LOGGER.fine("TOTAL VALUE DRAWDOWN BERECHNUNG: (" + String.format("%.6f", currentTotalValue) + " - " + 
                   String.format("%.6f", peakTotalValue) + ") / " + String.format("%.6f", peakTotalValue) + 
                   " * 100 = " + String.format("%.6f%%", result));
        
        return result;
    }
    
    /**
     * @deprecated Diese Methode war für Equity Drawdown - wird durch calculateTotalValueDrawdownPercent ersetzt.
     */
    @Deprecated
    private double calculateTrueDrawdownPercent(double currentEquity, double peakEquity) {
        LOGGER.warning("DEPRECATED: calculateTrueDrawdownPercent() für Equity Drawdown wurde durch calculateTotalValueDrawdownPercent() ersetzt");
        
        if (peakEquity == 0) {
            LOGGER.warning("WARNUNG: Peak Equity ist 0 - kann echten Drawdown nicht berechnen");
            return 0.0;
        }
        
        if (Double.isNaN(currentEquity) || Double.isInfinite(currentEquity) ||
            Double.isNaN(peakEquity) || Double.isInfinite(peakEquity)) {
            LOGGER.warning("WARNUNG: Ungültige Equity-Werte - Current: " + currentEquity + ", Peak: " + peakEquity);
            return 0.0;
        }
        
        // ECHTER DRAWDOWN: (Current Equity - Peak Equity) / Peak Equity * 100
        double result = ((currentEquity - peakEquity) / peakEquity) * 100.0;
        
        // DETAILLIERTES LOGGING für echten Drawdown
        LOGGER.fine("DEPRECATED EQUITY DRAWDOWN BERECHNUNG: (" + String.format("%.6f", currentEquity) + " - " + 
                   String.format("%.6f", peakEquity) + ") / " + String.format("%.6f", peakEquity) + 
                   " * 100 = " + String.format("%.6f%%", result));
        
        return result;
    }
    
    /**
     * Überprüft den Status der Drawdown-Serie
     */
    private void checkDrawdownSeriesStatus() {
        LOGGER.info("=== TOTAL VALUE DRAWDOWN-SERIES-STATUS CHECK ===");
        LOGGER.info("drawdownPercentSeries: " + (drawdownPercentSeries != null ? drawdownPercentSeries.getItemCount() + " Items" : "NULL"));
        
        // Detaillierte Drawdown-Serie Analyse
        if (drawdownPercentSeries != null && drawdownPercentSeries.getItemCount() > 0) {
            LOGGER.info("=== TOTAL VALUE DRAWDOWN SERIE DETAILS ===");
            for (int i = 0; i < Math.min(3, drawdownPercentSeries.getItemCount()); i++) {
                org.jfree.data.time.RegularTimePeriod period = drawdownPercentSeries.getTimePeriod(i);
                Number value = drawdownPercentSeries.getValue(i);
                LOGGER.info("Total Value Drawdown Item #" + (i+1) + ": Zeit=" + period + ", Wert=" + value + "%");
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
        LOGGER.info("Total Value Drawdown-Chart-Serie geleert");
    }
    
    /**
     * Aktualisiert den Total Value Drawdown-Chart-Titel
     */
    private void updateTotalValueDrawdownChartTitle(TimeScale timeScale, int tickCount) {
        if (drawdownChart != null) {
            drawdownChart.setTitle("Total Value Drawdown (%) - EQUITY+FLOATING - " + signalId + " (" + providerName + ") - " + 
                                  (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " [DIAGNOSE #" + updateCounter + "]");
        }
        
        LOGGER.info("Total Value Drawdown-Chart-Titel aktualisiert mit " + tickCount + " Ticks");
    }
    
    /**
     * @deprecated Diese Methode war für Equity Drawdown - wird durch updateTotalValueDrawdownChartTitle ersetzt.
     */
    @Deprecated
    private void updateDrawdownChartTitle(TimeScale timeScale, int tickCount) {
        LOGGER.warning("DEPRECATED: updateDrawdownChartTitle() wurde durch updateTotalValueDrawdownChartTitle() ersetzt");
        updateTotalValueDrawdownChartTitle(timeScale, tickCount);
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
            LOGGER.info("Total Value Drawdown-Chart: Ein-Punkt-Modus aktiviert (nur große Shapes)");
            
        } else if (tickCount <= 5) {
            // Bei wenigen Datenpunkten: Linien und große Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Große Punkte
            LOGGER.info("Total Value Drawdown-Chart: Wenig-Punkte-Modus aktiviert (Linien + große Shapes)");
            
        } else {
            // Bei vielen Datenpunkten: Linien und kleine Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-2, -2, 4, 4)); // Normale Punkte
            LOGGER.info("Total Value Drawdown-Chart: Viel-Punkte-Modus aktiviert (Linien + kleine Shapes)");
        }
    }
    
    /**
     * KORRIGIERT: Y-Achsen-Bereich-Anpassung für TOTAL VALUE Drawdown-Chart
     * OHNE hardcodierte Schwellenwerte - funktioniert für alle Drawdown-Bereiche
     */
    private void adjustTotalValueDrawdownChartYAxisRange(List<TickDataLoader.TickData> tickList) {
        if (drawdownChart == null || tickList.isEmpty()) {
            LOGGER.warning("Kann Total Value Drawdown Y-Achse nicht anpassen: Chart=" + (drawdownChart != null) + 
                          ", Ticks=" + (tickList != null ? tickList.size() : "null"));
            return;
        }
        
        LOGGER.info("=== TOTAL VALUE DRAWDOWN Y-ACHSEN ANPASSUNG START ===");
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // SCHRITT 1: Sortiere Ticks chronologisch für korrekte Peak-Berechnung
        List<TickDataLoader.TickData> sortedTicks = new java.util.ArrayList<>(tickList);
        sortedTicks.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // SCHRITT 2: Alle Total Value Drawdown-Werte berechnen (mit korrektem Peak-Tracking)
        double peakTotalValue = 0.0;
        double minDrawdown = Double.MAX_VALUE;
        double maxDrawdown = Double.MIN_VALUE;
        
        for (int i = 0; i < sortedTicks.size(); i++) {
            TickDataLoader.TickData tick = sortedTicks.get(i);
            double currentTotalValue = tick.getTotalValue();  // KORRIGIERT: Total Value statt Equity
            
            // Peak-Tracking für Total Value
            if (i == 0) {
                peakTotalValue = currentTotalValue;
            } else if (currentTotalValue > peakTotalValue) {
                peakTotalValue = currentTotalValue;
            }
            
            // Total Value Drawdown berechnen
            double drawdown = calculateTotalValueDrawdownPercent(currentTotalValue, peakTotalValue);
            
            if (drawdown < minDrawdown) minDrawdown = drawdown;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            
            if (i < 3) { // Nur erste 3 loggen
                LOGGER.info("Total Value Drawdown-Wert #" + (i+1) + ": " + String.format("%.6f%%", drawdown) + 
                           " (Total Value: " + String.format("%.6f", currentTotalValue) + ", Peak: " + String.format("%.6f", peakTotalValue) + ")");
            }
        }
        
        LOGGER.info("TOTAL VALUE DRAWDOWN-BEREICH: Min=" + String.format("%.6f%%", minDrawdown) + 
                   ", Max=" + String.format("%.6f%%", maxDrawdown));
        
        // SCHRITT 3: Fallback für ungültige Werte
        if (minDrawdown == Double.MAX_VALUE || maxDrawdown == Double.MIN_VALUE) {
            LOGGER.severe("FEHLER: Keine gültigen Total Value Drawdown-Werte - setze Notfall-Bereich");
            plot.getRangeAxis().setRange(-2.0, 1.0);
            return;
        }
        
        // SCHRITT 4: ALLGEMEINE BEREICHSBERECHNUNG (ohne hardcodierte Schwellenwerte)
        double dataRange = maxDrawdown - minDrawdown;
        double dataCenter = (minDrawdown + maxDrawdown) / 2.0;
        
        LOGGER.info("TOTAL VALUE DRAWDOWN-ANALYSE: Spanne=" + String.format("%.6f%%", dataRange) + 
                   ", Zentrum=" + String.format("%.6f%%", dataCenter));
        
        // SCHRITT 5: EINFACHE, ALLGEMEINE PADDING-BERECHNUNG
        double basePadding, lowerBound, upperBound;
        
        if (dataRange < 0.001) {
            // Alle Werte praktisch identisch - erstelle sinnvollen Bereich um den Zentral-Wert
            basePadding = Math.max(Math.abs(dataCenter) * 0.2, 0.1); // 20% des Wertes oder min. 0.1%
            lowerBound = dataCenter - basePadding;
            upperBound = dataCenter + basePadding;
            LOGGER.info("Identische Werte - Symmetrischer Bereich um " + String.format("%.6f%%", dataCenter));
            
        } else {
            // Normale Daten - ALLGEMEINE FORMEL ohne If-Abfragen
            
            // Basis-Padding: 50% der Datenspanne (großzügig für bessere Sichtbarkeit)
            basePadding = dataRange * 0.5;
            
            // Mindest-Padding basierend auf Absolutwerten (garantiert Sichtbarkeit)
            double maxAbsValue = Math.max(Math.abs(minDrawdown), Math.abs(maxDrawdown));
            double minPadding = Math.max(maxAbsValue * 0.1, 0.05); // 10% des größten Wertes oder min. 0.05%
            
            // Verwende das größere Padding
            double finalPadding = Math.max(basePadding, minPadding);
            
            lowerBound = minDrawdown - finalPadding;
            upperBound = maxDrawdown + finalPadding;
            
            LOGGER.info("Allgemeine Berechnung - Basis-Padding: " + String.format("%.6f%%", basePadding) + 
                       ", Min-Padding: " + String.format("%.6f%%", minPadding) + 
                       ", Final-Padding: " + String.format("%.6f%%", finalPadding));
        }
        
        // SCHRITT 6: Stelle sicher, dass 0% Linie sichtbar ist (wichtige Referenz für Drawdown)
        if (upperBound < 0) {
            // Alle Werte negativ - erweitere etwas nach oben
            upperBound = Math.max(0, upperBound + Math.abs(lowerBound) * 0.1);
        }
        if (lowerBound > 0) {
            // Alle Werte positiv - erweitere etwas nach unten
            lowerBound = Math.min(0, lowerBound - upperBound * 0.1);
        }
        
        // SCHRITT 7: Garantierter Mindestbereich für jede Situation
        double finalRange = upperBound - lowerBound;
        double minRequiredRange = 0.2; // Mindestens 0.2% Gesamtspanne
        
        if (finalRange < minRequiredRange) {
            double center = (upperBound + lowerBound) / 2;
            double halfRange = minRequiredRange / 2;
            lowerBound = center - halfRange;
            upperBound = center + halfRange;
            LOGGER.info("Mindestbereich angewendet: " + minRequiredRange + "%");
        }
        
        // SCHRITT 8: Y-Achsen-Bereich setzen
        plot.getRangeAxis().setRange(lowerBound, upperBound);
        
        LOGGER.info("=== TOTAL VALUE DRAWDOWN Y-ACHSEN-BEREICH GESETZT ===");
        LOGGER.info("Bereich: " + String.format("%.6f%% bis %.6f%%", lowerBound, upperBound));
        LOGGER.info("Finale Spanne: " + String.format("%.6f%%", upperBound - lowerBound));
        LOGGER.info("=== TOTAL VALUE DRAWDOWN Y-ACHSEN ANPASSUNG ENDE ===");
    }
    
    /**
     * @deprecated Diese Methode war für Equity Drawdown - wird durch adjustTotalValueDrawdownChartYAxisRange ersetzt.
     */
    @Deprecated
    private void adjustTrueDrawdownChartYAxisRange(List<TickDataLoader.TickData> tickList) {
        LOGGER.warning("DEPRECATED: adjustTrueDrawdownChartYAxisRange() wurde durch adjustTotalValueDrawdownChartYAxisRange() ersetzt");
        adjustTotalValueDrawdownChartYAxisRange(tickList);
    }
    
    /**
     * KORRIGIERT: INTELLIGENTE X-Achsen (Zeit) Kalibrierung für Drawdown-Chart
     * Verhindert zu enge Zeitbereiche bei wenigen Datenpunkten
     */
    private void adjustDrawdownChartXAxisRange(List<TickDataLoader.TickData> tickList) {
        if (drawdownChart == null || tickList.isEmpty()) {
            LOGGER.warning("Kann Drawdown X-Achse nicht anpassen: Chart=" + (drawdownChart != null) + 
                          ", Ticks=" + (tickList != null ? tickList.size() : "null"));
            return;
        }
        
        LOGGER.info("=== INTELLIGENTE DRAWDOWN X-ACHSEN KALIBRIERUNG START ===");
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // Sortiere für korrekte chronologische Reihenfolge
        List<TickDataLoader.TickData> sortedTicks = new java.util.ArrayList<>(tickList);
        sortedTicks.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        
        // Zeitbereich der Daten ermitteln
        java.time.LocalDateTime earliestTime = sortedTicks.get(0).getTimestamp();
        java.time.LocalDateTime latestTime = sortedTicks.get(sortedTicks.size() - 1).getTimestamp();
        
        LOGGER.info("ZEIT-DATEN:");
        LOGGER.info("  Anzahl Datenpunkte: " + sortedTicks.size());
        LOGGER.info("  Früheste Zeit: " + earliestTime);
        LOGGER.info("  Späteste Zeit: " + latestTime);
        
        // Zeitspanne in Millisekunden
        long timeSpanMillis = java.time.Duration.between(earliestTime, latestTime).toMillis();
        LOGGER.info("  Zeitspanne: " + timeSpanMillis + " ms");
        
        // Domain-Achse manuell kalibrieren
        plot.getDomainAxis().setAutoRange(false);
        
        java.time.LocalDateTime displayStart, displayEnd;
        
        if (sortedTicks.size() == 1) {
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
     * Aktualisiert die Farben des Drawdown-Charts für Total Value Drawdown
     */
    private void updateDrawdownChartColors(List<TickDataLoader.TickData> tickList) {
        if (drawdownChart == null || tickList.isEmpty()) {
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        // Für Total Value Drawdown ist die Farbe rot (Drawdown-Werte)
        renderer.setSeriesPaint(0, new Color(200, 0, 0)); // Rot für Drawdown
        LOGGER.info("Total Value Drawdown-Chart Farbe: Rot (für Drawdown-Werte)");
    }
    
    /**
     * @deprecated Diese Methode verwendet die alte Floating Profit % Berechnung.
     * Wird jetzt durch calculateTotalValueDrawdownPercent ersetzt.
     */
    @Deprecated
    private double calculateDrawdownPercent(double equity, double floatingProfit) {
        LOGGER.warning("DEPRECATED: calculateDrawdownPercent() verwendet alte Floating Profit % Berechnung. Verwende calculateTotalValueDrawdownPercent()");
        
        if (equity == 0) {
            LOGGER.warning("WARNUNG: Equity ist 0 - kann Floating Profit % nicht berechnen");
            return 0.0;
        }
        
        double result = (floatingProfit / equity) * 100.0;
        
        // DETAILLIERTES LOGGING - DEPRECATED
        LOGGER.fine("DEPRECATED FLOATING PROFIT % BERECHNUNG: " + floatingProfit + " / " + equity + " * 100 = " + String.format("%.6f%%", result));
        
        return result;
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