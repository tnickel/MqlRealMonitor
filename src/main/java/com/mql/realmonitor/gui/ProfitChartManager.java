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
 * NEU: Verwaltet den Profit-Chart mit Kontostand (grün) und Gesamtwert (gelb)
 * Zeigt die Entwicklung des Profits und der Open Equity über die Zeit
 */
public class ProfitChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(ProfitChartManager.class.getName());
    
    // Chart Komponenten
    private JFreeChart profitChart;
    
    // TimeSeries für Profit-Chart
    private TimeSeries equitySeries;      // Kontostand (grün)
    private TimeSeries totalValueSeries;  // Gesamtwert (gelb)
    
    private final String signalId;
    private final String providerName;
    
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
     * Erstellt den Profit-Chart
     */
    private void createProfitChart() {
        // TimeSeries für Kontostand (Equity)
        equitySeries = new TimeSeries("Kontostand");
        
        // TimeSeries für Gesamtwert (Equity + Floating Profit)
        totalValueSeries = new TimeSeries("Gesamtwert (inkl. Open Equity)");
        
        // TimeSeriesCollection für Profit-Chart
        TimeSeriesCollection profitDataset = new TimeSeriesCollection();
        profitDataset.addSeries(equitySeries);
        profitDataset.addSeries(totalValueSeries);
        
        // Profit-Chart erstellen
        profitChart = ChartFactory.createTimeSeriesChart(
            "Profit-Entwicklung - " + signalId + " (" + providerName + ") - DIAGNOSEMODUS",
            "Zeit",
            "Wert (€/$/etc.)",
            profitDataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Profit-Chart konfigurieren
        configureProfitChart();
        
        LOGGER.info("=== PROFIT-CHART ERSTELLT für Signal: " + signalId + " ===");
    }
    
    /**
     * Konfiguriert den Profit-Chart
     */
    private void configureProfitChart() {
        XYPlot plot = profitChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);  // Equity-Linie
        renderer.setSeriesShapesVisible(0, true); // Equity-Punkte
        renderer.setSeriesLinesVisible(1, true);  // Gesamtwert-Linie
        renderer.setSeriesShapesVisible(1, true); // Gesamtwert-Punkte
        
        // Shape-Größe für bessere Sichtbarkeit
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Equity
        renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Gesamtwert (etwas größer)
        
        // Farben setzen
        renderer.setSeriesPaint(0, new Color(0, 150, 0));   // Grün für Kontostand
        renderer.setSeriesPaint(1, new Color(255, 200, 0)); // Gelb für Gesamtwert
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // Kontostand-Linie
        renderer.setSeriesStroke(1, new BasicStroke(3.0f)); // Gesamtwert-Linie (dicker)
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        profitChart.setBackgroundPaint(new Color(250, 250, 250));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Wert - DIAGNOSEMODUS");
        plot.getDomainAxis().setLabel("Zeit");
        
        // Y-Achse manuell konfigurieren für intelligente Skalierung
        plot.getRangeAxis().setAutoRange(false);  // Deaktiviere Auto-Range für manuelle Kontrolle
        plot.getRangeAxis().setLowerMargin(0.02); // 2% Margin
        plot.getRangeAxis().setUpperMargin(0.02); // 2% Margin
    }
    
    /**
     * Aktualisiert den Profit-Chart mit gefilterten Daten
     */
    public void updateProfitChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        updateCounter++;
        
        LOGGER.info("=== PROFIT CHART UPDATE #" + updateCounter + " GESTARTET für Signal: " + signalId + " ===");
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
            
            // Beispiel-Daten loggen für Diagnostik
            if (filteredTicks.size() > 0) {
                TickDataLoader.TickData firstTick = filteredTicks.get(0);
                TickDataLoader.TickData lastTick = filteredTicks.get(filteredTicks.size() - 1);
                LOGGER.info("ERSTER TICK: " + formatTickForLog(firstTick));
                LOGGER.info("LETZTER TICK: " + formatTickForLog(lastTick));
            }
            
            // Chart Serien leeren
            LOGGER.info("Leere Profit-Chart-Serien...");
            clearProfitSeries();
            
            // SCHRITT-FÜR-SCHRITT: Gefilterte Tick-Daten zu beiden Serien hinzufügen
            LOGGER.info("=== BEGINNE PROFIT-DATEN-HINZUFÜGUNG ===");
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // Zeitstempel konvertieren
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    
                    // Kontostand (Equity) hinzufügen - GRÜN
                    double equity = tick.getEquity();
                    equitySeries.add(second, equity);
                    
                    // Gesamtwert (Equity + Floating Profit) hinzufügen - GELB
                    double totalValue = tick.getTotalValue();
                    totalValueSeries.add(second, totalValue);
                    
                    // DETAILLIERTES LOGGING für jeden Datenpunkt
                    if (i < 3 || i >= filteredTicks.size() - 3) { // Erste und letzte 3
                        LOGGER.info("PROFIT TICK #" + (i+1) + " HINZUGEFÜGT: Zeit=" + tick.getTimestamp() + 
                                   ", Equity=" + String.format("%.2f", equity) + 
                                   ", Floating=" + String.format("%.2f", tick.getFloatingProfit()) + 
                                   ", Total=" + String.format("%.2f", totalValue));
                    }
                    
                    addedCount++;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "FEHLER beim Hinzufügen von Profit-Tick #" + (i+1) + ": " + tick, e);
                }
            }
            
            LOGGER.info("HINZUGEFÜGTE PROFIT-DATENPUNKTE: " + addedCount + " von " + filteredTicks.size());
            
            // Serie-Status überprüfen
            checkProfitSeriesStatus();
            
            // Chart-Titel aktualisieren
            updateChartTitle(timeScale, filteredTicks.size());
            
            // Renderer für Profit-Chart anpassen basierend auf Datenpunkt-Anzahl
            adjustProfitChartRenderer(filteredTicks.size());
            
            // Y-Achsen-Bereich für bessere Sichtbarkeit anpassen
            adjustProfitChartYAxisRangeRobust(filteredTicks);
            
            LOGGER.info("=== PROFIT CHART UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im Profit Chart Update #" + updateCounter + " für Signal " + signalId, e);
        }
    }
    
    /**
     * Formatiert einen Tick für das Logging
     */
    private String formatTickForLog(TickDataLoader.TickData tick) {
        if (tick == null) {
            return "NULL";
        }
        return "Zeit=" + tick.getTimestamp() + 
               ", Equity=" + String.format("%.2f", tick.getEquity()) + 
               ", Floating=" + String.format("%.2f", tick.getFloatingProfit()) + 
               ", Total=" + String.format("%.2f", tick.getTotalValue());
    }
    
    /**
     * Überprüft den Status der Profit-Serien
     */
    private void checkProfitSeriesStatus() {
        LOGGER.info("=== PROFIT-SERIES-STATUS CHECK ===");
        LOGGER.info("equitySeries: " + (equitySeries != null ? equitySeries.getItemCount() + " Items" : "NULL"));
        LOGGER.info("totalValueSeries: " + (totalValueSeries != null ? totalValueSeries.getItemCount() + " Items" : "NULL"));
        
        // Detaillierte Equity-Serie Analyse
        if (equitySeries != null && equitySeries.getItemCount() > 0) {
            LOGGER.info("=== EQUITY SERIE DETAILS ===");
            for (int i = 0; i < Math.min(3, equitySeries.getItemCount()); i++) {
                org.jfree.data.time.RegularTimePeriod period = equitySeries.getTimePeriod(i);
                Number value = equitySeries.getValue(i);
                LOGGER.info("Equity Item #" + (i+1) + ": Zeit=" + period + ", Wert=" + value);
            }
        }
        
        // Detaillierte Gesamtwert-Serie Analyse
        if (totalValueSeries != null && totalValueSeries.getItemCount() > 0) {
            LOGGER.info("=== GESAMTWERT SERIE DETAILS ===");
            for (int i = 0; i < Math.min(3, totalValueSeries.getItemCount()); i++) {
                org.jfree.data.time.RegularTimePeriod period = totalValueSeries.getTimePeriod(i);
                Number value = totalValueSeries.getValue(i);
                LOGGER.info("Gesamtwert Item #" + (i+1) + ": Zeit=" + period + ", Wert=" + value);
            }
        }
    }
    
    /**
     * Leert die Profit-Chart-Serien
     */
    private void clearProfitSeries() {
        if (equitySeries != null) equitySeries.clear();
        if (totalValueSeries != null) totalValueSeries.clear();
        LOGGER.info("Profit-Chart-Serien geleert");
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
    
    /**
     * Passt den Profit-Chart Renderer basierend auf Datenpunkt-Anzahl an
     */
    private void adjustProfitChartRenderer(int tickCount) {
        if (profitChart == null) {
            LOGGER.warning("profitChart ist NULL - kann Renderer nicht anpassen");
            return;
        }
        
        XYPlot plot = profitChart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        if (tickCount <= 1) {
            // Bei einem Datenpunkt: Nur große Shapes anzeigen, keine Linien
            renderer.setSeriesLinesVisible(0, false);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesLinesVisible(1, false);
            renderer.setSeriesShapesVisible(1, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-8, -8, 16, 16)); // Sehr große Punkte
            renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-10, -10, 20, 20)); // Noch größer für Gesamtwert
            LOGGER.info("Profit-Chart: Ein-Punkt-Modus aktiviert (nur große Shapes)");
            
        } else if (tickCount <= 5) {
            // Bei wenigen Datenpunkten: Linien und große Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesLinesVisible(1, true);
            renderer.setSeriesShapesVisible(1, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-5, -5, 10, 10)); // Große Punkte
            renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-6, -6, 12, 12)); // Etwas größer
            LOGGER.info("Profit-Chart: Wenig-Punkte-Modus aktiviert (Linien + große Shapes)");
            
        } else {
            // Bei vielen Datenpunkten: Linien und normale Shapes anzeigen
            renderer.setSeriesLinesVisible(0, true);
            renderer.setSeriesShapesVisible(0, true);
            renderer.setSeriesLinesVisible(1, true);
            renderer.setSeriesShapesVisible(1, true);
            renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Normale Punkte
            renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Etwas größer
            LOGGER.info("Profit-Chart: Viel-Punkte-Modus aktiviert (Linien + normale Shapes)");
        }
    }
    
    /**
     * ALLGEMEINE DYNAMISCHE Y-Achsen-Bereich-Anpassung für Profit-Chart
     * Funktioniert für alle Signalprovider unabhängig von der Wertegröße
     */
    private void adjustProfitChartYAxisRangeRobust(List<TickDataLoader.TickData> filteredTicks) {
        if (profitChart == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Kann Profit Y-Achse nicht anpassen: Chart=" + (profitChart != null) + 
                          ", Ticks=" + (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        LOGGER.info("=== DYNAMISCHE PROFIT Y-ACHSEN KALIBRIERUNG START ===");
        
        XYPlot plot = profitChart.getXYPlot();
        
        // SCHRITT 1: Alle relevanten Werte sammeln
        double[] allValues = new double[filteredTicks.size() * 2]; // Equity + Total für jeden Tick
        int index = 0;
        
        double minEquity = Double.MAX_VALUE;
        double maxEquity = Double.MIN_VALUE;
        double minTotal = Double.MAX_VALUE;
        double maxTotal = Double.MIN_VALUE;
        
        for (TickDataLoader.TickData tick : filteredTicks) {
            double equity = tick.getEquity();
            double total = tick.getTotalValue();
            
            allValues[index++] = equity;
            allValues[index++] = total;
            
            if (equity < minEquity) minEquity = equity;
            if (equity > maxEquity) maxEquity = equity;
            if (total < minTotal) minTotal = total;
            if (total > maxTotal) maxTotal = total;
        }
        
        // SCHRITT 2: Globale Min/Max-Werte ermitteln
        double globalMin = Math.min(minEquity, minTotal);
        double globalMax = Math.max(maxEquity, maxTotal);
        double dataRange = globalMax - globalMin;
        
        LOGGER.info("DATEN-ANALYSE:");
        LOGGER.info("  Equity-Bereich: " + String.format("%.6f bis %.6f", minEquity, maxEquity));
        LOGGER.info("  Total-Bereich: " + String.format("%.6f bis %.6f", minTotal, maxTotal));
        LOGGER.info("  Globaler Bereich: " + String.format("%.6f bis %.6f", globalMin, globalMax));
        LOGGER.info("  Daten-Spanne: " + String.format("%.6f", dataRange));
        
        // SCHRITT 3: Dynamisches Padding berechnen
        double lowerBound, upperBound;
        
        if (dataRange == 0) {
            // Alle Werte identisch - erstelle minimalen sinnvollen Bereich
            double centerValue = globalMin;
            double minimalRange = Math.abs(centerValue) * 0.001; // 0.1% des Wertes
            if (minimalRange < 0.01) minimalRange = 0.01; // Mindestens 1 Cent
            
            lowerBound = centerValue - minimalRange;
            upperBound = centerValue + minimalRange;
            
            LOGGER.info("IDENTISCHE WERTE - Minimal-Bereich um " + String.format("%.6f", centerValue));
            
        } else {
            // DYNAMISCHE PADDING-BERECHNUNG basierend auf der Daten-Spanne
            
            // Basis-Padding: 15% der Daten-Spanne (visueller Puffer)
            double basePadding = dataRange * 0.15;
            
            // Zusätzliches relatives Padding basierend auf der Wertegröße
            double avgValue = (globalMin + globalMax) / 2.0;
            double relativePadding = Math.abs(avgValue) * 0.005; // 0.5% des Durchschnittswertes
            
            // Wähle das größere Padding für bessere Sichtbarkeit
            double finalPadding = Math.max(basePadding, relativePadding);
            
            // Mindest-Padding für sehr kleine Bereiche
            double minPadding = dataRange * 0.05; // Mindestens 5% der Spanne
            if (finalPadding < minPadding) {
                finalPadding = minPadding;
            }
            
            lowerBound = globalMin - finalPadding;
            upperBound = globalMax + finalPadding;
            
            LOGGER.info("PADDING-BERECHNUNG:");
            LOGGER.info("  Basis-Padding (15%): " + String.format("%.6f", basePadding));
            LOGGER.info("  Relatives Padding (0.5%): " + String.format("%.6f", relativePadding));
            LOGGER.info("  Finales Padding: " + String.format("%.6f", finalPadding));
        }
        
        // SCHRITT 4: Qualitätsprüfung des berechneten Bereichs
        double finalRange = upperBound - lowerBound;
        double rangeQuality = finalRange / Math.abs((globalMin + globalMax) / 2.0);
        
        LOGGER.info("BEREICHS-QUALITÄT:");
        LOGGER.info("  Finale Spanne: " + String.format("%.6f", finalRange));
        LOGGER.info("  Qualitäts-Verhältnis: " + String.format("%.6f", rangeQuality));
        
        // Bei extrem kleinen Bereichen: Erweitere den Bereich
        if (rangeQuality < 0.001) { // Weniger als 0.1% des Durchschnittswertes
            double centerValue = (lowerBound + upperBound) / 2.0;
            double enhancedRange = Math.abs(centerValue) * 0.01; // 1% des Zentralwertes
            if (enhancedRange < 0.1) enhancedRange = 0.1; // Mindestens 10 Cent
            
            lowerBound = centerValue - enhancedRange / 2.0;
            upperBound = centerValue + enhancedRange / 2.0;
            
            LOGGER.info("BEREICH ERWEITERT für bessere Sichtbarkeit: " + 
                       String.format("%.6f bis %.6f", lowerBound, upperBound));
        }
        
        // SCHRITT 5: Y-Achsen-Bereich setzen
        plot.getRangeAxis().setRange(lowerBound, upperBound);
        plot.getDomainAxis().setAutoRange(true);
        
        LOGGER.info("=== Y-ACHSEN-BEREICH FINAL GESETZT ===");
        LOGGER.info("Bereich: " + String.format("%.6f bis %.6f", lowerBound, upperBound));
        LOGGER.info("Sichtbare Spanne: " + String.format("%.6f", upperBound - lowerBound));
        LOGGER.info("Verhältnis Spanne/Daten: " + String.format("%.2f%%", 
                   ((upperBound - lowerBound) / dataRange - 1) * 100));
        LOGGER.info("=== DYNAMISCHE PROFIT Y-ACHSEN KALIBRIERUNG ENDE ===");
    }
    
    /**
     * INTELLIGENTE X-Achsen (Zeit) Kalibrierung für Profit-Chart
     * Verhindert zu enge Zeitbereiche bei wenigen Datenpunkten
     */
    private void adjustProfitChartXAxisRange(List<TickDataLoader.TickData> filteredTicks) {
        if (profitChart == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Kann Profit X-Achse nicht anpassen: Chart=" + (profitChart != null) + 
                          ", Ticks=" + (filteredTicks != null ? filteredTicks.size() : "null"));
            return;
        }
        
        LOGGER.info("=== INTELLIGENTE PROFIT X-ACHSEN KALIBRIERUNG START ===");
        
        XYPlot plot = profitChart.getXYPlot();
        
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
        
        // LocalDateTime zu Date konvertieren für JFreeChart
        java.util.Date startDate = java.util.Date.from(displayStart.atZone(java.time.ZoneId.systemDefault()).toInstant());
        java.util.Date endDate = java.util.Date.from(displayEnd.atZone(java.time.ZoneId.systemDefault()).toInstant());
        
        // Domain-Bereich setzen
        plot.getDomainAxis().setRange(startDate, endDate);
        
        LOGGER.info("=== X-ACHSEN-BEREICH GESETZT ===");
        LOGGER.info("Anzeige von: " + displayStart);
        LOGGER.info("Anzeige bis: " + displayEnd);
        LOGGER.info("Sichtbare Zeitspanne: " + java.time.Duration.between(displayStart, displayEnd).toMinutes() + " Minuten");
        LOGGER.info("=== INTELLIGENTE PROFIT X-ACHSEN KALIBRIERUNG ENDE ===");
    }
    
    // Getter-Methoden
    public JFreeChart getProfitChart() {
        return profitChart;
    }
    
    public TimeSeries getEquitySeries() {
        return equitySeries;
    }
    
    public TimeSeries getTotalValueSeries() {
        return totalValueSeries;
    }
}