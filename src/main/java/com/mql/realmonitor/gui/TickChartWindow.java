package com.mql.realmonitor.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
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
 * ERWEITERT: Tick Chart Window mit BEIDEN Charts (Drawdown + Profit), Scrolling, TICKDATEN-BUTTON + MQL5 Website Link
 * NEUE FEATURES: Scrollbares Layout, Profit-Chart, verbesserte Diagnostik, Tickdaten-Anzeige, MQL5 Website Button
 */
public class TickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindow.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private ScrolledComposite scrolledComposite;  // NEU: Für Scrolling
    private Composite chartsContainer;           // NEU: Container für beide Charts
    private Canvas drawdownCanvas;               // Drawdown-Chart Canvas
    private Canvas profitCanvas;                 // NEU: Profit-Chart Canvas
    private Label infoLabel;
    private Text detailsText;
    private Button refreshButton;
    private Button closeButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button resetZoomButton;
    private Button diagnosticButton;
    private Button tickDataButton;              // NEU: Tickdaten-Button
    private Button mql5WebsiteButton;           // NEU: MQL5 Website Button
    
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
    
    // Chart-Dimensionen - BEIDE CHARTS
    private int chartWidth = 800;
    private int drawdownChartHeight = 400;   // GEÄNDERT: Reduziert für zwei Charts
    private int profitChartHeight = 400;     // NEU: Höhe für Profit-Chart
    private int totalChartHeight = 850;      // NEU: Gesamthöhe (beide Charts + Abstand)
    private double zoomFactor = 1.0;
    
    // Status-Flags
    private volatile boolean isDataLoaded = false;
    private volatile boolean isWindowClosed = false;
    
    // Diagnose-Counter
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
        
        LOGGER.info("=== ERWEITERTE CHART WINDOW ERSTELLT (DRAWDOWN + PROFIT + TICKDATEN + MQL5 LINK) ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        LOGGER.info("Tick-Datei: " + tickFilePath);
        LOGGER.info("Chart-Dimensionen: " + chartWidth + "x" + drawdownChartHeight + " + " + chartWidth + "x" + profitChartHeight);
        
        createWindow(parent);
        loadDataAsync();
    }
    
    /**
     * ERWEITERT: Erstellt das Hauptfenster mit Scrolling
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Charts - " + signalId + " (" + providerName + ") [DRAWDOWN + PROFIT + TICKDATEN + MQL5]");
        shell.setSize(1000, 900); // GEÄNDERT: Größer für beide Charts
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        createInfoPanel();
        createTimeScalePanel();
        createScrollableChartsArea();  // NEU: Scrollbereich
        createButtonPanel();  // ERWEITERT: Mit MQL5 Website Button
        setupEventHandlers();
        
        LOGGER.info("Erweiterte ChartWindow UI erstellt für Signal: " + signalId);
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
     * ERWEITERT: Erstellt das Info-Panel
     */
    private void createInfoPanel() {
        Group infoGroup = new Group(shell, SWT.NONE);
        infoGroup.setText("Signal Information (DRAWDOWN + PROFIT CHARTS + TICKDATEN + MQL5 LINK)");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
        infoLabel = new Label(infoGroup, SWT.WRAP);
        GridData infoLabelData = new GridData(SWT.FILL, SWT.FILL, true, false);
        infoLabelData.horizontalSpan = 2;
        infoLabel.setLayoutData(infoLabelData);
        
        detailsText = new Text(infoGroup, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.horizontalSpan = 2;
        detailsData.heightHint = 100;
        detailsText.setLayoutData(detailsData);
        
        updateInfoPanelInitial();
    }
    
    /**
     * Erstellt das Zeitintervall-Panel mit Buttons
     */
    private void createTimeScalePanel() {
        Group timeScaleGroup = new Group(shell, SWT.NONE);
        timeScaleGroup.setText("Zeitintervall (Beide Charts mit Fallback-Strategien)");
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
     * NEU: Erstellt scrollbaren Charts-Bereich mit beiden Charts
     */
    private void createScrollableChartsArea() {
        Group chartsGroup = new Group(shell, SWT.NONE);
        chartsGroup.setText("Charts (Scrollbar) - " + currentTimeScale.getLabel());
        chartsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartsGroup.setLayout(new GridLayout(1, false));
        
        // ScrolledComposite erstellen
        scrolledComposite = new ScrolledComposite(chartsGroup, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        
        // Container für Charts erstellen
        chartsContainer = new Composite(scrolledComposite, SWT.NONE);
        chartsContainer.setLayout(new GridLayout(1, false));
        chartsContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // 1. Drawdown-Chart Canvas
        createDrawdownCanvas();
        
        // Trennlinie
        Label separator = new Label(chartsContainer, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // 2. Profit-Chart Canvas
        createProfitCanvas();
        
        // ScrolledComposite konfigurieren
        scrolledComposite.setContent(chartsContainer);
        scrolledComposite.setMinSize(chartWidth, totalChartHeight);
        
        // Resize-Handler für ScrolledComposite
        scrolledComposite.addListener(SWT.Resize, event -> {
            updateChartDimensions();
            renderBothChartsToImages();
        });
        
        LOGGER.info("Scrollbarer Charts-Bereich erstellt mit beiden Charts");
    }
    
    /**
     * NEU: Erstellt das Drawdown-Chart Canvas
     */
    private void createDrawdownCanvas() {
        // Label für Drawdown-Chart
        Label drawdownLabel = new Label(chartsContainer, SWT.NONE);
        drawdownLabel.setText("Equity Drawdown Chart (%)");
        drawdownLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        drawdownLabel.setFont(parentGui.getBoldFont());
        
        drawdownCanvas = new Canvas(chartsContainer, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData drawdownData = new GridData(SWT.FILL, SWT.FILL, true, false);
        drawdownData.heightHint = drawdownChartHeight;
        drawdownCanvas.setLayoutData(drawdownData);
        drawdownCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        drawdownCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintDrawdownChart(e.gc);
            }
        });
        
        LOGGER.info("Drawdown-Chart Canvas erstellt");
    }
    
    /**
     * NEU: Erstellt das Profit-Chart Canvas
     */
    private void createProfitCanvas() {
        // Label für Profit-Chart
        Label profitLabel = new Label(chartsContainer, SWT.NONE);
        profitLabel.setText("Profit-Entwicklung Chart (Grün: Kontostand, Gelb: Gesamtwert)");
        profitLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        profitLabel.setFont(parentGui.getBoldFont());
        
        profitCanvas = new Canvas(chartsContainer, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        GridData profitData = new GridData(SWT.FILL, SWT.FILL, true, false);
        profitData.heightHint = profitChartHeight;
        profitCanvas.setLayoutData(profitData);
        profitCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        profitCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintProfitChart(e.gc);
            }
        });
        
        LOGGER.info("Profit-Chart Canvas erstellt");
    }
    
    /**
     * ERWEITERT: Erstellt das Button-Panel mit Tickdaten-Button + MQL5 Website Button
     */
    private void createButtonPanel() {
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonComposite.setLayout(new GridLayout(9, false)); // GEÄNDERT: von 8 auf 9 für MQL5 Button
        
        refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        diagnosticButton = new Button(buttonComposite, SWT.PUSH);
        diagnosticButton.setText("Diagnostik");
        diagnosticButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        diagnosticButton.setToolTipText("Zeigt detaillierte Diagnostik-Informationen");
        
        // NEU: Tickdaten-Button
        tickDataButton = new Button(buttonComposite, SWT.PUSH);
        tickDataButton.setText("Tickdaten");
        tickDataButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        tickDataButton.setToolTipText("Zeigt die rohen Tickdaten aus der Datei");
        
        // NEU: MQL5 Website Button
        mql5WebsiteButton = new Button(buttonComposite, SWT.PUSH);
        mql5WebsiteButton.setText("🌐 MQL5 Website");
        mql5WebsiteButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        mql5WebsiteButton.setToolTipText("Öffnet die MQL5-Seite dieses Signalproviders im Browser");
        
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
        
        LOGGER.info("Button-Panel erstellt mit MQL5 Website Button");
    }
    
    /**
     * ERWEITERT: Setup Event Handler für beide Charts + Tickdaten-Button + MQL5 Website Button
     */
    private void setupEventHandlers() {
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshData();
            }
        });
        
        diagnosticButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDiagnosticReport();
            }
        });
        
        // NEU: Event Handler für Tickdaten-Button
        tickDataButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showTickDataWindow();
            }
        });
        
        // NEU: Event Handler für MQL5 Website Button
        mql5WebsiteButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openMql5Website();
            }
        });
        
        zoomInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor *= 1.2;
                renderBothChartsToImages();  // GEÄNDERT: Beide Charts
                LOGGER.info("Zoom In: " + zoomFactor);
            }
        });
        
        zoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor /= 1.2;
                renderBothChartsToImages();  // GEÄNDERT: Beide Charts
                LOGGER.info("Zoom Out: " + zoomFactor);
            }
        });
        
        resetZoomButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor = 1.0;
                renderBothChartsToImages();  // GEÄNDERT: Beide Charts
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
     * NEU: Öffnet das Tickdaten-Fenster mit den rohen Daten aus der Datei
     */
    private void showTickDataWindow() {
        try {
            LOGGER.info("=== TICKDATEN-FENSTER ANGEFORDERT für Signal: " + signalId + " ===");
            LOGGER.info("Tickdaten-Datei: " + tickFilePath);
            
            // Erstelle TickDataDisplayWindow-Instanz
            TickDataDisplayWindow tickDataWindow = new TickDataDisplayWindow(parentGui);
            
            // Öffne das Tickdaten-Fenster
            tickDataWindow.showTickDataWindow(signalId, providerName);
            
            LOGGER.info("Tickdaten-Fenster erfolgreich geöffnet für Signal: " + signalId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Tickdaten-Fensters für Signal: " + signalId, e);
            
            // Zeige Fehlermeldung
            MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            errorBox.setText("Fehler beim Öffnen der Tickdaten");
            errorBox.setMessage("Konnte Tickdaten-Fenster für Signal " + signalId + " nicht öffnen:\n\n" + 
                               e.getMessage() + "\n\nTick-Datei: " + tickFilePath);
            errorBox.open();
        }
    }
    
    /**
     * NEU: Öffnet die MQL5-Website für diesen Signalprovider
     */
    private void openMql5Website() {
        try {
            LOGGER.info("=== ÖFFNE MQL5 WEBSITE für Signal: " + signalId + " ===");
            
            // URL aus der Konfiguration erstellen
            String websiteUrl = parentGui.getMonitor().getConfig().buildSignalUrl(signalId);
            LOGGER.info("Generierte URL: " + websiteUrl);
            
            // Bestätigungsdialog anzeigen
            if (showWebsiteConfirmationDialog(websiteUrl)) {
                openUrlInBrowser(websiteUrl);
                LOGGER.info("MQL5 Website erfolgreich geöffnet für Signal: " + signalId);
            } else {
                LOGGER.info("Benutzer hat das Öffnen der Website abgebrochen");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen der MQL5-Website für Signal: " + signalId, e);
            showWebsiteErrorDialog(e);
        }
    }
    
    /**
     * NEU: Zeigt Bestätigungsdialog vor dem Öffnen der Website
     */
    private boolean showWebsiteConfirmationDialog(String url) {
        MessageBox confirmBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("MQL5 Website öffnen");
        
        StringBuilder message = new StringBuilder();
        message.append("MQL5-Seite des Signalproviders im Browser öffnen?\n\n");
        message.append("Signal ID: ").append(signalId).append("\n");
        message.append("Provider: ").append(providerName).append("\n\n");
        message.append("URL: ").append(url).append("\n\n");
        message.append("Diese Aktion öffnet Ihren Standard-Webbrowser.");
        
        confirmBox.setMessage(message.toString());
        
        return confirmBox.open() == SWT.YES;
    }
    
    /**
     * NEU: Zeigt Fehlerdialog für Website-Öffnung
     */
    private void showWebsiteErrorDialog(Exception e) {
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Öffnen der Website");
        
        String url = parentGui.getMonitor().getConfig().buildSignalUrl(signalId);
        
        StringBuilder message = new StringBuilder();
        message.append("Konnte MQL5-Website nicht öffnen:\n\n");
        message.append("Fehler: ").append(e.getMessage()).append("\n\n");
        message.append("Sie können die URL manuell kopieren und im Browser öffnen:\n\n");
        message.append(url);
        
        errorBox.setMessage(message.toString());
        errorBox.open();
    }
    
    /**
     * NEU: Öffnet eine URL im Standard-Browser
     */
    private void openUrlInBrowser(String url) throws Exception {
        LOGGER.info("Versuche URL im Browser zu öffnen: " + url);
        
        // Versuche Desktop.browse() - Moderner Ansatz
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                URI uri = new URI(url);
                desktop.browse(uri);
                LOGGER.info("URL erfolgreich mit Desktop.browse() geöffnet");
                return;
            }
        }
        
        // Fallback: System-spezifische Kommandos
        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.info("Desktop.browse() nicht verfügbar, verwende OS-spezifischen Ansatz: " + os);
        
        ProcessBuilder processBuilder;
        
        if (os.contains("win")) {
            // Windows
            processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            // macOS
            processBuilder = new ProcessBuilder("open", url);
        } else {
            // Linux/Unix
            processBuilder = new ProcessBuilder("xdg-open", url);
        }
        
        Process process = processBuilder.start();
        
        // Warte kurz und prüfe Exit-Code
        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        if (finished) {
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                LOGGER.info("URL erfolgreich mit OS-Kommando geöffnet (Exit-Code: " + exitCode + ")");
            } else {
                LOGGER.warning("OS-Kommando beendet mit Exit-Code: " + exitCode);
            }
        } else {
            LOGGER.info("OS-Kommando läuft noch (normal für Browser-Start)");
        }
    }
    
    /**
     * ERWEITERT: Zeigt einen detaillierten Diagnostik-Bericht für beide Charts
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
        report.append("Zoom Factor: ").append(zoomFactor).append("\n");
        report.append("Chart Typ: DRAWDOWN + PROFIT (DUAL-CHART) + TICKDATEN + MQL5 LINK\n\n");
        
        // SignalData Info
        report.append("SIGNALDATA:\n");
        if (signalData != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Details: ").append(signalData.getSummary()).append("\n");
            report.append("MQL5 URL: ").append(parentGui.getMonitor().getConfig().buildSignalUrl(signalId)).append("\n");
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
        
        // ERWEITERT: Chart Status für beide Charts + Tickdaten + MQL5 Link
        report.append("CHARTS STATUS:\n");
        report.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
        report.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
        report.append("HasValidDrawdownImage: ").append(imageRenderer != null ? imageRenderer.hasValidDrawdownImage() : "N/A").append("\n");
        report.append("HasValidProfitImage: ").append(imageRenderer != null ? imageRenderer.hasValidProfitImage() : "N/A").append("\n");
        report.append("Canvas Größen: ").append(chartWidth).append("x").append(drawdownChartHeight).append(" + ").append(chartWidth).append("x").append(profitChartHeight).append("\n");
        report.append("Scroll-Container: ").append(scrolledComposite != null && !scrolledComposite.isDisposed() ? "OK" : "FEHLER").append("\n");
        report.append("Tickdaten-Button: ").append(tickDataButton != null && !tickDataButton.isDisposed() ? "OK" : "FEHLER").append("\n");
        report.append("MQL5 Website-Button: ").append(mql5WebsiteButton != null && !mql5WebsiteButton.isDisposed() ? "OK" : "FEHLER").append("\n");
        
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
        
        // Daten neu filtern und beide Charts aktualisieren
        if (tickDataSet != null) {
            updateChartsWithCurrentData();
        } else {
            LOGGER.warning("Kann Zeitintervall nicht wechseln - tickDataSet ist NULL");
        }
    }
    
    /**
     * ERWEITERT: Aktualisiert beide Charts mit aktuellen Daten
     */
    private void updateChartsWithCurrentData() {
        LOGGER.info("=== UPDATE BEIDE CHARTS MIT AKTUELLEN DATEN ===");
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
            
            // ERWEITERT: Beide Charts über ChartManager aktualisieren
            chartManager.updateChartsWithData(filteredTicks, currentTimeScale);
            
            // Images rendern
            renderBothChartsToImages();
            
            // Info-Panel aktualisieren
            updateInfoPanel();
            
            LOGGER.info("Beide Charts erfolgreich aktualisiert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FEHLER beim Aktualisieren der Charts", e);
        }
    }
    
    /**
     * NEU: Rendert beide Charts als Images
     */
    private void renderBothChartsToImages() {
        if (chartManager == null || imageRenderer == null) {
            LOGGER.warning("Kann Charts nicht rendern - Manager oder Renderer ist NULL");
            return;
        }
        
        try {
            imageRenderer.renderBothChartsToImages(
                chartManager.getDrawdownChart(),
                chartManager.getProfitChart(),
                chartWidth,
                drawdownChartHeight,
                profitChartHeight,
                zoomFactor
            );
            
            // Beide Canvas neu zeichnen
            if (!drawdownCanvas.isDisposed()) {
                drawdownCanvas.redraw();
            }
            if (!profitCanvas.isDisposed()) {
                profitCanvas.redraw();
            }
            
            LOGGER.fine("Beide Charts erfolgreich gerendert");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern der Charts", e);
        }
    }
    
    /**
     * NEU: Aktualisiert Chart-Dimensionen basierend auf Container-Größe
     */
    private void updateChartDimensions() {
        if (scrolledComposite == null || scrolledComposite.isDisposed()) {
            return;
        }
        
        Point size = scrolledComposite.getSize();
        if (size.x > 0 && size.y > 0) {
            chartWidth = Math.max(400, size.x - 50); // Mindestbreite mit Padding
            
            LOGGER.info("Chart-Dimensionen aktualisiert: " + chartWidth + "x" + drawdownChartHeight + " + " + chartWidth + "x" + profitChartHeight);
            
            // ScrolledComposite Minimum Size aktualisieren
            scrolledComposite.setMinSize(chartWidth, totalChartHeight);
        }
    }
    
    /**
     * Lädt Daten asynchron mit umfassender Diagnostik
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
        }, "TickDataLoader-DualChart-Tickdaten-MQL5Link-" + signalId).start();
    }
    
    /**
     * NEU: Zeichnet den Drawdown-Chart auf seinem Canvas
     */
    private void paintDrawdownChart(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(drawdownCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = drawdownCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidDrawdownImage()) {
            // Drawdown-Chart zentriert zeichnen
            org.eclipse.swt.graphics.Rectangle imagesBounds = imageRenderer.getDrawdownChartImage().getBounds();
            int x = (canvasBounds.width - imagesBounds.width) / 2;
            int y = (canvasBounds.height - imagesBounds.height) / 2;
            
            gc.drawImage(imageRenderer.getDrawdownChartImage(), Math.max(0, x), Math.max(0, y));
            
        } else if (!isDataLoaded) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Drawdown-Chart wird geladen...", 20, 20, true);
            gc.drawText("Signal: " + signalId + " (" + providerName + ")", 20, 40, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden des Drawdown-Charts", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
        }
    }
    
    /**
     * NEU: Zeichnet den Profit-Chart auf seinem Canvas
     */
    private void paintProfitChart(GC gc) {
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(profitCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = profitCanvas.getBounds();
        
        if (imageRenderer != null && imageRenderer.hasValidProfitImage()) {
            // Profit-Chart zentriert zeichnen
            org.eclipse.swt.graphics.Rectangle imagesBounds = imageRenderer.getProfitChartImage().getBounds();
            int x = (canvasBounds.width - imagesBounds.width) / 2;
            int y = (canvasBounds.height - imagesBounds.height) / 2;
            
            gc.drawImage(imageRenderer.getProfitChartImage(), Math.max(0, x), Math.max(0, y));
            
        } else if (!isDataLoaded) {
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Profit-Chart wird geladen...", 20, 20, true);
            gc.drawText("Grün: Kontostand, Gelb: Gesamtwert", 20, 40, true);
        } else {
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("FEHLER beim Laden des Profit-Charts", 20, 20, true);
            gc.drawText("Signal: " + signalId, 20, 40, true);
        }
    }
    
    /**
     * Zeigt initialen Info-Text
     */
    private void updateInfoPanelInitial() {
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Lädt... [DUAL-CHART-MODUS + TICKDATEN + MQL5]";
        infoLabel.setText(info);
        detailsText.setText("=== DUAL-CHART-MODUS + TICKDATEN + MQL5 LINK AKTIV ===\n" +
                           "Tick-Daten werden geladen...\n" +
                           "Tick-Datei: " + tickFilePath + "\n" +
                           "CHART 1: Drawdown-Chart (Magenta)\n" +
                           "CHART 2: Profit-Chart (Grün: Kontostand, Gelb: Gesamtwert)\n" +
                           "TICKDATEN: Button für rohe Datenansicht verfügbar\n" +
                           "MQL5 LINK: Button öffnet Signalprovider-Seite im Browser\n" +
                           "Fenster ist scrollbar für beide Charts.\n" +
                           "Bitte warten Sie einen Moment.");
    }
    
    /**
     * ERWEITERT: Aktualisiert das Info-Panel für beide Charts + Tickdaten + MQL5 Link
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
        
        details.append("=== DUAL-CHART-MODUS + TICKDATEN + MQL5 LINK - DETAILLIERTE INFORMATIONEN ===\n");
        details.append("Zeitintervall: ").append(currentTimeScale.getLabel())
               .append(" (letzte ").append(currentTimeScale.getDisplayMinutes()).append(" Minuten)\n");
        details.append("Chart 1: Drawdown-Chart (robuste Auto-Skalierung)\n");
        details.append("Chart 2: Profit-Chart (Grün: Kontostand, Gelb: Gesamtwert)\n");
        details.append("Tickdaten: Button für scrollbare Rohdaten-Anzeige\n");
        details.append("MQL5 Link: Button öffnet Signalprovider-Seite im Browser\n");
        details.append("Layout: Scrollbar für beide Charts vertikal\n");
        details.append("Tick-Datei: ").append(tickFilePath).append("\n");
        details.append("MQL5 URL: ").append(parentGui.getMonitor().getConfig().buildSignalUrl(signalId)).append("\n");
        
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
                        details.append("\n=== DRAWDOWN STATISTIK ===\n");
                        details.append("Min. Drawdown: ").append(stats.getFormattedMinDrawdown()).append("\n");
                        details.append("Max. Drawdown: ").append(stats.getFormattedMaxDrawdown()).append("\n");
                        details.append("Durchschn. Drawdown: ").append(stats.getFormattedAvgDrawdown()).append("\n");
                    }
                    
                    // Profit-Statistiken
                    double minEquity = filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getEquity).min().orElse(0);
                    double maxEquity = filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getEquity).max().orElse(0);
                    double minTotal = filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getTotalValue).min().orElse(0);
                    double maxTotal = filteredTicks.stream().mapToDouble(TickDataLoader.TickData::getTotalValue).max().orElse(0);
                    
                    details.append("\n=== PROFIT STATISTIK ===\n");
                    details.append("Kontostand-Bereich: ").append(String.format("%.2f - %.2f", minEquity, maxEquity)).append("\n");
                    details.append("Gesamtwert-Bereich: ").append(String.format("%.2f - %.2f", minTotal, maxTotal)).append("\n");
                    
                    // Chart-Status
                    details.append("\n=== CHART STATUS ===\n");
                    details.append("Drawdown-Chart: ").append(imageRenderer != null && imageRenderer.hasValidDrawdownImage() ? "OK" : "FEHLER").append("\n");
                    details.append("Profit-Chart: ").append(imageRenderer != null && imageRenderer.hasValidProfitImage() ? "OK" : "FEHLER").append("\n");
                    details.append("Tickdaten-Button: ").append(tickDataButton != null && !tickDataButton.isDisposed() ? "OK" : "FEHLER").append("\n");
                    details.append("MQL5 Website-Button: ").append(mql5WebsiteButton != null && !mql5WebsiteButton.isDisposed() ? "OK" : "FEHLER").append("\n");
                }
            } else {
                details.append("PROBLEM: filteredTicks ist NULL!\n");
            }
        } else {
            details.append("PROBLEM: Keine Tick-Daten verfügbar oder noch nicht geladen.\n");
        }
        
        details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
        details.append("Chart-Dimensionen: ").append(chartWidth).append("x").append(drawdownChartHeight).append(" + ").append(chartWidth).append("x").append(profitChartHeight).append("\n");
        details.append("Refresh Counter: ").append(refreshCounter).append("\n");
        details.append("\nKlicken Sie auf 'Diagnostik' für vollständigen Bericht.\n");
        details.append("Klicken Sie auf 'Tickdaten' für scrollbare Rohdaten-Anzeige.\n");
        details.append("Klicken Sie auf '🌐 MQL5 Website' um die Signalprovider-Seite zu öffnen.\n");
        
        detailsText.setText(details.toString());
        
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Charts - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel() + " [DUAL+TICKDATEN+MQL5 #" + refreshCounter + "]");
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten verfügbar [DUAL-CHART-MODUS + TICKDATEN + MQL5]");
        detailsText.setText("=== KEINE TICK-DATEN GEFUNDEN ===\n" +
                           "Datei: " + tickFilePath + "\n" +
                           "Mögliche Ursachen:\n" +
                           "- Datei ist leer\n" +
                           "- Datei hat ungültiges Format\n" +
                           "- Noch keine Daten für dieses Signal gesammelt\n\n" +
                           "Prüfen Sie die Datei manuell oder warten Sie auf neue Daten.\n" +
                           "Der Tickdaten-Button zeigt den Dateiinhalt auch bei leeren Charts an.\n" +
                           "Der MQL5 Website-Button funktioniert weiterhin und öffnet:\n" + 
                           parentGui.getMonitor().getConfig().buildSignalUrl(signalId));
        drawdownCanvas.redraw();
        profitCanvas.redraw();
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        infoLabel.setText("Signal ID: " + signalId + " - Fehler beim Laden [DUAL-CHART-MODUS + TICKDATEN + MQL5]");
        detailsText.setText("=== FEHLER BEIM LADEN DER TICK-DATEN ===\n" +
                           "FEHLER: " + message + "\n\n" +
                           "Tick-Datei: " + tickFilePath + "\n\n" +
                           "Prüfen Sie:\n" +
                           "- Existiert die Datei?\n" +
                           "- Sind die Berechtigungen korrekt?\n" +
                           "- Ist die Datei nicht beschädigt?\n\n" +
                           "Der Tickdaten-Button kann trotzdem versuchen, die Datei zu lesen.\n" +
                           "Der MQL5 Website-Button funktioniert weiterhin und öffnet:\n" + 
                           parentGui.getMonitor().getConfig().buildSignalUrl(signalId));
        
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Laden der Tick-Daten");
        errorBox.setMessage("DUAL-CHART-MODUS + TICKDATEN + MQL5 LINK:\n\n" + message + "\n\nSiehe Details-Panel für weitere Informationen.\n\nDer Tickdaten-Button und MQL5 Website-Button sind weiterhin verfügbar.");
        errorBox.open();
    }
    
    /**
     * Aktualisiert die Daten mit Refresh-Counter
     */
    private void refreshData() {
        refreshCounter++;
        LOGGER.info("=== MANUELLER REFRESH #" + refreshCounter + " für Signal: " + signalId + " ===");
        
        refreshButton.setEnabled(false);
        refreshButton.setText("Lädt...");
        
        isDataLoaded = false;
        drawdownCanvas.redraw();
        profitCanvas.redraw();
        
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
        
        LOGGER.info("=== SCHLIESSE DUAL CHART WINDOW + TICKDATEN + MQL5 LINK für Signal: " + signalId + " ===");
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
        LOGGER.info("DualChartWindow (DRAWDOWN + PROFIT + TICKDATEN + MQL5 LINK) geöffnet für Signal: " + signalId);
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