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
 * WIEDERVERWENDBARE Equity Drawdown Chart Panel Komponente
 * Kapselt die komplette Drawdown-Chart-Anzeige (Canvas, Painting, Event-Handling)
 * Kann in verschiedenen Fenstern verwendet werden
 */
public class EquityDrawdownChartPanel extends Composite {
    
    private static final Logger LOGGER = Logger.getLogger(EquityDrawdownChartPanel.class.getName());
    
    // UI Komponenten
    private Canvas drawdownCanvas;
    private Label drawdownLabel;
    
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
    public EquityDrawdownChartPanel(Composite parent, MqlRealMonitorGUI parentGui, 
                                   String signalId, String providerName, 
                                   ChartImageRenderer imageRenderer) {
        super(parent, SWT.NONE);
        
        this.parentGui = parentGui;
        this.signalId = signalId;
        this.providerName = providerName;
        this.imageRenderer = imageRenderer;
        
        createComponents();
        
        LOGGER.info("EquityDrawdownChartPanel erstellt für Signal: " + signalId);
    }
    
    /**
     * Erstellt die UI-Komponenten
     */
    private void createComponents() {
        setLayout(new org.eclipse.swt.layout.GridLayout(1, false));
        
        // Label für Drawdown-Chart
        drawdownLabel = new Label(this, SWT.NONE);
        drawdownLabel.setText("Equity Drawdown Chart (%)");
        drawdownLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        if (parentGui != null && parentGui.getBoldFont() != null) {
            drawdownLabel.setFont(parentGui.getBoldFont());
        }
        
        // Canvas für Drawdown-Chart
        drawdownCanvas = new Canvas(this, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData drawdownData = new GridData(SWT.FILL, SWT.FILL, true, false);
        drawdownData.heightHint = chartHeight;
        drawdownCanvas.setLayoutData(drawdownData);
        drawdownCanvas.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        
        // Paint Listener
        drawdownCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintDrawdownChart(e.gc);
            }
        });
        
        LOGGER.fine("Drawdown-Chart Panel Komponenten erstellt");
    }
    
    /**
     * Zeichnet den Drawdown-Chart
     * 
     * @param gc Der Graphics Context
     */
    private void paintDrawdownChart(GC gc) {
        gc.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(drawdownCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = drawdownCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidDrawdownImage()) {
            // Drawdown-Chart zentriert zeichnen
            org.eclipse.swt.graphics.Rectangle imagesBounds = imageRenderer.getDrawdownChartImage().getBounds();
            int x = (canvasBounds.width - imagesBounds.width) / 2;
            int y = (canvasBounds.height - imagesBounds.height) / 2;
            
            gc.drawImage(imageRenderer.getDrawdownChartImage(), Math.max(0, x), Math.max(0, y));
            
        } else if (!isDataLoaded) {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Drawdown-Chart wird geladen...", 20, 20, true);
            gc.drawText("Signal: " + signalId + " (" + providerName + ")", 20, 40, true);
        } else {
            gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden des Drawdown-Charts", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
        }
    }
    
    /**
     * Aktualisiert den Drawdown-Chart mit neuen Daten
     * 
     * @param filteredTicks Die gefilterten Tick-Daten
     * @param timeScale Die Zeitskala
     * @param chartManager Der Chart-Manager
     */
    public void updateChart(List<TickDataLoader.TickData> filteredTicks, TimeScale timeScale, 
                           TickChartManager chartManager) {
        if (chartManager == null) {
            LOGGER.warning("ChartManager ist null - kann Drawdown-Chart nicht aktualisieren");
            return;
        }
        
        try {
            LOGGER.info("Aktualisiere Drawdown-Chart Panel für Signal: " + signalId);
            
            // Chart-Manager aktualisieren (nur Drawdown)
            chartManager.updateDrawdownChartWithData(filteredTicks, timeScale);
            
            // Image rendern
            if (imageRenderer != null) {
                imageRenderer.renderDrawdownChartToImage(
                    chartManager.getDrawdownChart(),
                    chartWidth,
                    chartHeight,
                    1.0 // Standard Zoom
                );
            }
            
            // Canvas neu zeichnen
            if (!drawdownCanvas.isDisposed()) {
                drawdownCanvas.redraw();
            }
            
            LOGGER.fine("Drawdown-Chart Panel erfolgreich aktualisiert");
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Aktualisieren des Drawdown-Chart Panels: " + e.getMessage());
        }
    }
    
    /**
     * Rendert den Chart mit spezifischem Zoom-Faktor
     * 
     * @param drawdownChart Der JFreeChart
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderChart(org.jfree.chart.JFreeChart drawdownChart, double zoomFactor) {
        if (imageRenderer != null && drawdownChart != null) {
            imageRenderer.renderDrawdownChartToImage(drawdownChart, chartWidth, chartHeight, zoomFactor);
            
            if (!drawdownCanvas.isDisposed()) {
                drawdownCanvas.redraw();
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
        if (!drawdownCanvas.isDisposed()) {
            drawdownCanvas.redraw();
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
            GridData layoutData = (GridData) drawdownCanvas.getLayoutData();
            if (layoutData != null) {
                layoutData.heightHint = height;
                drawdownCanvas.setLayoutData(layoutData);
            }
            
            // Parent Layout aktualisieren
            getParent().layout(true);
            
            LOGGER.fine("Drawdown-Chart Dimensionen aktualisiert: " + width + "x" + height);
        }
    }
    
    /**
     * Aktualisiert das Label
     * 
     * @param labelText Der neue Label-Text
     */
    public void updateLabel(String labelText) {
        if (!drawdownLabel.isDisposed()) {
            drawdownLabel.setText(labelText);
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
        return drawdownCanvas;
    }
    
    /**
     * Prüft ob das Panel bereit ist
     */
    public boolean isReady() {
        return !drawdownCanvas.isDisposed() && imageRenderer != null;
    }
    
    @Override
    public void dispose() {
        LOGGER.fine("EquityDrawdownChartPanel wird disposed für Signal: " + signalId);
        super.dispose();
    }
}