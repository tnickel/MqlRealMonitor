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
 * VERBESSERT: Tick Chart Window mit verbesserter Diagnostik und Fehlerbehandlung
 * ALLE PROBLEME BEHOBEN: Umfassende Logging und robuste Chart-Darstellung
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
    private Button diagnosticButton; // NEU: Diagnostik-Button
    
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
    
    // Chart-Dimensionen - VERBESSERT: Drawdown Chart Höhe um 30% erhöht
    private int chartWidth = 800;
    private int mainChartHeight = 300;      // 55% der Gesamthöhe (reduziert)
    private int drawdownChartHeight = 260;  // GEÄNDERT: von 200 auf 260 (+30%)
    private double zoomFactor = 1.0;
    
    // Status-Flags
    private volatile boolean isDataLoaded = false;
    private volatile boolean isWindowClosed = false;
    
    // NEU: Diagnose-Counter
    private int refreshCounter = 0;
    
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
        
        LOGGER.info("=== TICK CHART WINDOW ERSTELLT (VERBESSERT) ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        LOGGER.info("Tick-Datei: " + tickFilePath);
        LOGGER.info("SignalData: " + (signalData != null ? signalData.getSummary() : "NULL"));
        
        createWindow(parent);
        loadDataAsync();
    }
    
    /**
     * Erstellt das Hauptfenster
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Tick Chart - " + signalId + " (" + providerName + ") [DIAGNOSEMODUS]");
        shell.setSize(1000, 950); // GEÄNDERT: Fenster um 50px höher für bessere Drawdown-Chart Darstellung
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        createInfoPanel();
        createTimeScalePanel();
        createChartCanvas();
        createButtonPanel(); // Enthält jetzt auch Diagnostik-Button
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
        infoGroup.setText("Signal Information (DIAGNOSEMODUS)");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
        infoLabel = new Label(infoGroup, SWT.WRAP);
        GridData infoLabelData = new GridData(SWT.FILL, SWT.FILL, true, false);
        infoLabelData.horizontalSpan = 2;
        infoLabel.setLayoutData(infoLabelData);
        
        detailsText = new Text(infoGroup, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.horizontalSpan = 2;
        detailsData.heightHint = 100; // Erhöht für mehr Diagnose-Info
        detailsText.setLayoutData(detailsData);
        
        updateInfoPanelInitial();
    }
    
    /**
     * Erstellt das Zeitintervall-Panel mit Buttons
     */
    private void createTimeScalePanel() {
        Group timeScaleGroup = new Group(shell, SWT.NONE);
        timeScaleGroup.setText("Zeitintervall (mit Fallback-Strategien)");
        timeScaleGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        timeScaleGroup.setLayout(new GridLayout(TimeScale.values().length, false));
        
        timeScaleButtons = new Button[TimeScale.values().length];
        
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            
            Button button = new Button(timeScaleGroup, SWT.TOGGLE);
            button.setText(scale.getLabel());
            button.setToolTipText("Zeige letzte " + scale.getDisplayMinutes() + " Minuten");
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
        chartGroup.setText("Tick Charts (DIAGNOSEMODUS) - " + currentTimeScale.getLabel());
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
                // VERBESSERT: Neue Verteilung - Drawdown Chart bekommt mehr Platz
                mainChartHeight = (int) (totalHeight * 0.55);      // 55% (reduziert von 60%)
                drawdownChartHeight = (int) (totalHeight * 0.45);  // 45% (erhöht von 40%)
                
                LOGGER.info("Canvas Resize: " + chartWidth + "x" + totalHeight + 
                           " -> Main:" + mainChartHeight + ", Drawdown:" + drawdownChartHeight);
                
                renderBothChartsToImages();
            }
        });
        
        LOGGER.info("Chart-Canvas für beide Charts erstellt");
    }
    
    /**
     * ERWEITERT: Erstellt das Button-Panel (jetzt mit Diagnostik-Button)
     */
    private void createButtonPanel() {
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonComposite.setLayout(new GridLayout(7, false)); // 7 statt 6 Buttons
        
        refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        // NEU: Diagnostik-Button
        diagnosticButton = new Button(buttonComposite, SWT.PUSH);
        diagnosticButton.setText("Diagnostik");
        diagnosticButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        diagnosticButton.setToolTipText("Zeigt detaillierte Diagnostik-Informationen");
        
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
     * ERWEITERT: Setup Event Handler (jetzt mit Diagnostik-Button)
     */
    private void setupEventHandlers() {
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshData();
            }
        });
        
        // NEU: Diagnostik-Button Handler
        diagnosticButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDiagnosticReport();
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
     * NEU: Zeigt einen detaillierten Diagnostik-Bericht
     */
    private void showDiagnosticReport() {
        LOGGER.info("=== DIAGNOSTIK-BERICHT ANGEFORDERT für Signal: " + signalId + " ===");
        
        StringBuilder report = new StringBuilder();
        report.append("=== DIAGNOSTIK-BERICHT für Signal ").append(signalId).append(" ===\n\n");
        
        // Grunddaten
        report.append("GRUNDDATEN:\n");
        report.append("Signal ID: ").append(signalId).append("\n");
        report.append("Provider Name: ").append(providerName).append("\n");
        report.append("Tick-Datei: ").append(tickFilePath).append("\n");
        report.append("Aktuelle Zeitskala: ").append(currentTimeScale.getLabel()).append("\n");
        report.append("Refresh Counter: ").append(refreshCounter).append("\n");
        report.append("Data Loaded: ").append(isDataLoaded).append("\n");
        report.append("Zoom Factor: ").append(zoomFactor).append("\n\n");
        
        // SignalData Info
        report.append("SIGNALDATA:\n");
        if (signalData != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Details: ").append(signalData.getSummary()).append("\n");
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // TickDataSet Info
        report.append("TICK DATASET:\n");
        if (tickDataSet != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            if (tickDataSet.getTickCount() > 0) {
                report.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                       .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            }
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // Gefilterte Daten
        report.append("GEFILTERTE DATEN:\n");
        if (filteredTicks != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Anzahl: ").append(filteredTicks.size()).append("\n");
            
            if (!filteredTicks.isEmpty()) {
                report.append("Filter-Report:\n");
                if (tickDataSet != null) {
                    report.append(TickDataFilter.createFilterReport(tickDataSet, currentTimeScale));
                }
            }
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // Chart Status
        report.append("CHART STATUS:\n");
        report.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
        report.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
        report.append("HasValidImages: ").append(imageRenderer != null ? imageRenderer.hasValidImages() : "N/A").append("\n");
        report.append("Canvas Größe: ").append(chartWidth).append("x").append(mainChartHeight + drawdownChartHeight).append("\n");
        report.append("Main Chart Höhe: ").append(mainChartHeight).append("\n");
        report.append("Drawdown Chart Höhe: ").append(drawdownChartHeight).append("\n");
        
        // Zeige Bericht in einem neuen Dialog
        Shell diagnosticShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.MODELESS | SWT.RESIZE);
        diagnosticShell.setText("Diagnostik-Bericht - " + signalId);
        diagnosticShell.setSize(600, 500);
        diagnosticShell.setLayout(new GridLayout(1, false));
        
        Text reportText = new Text(diagnosticShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        reportText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        reportText.setText(report.toString());
        reportText.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        Button closeReportButton = new Button(diagnosticShell, SWT.PUSH);
        closeReportButton.setText("Schließen");
        closeReportButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        closeReportButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                diagnosticShell.close();
            }
        });
        
        // Zentriere Dialog
        Point parentLocation = shell.getLocation();
        Point parentSize = shell.getSize();
        Point dialogSize = diagnosticShell.getSize();
        
        int x = parentLocation.x + (parentSize.x - dialogSize.x) / 2;
        int y = parentLocation.y + (parentSize.y - dialogSize.y) / 2;
        diagnosticShell.setLocation(x, y);
        
        diagnosticShell.open();
        
        LOGGER.info("Diagnostik-Bericht angezeigt");
    }
    
    /**
     * Wechselt das Zeitintervall und aktualisiert beide Charts
     */
    private void changeTimeScale(TimeScale newScale) {
        if (newScale == currentTimeScale) {
            return;
        }
        
        LOGGER.info("=== ZEITINTERVALL-WECHSEL ===");
        LOGGER.info("Von: " + currentTimeScale.getLabel() + " -> Zu: " + newScale.getLabel());
        
        // Buttons aktualisieren
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            timeScaleButtons[i].setSelection(scale == newScale);
        }
        
        currentTimeScale = newScale;
        
        // Daten neu filtern und Charts aktualisieren
        if (tickDataSet != null) {
            updateChartsWithCurrentData();
        } else {
            LOGGER.warning("Kann Zeitintervall nicht wechseln - tickDataSet ist NULL");
        }
    }
    
    /**
     * VERBESSERT: Aktualisiert Charts mit aktuellen Daten
     */
    private void updateChartsWithCurrentData() {
        LOGGER.info("=== UPDATE CHARTS MIT AKTUELLEN DATEN ===");
        LOGGER.info("TickDataSet: " + (tickDataSet != null ? tickDataSet.getTickCount() + " Ticks" : "NULL"));
        LOGGER.info("Aktuelle Zeitskala: " + currentTimeScale.getLabel());
        
        if (tickDataSet == null) {
            LOGGER.warning("FEHLER: tickDataSet ist NULL - kann Charts nicht aktualisieren");
            return;
        }
        
        try {
            // Daten filtern
            filteredTicks = TickDataFilter.filterTicksForTimeScale(tickDataSet, currentTimeScale);
            LOGGER.info("Gefilterte Daten: " + (filteredTicks != null ? filteredTicks.size() + " Ticks" : "NULL"));
            
            if (filteredTicks == null || filteredTicks.isEmpty()) {
                LOGGER.warning("WARNUNG: Keine gefilterten Daten für Chart-Update");
                // Fallback: Verwende alle verfügbaren Daten
                filteredTicks = tickDataSet.getTicks();
                LOGGER.info("FALLBACK: Verwende alle " + filteredTicks.size() + " verfügbaren Ticks");
            }
            
            // Charts aktualisieren
            chartManager.updateChartsWithData(filteredTicks, currentTimeScale);
            
            // Images rendern
            renderBothChartsToImages();
            
            // Info-Panel aktualisieren
            updateInfoPanel();
            
            LOGGER.info("Charts erfolgreich aktualisiert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FEHLER beim Aktualisieren der Charts", e);
        }
    }
    
    /**
     * Rendert beide Charts als Images
     */
    private void renderBothChartsToImages() {
        if (chartManager == null || imageRenderer == null) {
            LOGGER.warning("Kann Charts nicht rendern - Manager oder Renderer ist NULL");
            return;
        }
        
        try {
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
            
            LOGGER.fine("Charts erfolgreich gerendert");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern der Charts", e);
        }
    }
    
    /**
     * VERBESSERT: Lädt Daten asynchron mit umfassender Diagnostik
     */
    private void loadDataAsync() {
        new Thread(() -> {
            try {
                LOGGER.info("=== ASYNCHRONER DATEN-LOAD START für Signal: " + signalId + " ===");
                LOGGER.info("Tick-Datei-Pfad: " + tickFilePath);
                
                // Prüfe Datei-Existenz
                java.io.File tickFile = new java.io.File(tickFilePath);
                if (!tickFile.exists()) {
                    LOGGER.severe("KRITISCHER FEHLER: Tick-Datei existiert nicht: " + tickFilePath);
                    
                    display.asyncExec(() -> {
                        if (!isWindowClosed && !shell.isDisposed()) {
                            showErrorMessage("Tick-Datei nicht gefunden: " + tickFilePath);
                        }
                    });
                    return;
                }
                
                LOGGER.info("Tick-Datei gefunden, Größe: " + tickFile.length() + " Bytes");
                
                // Tick-Daten laden
                tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    LOGGER.warning("WARNUNG: Keine Tick-Daten geladen für Signal: " + signalId);
                    
                    display.asyncExec(() -> {
                        if (!isWindowClosed && !shell.isDisposed()) {
                            showNoDataMessage();
                        }
                    });
                    return;
                }
                
                isDataLoaded = true;
                LOGGER.info("ERFOLG: Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks für Signal: " + signalId);
                LOGGER.info("Zeitraum: " + tickDataSet.getFirstTick().getTimestamp() + 
                           " bis " + tickDataSet.getLatestTick().getTimestamp());
                
                // UI-Updates im SWT Thread
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateChartsWithCurrentData();
                    }
                });
                
                LOGGER.info("=== ASYNCHRONER DATEN-LOAD ENDE ===");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER beim Laden der Tick-Daten für Signal: " + signalId, e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Schwerwiegender Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "TickDataLoader-Improved-" + signalId).start();
    }
    
    /**
     * Zeichnet beide Charts untereinander auf dem Canvas
     */
    private void paintBothCharts(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(chartCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = chartCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidImages()) {
            // Haupt-Chart oben zeichnen
            org.eclipse.swt.graphics.Rectangle mainImageBounds = imageRenderer.getMainChartImage().getBounds();
            int mainX = (canvasBounds.width - mainImageBounds.width) / 2;
            int mainY = 5;
            
            gc.drawImage(imageRenderer.getMainChartImage(), Math.max(0, mainX), mainY);
            
            // Trennlinie zwischen den Charts
            int separatorY = mainY + mainImageBounds.height + 5;
            gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
            gc.drawLine(10, separatorY, canvasBounds.width - 10, separatorY);
            
            // Drawdown-Chart unten zeichnen (jetzt mit mehr Platz)
            org.eclipse.swt.graphics.Rectangle drawdownImageBounds = imageRenderer.getDrawdownChartImage().getBounds();
            int drawdownX = (canvasBounds.width - drawdownImageBounds.width) / 2;
            int drawdownY = mainY + mainImageBounds.height + 15;
            
            gc.drawImage(imageRenderer.getDrawdownChartImage(), Math.max(0, drawdownX), drawdownY);
            
        } else if (!isDataLoaded) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Charts werden geladen... (DIAGNOSEMODUS)", 20, 20, true);
            gc.drawText("Signal: " + signalId + " (" + providerName + ")", 20, 40, true);
            gc.drawText("Refresh Counter: " + refreshCounter, 20, 60, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden der Charts - siehe Diagnostik", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
            gc.drawText("Tick-Datei: " + tickFilePath, 20, 60, true);
        }
    }
    
    /**
     * Zeigt initialen Info-Text
     */
    private void updateInfoPanelInitial() {
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Lädt... [DIAGNOSEMODUS]";
        infoLabel.setText(info);
        detailsText.setText("=== DIAGNOSEMODUS AKTIV ===\n" +
                           "Tick-Daten werden geladen...\n" +
                           "Tick-Datei: " + tickFilePath + "\n" +
                           "Alle Chart-Updates werden detailliert geloggt.\n" +
                           "Bitte warten Sie einen Moment.");
    }
    
    /**
     * VERBESSERT: Aktualisiert das Info-Panel mit umfassenden Informationen
     */
    private void updateInfoPanel() {
        StringBuilder info = new StringBuilder();
        
        info.append("Signal ID: ").append(signalId).append("   ");
        info.append("Provider: ").append(providerName).append("   ");
        info.append("Zeitintervall: ").append(currentTimeScale.getLabel()).append("   ");
        info.append("Refresh: #").append(refreshCounter).append("   ");
        
        if (signalData != null) {
            info.append("Aktuell: ").append(signalData.getFormattedTotalValue())
                .append(" ").append(signalData.getCurrency()).append("   ");
            info.append("Stand: ").append(signalData.getFormattedTimestamp());
        }
        
        infoLabel.setText(info.toString());
        
        StringBuilder details = new StringBuilder();
        
        details.append("=== DIAGNOSEMODUS - DETAILLIERTE INFORMATIONEN ===\n");
        details.append("Zeitintervall: ").append(currentTimeScale.getLabel())
               .append(" (letzte ").append(currentTimeScale.getDisplayMinutes()).append(" Minuten)\n");
        details.append("Charts: Haupt-Chart + Drawdown-Chart (robuste Auto-Skalierung)\n");
        details.append("Tick-Datei: ").append(tickFilePath).append("\n");
        
        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
            details.append("Gesamt Ticks in Datei: ").append(tickDataSet.getTickCount()).append("\n");
            
            if (filteredTicks != null) {
                details.append("Angezeigte Ticks (gefiltert): ").append(filteredTicks.size()).append("\n");
                
                if (!filteredTicks.isEmpty()) {
                    details.append("Angezeigter Zeitraum: ").append(filteredTicks.get(0).getTimestamp())
                           .append(" bis ").append(filteredTicks.get(filteredTicks.size() - 1).getTimestamp()).append("\n");
                    
                    // Drawdown-Statistiken
                    TickDataFilter.DrawdownStatistics stats = TickDataFilter.calculateDrawdownStatistics(filteredTicks);
                    if (stats.hasData()) {
                        details.append("\n=== DRAWDOWN STATISTIK (DIAGNOSE) ===\n");
                        details.append("Min. Drawdown: ").append(stats.getFormattedMinDrawdown()).append("\n");
                        details.append("Max. Drawdown: ").append(stats.getFormattedMaxDrawdown()).append("\n");
                        details.append("Durchschn. Drawdown: ").append(stats.getFormattedAvgDrawdown()).append("\n");
                        
                        // Zusätzliche Diagnose-Info
                        details.append("Chart-Status: ").append(imageRenderer != null && imageRenderer.hasValidImages() ? "OK" : "FEHLER").append("\n");
                    }
                }
            } else {
                details.append("PROBLEM: filteredTicks ist NULL!\n");
            }
        } else {
            details.append("PROBLEM: Keine Tick-Daten verfügbar oder noch nicht geladen.\n");
        }
        
        details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
        details.append("Drawdown Chart Höhe: ").append(drawdownChartHeight).append("px (+30% verbessert)\n");
        details.append("Refresh Counter: ").append(refreshCounter).append("\n");
        details.append("\nKlicken Sie auf 'Diagnostik' für vollständigen Bericht.\n");
        
        detailsText.setText(details.toString());
        
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Tick Chart - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel() + " [DIAGNOSE #" + refreshCounter + "]");
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten verfügbar [DIAGNOSEMODUS]");
        detailsText.setText("=== KEINE TICK-DATEN GEFUNDEN ===\n" +
                           "Datei: " + tickFilePath + "\n" +
                           "Mögliche Ursachen:\n" +
                           "- Datei ist leer\n" +
                           "- Datei hat ungültiges Format\n" +
                           "- Noch keine Daten für dieses Signal gesammelt\n\n" +
                           "Prüfen Sie die Datei manuell oder warten Sie auf neue Daten.");
        chartCanvas.redraw();
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        infoLabel.setText("Signal ID: " + signalId + " - Fehler beim Laden [DIAGNOSEMODUS]");
        detailsText.setText("=== FEHLER BEIM LADEN DER TICK-DATEN ===\n" +
                           "FEHLER: " + message + "\n\n" +
                           "Tick-Datei: " + tickFilePath + "\n\n" +
                           "Prüfen Sie:\n" +
                           "- Existiert die Datei?\n" +
                           "- Sind die Berechtigungen korrekt?\n" +
                           "- Ist die Datei nicht beschädigt?");
        
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Laden der Tick-Daten");
        errorBox.setMessage("DIAGNOSEMODUS:\n\n" + message + "\n\nSiehe Details-Panel für weitere Informationen.");
        errorBox.open();
    }
    
    /**
     * VERBESSERT: Aktualisiert die Daten mit Refresh-Counter
     */
    private void refreshData() {
        refreshCounter++;
        LOGGER.info("=== MANUELLER REFRESH #" + refreshCounter + " für Signal: " + signalId + " ===");
        
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
        
        LOGGER.info("=== SCHLIESSE TICK CHART WINDOW für Signal: " + signalId + " ===");
        LOGGER.info("Refresh Counter final: " + refreshCounter);
        
        // Ressourcen freigeben
        if (imageRenderer != null) {
            imageRenderer.disposeImages();
        }
        
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Zeigt das Fenster an
     */
    public void open() {
        shell.open();
        LOGGER.info("TickChartWindow (DIAGNOSEMODUS) geöffnet für Signal: " + signalId);
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