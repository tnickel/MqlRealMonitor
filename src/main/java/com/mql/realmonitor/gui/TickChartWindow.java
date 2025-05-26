package com.mql.realmonitor.gui;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * Tick Chart Window mit Zeitintervall-Skalierung und Drawdown-Chart
 * Refactored - nutzt separate Helfer-Klassen für bessere Wartbarkeit
 */
public class TickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindow.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private Canvas chartCanvas;
    private Label infoLabel;
    private Text detailsText;
    private Button refreshButton;
    private Button closeButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button resetZoomButton;
    
    // Zeitintervall-Buttons
    private Button[] timeScaleButtons;
    private TimeScale currentTimeScale = TimeScale.M15; // Standard M15
    
    // Helfer-Klassen
    private TickChartManager chartManager;
    private ChartImageRenderer imageRenderer;
    
    // Daten
    private final String signalId;
    private final String providerName;
    private final SignalData signalData;
    private final String tickFilePath;
    private TickDataLoader.TickDataSet tickDataSet;
    private List<TickDataLoader.TickData> filteredTicks;
    
    // Parent GUI für Callbacks
    private final MqlRealMonitorGUI parentGui;
    private final Display display;
    
    // Chart-Dimensionen
    private int chartWidth = 800;
    private int mainChartHeight = 300;      // 60% der Gesamthöhe
    private int drawdownChartHeight = 200;  // 40% der Gesamthöhe
    private double zoomFactor = 1.0;
    
    // Status-Flags
    private volatile boolean isDataLoaded = false;
    private volatile boolean isWindowClosed = false;
    
    /**
     * Konstruktor
     */
    public TickChartWindow(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                          String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        // Helfer-Klassen initialisieren
        this.chartManager = new TickChartManager(signalId, providerName);
        this.imageRenderer = new ChartImageRenderer(display);
        
        LOGGER.info("Erstelle TickChartWindow (Refactored) für Signal: " + signalId + " (" + providerName + ")");
        
        createWindow(parent);
        loadDataAsync();
    }
    
    /**
     * Erstellt das Hauptfenster
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Tick Chart - " + signalId + " (" + providerName + ")");
        shell.setSize(1000, 900);
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        createInfoPanel();
        createTimeScalePanel();
        createChartCanvas();
        createButtonPanel();
        setupEventHandlers();
        
        LOGGER.info("TickChartWindow UI erstellt für Signal: " + signalId);
    }
    
    /**
     * Zentriert das Fenster relativ zum Parent
     */
    private void centerWindow(Shell parent) {
        Point parentSize = parent.getSize();
        Point parentLocation = parent.getLocation();
        Point size = shell.getSize();
        
        int x = parentLocation.x + (parentSize.x - size.x) / 2;
        int y = parentLocation.y + (parentSize.y - size.y) / 2;
        
        shell.setLocation(x, y);
    }
    
    /**
     * Erstellt das Info-Panel
     */
    private void createInfoPanel() {
        Group infoGroup = new Group(shell, SWT.NONE);
        infoGroup.setText("Signal Information");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
        infoLabel = new Label(infoGroup, SWT.WRAP);
        GridData infoLabelData = new GridData(SWT.FILL, SWT.FILL, true, false);
        infoLabelData.horizontalSpan = 2;
        infoLabel.setLayoutData(infoLabelData);
        
        detailsText = new Text(infoGroup, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.horizontalSpan = 2;
        detailsData.heightHint = 80;
        detailsText.setLayoutData(detailsData);
        
        updateInfoPanelInitial();
    }
    
    /**
     * Erstellt das Zeitintervall-Panel mit Buttons
     */
    private void createTimeScalePanel() {
        Group timeScaleGroup = new Group(shell, SWT.NONE);
        timeScaleGroup.setText("Zeitintervall");
        timeScaleGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        timeScaleGroup.setLayout(new GridLayout(TimeScale.values().length, false));
        
        timeScaleButtons = new Button[TimeScale.values().length];
        
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            
            Button button = new Button(timeScaleGroup, SWT.TOGGLE);
            button.setText(scale.getLabel());
            button.setToolTipText(scale.getToolTipText());
            button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            if (scale == currentTimeScale) {
                button.setSelection(true);
            }
            
            final TimeScale selectedScale = scale;
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    changeTimeScale(selectedScale);
                }
            });
            
            timeScaleButtons[i] = button;
        }
        
        LOGGER.info("Zeitintervall-Panel erstellt mit " + TimeScale.values().length + " Optionen");
    }
    
    /**
     * Chart-Canvas erstellen
     */
    private void createChartCanvas() {
        Group chartGroup = new Group(shell, SWT.NONE);
        chartGroup.setText("Tick Charts (Haupt-Chart + Equity Drawdown) - " + currentTimeScale.getLabel());
        chartGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartGroup.setLayout(new GridLayout(1, false));
        
        chartCanvas = new Canvas(chartGroup, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        chartCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        chartCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintBothCharts(e.gc);
            }
        });
        
        chartCanvas.addListener(SWT.Resize, event -> {
            Point size = chartCanvas.getSize();
            if (size.x > 0 && size.y > 0) {
                chartWidth = size.x;
                int totalHeight = size.y;
                mainChartHeight = (int) (totalHeight * 0.6);
                drawdownChartHeight = (int) (totalHeight * 0.4);
                
                renderBothChartsToImages();
            }
        });
        
        LOGGER.info("Chart-Canvas für beide Charts erstellt");
    }
    
    /**
     * Erstellt das Button-Panel
     */
    private void createButtonPanel() {
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonComposite.setLayout(new GridLayout(6, false));
        
        refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        zoomInButton = new Button(buttonComposite, SWT.PUSH);
        zoomInButton.setText("Zoom +");
        zoomInButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        zoomOutButton = new Button(buttonComposite, SWT.PUSH);
        zoomOutButton.setText("Zoom -");
        zoomOutButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        resetZoomButton = new Button(buttonComposite, SWT.PUSH);
        resetZoomButton.setText("Reset Zoom");
        resetZoomButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        Label spacer = new Label(buttonComposite, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        closeButton = new Button(buttonComposite, SWT.PUSH);
        closeButton.setText("Schließen");
        closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    }
    
    /**
     * Setup Event Handler
     */
    private void setupEventHandlers() {
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshData();
            }
        });
        
        zoomInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor *= 1.2;
                renderBothChartsToImages();
                LOGGER.info("Zoom In: " + zoomFactor);
            }
        });
        
        zoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor /= 1.2;
                renderBothChartsToImages();
                LOGGER.info("Zoom Out: " + zoomFactor);
            }
        });
        
        resetZoomButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor = 1.0;
                renderBothChartsToImages();
                LOGGER.info("Zoom Reset");
            }
        });
        
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                closeWindow();
            }
        });
        
        shell.addListener(SWT.Close, event -> {
            closeWindow();
        });
    }
    
    /**
     * Wechselt das Zeitintervall und aktualisiert beide Charts
     */
    private void changeTimeScale(TimeScale newScale) {
        if (newScale == currentTimeScale) {
            return;
        }
        
        LOGGER.info("Wechsle Zeitintervall von " + currentTimeScale.getLabel() + " zu " + newScale.getLabel());
        
        // Buttons aktualisieren
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            timeScaleButtons[i].setSelection(scale == newScale);
        }
        
        currentTimeScale = newScale;
        
        // Daten neu filtern und Charts aktualisieren
        if (tickDataSet != null) {
            updateChartsWithCurrentData();
        }
    }
    
    /**
     * Aktualisiert Charts mit aktuellen Daten
     */
    private void updateChartsWithCurrentData() {
        filteredTicks = TickDataFilter.filterTicksForTimeScale(tickDataSet, currentTimeScale);
        chartManager.updateChartsWithData(filteredTicks, currentTimeScale);
        renderBothChartsToImages();
        updateInfoPanel();
    }
    
    /**
     * Rendert beide Charts als Images
     */
    private void renderBothChartsToImages() {
        imageRenderer.renderBothChartsToImages(
            chartManager.getMainChart(),
            chartManager.getDrawdownChart(),
            chartWidth,
            mainChartHeight,
            drawdownChartHeight,
            zoomFactor
        );
        
        if (!chartCanvas.isDisposed()) {
            chartCanvas.redraw();
        }
    }
    
    /**
     * Lädt Daten asynchron
     */
    private void loadDataAsync() {
        new Thread(() -> {
            try {
                LOGGER.info("Lade Tick-Daten für beide Charts - Signal: " + signalId + " von " + tickFilePath);
                
                tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
                isDataLoaded = true;
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    LOGGER.warning("Keine Tick-Daten gefunden für Signal: " + signalId);
                    
                    display.asyncExec(() -> {
                        if (!isWindowClosed && !shell.isDisposed()) {
                            showNoDataMessage();
                        }
                    });
                    return;
                }
                
                LOGGER.info("Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks für beide Charts - Signal: " + signalId);
                
                // UI-Updates im SWT Thread
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateChartsWithCurrentData();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden der Tick-Daten für beide Charts - Signal: " + signalId, e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "TickDataLoader-Refactored-" + signalId).start();
    }
    
    /**
     * Zeichnet beide Charts untereinander auf dem Canvas
     */
    private void paintBothCharts(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(chartCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = chartCanvas.getBounds();
        
        if (imageRenderer.hasValidImages()) {
            // Haupt-Chart oben zeichnen
            org.eclipse.swt.graphics.Rectangle mainImageBounds = imageRenderer.getMainChartImage().getBounds();
            int mainX = (canvasBounds.width - mainImageBounds.width) / 2;
            int mainY = 5;
            
            gc.drawImage(imageRenderer.getMainChartImage(), Math.max(0, mainX), mainY);
            
            // Trennlinie zwischen den Charts
            int separatorY = mainY + mainImageBounds.height + 5;
            gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
            gc.drawLine(10, separatorY, canvasBounds.width - 10, separatorY);
            
            // Drawdown-Chart unten zeichnen
            org.eclipse.swt.graphics.Rectangle drawdownImageBounds = imageRenderer.getDrawdownChartImage().getBounds();
            int drawdownX = (canvasBounds.width - drawdownImageBounds.width) / 2;
            int drawdownY = mainY + mainImageBounds.height + 15;
            
            gc.drawImage(imageRenderer.getDrawdownChartImage(), Math.max(0, drawdownX), drawdownY);
            
        } else if (!isDataLoaded) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Charts werden geladen...", 20, 20, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("Fehler beim Laden der Charts", 20, 20, true);
        }
    }
    
    /**
     * Zeigt initialen Info-Text
     */
    private void updateInfoPanelInitial() {
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Lädt...";
        infoLabel.setText(info);
        detailsText.setText("Tick-Daten werden geladen...\nBitte warten Sie einen Moment.");
    }
    
    /**
     * Aktualisiert das Info-Panel
     */
    private void updateInfoPanel() {
        StringBuilder info = new StringBuilder();
        
        info.append("Signal ID: ").append(signalId).append("   ");
        info.append("Provider: ").append(providerName).append("   ");
        info.append("Zeitintervall: ").append(currentTimeScale.getLabel()).append("   ");
        
        if (signalData != null) {
            info.append("Aktuell: ").append(signalData.getFormattedTotalValue())
                .append(" ").append(signalData.getCurrency()).append("   ");
            info.append("Stand: ").append(signalData.getFormattedTimestamp());
        }
        
        infoLabel.setText(info.toString());
        
        StringBuilder details = new StringBuilder();
        
        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
            details.append("=== Tick-Daten Statistik (Refactored) ===\n");
            details.append("Zeitintervall: ").append(currentTimeScale.getLabel())
                   .append(" (letzte ").append(currentTimeScale.getDisplayMinutes()).append(" Minuten)\n");
            details.append("Charts: Haupt-Chart (Equity/Floating/Total) + Drawdown-Chart (%)\n");
            details.append("Datei: ").append(tickFilePath).append("\n");
            details.append("Gesamt Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            
            if (filteredTicks != null) {
                details.append("Angezeigte Ticks: ").append(filteredTicks.size()).append("\n");
                
                if (!filteredTicks.isEmpty()) {
                    details.append("Angezeigter Zeitraum: ").append(filteredTicks.get(0).getTimestamp())
                           .append(" bis ").append(filteredTicks.get(filteredTicks.size() - 1).getTimestamp()).append("\n");
                    
                    // Drawdown-Statistiken
                    TickDataFilter.DrawdownStatistics stats = TickDataFilter.calculateDrawdownStatistics(filteredTicks);
                    if (stats.hasData()) {
                        details.append("\n=== Drawdown Statistik ===\n");
                        details.append("Min. Drawdown: ").append(stats.getFormattedMinDrawdown()).append("\n");
                        details.append("Max. Drawdown: ").append(stats.getFormattedMaxDrawdown()).append("\n");
                        details.append("Durchschn. Drawdown: ").append(stats.getFormattedAvgDrawdown()).append("\n");
                    }
                }
            }
            
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
        } else {
            details.append("Keine Tick-Daten verfügbar oder noch nicht geladen.\n");
        }
        
        detailsText.setText(details.toString());
        
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Tick Chart - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel());
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten verfügbar");
        detailsText.setText("Keine Tick-Daten gefunden in: " + tickFilePath);
        chartCanvas.redraw();
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        infoLabel.setText("Signal ID: " + signalId + " - Fehler beim Laden");
        detailsText.setText("FEHLER: " + message);
        
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Laden der Tick-Daten");
        errorBox.setMessage(message);
        errorBox.open();
    }
    
    /**
     * Aktualisiert die Daten
     */
    private void refreshData() {
        LOGGER.info("Manueller Refresh für beide Charts - Signal: " + signalId);
        
        refreshButton.setEnabled(false);
        refreshButton.setText("Lädt...");
        
        isDataLoaded = false;
        chartCanvas.redraw();
        
        loadDataAsync();
        
        display.timerExec(3000, () -> {
            if (!isWindowClosed && !refreshButton.isDisposed()) {
                refreshButton.setEnabled(true);
                refreshButton.setText("Aktualisieren");
            }
        });
    }
    
    /**
     * Schließt das Fenster sauber
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        LOGGER.info("Schließe TickChartWindow (Refactored) für Signal: " + signalId);
        
        // Ressourcen freigeben
        imageRenderer.disposeImages();
        
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Zeigt das Fenster an
     */
    public void open() {
        shell.open();
        LOGGER.info("TickChartWindow (Refactored) geöffnet für Signal: " + signalId);
    }
    
    /**
     * Prüft ob das Fenster noch geöffnet ist
     */
    public boolean isOpen() {
        return shell != null && !shell.isDisposed() && !isWindowClosed;
    }
    
    /**
     * Gibt die Signal-ID zurück
     */
    public String getSignalId() {
        return signalId;
    }
}