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
 * KORRIGIERT: Profit-Chart-Manager für korrekte DIAGNOSE #2 Darstellung
 * BEHEBT: Y-Achse Auto-Range und normale X-Achse wie in Original DIAGNOSE #2
 * KORRIGIERT: Entfernt überkomplexe Achsen-Kalibrierung für normale Darstellung
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
    
    // KRITISCH: Aktuelle TimeScale für X-Achsen-Kalibrierung
    private TimeScale currentTimeScale = TimeScale.M15;  // Default
    
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
     * KORRIGIERT: Erstellt Chart mit Performance UND korrekter DIAGNOSE #2 Darstellung
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
            "Profit - DIAGNOSEMODUS",
            profitDataset,
            true,  // Legend
            true,  // Tooltips - REAKTIVIERT für bessere UX
            false  // URLs
        );
        
        // KORRIGIERT: Chart-Konfiguration mit DIAGNOSE #2 Darstellung
        configureProfitChartForDiagnose2();
        
        LOGGER.info("=== KORRIGIERTER PROFIT-CHART ERSTELLT für Signal: " + signalId + " (DIAGNOSE #2 Style) ===");
    }
    
    /**
     * KORRIGIERT: Konfiguriert Chart für DIAGNOSE #2 Darstellung (wie im Original Bild)
     */
    private void configureProfitChartForDiagnose2() {
        XYPlot plot = profitChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // KORRIGIERT: Shapes REAKTIVIERT für korrekte DIAGNOSE #2 Darstellung 
        renderer.setSeriesLinesVisible(0, true);  // Profit-Linie
        renderer.setSeriesShapesVisible(0, true); // Profit-Punkte REAKTIVIERT
        renderer.setSeriesLinesVisible(1, true);  // Profit+Open-Linie
        renderer.setSeriesShapesVisible(1, true); // Profit+Open-Punkte REAKTIVIERT
        
        // KORREKTE Shape-Größe für bessere Sichtbarkeit (wie DIAGNOSE #2)
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Profit
        renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Profit+Open (etwas größer)
        
        // KORREKTE Farben (wie DIAGNOSE #2)
        renderer.setSeriesPaint(0, new Color(0, 150, 0));   // Grün für Profit
        renderer.setSeriesPaint(1, new Color(255, 200, 0)); // Gelb für Profit + Open Equity
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // KORRIGIERT: Ursprüngliche Stärke
        renderer.setSeriesStroke(1, new BasicStroke(3.0f)); // KORRIGIERT: Ursprüngliche Stärke
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben (wie DIAGNOSE #2)
        profitChart.setBackgroundPaint(new Color(250, 250, 250));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels (wie DIAGNOSE #2)
        plot.getRangeAxis().setLabel("Profit - DIAGNOSEMODUS");
        plot.getDomainAxis().setLabel("Zeit");
        
        // KRITISCH FIX: Y-Achse AUTO-RANGE aktivieren (wie DIAGNOSE #2)
        plot.getRangeAxis().setAutoRange(true);  // KORRIGIERT: War false, jetzt true!
        plot.getRangeAxis().setLowerMargin(0.02); // KORRIGIERT: Ursprüngliche Margins
        plot.getRangeAxis().setUpperMargin(0.02);
        
        // KRITISCH FIX: X-Achse AUTO-RANGE aktivieren (wie DIAGNOSE #2)
        plot.getDomainAxis().setAutoRange(true);  // KORRIGIERT: Automatische Zeitachse
    }
    
    /**
     * KORRIGIERT: Chart-Update mit Performance-Schutz UND einfacher DIAGNOSE #2 Darstellung
     */
    public void updateProfitChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        updateCounter++;
        
        LOGGER.info("=== KORRIGIERTER PROFIT CHART UPDATE #" + updateCounter + " GESTARTET für Signal: " + signalId + " (DIAGNOSE #2 Style) ===");
        LOGGER.info("Input-Daten: filteredTicks=" + (filteredTicks != null ? filteredTicks.size() : "NULL") + 
                   ", timeScale=" + (timeScale != null ? timeScale.getLabel() : "NULL"));
        
        if (filteredTicks == null) {
            LOGGER.severe("KRITISCHER FEHLER: filteredTicks ist NULL!");
            return;
        }
        
        if (filteredTicks.isEmpty()) {
            LOGGER.warning("WARNUNG: Keine gefilterten Ticks - setze leeren Chart für TimeScale: " + 
                          (timeScale != null ? timeScale.getLabel() : "NULL"));
            setEmptyChart(timeScale);
            return;
        }
        
        if (profitChart == null) {
            LOGGER.severe("KRITISCHER FEHLER: profitChart ist NULL!");
            return;
        }
        
        try {
            LOGGER.info("Anzahl gefilterte Ticks: " + filteredTicks.size() + " für TimeScale: " + 
                       (timeScale != null ? timeScale.getLabel() : "NULL"));
            
            // KRITISCH: Aktuelle TimeScale speichern für Titel
            if (timeScale != null) {
                this.currentTimeScale = timeScale;
                LOGGER.info("TimeScale aktualisiert auf: " + timeScale.getLabel());
            }
            
            // KRITISCH: Zero-Data-Pattern-Check nur für extreme Fälle (99% Nullwerte)
            if (isExtremeZeroDataPattern(filteredTicks)) {
                LOGGER.warning("EXTREME ZERO-DATA-PATTERN ERKANNT - verwende Fast-Rendering für TimeScale: " + 
                              (timeScale != null ? timeScale.getLabel() : "NULL"));
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
            
            // SCHRITT 2: KORREKTE Daten-Hinzufügung (wie DIAGNOSE #2)
            LOGGER.info("=== BEGINNE PROFIT-DATEN-HINZUFÜGUNG (DIAGNOSE #2 BERECHNUNG) ===");
            int addedCount = 0;
            
            for (int i = 0; i < filteredTicks.size(); i++) {
                TickDataLoader.TickData tick = filteredTicks.get(i);
                
                try {
                    // Zeitstempel konvertieren
                    Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                    Second second = new Second(javaDate);
                    
                    // KORREKTE Profit-Berechnung (wie DIAGNOSE #2)
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
            
            // Chart-Titel aktualisieren (DIAGNOSE #2 Style)
            updateChartTitleForDiagnose2(timeScale, filteredTicks.size());
            
            // KEINE komplexe Y-Achsen-Kalibrierung - verwende AUTO-RANGE (wie DIAGNOSE #2)
            LOGGER.info("Y-Achse: Verwende AUTO-RANGE (wie DIAGNOSE #2)");
            
            // KEINE komplexe X-Achsen-Kalibrierung - verwende AUTO-RANGE (wie DIAGNOSE #2)
            LOGGER.info("X-Achse: Verwende AUTO-RANGE (wie DIAGNOSE #2)");
            
            LOGGER.info("=== KORRIGIERTER PROFIT CHART UPDATE #" + updateCounter + " ERFOLGREICH ABGESCHLOSSEN (DIAGNOSE #2 Style) ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER im korrigierten Profit Chart Update #" + updateCounter + " für Signal " + signalId, e);
            setEmptyChart(timeScale);
        }
    }
    
    /**
     * KORRIGIERT: Erkenne nur EXTREME Zero-Data-Pattern (99% Nullwerte)
     * BEHEBT: TimeScale-Button-Problem durch zu aggressive Zero-Pattern-Erkennung
     */
    private boolean isExtremeZeroDataPattern(List<TickDataLoader.TickData> filteredTicks) {
        if (filteredTicks.size() < 5) return false; // Zu wenige Daten für Pattern-Erkennung
        
        TickDataLoader.TickData firstTick = filteredTicks.get(0);
        double initialEquity = firstTick.getEquity();
        
        int zeroCount = 0;
        for (TickDataLoader.TickData tick : filteredTicks) {
            double realizedProfit = tick.getEquity() - initialEquity;
            double profitPlusOpen = realizedProfit + tick.getFloatingProfit();
            
            // SEHR STRENGE Schwellenwerte - nur echte Nullwerte
            if (Math.abs(realizedProfit) < 0.001 && Math.abs(profitPlusOpen) < 0.001) {
                zeroCount++;
            }
        }
        
        // EXTREM STRENG: 99% müssen Zero sein UND mindestens 10 Datenpunkte
        boolean isExtremeZeroPattern = zeroCount > filteredTicks.size() * 0.99 && filteredTicks.size() >= 10;
        
        if (isExtremeZeroPattern) {
            LOGGER.warning("EXTREME ZERO-PATTERN: " + zeroCount + "/" + filteredTicks.size() + " Null-Werte");
        } else if (zeroCount > filteredTicks.size() * 0.8) {
            LOGGER.info("Viele Null-Werte erkannt (" + zeroCount + "/" + filteredTicks.size() + 
                       ") aber normal verarbeitet für TimeScale");
        }
        
        return isExtremeZeroPattern;
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
        
        // KORRIGIERT: Chart-Titel mit TimeScale-Information (DIAGNOSE #2 Style)
        String timeScaleInfo = (timeScale != null) ? timeScale.getLabel() : "Unbekannt";
        profitChart.setTitle("Profit-Entwicklung - " + signalId + " (" + providerName + ") - " + 
                            timeScaleInfo + " [DIAGNOSE #" + updateCounter + "] ZERO-DATA FAST");
        
        LOGGER.info("=== ZERO-DATA FAST-RENDERING BEENDET für TimeScale: " + timeScaleInfo + " ===");
    }
    
    /**
     * Aktualisiert den Chart-Titel (DIAGNOSE #2 Style)
     */
    private void updateChartTitleForDiagnose2(TimeScale timeScale, int tickCount) {
        if (profitChart != null) {
            profitChart.setTitle("Profit-Entwicklung - " + signalId + " (" + providerName + ") - " + 
                                (timeScale != null ? timeScale.getLabel() : "Unbekannt") + " [DIAGNOSE #" + updateCounter + "]");
        }
        
        LOGGER.info("Profit-Chart-Titel aktualisiert mit " + tickCount + " Ticks (DIAGNOSE #2 Style)");
    }
    
    /**
     * KORRIGIERT: Setzt einen leeren Chart mit TimeScale-Information
     */
    private void setEmptyChart(TimeScale timeScale) {
        clearProfitSeries();
        
        // KEINE manuelle Y-Achsen-Range - verwende AUTO-RANGE
        LOGGER.info("Empty Chart: Verwende AUTO-RANGE für Y-Achse");
        
        String timeScaleInfo = (timeScale != null) ? 
            " - " + timeScale.getLabel() + " (keine Daten)" : " - KEINE DATEN";
        profitChart.setTitle("Profit-Entwicklung - " + signalId + " (" + providerName + ")" + timeScaleInfo);
        
        LOGGER.info("Empty Chart gesetzt für TimeScale: " + (timeScale != null ? timeScale.getLabel() : "NULL"));
    }
    
    /**
     * LEGACY: Überladene Methode für Rückwärtskompatibilität
     */
    private void setEmptyChart() {
        setEmptyChart(null);
    }
    
    /**
     * Leert die Profit-Chart-Serien (OPTIMIERT)
     */
    private void clearProfitSeries() {
        if (profitSeries != null) profitSeries.clear();
        if (profitPlusOpenSeries != null) profitPlusOpenSeries.clear();
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