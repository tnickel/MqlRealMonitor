package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * ERWEITERT: Verwaltet beide Charts mit verbesserter Datenkompression
 * OPTIMIERT: Aggressivere Datenkompression für schnelleres Rendering
 * KORRIGIERT: Profit-Chart verwendet relative Profit-Werte statt absolute Beträge
 * KORRIGIERT: Shapes (Punkte) aktiviert für DIAGNOSE #2 Darstellung
 * LOGGING: Detaillierte Informationen über Kompression und Performance
 */
public class TickChartManager {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartManager.class.getName());
    
    // Chart-Instanzen
    private JFreeChart drawdownChart;
    private JFreeChart profitChart;
    
    // Provider-Informationen
    private final String signalId;
    private final String providerName;
    
    // REDUZIERT: Weniger Datenpunkte für schnelleres Rendering
    private static final int MAX_DATA_POINTS = 100;  // Reduziert von 200 auf 100
    private static final int AGGRESSIVE_MAX_POINTS = 50;  // Für große Datenmengen
    
    // Performance-Tracking
    private long lastOptimizationTime = 0;
    private int totalOriginalPoints = 0;
    private int totalOptimizedPoints = 0;
    
    /**
     * Konstruktor
     */
    public TickChartManager(String signalId, String providerName) {
        this.signalId = signalId;
        this.providerName = providerName;
        
        LOGGER.info("TickChartManager erstellt für Signal: " + signalId + " (" + providerName + ")");
    }
    
    /**
     * ERWEITERT: Optimiert die Tick-Daten durch intelligentes Sampling mit detailliertem Logging
     * AGGRESSIVER: Verwendet weniger Punkte für bessere Performance
     * 
     * @param tickDataList Die originalen Tick-Daten
     * @param maxDataPoints Maximale Anzahl der Datenpunkte
     * @return Optimierte Tick-Daten
     */
    private List<TickDataLoader.TickData> optimizeTickDataBySampling(List<TickDataLoader.TickData> tickDataList, int maxDataPoints) {
        long startTime = System.currentTimeMillis();
        
        if (tickDataList == null || tickDataList.isEmpty()) {
            LOGGER.info("SAMPLING: Keine Daten zum Optimieren");
            return tickDataList;
        }
        
        int originalSize = tickDataList.size();
        
        // Wenn weniger Daten als Maximum, keine Optimierung nötig
        if (originalSize <= maxDataPoints) {
            LOGGER.info("SAMPLING: Keine Optimierung nötig (" + originalSize + " <= " + maxDataPoints + ")");
            return tickDataList;
        }
        
        LOGGER.info("=== SAMPLING GESTARTET ===");
        LOGGER.info("Original Datenpunkte: " + originalSize);
        LOGGER.info("Ziel Datenpunkte: " + maxDataPoints);
        
        // AGGRESSIVER: Bei sehr großen Datenmengen noch weniger Punkte
        int effectiveMaxPoints = maxDataPoints;
        if (originalSize > 1000) {
            effectiveMaxPoints = AGGRESSIVE_MAX_POINTS;
            LOGGER.warning("AGGRESSIVE KOMPRESSION: Große Datenmenge (" + originalSize + ") -> verwende nur " + effectiveMaxPoints + " Punkte");
        }
        
        // Intelligentes Sampling
        List<TickDataLoader.TickData> optimizedList = new ArrayList<>();
        
        // Immer ersten und letzten Punkt behalten
        optimizedList.add(tickDataList.get(0));
        
        // Sampling-Rate berechnen
        double samplingRate = (double) originalSize / effectiveMaxPoints;
        LOGGER.info("Sampling-Rate: " + String.format("%.2f", samplingRate));
        
        // Punkte mit signifikanten Änderungen bevorzugen
        double lastTotalValue = tickDataList.get(0).getTotalValue();
        double lastFloatingProfit = tickDataList.get(0).getFloatingProfit();
        
        int sampledPoints = 0;
        int significantPoints = 0;
        
        for (int i = 1; i < originalSize - 1; i++) {
            TickDataLoader.TickData tick = tickDataList.get(i);
            
            // Behalte Punkt wenn:
            // 1. Er im Sampling-Intervall liegt
            boolean shouldSample = (i % (int)Math.max(1, samplingRate)) == 0;
            
            // 2. Oder signifikante Änderung (>0.5%) im Total Value - VERSCHÄRFT
            double totalValueChange = Math.abs(tick.getTotalValue() - lastTotalValue) / Math.max(1.0, Math.abs(lastTotalValue));
            boolean significantTotalChange = totalValueChange > 0.005; // Von 0.01 auf 0.005
            
            // 3. Oder signifikante Änderung im Floating Profit (>2 Einheiten) - VERSCHÄRFT  
            double floatingChange = Math.abs(tick.getFloatingProfit() - lastFloatingProfit);
            boolean significantFloatingChange = floatingChange > 2.0; // Von 5.0 auf 2.0
            
            if (shouldSample || significantTotalChange || significantFloatingChange) {
                // Werte haben sich geändert - Punkt behalten
                optimizedList.add(tick);
                
                if (shouldSample) sampledPoints++;
                if (significantTotalChange || significantFloatingChange) significantPoints++;
                
                // Werte für nächsten Vergleich aktualisieren
                lastTotalValue = tick.getTotalValue();
                lastFloatingProfit = tick.getFloatingProfit();
                
                // Frühzeitig beenden wenn Maximum erreicht
                if (optimizedList.size() >= effectiveMaxPoints - 1) {
                    LOGGER.info("SAMPLING: Maximum erreicht bei Index " + i + "/" + originalSize);
                    break;
                }
            }
        }
        
        // Immer letzten Punkt hinzufügen
        optimizedList.add(tickDataList.get(originalSize - 1));
        
        long processingTime = System.currentTimeMillis() - startTime;
        int finalSize = optimizedList.size();
        double reductionPercent = ((double)(originalSize - finalSize) / originalSize) * 100.0;
        
        LOGGER.info("=== SAMPLING ABGESCHLOSSEN ===");
        LOGGER.info("Datenpunkte: " + originalSize + " -> " + finalSize);
        LOGGER.info("Reduktion: " + (originalSize - finalSize) + " Punkte (" + String.format("%.1f%%", reductionPercent) + ")");
        LOGGER.info("Sampled Points: " + sampledPoints);
        LOGGER.info("Significant Points: " + significantPoints);
        LOGGER.info("Processing Zeit: " + processingTime + "ms");
        LOGGER.info("Kompression Ratio: " + String.format("%.1f:1", (double)originalSize / finalSize));
        
        return optimizedList;
    }
    
    /**
     * ERWEITERT: Entfernt doppelte Werte mit detailliertem Logging
     */
    private List<TickDataLoader.TickData> removeDuplicateValues(List<TickDataLoader.TickData> tickDataList) {
        long startTime = System.currentTimeMillis();
        
        if (tickDataList == null || tickDataList.size() <= 2) {
            LOGGER.info("DUPLIKAT-ENTFERNUNG: Zu wenige Daten (" + (tickDataList != null ? tickDataList.size() : "null") + ")");
            return tickDataList;
        }
        
        LOGGER.info("=== DUPLIKAT-ENTFERNUNG GESTARTET ===");
        LOGGER.info("Original Datenpunkte: " + tickDataList.size());
        
        List<TickDataLoader.TickData> cleanedList = new ArrayList<>();
        
        // Ersten Punkt immer behalten
        cleanedList.add(tickDataList.get(0));
        
        // Vorherige Werte für Vergleich
        double lastEquity = tickDataList.get(0).getEquity();
        double lastFloatingProfit = tickDataList.get(0).getFloatingProfit();
        double lastProfit = tickDataList.get(0).getProfit();
        double lastTotalValue = tickDataList.get(0).getTotalValue();
        
        int duplicateCount = 0;
        int processedCount = 0;
        
        // Durch alle Datenpunkte iterieren (außer dem ersten und letzten)
        for (int i = 1; i < tickDataList.size() - 1; i++) {
            TickDataLoader.TickData tick = tickDataList.get(i);
            processedCount++;
            
            // Prüfe ob sich irgendein Wert geändert hat
            boolean hasChanged = 
                Math.abs(tick.getEquity() - lastEquity) > 0.001 ||
                Math.abs(tick.getFloatingProfit() - lastFloatingProfit) > 0.001 ||
                Math.abs(tick.getProfit() - lastProfit) > 0.001 ||
                Math.abs(tick.getTotalValue() - lastTotalValue) > 0.001;
            
            if (hasChanged) {
                // Werte haben sich geändert - Punkt behalten
                cleanedList.add(tick);
                
                // Werte für nächsten Vergleich aktualisieren
                lastEquity = tick.getEquity();
                lastFloatingProfit = tick.getFloatingProfit();
                lastProfit = tick.getProfit();
                lastTotalValue = tick.getTotalValue();
            } else {
                // Duplikat gefunden
                duplicateCount++;
            }
        }
        
        // Letzten Punkt immer behalten
        cleanedList.add(tickDataList.get(tickDataList.size() - 1));
        
        long processingTime = System.currentTimeMillis() - startTime;
        int finalSize = cleanedList.size();
        double reductionPercent = ((double)duplicateCount / tickDataList.size()) * 100.0;
        
        LOGGER.info("=== DUPLIKAT-ENTFERNUNG ABGESCHLOSSEN ===");
        LOGGER.info("Verarbeitete Punkte: " + processedCount);
        LOGGER.info("Gefundene Duplikate: " + duplicateCount);
        LOGGER.info("Datenpunkte: " + tickDataList.size() + " -> " + finalSize);
        LOGGER.info("Duplikat-Reduktion: " + String.format("%.1f%%", reductionPercent));
        LOGGER.info("Processing Zeit: " + processingTime + "ms");
        
        return cleanedList;
    }
    
    /**
     * ERWEITERT: Kombinierte Datenoptimierung mit Performance-Tracking
     */
    private List<TickDataLoader.TickData> optimizeTickData(List<TickDataLoader.TickData> tickDataList, int maxDataPoints) {
        long totalStartTime = System.currentTimeMillis();
        
        if (tickDataList == null || tickDataList.isEmpty()) {
            LOGGER.info("DATENOPTIMIERUNG: Keine Daten zum Optimieren");
            return tickDataList;
        }
        
        int originalSize = tickDataList.size();
        totalOriginalPoints = originalSize;
        
        LOGGER.info("============================================");
        LOGGER.info("=== DATENOPTIMIERUNG GESTARTET ===");
        LOGGER.info("Signal: " + signalId);
        LOGGER.info("Original Datenpunkte: " + originalSize);
        LOGGER.info("Ziel Datenpunkte: " + maxDataPoints);
        LOGGER.info("============================================");
        
        // SCHRITT 1: Duplikate entfernen
        List<TickDataLoader.TickData> deduplicatedData = removeDuplicateValues(tickDataList);
        
        // SCHRITT 2: Sampling anwenden falls immer noch zu viele Punkte
        List<TickDataLoader.TickData> finalData = optimizeTickDataBySampling(deduplicatedData, maxDataPoints);
        
        // Performance-Tracking
        long totalTime = System.currentTimeMillis() - totalStartTime;
        lastOptimizationTime = totalTime;
        totalOptimizedPoints = finalData.size();
        
        double totalReduction = ((double)(originalSize - finalData.size()) / originalSize) * 100.0;
        double compressionRatio = (double)originalSize / finalData.size();
        
        LOGGER.info("============================================");
        LOGGER.info("=== DATENOPTIMIERUNG ABGESCHLOSSEN ===");
        LOGGER.info("FINAL: " + originalSize + " -> " + finalData.size() + " Datenpunkte");
        LOGGER.info("TOTAL REDUKTION: " + (originalSize - finalData.size()) + " Punkte (" + String.format("%.1f%%", totalReduction) + ")");
        LOGGER.info("KOMPRESSION: " + String.format("%.1f:1", compressionRatio));
        LOGGER.info("PERFORMANCE: " + totalTime + "ms Gesamt-Verarbeitungszeit");
        
        if (compressionRatio < 2.0 && originalSize > 500) {
            LOGGER.warning("WARNUNG: Niedrige Kompression (" + String.format("%.1f:1", compressionRatio) + ") bei großer Datenmenge");
        }
        
        if (totalTime > 1000) {
            LOGGER.warning("WARNUNG: Langsame Datenoptimierung (" + totalTime + "ms) - könnte Render-Timeout verursachen");
        }
        
        LOGGER.info("============================================");
        
        return finalData;
    }
    
    /**
     * OPTIMIERT: Erstellt oder aktualisiert den Equity Drawdown Chart
     * NEU: Mit aggressiverer Data-Kompression für bessere Performance
     */
    public void createOrUpdateDrawdownChart(List<TickDataLoader.TickData> tickDataList, 
                                          double peakTotalValue, 
                                          boolean showDiagnoseTag,
                                          TimeScale timeScale) {
        if (tickDataList == null || tickDataList.isEmpty()) {
            LOGGER.warning("Keine Tick-Daten für Drawdown-Chart vorhanden");
            return;
        }
        
        LOGGER.info("=== ERSTELLE/UPDATE EQUITY DRAWDOWN CHART ===");
        LOGGER.info("Original Datenpunkte: " + tickDataList.size());
        LOGGER.info("Peak Total Value: " + String.format("%.2f", peakTotalValue));
        
        // ERWEITERT: Aggressivere Data-Kompression
        List<TickDataLoader.TickData> optimizedData = optimizeTickData(tickDataList, MAX_DATA_POINTS);

        LOGGER.info("Verwende " + optimizedData.size() + " Datenpunkte für Drawdown-Chart (Kompression: " + 
                   String.format("%.1f:1", (double)tickDataList.size() / optimizedData.size()) + ")");
        
        // TimeSeries für Total Value Drawdown erstellen
        TimeSeries totalValueDrawdownSeries = new TimeSeries("Total Value Drawdown (%)");
        
        // Peak-Tracking für konsistente Drawdown-Berechnung
        double runningPeak = peakTotalValue;
        boolean firstData = true;
        
        for (TickDataLoader.TickData tick : optimizedData) {
            try {
                double totalValue = tick.getTotalValue();
                
                // Peak-Tracking (konsistent mit SignalProviderTable)
                if (firstData) {
                    if (totalValue > runningPeak) {
                        runningPeak = totalValue;
                    }
                    firstData = false;
                } else if (totalValue > runningPeak) {
                    runningPeak = totalValue;
                }
                
                // Drawdown berechnen (konsistent mit SignalData)
                double drawdownPercent = 0.0;
                if (runningPeak > 0) {
                    drawdownPercent = ((totalValue - runningPeak) / runningPeak) * 100.0;
                }
                
                // KORRIGIERT: LocalDateTime zu Date konvertieren
                LocalDateTime localDateTime = tick.getTimestamp();
                Date timestamp = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                Millisecond millisecond = new Millisecond(timestamp);
                
                // Datenpunkt hinzufügen
                totalValueDrawdownSeries.addOrUpdate(millisecond, drawdownPercent);
                
            } catch (Exception e) {
                LOGGER.warning("Fehler beim Hinzufügen von Drawdown-Datenpunkt: " + e.getMessage());
            }
        }
        
        // Dataset erstellen
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(totalValueDrawdownSeries);
        
        // Chart erstellen oder aktualisieren
        if (drawdownChart == null) {
            drawdownChart = createDrawdownChartInstance(dataset, showDiagnoseTag, timeScale);
            LOGGER.info("Neuer Drawdown-Chart erstellt");
        } else {
            // Bestehenden Chart aktualisieren
            XYPlot plot = drawdownChart.getXYPlot();
            plot.setDataset(dataset);
            
            // Titel aktualisieren
            String timeScaleLabel = (timeScale != null) ? timeScale.getLabel() : "ALL";
            String diagnoseTag = showDiagnoseTag ? " [DIAGNOSE #0]" : "";
            drawdownChart.setTitle("Total Value Drawdown (%) - EQUITY+FLOATING - " + signalId + 
                                  " (" + providerName + ") - " + timeScaleLabel + diagnoseTag);
            
            // KORRIGIERT: X-Achse korrekt kalibrieren
            configureDateAxis(plot, optimizedData);
            
            LOGGER.info("Bestehender Drawdown-Chart aktualisiert");
        }
        
        LOGGER.info("EQUITY DRAWDOWN CHART ERFOLGREICH ERSTELLT/AKTUALISIERT (Optimierte Performance)");
    }
    
    /**
     * KORRIGIERT: Erstellt oder aktualisiert den Profit Development Chart
     * FIX: Verwendet relative Profit-Werte statt absolute Beträge für korrekte Y-Achse
     */
    public void createOrUpdateProfitChart(List<TickDataLoader.TickData> tickDataList, 
                                        boolean showDiagnoseTag,
                                        TimeScale timeScale) {
        if (tickDataList == null || tickDataList.isEmpty()) {
            LOGGER.warning("Keine Tick-Daten für Profit-Chart vorhanden");
            return;
        }
        
        LOGGER.info("=== ERSTELLE/UPDATE PROFIT DEVELOPMENT CHART (KORRIGIERTE PROFIT-BERECHNUNG) ===");
        LOGGER.info("Original Datenpunkte: " + tickDataList.size());
        
        // ERWEITERT: Aggressivere Data-Kompression
        List<TickDataLoader.TickData> optimizedData = optimizeTickData(tickDataList, MAX_DATA_POINTS);
        LOGGER.info("Verwende " + optimizedData.size() + " Datenpunkte für Profit-Chart (Kompression: " + 
                   String.format("%.1f:1", (double)tickDataList.size() / optimizedData.size()) + ")");
        
        // KORRIGIERT: Initial Equity für Profit-Berechnung bestimmen
        if (optimizedData.isEmpty()) {
            LOGGER.warning("Keine optimierten Daten für Profit-Berechnung");
            return;
        }
        
        double initialEquity = optimizedData.get(0).getEquity();
        LOGGER.info("PROFIT-BASIS: Initial Equity = " + String.format("%.2f", initialEquity));
        
        // TimeSeries für RELATIVE Profit-Werte erstellen (nicht absolute Beträge!)
        TimeSeries realizedProfitSeries = new TimeSeries("Kontostand (Profit)");
        TimeSeries totalProfitSeries = new TimeSeries("Gesamtwert (Profit + Floating)");
        
        int addedPoints = 0;
        double minProfit = Double.MAX_VALUE;
        double maxProfit = Double.MIN_VALUE;
        
        for (TickDataLoader.TickData tick : optimizedData) {
            try {
                // KORRIGIERT: LocalDateTime zu Date konvertieren
                LocalDateTime localDateTime = tick.getTimestamp();
                Date timestamp = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
                Millisecond millisecond = new Millisecond(timestamp);
                
                // KORRIGIERT: Relative Profit-Berechnung (wie im unteren Bild)
                double currentEquity = tick.getEquity();
                double realizedProfit = currentEquity - initialEquity;  // Relativer Gewinn/Verlust
                
                double floatingProfit = tick.getFloatingProfit();
                double totalProfit = realizedProfit + floatingProfit;   // Gesamter Profit inkl. Floating
                
                // Y-Achse zeigt jetzt PROFIT in Euro (0, 5, 10, 15) statt absolute Beträge (2800, 2805)
                realizedProfitSeries.addOrUpdate(millisecond, realizedProfit);
                totalProfitSeries.addOrUpdate(millisecond, totalProfit);
                
                addedPoints++;
                
                // Min/Max für Logging
                if (totalProfit < minProfit) minProfit = totalProfit;
                if (totalProfit > maxProfit) maxProfit = totalProfit;
                
            } catch (Exception e) {
                LOGGER.warning("Fehler beim Hinzufügen von Profit-Datenpunkt: " + e.getMessage());
            }
        }
        
        LOGGER.info("PROFIT-DATENPUNKTE HINZUGEFÜGT: " + addedPoints + " von " + optimizedData.size());
        if (minProfit != Double.MAX_VALUE && maxProfit != Double.MIN_VALUE) {
            LOGGER.info("PROFIT-BEREICH: " + String.format("%.2f", minProfit) + " bis " + String.format("%.2f", maxProfit) + " Euro");
        }
        
        // Dataset erstellen mit korrigierten Profit-Serien
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(realizedProfitSeries);  // KORRIGIERT: Relative Profit-Werte
        dataset.addSeries(totalProfitSeries);     // KORRIGIERT: Relative Total-Profit-Werte
        
        // Chart erstellen oder aktualisieren
        if (profitChart == null) {
            profitChart = createProfitChartInstance(dataset, showDiagnoseTag, timeScale);
            LOGGER.info("Neuer Profit-Chart erstellt");
        } else {
            // Bestehenden Chart aktualisieren
            XYPlot plot = profitChart.getXYPlot();
            plot.setDataset(dataset);
            
            // Titel aktualisieren
            String timeScaleLabel = (timeScale != null) ? timeScale.getLabel() : "ALL";
            String diagnoseTag = showDiagnoseTag ? " [DIAGNOSE #1]" : "";
            profitChart.setTitle("Profit-Entwicklung - " + signalId + 
                               " (" + providerName + ") - " + timeScaleLabel + diagnoseTag);
            
            // KORRIGIERT: X-Achse korrekt kalibrieren
            configureDateAxis(plot, optimizedData);
            
            LOGGER.info("Bestehender Profit-Chart aktualisiert (mit relativen Profit-Werten)");
        }
        
        LOGGER.info("PROFIT DEVELOPMENT CHART ERFOLGREICH ERSTELLT/AKTUALISIERT (Relative Profit-Werte, Optimierte Performance)");
    }
    
    /**
     * Erstellt eine neue Drawdown-Chart-Instanz
     * OPTIMIERT: Anti-Aliasing deaktiviert für bessere Performance
     */
    private JFreeChart createDrawdownChartInstance(TimeSeriesCollection dataset, 
                                                  boolean showDiagnoseTag,
                                                  TimeScale timeScale) {
        String timeScaleLabel = (timeScale != null) ? timeScale.getLabel() : "ALL";
        String diagnoseTag = showDiagnoseTag ? " [DIAGNOSE #0]" : "";
        
        // Plot erstellen
        DateAxis timeAxis = new DateAxis("Zeit");
        NumberAxis valueAxis = new NumberAxis("Total Value Drawdown (%) - EQUITY+FLOATING");
        
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        
        XYPlot plot = new XYPlot(dataset, timeAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // NEU: Performance-Optimierungen
        plot.setDomainPannable(false);
        plot.setRangePannable(false);
        
        // Achsen konfigurieren
        configureDateAxis(plot, null);
        configureValueAxis(valueAxis, true);
        
        // 0-Linie hinzufügen
        valueAxis.setAutoRangeIncludesZero(true);
        plot.setRangeZeroBaselinePaint(Color.BLACK);
        plot.setRangeZeroBaselineStroke(new BasicStroke(1.0f));
        plot.setRangeZeroBaselineVisible(true);
        
        // Chart erstellen
        JFreeChart chart = new JFreeChart(
            "Total Value Drawdown (%) - EQUITY+FLOATING - " + signalId + 
            " (" + providerName + ") - " + timeScaleLabel + diagnoseTag,
            JFreeChart.DEFAULT_TITLE_FONT,
            plot,
            true
        );
        
        chart.setBackgroundPaint(Color.WHITE);
        
        // NEU: Anti-Aliasing deaktivieren für bessere Performance
        chart.setAntiAlias(false);
        chart.setTextAntiAlias(false);
        
        return chart;
    }
    
    /**
     * KORRIGIERT: Erstellt eine neue Profit-Chart-Instanz mit SHAPES (Punkte) aktiviert
     * Y-Achse zeigt "Profit" statt "Wert" für relative Gewinn-Darstellung
     * SHAPES: Punkte sind für DIAGNOSE #2 Darstellung aktiviert
     */
    private JFreeChart createProfitChartInstance(TimeSeriesCollection dataset, 
                                               boolean showDiagnoseTag,
                                               TimeScale timeScale) {
        String timeScaleLabel = (timeScale != null) ? timeScale.getLabel() : "ALL";
        String diagnoseTag = showDiagnoseTag ? " [DIAGNOSE #1]" : "";
        
        // Plot erstellen
        DateAxis timeAxis = new DateAxis("Zeit");
        NumberAxis valueAxis = new NumberAxis("Profit");  // KORRIGIERT: "Profit" statt "Wert"
        
        // KORRIGIERT: Renderer mit Linien UND Shapes (Punkte) für DIAGNOSE #2 Darstellung
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);  // true, true = Linien + Shapes
        
        // Farben für die beiden Serien
        renderer.setSeriesPaint(0, new Color(0, 128, 0)); // Dunkelgrün für Realized Profit
        renderer.setSeriesPaint(1, new Color(255, 215, 0)); // Gold für Total Profit
        
        // Linienstärke
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        
        // EXPLIZIT: Shapes (Punkte) aktivieren für beide Serien (wie DIAGNOSE #2)
        renderer.setSeriesShapesVisible(0, true);  // Grüne Punkte für Realized Profit
        renderer.setSeriesShapesVisible(1, true);  // Gelbe Punkte für Total Profit
        renderer.setSeriesLinesVisible(0, true);   // Grüne Linie
        renderer.setSeriesLinesVisible(1, true);   // Gelbe Linie
        
        // KORREKTE Shape-Größe für bessere Sichtbarkeit (wie DIAGNOSE #2)
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)); // Realized Profit
        renderer.setSeriesShape(1, new java.awt.geom.Ellipse2D.Double(-4, -4, 8, 8)); // Total Profit (etwas größer)
        
        XYPlot plot = new XYPlot(dataset, timeAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // NEU: Performance-Optimierungen
        plot.setDomainPannable(false);
        plot.setRangePannable(false);
        
        // Achsen konfigurieren
        configureDateAxis(plot, null);
        configureValueAxis(valueAxis, false);  // false = Profit-Chart (nicht Drawdown)
        
        // Chart erstellen
        JFreeChart chart = new JFreeChart(
            "Profit-Entwicklung - " + signalId + 
            " (" + providerName + ") - " + timeScaleLabel + diagnoseTag,
            JFreeChart.DEFAULT_TITLE_FONT,
            plot,
            true
        );
        
        chart.setBackgroundPaint(Color.WHITE);
        
        // NEU: Anti-Aliasing deaktivieren für bessere Performance
        chart.setAntiAlias(false);
        chart.setTextAntiAlias(false);
        
        // Legende positionieren
        chart.getLegend().setPosition(org.jfree.chart.ui.RectangleEdge.TOP);
        
        LOGGER.info("Profit-Chart erstellt mit aktivierten Shapes (Punkte) für DIAGNOSE #2 Darstellung");
        
        return chart;
    }
    
    /**
     * KORRIGIERT: Konfiguriert die Datums-Achse (X-Achse) mit korrekter Kalibrierung
     * FIX: LocalDateTime zu Date Konvertierung
     * 
     * @param plot Der XYPlot
     * @param tickDataList Optionale Tick-Daten für präzise Range-Einstellung
     */
    private void configureDateAxis(XYPlot plot, List<TickDataLoader.TickData> tickDataList) {
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        
        // Zeitzone auf System-Default setzen
        axis.setTimeZone(TimeZone.getDefault());
        
        // Format für die Achsenbeschriftung
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        dateFormat.setTimeZone(TimeZone.getDefault());
        axis.setDateFormatOverride(dateFormat);
        
        // Achsenbeschriftung
        axis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));
        axis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 10));
        
        // KORRIGIERT: Auto-Range aktivieren für korrekte Skalierung
        axis.setAutoRange(true);
        axis.setAutoRangeMinimumSize(60000); // Mindestens 1 Minute Range
        
        // Optional: Wenn Tick-Daten vorhanden, präzise Range setzen
        if (tickDataList != null && !tickDataList.isEmpty()) {
            // KORRIGIERT: LocalDateTime zu Date konvertieren
            LocalDateTime minDateTime = tickDataList.get(0).getTimestamp();
            LocalDateTime maxDateTime = tickDataList.get(tickDataList.size() - 1).getTimestamp();
            
            // Konvertiere LocalDateTime zu Date
            Date minDate = Date.from(minDateTime.atZone(ZoneId.systemDefault()).toInstant());
            Date maxDate = Date.from(maxDateTime.atZone(ZoneId.systemDefault()).toInstant());
            
            // 5% Padding hinzufügen
            long range = maxDate.getTime() - minDate.getTime();
            long padding = (long)(range * 0.05);
            
            axis.setRange(new Date(minDate.getTime() - padding), 
                         new Date(maxDate.getTime() + padding));
            
            LOGGER.fine("X-Achse kalibriert: " + minDate + " bis " + maxDate);
        }
    }
    
    /**
     * Konfiguriert die Wert-Achse (Y-Achse)
     */
    private void configureValueAxis(NumberAxis axis, boolean isDrawdownChart) {
        axis.setLabelFont(new Font("SansSerif", Font.BOLD, 12));
        axis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 10));
        
        if (isDrawdownChart) {
            // Für Drawdown-Chart: Negative Werte
            axis.setNumberFormatOverride(new DecimalFormat("0.0"));
            axis.setAutoRangeIncludesZero(true);
        } else {
            // Für Profit-Chart: Positive Werte
            axis.setNumberFormatOverride(new DecimalFormat("#,##0.00"));
            axis.setAutoRangeIncludesZero(false);
        }
        
        // Auto-Range aktivieren
        axis.setAutoRange(true);
        axis.setAutoRangeMinimumSize(1.0);
    }
    
    // ========== KOMPATIBILITÄTS-METHODEN FÜR PANEL-KLASSEN ==========
    
    /**
     * KOMPATIBILITÄTS-METHODE für EquityDrawdownChartPanel
     * Aktualisiert nur den Drawdown-Chart mit gefilterten Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     */
    public void updateDrawdownChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Keine gefilterten Ticks für Drawdown-Chart");
            return;
        }
        
        // Peak-Total-Value aus den Daten berechnen
        double peakTotalValue = calculatePeakFromData(filteredTicks);
        
        // Chart erstellen/aktualisieren mit optimierten Daten
        createOrUpdateDrawdownChart(filteredTicks, peakTotalValue, false, timeScale);
    }
    
    /**
     * KOMPATIBILITÄTS-METHODE für SignalOverviewPanel
     * Aktualisiert beide Charts mit gefilterten Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     */
    public void updateChartsWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Keine gefilterten Ticks für Charts");
            return;
        }
        
        // Peak-Total-Value aus den Daten berechnen
        double peakTotalValue = calculatePeakFromData(filteredTicks);
        
        // Beide Charts erstellen/aktualisieren
        createOrUpdateDrawdownChart(filteredTicks, peakTotalValue, false, timeScale);
        createOrUpdateProfitChart(filteredTicks, false, timeScale);
    }
    
    /**
     * KOMPATIBILITÄTS-METHODE für ProfitDevelopmentChartPanel
     * Gibt einen Dummy-ProfitChartManager zurück (für Kompatibilität)
     * 
     * @return this (als ProfitChartManager-Ersatz)
     */
    public TickChartManager getProfitChartManager() {
        // Rückgabe von this, da TickChartManager beide Charts verwaltet
        return this;
    }
    
    /**
     * KOMPATIBILITÄTS-METHODE für ProfitDevelopmentChartPanel
     * Aktualisiert nur den Profit-Chart mit gefilterten Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     */
    public void updateProfitChartWithData(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        if (filteredTicks == null || filteredTicks.isEmpty()) {
            LOGGER.warning("Keine gefilterten Ticks für Profit-Chart");
            return;
        }
        
        // Chart erstellen/aktualisieren mit optimierten Daten
        createOrUpdateProfitChart(filteredTicks, false, timeScale);
    }
    
    /**
     * Hilfsmethode: Berechnet den Peak-Total-Value aus den Daten
     * 
     * @param tickDataList Die Tick-Daten
     * @return Der Peak-Total-Value
     */
    private double calculatePeakFromData(List<TickDataLoader.TickData> tickDataList) {
        if (tickDataList == null || tickDataList.isEmpty()) {
            return 0.0;
        }
        
        double peak = tickDataList.get(0).getTotalValue();
        
        for (TickDataLoader.TickData tick : tickDataList) {
            double totalValue = tick.getTotalValue();
            if (totalValue > peak) {
                peak = totalValue;
            }
        }
        
        LOGGER.fine("Peak-Total-Value berechnet: " + String.format("%.2f", peak));
        return peak;
    }
    
    /**
     * NEU: Gibt Performance-Statistiken zurück
     */
    public String getPerformanceStats() {
        if (totalOriginalPoints == 0) {
            return "Keine Performance-Daten verfügbar";
        }
        
        double compressionRatio = (double)totalOriginalPoints / totalOptimizedPoints;
        double reductionPercent = ((double)(totalOriginalPoints - totalOptimizedPoints) / totalOriginalPoints) * 100.0;
        
        return String.format("Kompression: %d->%d Punkte (%.1f:1, %.1f%% Reduktion) in %dms", 
                           totalOriginalPoints, totalOptimizedPoints, compressionRatio, 
                           reductionPercent, lastOptimizationTime);
    }
    
    // ========== STANDARD GETTER-METHODEN ==========
    
    /**
     * Gibt den Drawdown-Chart zurück
     */
    public JFreeChart getDrawdownChart() {
        return drawdownChart;
    }
    
    /**
     * Gibt den Profit-Chart zurück
     */
    public JFreeChart getProfitChart() {
        return profitChart;
    }
    
    /**
     * Gibt zurück ob Charts vorhanden sind
     */
    public boolean hasCharts() {
        return drawdownChart != null && profitChart != null;
    }
    
    /**
     * Löscht alle Charts
     */
    public void clearCharts() {
        drawdownChart = null;
        profitChart = null;
        LOGGER.info("Alle Charts gelöscht");
    }
}