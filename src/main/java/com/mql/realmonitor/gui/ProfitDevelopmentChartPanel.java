package com.mql.realmonitor.gui;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * WIEDERVERWENDBARE Profit Development Chart Panel Komponente
 * Kapselt die komplette Profit-Chart-Anzeige (Canvas, Painting, Event-Handling)
 * Kann in verschiedenen Fenstern verwendet werden
 * KORRIGIERT: Bessere Beschriftung für Profit-Chart mit automatischer Legende
 */
public class ProfitDevelopmentChartPanel extends Composite {
    
    private static final Logger LOGGER = Logger.getLogger(ProfitDevelopmentChartPanel.class.getName());
    
    // UI Komponenten
    private Canvas profitCanvas;
    private Label profitLabel;
    
    // Chart-Verwaltung
    private final ChartImageRenderer imageRenderer;
    private final String signalId;
    private final String providerName;
    private final MqlRealMonitorGUI parentGui;
    
    // Chart-Dimensionen
    private int chartWidth = 800;
    private int chartHeight = 400;
    
    // Status
    private volatile boolean isDataLoaded = false;
    
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
        
        LOGGER.info("ProfitDevelopmentChartPanel erstellt für Signal: " + signalId);
    }
    
    /**
     * Erstellt die UI-Komponenten
     */
    private void createComponents() {
        setLayout(new org.eclipse.swt.layout.GridLayout(1, false));
        
        // Label für Profit-Chart (KORRIGIERT - Generischer Text mit Hinweis auf Legende)
        profitLabel = new Label(this, SWT.NONE);
        profitLabel.setText("Profit-Entwicklung Chart (siehe Legende im Chart)");
        profitLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        if (parentGui != null && parentGui.getBoldFont() != null) {
            profitLabel.setFont(parentGui.getBoldFont());
        }
        
        // Canvas für Profit-Chart
        profitCanvas = new Canvas(this, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData profitData = new GridData(SWT.FILL, SWT.FILL, true, false);
        profitData.heightHint = chartHeight;
        profitCanvas.setLayoutData(profitData);
        profitCanvas.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        
        // Paint Listener
        profitCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintProfitChart(e.gc);
            }
        });
        
        LOGGER.fine("Profit-Chart Panel Komponenten erstellt");
    }
    
    /**
     * Zeichnet den Profit-Chart
     * 
     * @param gc Der Graphics Context
     */
    private void paintProfitChart(GC gc) {
        gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(profitCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = profitCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidProfitImage()) {
            // Profit-Chart zentriert zeichnen
            org.eclipse.swt.graphics.Rectangle imagesBounds = imageRenderer.getProfitChartImage().getBounds();
            int x = (canvasBounds.width - imagesBounds.width) / 2;
            int y = (canvasBounds.height - imagesBounds.height) / 2;
            
            gc.drawImage(imageRenderer.getProfitChartImage(), Math.max(0, x), Math.max(0, y));
            
        } else if (!isDataLoaded) {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Profit-Chart wird geladen...", 20, 20, true);
            gc.drawText("Grün: Profit, Gelb: Profit + Open Equity", 20, 40, true);
        } else {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden des Profit-Charts", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
        }
    }
    
    /**
     * Aktualisiert den Profit-Chart mit neuen Daten
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
            LOGGER.info("Aktualisiere Profit-Chart Panel für Signal: " + signalId);
            
            // Profit-Chart-Manager aktualisieren
            chartManager.getProfitChartManager().updateProfitChartWithData(filteredTicks, timeScale);
            
            // Image rendern
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
            
            LOGGER.fine("Profit-Chart Panel erfolgreich aktualisiert");
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Aktualisieren des Profit-Chart Panels: " + e.getMessage());
        }
    }
    
    /**
     * Rendert den Chart mit spezifischem Zoom-Faktor
     * 
     * @param profitChart Der JFreeChart
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderChart(org.jfree.chart.JFreeChart profitChart, double zoomFactor) {
        if (imageRenderer != null && profitChart != null) {
            imageRenderer.renderProfitChartToImage(profitChart, chartWidth, chartHeight, zoomFactor);
            
            if (!profitCanvas.isDisposed()) {
                profitCanvas.redraw();
            }
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
     * Aktualisiert die Chart-Dimensionen
     * 
     * @param width Die neue Breite
     * @param height Die neue Höhe
     */
    public void updateChartDimensions(int width, int height) {
        if (width > 0 && height > 0) {
            this.chartWidth = width;
            this.chartHeight = height;
            
            // Layout-Daten aktualisieren
            GridData layoutData = (GridData) profitCanvas.getLayoutData();
            if (layoutData != null) {
                layoutData.heightHint = height;
                profitCanvas.setLayoutData(layoutData);
            }
            
            // Parent Layout aktualisieren
            getParent().layout(true);
            
            LOGGER.fine("Profit-Chart Dimensionen aktualisiert: " + width + "x" + height);
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
     * Prüft ob das Panel bereit ist
     */
    public boolean isReady() {
        return !profitCanvas.isDisposed() && imageRenderer != null;
    }
    
    @Override
    public void dispose() {
        LOGGER.fine("ProfitDevelopmentChartPanel wird disposed für Signal: " + signalId);
        super.dispose();
    }
}