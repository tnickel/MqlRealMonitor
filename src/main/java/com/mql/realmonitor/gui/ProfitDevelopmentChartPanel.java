package com.mql.realmonitor.gui;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * STARK ERWEITERTE Profit Development Chart Panel Komponente
 * NEU: ScrolledComposite für große Charts (ALL-Modus, lange Zeiträume)
 * NEU: Dynamische Chart-Größe basierend auf TimeScale und Datenmenge
 * NEU: Optimierte Scroll-Performance für große Datenmengen
 * Kapselt die komplette Profit-Chart-Anzeige (Canvas, Painting, Event-Handling)
 * Kann in verschiedenen Fenstern verwendet werden
 * KORRIGIERT: Bessere Beschriftung für Profit-Chart mit automatischer Legende
 */
public class ProfitDevelopmentChartPanel extends Composite {
    
    private static final Logger LOGGER = Logger.getLogger(ProfitDevelopmentChartPanel.class.getName());
    
    // UI Komponenten
    private ScrolledComposite scrolledComposite;  // NEU: Für scrollbare Charts
    private Canvas profitCanvas;
    private Label profitLabel;
    
    // Chart-Verwaltung
    private final ChartImageRenderer imageRenderer;
    private final String signalId;
    private final String providerName;
    private final MqlRealMonitorGUI parentGui;
    
    // Chart-Dimensionen - NEU: Dynamisch
    private int chartWidth = 800;
    private int chartHeight = 400;
    private int maxDisplayWidth = 1200;  // NEU: Maximale Anzeige-Breite bevor Scrolling
    
    // Status
    private volatile boolean isDataLoaded = false;
    private TimeScale currentTimeScale = null;  // NEU: Aktuelle TimeScale verfolgen
    
    /**
     * Konstruktor
     * 
     * @param parent Das Parent-Composite
     * @param parentGui Die Parent-GUI für Styling
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     * @param imageRenderer Der Chart-Image-Renderer
     */
    public ProfitDevelopmentChartPanel(Composite parent, MqlRealMonitorGUI parentGui, 
                                      String signalId, String providerName, 
                                      ChartImageRenderer imageRenderer) {
        super(parent, SWT.NONE);
        
        this.parentGui = parentGui;
        this.signalId = signalId;
        this.providerName = providerName;
        this.imageRenderer = imageRenderer;
        
        createComponents();
        
        LOGGER.info("ERWEITERTE ProfitDevelopmentChartPanel erstellt für Signal: " + signalId + " (mit ScrolledComposite)");
    }
    
    /**
     * ERWEITERT: Erstellt die UI-Komponenten mit ScrolledComposite
     */
    private void createComponents() {
        setLayout(new GridLayout(1, false));
        
        // Label für Profit-Chart (KORRIGIERT - Generischer Text mit Hinweis auf Legende)
        profitLabel = new Label(this, SWT.NONE);
        profitLabel.setText("Profit-Entwicklung Chart - Grün: Profit, Gelb: Profit+Floating - Scrollbar bei großen Zeiträumen");
        profitLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        if (parentGui != null && parentGui.getBoldFont() != null) {
            profitLabel.setFont(parentGui.getBoldFont());
        }
        
        // NEU: ScrolledComposite für große Charts
        scrolledComposite = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        
        // NEU: Scroll-Performance optimieren
        scrolledComposite.getHorizontalBar().setIncrement(50);
        scrolledComposite.getVerticalBar().setIncrement(20);
        scrolledComposite.getHorizontalBar().setPageIncrement(200);
        scrolledComposite.getVerticalBar().setPageIncrement(100);
        
        // Canvas für Profit-Chart (INNERHALB des ScrolledComposite)
        profitCanvas = new Canvas(scrolledComposite, SWT.DOUBLE_BUFFERED);
        profitCanvas.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        
        // ScrolledComposite-Content setzen
        scrolledComposite.setContent(profitCanvas);
        
        // Paint Listener
        profitCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintProfitChart(e.gc);
            }
        });
        
        LOGGER.fine("ERWEITERTE Profit-Chart Panel Komponenten erstellt (mit ScrolledComposite)");
    }
    
    /**
     * ERWEITERT: Zeichnet den Profit-Chart mit Scroll-Information
     * 
     * @param gc Der Graphics Context
     */
    private void paintProfitChart(GC gc) {
        gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(profitCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = profitCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidProfitImage()) {
            // Profit-Chart zeichnen
            org.eclipse.swt.graphics.Rectangle imagesBounds = imageRenderer.getProfitChartImage().getBounds();
            
            // NEU: Bei großen Charts linksbündig zeichnen (für Scrolling)
            int x = 0;  // Immer linksbündig bei Scrolling
            int y = Math.max(0, (canvasBounds.height - imagesBounds.height) / 2);  // Vertikal zentriert
            
            gc.drawImage(imageRenderer.getProfitChartImage(), x, y);
            
            // NEU: Scroll-Info anzeigen bei großen Charts
            if (chartWidth > maxDisplayWidth) {
                gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
                gc.drawText("◄ Scrollen für kompletten Zeitraum ►", 10, 10, true);
                
                if (currentTimeScale != null && currentTimeScale.isAll()) {
                    gc.drawText("ALL-Modus: Zeigt kompletten verfügbaren Zeitraum", 10, 30, true);
                }
            }
            
        } else if (!isDataLoaded) {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Profit-Chart wird geladen...", 20, 20, true);
            gc.drawText("Grün: Profit, Gelb: Profit + Open Equity", 20, 40, true);
            gc.drawText("Signal: " + signalId + " (" + providerName + ")", 20, 60, true);
            
            if (currentTimeScale != null) {
                gc.drawText("TimeScale: " + currentTimeScale.getLabel(), 20, 80, true);
                if (currentTimeScale.isAll()) {
                    gc.drawText("(Kompletter verfügbarer Zeitraum)", 20, 100, true);
                }
            }
        } else {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden des Profit-Charts", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
        }
    }
    
    /**
     * STARK ERWEITERT: Aktualisiert den Profit-Chart mit neuen Daten
     * NEU: Dynamische Chart-Größe und ScrolledComposite-Management
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     * @param chartManager Der Chart-Manager
     */
    public void updateChart(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale, 
                           TickChartManager chartManager) {
        if (chartManager == null || chartManager.getProfitChartManager() == null) {
            LOGGER.warning("ChartManager oder ProfitChartManager ist null - kann Profit-Chart nicht aktualisieren");
            return;
        }
        
        try {
            LOGGER.info("ERWEITERTE Aktualisierung: Profit-Chart Panel für Signal: " + signalId + 
                       " (TimeScale: " + (timeScale != null ? timeScale.getLabel() : "NULL") + ")");
            
            // NEU: Aktuelle TimeScale verfolgen
            this.currentTimeScale = timeScale;
            
            // NEU: Optimale Chart-Breite berechnen
            int optimalWidth = calculateOptimalChartWidth(filteredTicks, timeScale);
            updateChartDimensions(optimalWidth, chartHeight);
            
            // Profit-Chart-Manager aktualisieren mit erweiterter X-Achse
            chartManager.getProfitChartManager().updateProfitChartWithData(filteredTicks, timeScale);
            
            // Image rendern mit optimaler Größe
            if (imageRenderer != null) {
                imageRenderer.renderProfitChartToImage(
                    chartManager.getProfitChart(),
                    chartWidth,
                    chartHeight,
                    1.0 // Standard Zoom
                );
            }
            
            // Canvas neu zeichnen
            if (!profitCanvas.isDisposed()) {
                profitCanvas.redraw();
            }
            
            // NEU: Label mit TimeScale-Info aktualisieren
            updateLabelWithTimeScaleInfo(timeScale, filteredTicks);
            
            LOGGER.info("ERWEITERTE Profit-Chart Panel erfolgreich aktualisiert (Optimale Breite: " + 
                       chartWidth + "px, Scrollbar: " + (chartWidth > maxDisplayWidth ? "JA" : "NEIN") + ")");
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim ERWEITERTEN Aktualisieren des Profit-Chart Panels: " + e.getMessage());
        }
    }
    
    /**
     * NEU: Berechnet optimale Chart-Breite basierend auf Daten und TimeScale
     */
    private int calculateOptimalChartWidth(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale) {
        // Delegate to TickDataFilter for consistent calculation
        return TickDataFilter.calculateOptimalChartWidth(filteredTicks, timeScale);
    }
    
    /**
     * NEU: Aktualisiert Label mit TimeScale-Information
     */
    private void updateLabelWithTimeScaleInfo(TimeScale timeScale, List<TickDataLoader.TickData> filteredTicks) {
        if (profitLabel.isDisposed()) return;
        
        StringBuilder labelText = new StringBuilder("Profit-Entwicklung Chart - Grün: Profit, Gelb: Profit+Floating");
        
        if (timeScale != null) {
            labelText.append(" - ").append(timeScale.getLabel());
            
            if (timeScale.isAll()) {
                labelText.append(" (Kompletter Zeitraum)");
            }
            
            // Scroll-Hinweis
            if (chartWidth > maxDisplayWidth) {
                labelText.append(" - Scrollbar verfügbar");
            }
        }
        
        // Daten-Info
        if (filteredTicks != null && !filteredTicks.isEmpty()) {
            labelText.append(" [").append(filteredTicks.size()).append(" Datenpunkte]");
        }
        
        profitLabel.setText(labelText.toString());
    }
    
    /**
     * STARK ERWEITERT: Aktualisiert die Chart-Dimensionen mit ScrolledComposite-Management
     * 
     * @param width Die neue Breite
     * @param height Die neue Höhe
     */
    public void updateChartDimensions(int width, int height) {
        if (width > 0 && height > 0) {
            this.chartWidth = width;
            this.chartHeight = height;
            
            // Canvas-Größe setzen (wichtig für ScrolledComposite)
            profitCanvas.setSize(width, height);
            
            // NEU: ScrolledComposite minimum size setzen
            scrolledComposite.setMinSize(width, height);
            
            // NEU: Bei großen Charts Scroll-Verhalten optimieren
            if (width > maxDisplayWidth) {
                LOGGER.info("Große Chart-Breite erkannt (" + width + "px) - aktiviere horizontales Scrolling");
                
                // Horizontales Scrolling optimieren
                scrolledComposite.getHorizontalBar().setVisible(true);
                scrolledComposite.setAlwaysShowScrollBars(true);
                
                // Scroll-Increment für große Charts anpassen
                scrolledComposite.getHorizontalBar().setIncrement(Math.max(50, width / 50));
                scrolledComposite.getHorizontalBar().setPageIncrement(Math.max(200, width / 10));
                
            } else {
                // Kleine Charts: Kein Scrolling notwendig
                scrolledComposite.getHorizontalBar().setVisible(false);
                scrolledComposite.setAlwaysShowScrollBars(false);
            }
            
            // Parent Layout aktualisieren
            getParent().layout(true);
            
            LOGGER.info("ERWEITERTE Profit-Chart Dimensionen aktualisiert: " + width + "x" + height + 
                       " (Max Display: " + maxDisplayWidth + "px, Scrolling: " + 
                       (width > maxDisplayWidth ? "AKTIV" : "INAKTIV") + ")");
        }
    }
    
    /**
     * NEU: Setzt maximale Anzeige-Breite (bevor Scrolling aktiviert wird)
     */
    public void setMaxDisplayWidth(int maxWidth) {
        this.maxDisplayWidth = maxWidth;
        LOGGER.info("Maximale Anzeige-Breite gesetzt: " + maxWidth + "px");
        
        // Aktuelles Layout neu bewerten
        if (chartWidth > maxDisplayWidth) {
            updateChartDimensions(chartWidth, chartHeight);
        }
    }
    
    /**
     * NEU: Scrollt zu einer bestimmten Position (z.B. neueste Daten)
     */
    public void scrollToPosition(double positionPercent) {
        if (scrolledComposite == null || scrolledComposite.isDisposed()) return;
        
        // Position zwischen 0.0 (links) und 1.0 (rechts)
        positionPercent = Math.max(0.0, Math.min(1.0, positionPercent));
        
        int maxScroll = Math.max(0, chartWidth - maxDisplayWidth);
        int scrollPosition = (int)(maxScroll * positionPercent);
        
        scrolledComposite.setOrigin(scrollPosition, 0);
        
        LOGGER.fine("Scrolled zu Position: " + (positionPercent * 100) + "% (Pixel: " + scrollPosition + ")");
    }
    
    /**
     * NEU: Scrollt zu den neuesten Daten (rechts)
     */
    public void scrollToLatest() {
        scrollToPosition(1.0);
    }
    
    /**
     * NEU: Scrollt zu den ältesten Daten (links)
     */
    public void scrollToEarliest() {
        scrollToPosition(0.0);
    }
    
    /**
     * ERWEITERT: Rendert den Chart mit spezifischem Zoom-Faktor
     * 
     * @param profitChart Der JFreeChart
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderChart(org.jfree.chart.JFreeChart profitChart, double zoomFactor) {
        if (imageRenderer != null && profitChart != null) {
            // Bei Zoom Chart-Dimensionen anpassen
            int zoomedWidth = (int)(chartWidth * zoomFactor);
            int zoomedHeight = (int)(chartHeight * zoomFactor);
            
            imageRenderer.renderProfitChartToImage(profitChart, zoomedWidth, zoomedHeight, 1.0);
            
            // ScrolledComposite für gezoomte Größe anpassen
            if (zoomFactor != 1.0) {
                profitCanvas.setSize(zoomedWidth, zoomedHeight);
                scrolledComposite.setMinSize(zoomedWidth, zoomedHeight);
            }
            
            if (!profitCanvas.isDisposed()) {
                profitCanvas.redraw();
            }
            
            LOGGER.info("Chart gerendert mit Zoom: " + zoomFactor + " (Größe: " + zoomedWidth + "x" + zoomedHeight + ")");
        }
    }
    
    /**
     * Setzt den Daten-Lade-Status
     * 
     * @param loaded Ob Daten geladen sind
     */
    public void setDataLoaded(boolean loaded) {
        this.isDataLoaded = loaded;
        if (!profitCanvas.isDisposed()) {
            profitCanvas.redraw();
        }
    }
    
    /**
     * Aktualisiert das Label
     * 
     * @param labelText Der neue Label-Text
     */
    public void updateLabel(String labelText) {
        if (!profitLabel.isDisposed()) {
            profitLabel.setText(labelText);
        }
    }
    
    /**
     * NEU: Gibt Chart-Scroll-Status zurück
     */
    public boolean isScrollingEnabled() {
        return chartWidth > maxDisplayWidth;
    }
    
    /**
     * NEU: Gibt aktuelle Scroll-Position zurück (0.0-1.0)
     */
    public double getScrollPosition() {
        if (!isScrollingEnabled() || scrolledComposite.isDisposed()) {
            return 0.0;
        }
        
        int currentScroll = scrolledComposite.getOrigin().x;
        int maxScroll = Math.max(1, chartWidth - maxDisplayWidth);
        
        return (double)currentScroll / maxScroll;
    }
    
    /**
     * NEU: Gibt verfügbare Scroll-Methoden zurück
     */
    public String getScrollInfo() {
        if (!isScrollingEnabled()) {
            return "Kein Scrolling erforderlich (Chart-Breite: " + chartWidth + "px <= " + maxDisplayWidth + "px)";
        }
        
        return String.format("Scrolling aktiv - Chart: %dpx, Display: %dpx, Position: %.1f%%", 
                           chartWidth, maxDisplayWidth, getScrollPosition() * 100);
    }
    
    // ========== STANDARD GETTER-METHODEN ==========
    
    /**
     * Gibt die Chart-Breite zurück
     */
    public int getChartWidth() {
        return chartWidth;
    }
    
    /**
     * Gibt die Chart-Höhe zurück
     */
    public int getChartHeight() {
        return chartHeight;
    }
    
    /**
     * Gibt das Canvas zurück
     */
    public Canvas getCanvas() {
        return profitCanvas;
    }
    
    /**
     * NEU: Gibt das ScrolledComposite zurück
     */
    public ScrolledComposite getScrolledComposite() {
        return scrolledComposite;
    }
    
    /**
     * Prüft ob das Panel bereit ist
     */
    public boolean isReady() {
        return !profitCanvas.isDisposed() && imageRenderer != null;
    }
    
    /**
     * NEU: Gibt die maximale Anzeige-Breite zurück
     */
    public int getMaxDisplayWidth() {
        return maxDisplayWidth;
    }
    
    /**
     * NEU: Gibt die aktuelle TimeScale zurück
     */
    public TimeScale getCurrentTimeScale() {
        return currentTimeScale;
    }
    
    @Override
    public void dispose() {
        LOGGER.info("ERWEITERTE ProfitDevelopmentChartPanel wird disposed für Signal: " + signalId);
        super.dispose();
    }
}