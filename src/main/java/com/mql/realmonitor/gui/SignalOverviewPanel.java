package com.mql.realmonitor.gui;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * Panel für einen einzelnen Signalprovider in der Übersicht
 * Zeigt Provider-Info, Drawdown-Chart und Profit-Chart in 3 Spalten
 * Charts sind 50% der normalen Größe, Timeframe ist fest auf M15
 */
public class SignalOverviewPanel extends Composite {
    
    private static final Logger LOGGER = Logger.getLogger(SignalOverviewPanel.class.getName());
    
    // UI Komponenten
    private Label providerInfoLabel;
    private Canvas drawdownCanvas;
    private Canvas profitCanvas;
    
    // Chart-Verwaltung
    private TickChartManager chartManager;
    private ChartImageRenderer imageRenderer;
    
    // Daten
    private final String signalId;
    private final String providerName;
    private final TickDataLoader.TickDataSet tickDataSet;
    private final int chartWidth;
    private final int chartHeight;
    private final TimeScale timeScale;
    private List<TickDataLoader.TickData> filteredTicks;
    
    // Parent GUI
    private final MqlRealMonitorGUI parentGui;
    private final Display display;
    
    // Status
    private volatile boolean isChartsRendered = false;
    
    /**
     * Konstruktor
     */
    public SignalOverviewPanel(Composite parent, MqlRealMonitorGUI parentGui,
                              String signalId, String providerName,
                              TickDataLoader.TickDataSet tickDataSet,
                              int chartWidth, int chartHeight, TimeScale timeScale) {
        super(parent, SWT.BORDER);
        
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.tickDataSet = tickDataSet;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.timeScale = timeScale;
        
        // Chart-Verwaltung initialisieren
        this.chartManager = new TickChartManager(signalId, providerName);
        this.imageRenderer = new ChartImageRenderer(display);
        
        LOGGER.info("=== OVERVIEW PANEL ERSTELLT für " + signalId + " (" + providerName + ") ===");
        LOGGER.info("Chart-Dimensionen: " + chartWidth + "x" + chartHeight + ", Timeframe: " + timeScale.getLabel());
        
        createComponents();
        loadAndRenderChartsAsync();
    }
    
    /**
     * Erstellt die UI-Komponenten
     */
    private void createComponents() {
        setLayout(new GridLayout(3, false)); // 3 Spalten: Info, Drawdown, Profit
        setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // Spalte 1: Provider-Information
        createProviderInfoSection();
        
        // Spalte 2: Drawdown-Chart
        createDrawdownChartSection();
        
        // Spalte 3: Profit-Chart
        createProfitChartSection();
        
        LOGGER.fine("UI-Komponenten erstellt für " + signalId);
    }
    
    /**
     * Erstellt die Provider-Info-Sektion (Spalte 1)
     */
    private void createProviderInfoSection() {
        Group infoGroup = new Group(this, SWT.NONE);
        infoGroup.setText("Provider-Info");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        infoGroup.setLayout(new GridLayout(1, false));
        
        providerInfoLabel = new Label(infoGroup, SWT.WRAP);
        providerInfoLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        // Provider-Informationen zusammenstellen
        StringBuilder info = new StringBuilder();
        info.append("Signal ID:\n").append(signalId).append("\n\n");
        info.append("Provider:\n").append(providerName).append("\n\n");
        info.append("Timeframe:\n").append(timeScale.getLabel()).append("\n\n");
        
        if (tickDataSet != null) {
            info.append("Ticks:\n").append(tickDataSet.getTickCount()).append("\n\n");
            if (tickDataSet.getTickCount() > 0) {
                info.append("Zeitraum:\n");
                info.append(tickDataSet.getFirstTick().getTimestamp().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy\nHH:mm:ss"))).append("\nbis\n");
                info.append(tickDataSet.getLatestTick().getTimestamp().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy\nHH:mm:ss")));
            }
        } else {
            info.append("Status:\nKeine Daten");
        }
        
        providerInfoLabel.setText(info.toString());
        
        if (parentGui.getBoldFont() != null) {
            providerInfoLabel.setFont(parentGui.getBoldFont());
        }
        
        LOGGER.fine("Provider-Info-Sektion erstellt für " + signalId);
    }
    
    /**
     * Erstellt die Drawdown-Chart-Sektion (Spalte 2)
     */
    private void createDrawdownChartSection() {
        Group drawdownGroup = new Group(this, SWT.NONE);
        drawdownGroup.setText("Equity Drawdown (%)");
        drawdownGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        drawdownGroup.setLayout(new GridLayout(1, false));
        
        drawdownCanvas = new Canvas(drawdownGroup, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData drawdownData = new GridData(SWT.FILL, SWT.FILL, true, true);
        drawdownData.widthHint = chartWidth;
        drawdownData.heightHint = chartHeight;
        drawdownCanvas.setLayoutData(drawdownData);
        drawdownCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        drawdownCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintDrawdownChart(e.gc);
            }
        });
        
        LOGGER.fine("Drawdown-Chart-Sektion erstellt für " + signalId);
    }
    
    /**
     * Erstellt die Profit-Chart-Sektion (Spalte 3)
     */
    private void createProfitChartSection() {
        Group profitGroup = new Group(this, SWT.NONE);
        profitGroup.setText("Profit-Entwicklung");
        profitGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        profitGroup.setLayout(new GridLayout(1, false));
        
        profitCanvas = new Canvas(profitGroup, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData profitData = new GridData(SWT.FILL, SWT.FILL, true, true);
        profitData.widthHint = chartWidth;
        profitData.heightHint = chartHeight;
        profitCanvas.setLayoutData(profitData);
        profitCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        profitCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintProfitChart(e.gc);
            }
        });
        
        LOGGER.fine("Profit-Chart-Sektion erstellt für " + signalId);
    }
    
    /**
     * Zeichnet den Drawdown-Chart
     */
    private void paintDrawdownChart(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(drawdownCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = drawdownCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidDrawdownImage()) {
            // Drawdown-Chart zentriert zeichnen
            Image drawdownImage = imageRenderer.getDrawdownChartImage();
            org.eclipse.swt.graphics.Rectangle imageBounds = drawdownImage.getBounds();
            
            int x = (canvasBounds.width - imageBounds.width) / 2;
            int y = (canvasBounds.height - imageBounds.height) / 2;
            
            gc.drawImage(drawdownImage, Math.max(0, x), Math.max(0, y));
            
        } else if (!isChartsRendered) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Drawdown-Chart", 10, 10, true);
            gc.drawText("wird geladen...", 10, 30, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden", 10, 10, true);
            gc.drawText("des Drawdown-Charts", 10, 30, true);
        }
    }
    
    /**
     * Zeichnet den Profit-Chart
     */
    private void paintProfitChart(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(profitCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = profitCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidProfitImage()) {
            // Profit-Chart zentriert zeichnen
            Image profitImage = imageRenderer.getProfitChartImage();
            org.eclipse.swt.graphics.Rectangle imageBounds = profitImage.getBounds();
            
            int x = (canvasBounds.width - imageBounds.width) / 2;
            int y = (canvasBounds.height - imageBounds.height) / 2;
            
            gc.drawImage(profitImage, Math.max(0, x), Math.max(0, y));
            
        } else if (!isChartsRendered) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Profit-Chart", 10, 10, true);
            gc.drawText("wird geladen...", 10, 30, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden", 10, 10, true);
            gc.drawText("des Profit-Charts", 10, 30, true);
        }
    }
    
    /**
     * Lädt und rendert Charts asynchron
     */
    private void loadAndRenderChartsAsync() {
        new Thread(() -> {
            try {
                LOGGER.info("=== LADE UND RENDERE CHARTS für " + signalId + " ===");
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    LOGGER.warning("Keine Tick-Daten für " + signalId);
                    return;
                }
                
                // Tick-Daten für Timeframe filtern
                filteredTicks = TickDataFilter.filterTicksForTimeScale(tickDataSet, timeScale);
                
                if (filteredTicks == null || filteredTicks.isEmpty()) {
                    LOGGER.warning("Keine gefilterten Ticks für " + signalId + " im Timeframe " + timeScale.getLabel());
                    filteredTicks = tickDataSet.getTicks(); // Fallback: alle Ticks verwenden
                }
                
                LOGGER.info("Gefilterte Ticks für " + signalId + ": " + filteredTicks.size() + " von " + tickDataSet.getTickCount());
                
                // Charts mit gefilterten Daten aktualisieren
                if (chartManager != null) {
                    chartManager.updateChartsWithData(filteredTicks, timeScale);
                }
                
                // Charts zu Images rendern (50% Größe)
                if (imageRenderer != null && chartManager != null) {
                    imageRenderer.renderBothChartsToImages(
                        chartManager.getDrawdownChart(),
                        chartManager.getProfitChart(),
                        chartWidth,
                        chartHeight,
                        chartHeight, // Beide Charts gleiche Höhe
                        1.0 // Zoom-Faktor 1.0 (die 50% Reduktion ist bereits in den Dimensionen berücksichtigt)
                    );
                }
                
                isChartsRendered = true;
                
                // Canvas neu zeichnen
                display.asyncExec(() -> {
                    if (!isDisposed()) {
                        if (!drawdownCanvas.isDisposed()) {
                            drawdownCanvas.redraw();
                        }
                        if (!profitCanvas.isDisposed()) {
                            profitCanvas.redraw();
                        }
                    }
                });
                
                LOGGER.info("Charts erfolgreich gerendert für " + signalId + " (50% Größe, " + timeScale.getLabel() + ")");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden/Rendern der Charts für " + signalId, e);
                
                display.asyncExec(() -> {
                    if (!isDisposed()) {
                        isChartsRendered = true; // Verhindert "lädt..." Anzeige
                        
                        if (!drawdownCanvas.isDisposed()) {
                            drawdownCanvas.redraw();
                        }
                        if (!profitCanvas.isDisposed()) {
                            profitCanvas.redraw();
                        }
                    }
                });
            }
        }, "ChartRenderer-" + signalId).start();
    }
    
    /**
     * Aktualisiert die Charts mit neuen Daten
     */
    public void updateCharts() {
        LOGGER.info("Chart-Update angefordert für " + signalId);
        isChartsRendered = false;
        
        // Canvas neu zeichnen (zeigt "lädt..." an)
        if (!drawdownCanvas.isDisposed()) {
            drawdownCanvas.redraw();
        }
        if (!profitCanvas.isDisposed()) {
            profitCanvas.redraw();
        }
        
        // Charts neu laden
        loadAndRenderChartsAsync();
    }
    
    /**
     * Prüft ob das Panel bereit ist
     */
    public boolean isReady() {
        return isChartsRendered && imageRenderer != null && 
               imageRenderer.hasValidDrawdownImage() && imageRenderer.hasValidProfitImage();
    }
    
    /**
     * Gibt die Signal-ID zurück
     */
    public String getSignalId() {
        return signalId;
    }
    
    /**
     * Gibt den Provider-Namen zurück
     */
    public String getProviderName() {
        return providerName;
    }
    
    /**
     * Gibt das Timeframe zurück
     */
    public TimeScale getTimeScale() {
        return timeScale;
    }
    
    /**
     * Gibt die Anzahl der gefilterten Ticks zurück
     */
    public int getFilteredTickCount() {
        return filteredTicks != null ? filteredTicks.size() : 0;
    }
    
    /**
     * Gibt Chart-Status zurück
     */
    public String getChartStatus() {
        if (!isChartsRendered) {
            return "Lädt...";
        } else if (isReady()) {
            return "OK (" + getFilteredTickCount() + " Ticks)";
        } else {
            return "Fehler";
        }
    }
    
    @Override
    public void dispose() {
        LOGGER.info("=== DISPOSE OVERVIEW PANEL für " + signalId + " ===");
        
        // Image-Renderer Ressourcen freigeben
        if (imageRenderer != null) {
            imageRenderer.disposeImages();
        }
        
        // Chart-Manager hat keine speziellen dispose-Methoden, aber wir setzen die Referenz auf null
        chartManager = null;
        imageRenderer = null;
        filteredTicks = null;
        
        super.dispose();
        
        LOGGER.fine("Overview Panel disposed für " + signalId);
    }
    
    @Override
    public String toString() {
        return String.format("SignalOverviewPanel{signalId='%s', providerName='%s', timeScale=%s, ready=%s}", 
                           signalId, providerName, timeScale.getLabel(), isReady());
    }
}