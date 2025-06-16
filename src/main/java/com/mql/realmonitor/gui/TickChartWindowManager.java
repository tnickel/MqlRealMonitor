package com.mql.realmonitor.gui;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * OPTIMIERT: TickChartWindowManager für asynchrones Laden optimiert
 * Reduziert die Zeit für initiales UI-Setup und verbessert Responsiveness
 * Koordiniert alle Komponenten: Chart-Panels, Toolbar, Layout, Daten-Loading
 */
public class TickChartWindowManager implements TickChartWindowToolbar.ToolbarCallbacks {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindowManager.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private ScrolledComposite scrolledComposite;
    private Composite chartsContainer;
    private Label infoLabel;
    private Text detailsText;
    
    // Chart-Panels - REFACTORED: Aufgeteilt in separate Klassen
    private EquityDrawdownChartPanel drawdownPanel;
    private ProfitDevelopmentChartPanel profitPanel;
    private TickChartWindowToolbar toolbar;
    
    // Zeitintervall-Buttons
    private Button[] timeScaleButtons;
    private TimeScale currentTimeScale = TimeScale.M15;
    
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
    
    // Parent GUI
    private final MqlRealMonitorGUI parentGui;
    private final Display display;
    
    // Chart-Dimensionen
    private int chartWidth = 800;
    private int drawdownChartHeight = 400;
    private int profitChartHeight = 400;
    private int totalChartHeight = 850;
    private double zoomFactor = 1.0;
    
    // Status-Flags
    private volatile boolean isDataLoaded = false;
    private volatile boolean isWindowClosed = false;
    private volatile boolean isInitialSetupComplete = false;
    private int refreshCounter = 0;
    
    /**
     * OPTIMIERT: Konstruktor - Erstellt nur minimales UI, lädt Daten asynchron
     */
    public TickChartWindowManager(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                                 String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        LOGGER.info("=== OPTIMIZED CHART WINDOW MANAGER ERSTELLT ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        
        // PHASE 1: Minimales Fenster sofort erstellen (schnell)
        createMinimalWindow(parent);
        
        // PHASE 2: Helfer-Klassen initialisieren (mittel)
        initializeHelperClasses();
        
        // PHASE 3: UI-Setup und Daten-Loading asynchron fortsetzen
        continueSetupAsync();
    }
    
    /**
     * PHASE 1: Erstellt nur das minimale Hauptfenster (SCHNELL - kein Blocking)
     */
    private void createMinimalWindow(Shell parent) {
        LOGGER.info("PHASE 1: Erstelle minimales Fenster...");
        
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Charts - " + signalId + " (" + providerName + ") [LOADING...]");
        shell.setSize(1000, 900);
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        // Nur Info-Label - alles andere wird async hinzugefügt
        infoLabel = new Label(shell, SWT.WRAP);
        infoLabel.setText("Initialisiere Chart-Fenster für " + providerName + "...");
        infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        setupEventHandlers();
        
        LOGGER.info("PHASE 1 abgeschlossen - Minimales Fenster erstellt");
    }
    
    /**
     * PHASE 2: Initialisiert Helfer-Klassen (MITTEL - benötigt etwas Zeit)
     */
    private void initializeHelperClasses() {
        LOGGER.info("PHASE 2: Initialisiere Helfer-Klassen...");
        
        // Helfer-Klassen initialisieren
        this.chartManager = new TickChartManager(signalId, providerName);
        this.imageRenderer = new ChartImageRenderer(display);
        
        LOGGER.info("PHASE 2 abgeschlossen - Helfer-Klassen initialisiert");
    }
    
    /**
     * PHASE 3: Setzt UI-Setup und Daten-Loading asynchron fort
     */
    private void continueSetupAsync() {
        LOGGER.info("PHASE 3: Starte asynchrones Setup...");
        
        // UI-Setup in kleinen Schritten, damit UI responsiv bleibt
        display.asyncExec(() -> {
            if (!isWindowClosed && !shell.isDisposed()) {
                createInfoPanelAsync();
            }
        });
        
        display.timerExec(100, () -> {
            if (!isWindowClosed && !shell.isDisposed()) {
                createTimeScalePanelAsync();
            }
        });
        
        display.timerExec(200, () -> {
            if (!isWindowClosed && !shell.isDisposed()) {
                createScrollableChartsAreaAsync();
            }
        });
        
        display.timerExec(300, () -> {
            if (!isWindowClosed && !shell.isDisposed()) {
                createToolbarPanelAsync();
                isInitialSetupComplete = true;
                
                // Erst nach UI-Setup: Daten laden
                loadDataAsync();
            }
        });
    }
    
    /**
     * Erstellt das Info-Panel asynchron
     */
    private void createInfoPanelAsync() {
        LOGGER.info("Erstelle Info-Panel...");
        
        Group infoGroup = new Group(shell, SWT.NONE);
        infoGroup.setText("Signal Information (OPTIMIZED - Async Loading)");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
        // Altes Label ersetzen
        infoLabel.dispose();
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
        shell.layout();
    }
    
    /**
     * Erstellt das Zeitintervall-Panel asynchron
     */
    private void createTimeScalePanelAsync() {
        LOGGER.info("Erstelle Zeitintervall-Panel...");
        
        Group timeScaleGroup = new Group(shell, SWT.NONE);
        timeScaleGroup.setText("Zeitintervall (Optimized - Async Setup)");
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
        
        shell.layout();
        LOGGER.info("Zeitintervall-Panel erstellt");
    }
    
    /**
     * OPTIMIZED: Erstellt scrollbaren Charts-Bereich asynchron
     */
    private void createScrollableChartsAreaAsync() {
        LOGGER.info("Erstelle scrollbaren Charts-Bereich...");
        
        Group chartsGroup = new Group(shell, SWT.NONE);
        chartsGroup.setText("Charts (Optimized - Loading...) - " + currentTimeScale.getLabel());
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
        
        // Chart-Panels werden später erstellt wenn Daten verfügbar sind
        Label placeholderLabel = new Label(chartsContainer, SWT.CENTER);
        placeholderLabel.setText("Charts werden geladen...\nBitte warten Sie einen Moment.");
        placeholderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
        
        // ScrolledComposite konfigurieren
        scrolledComposite.setContent(chartsContainer);
        scrolledComposite.setMinSize(chartWidth, 200); // Minimale Höhe erstmal
        
        shell.layout();
        LOGGER.info("Scrollbarer Charts-Bereich (Placeholder) erstellt");
    }
    
    /**
     * OPTIMIZED: Erstellt die Toolbar asynchron
     */
    private void createToolbarPanelAsync() {
        LOGGER.info("Erstelle Toolbar...");
        
        toolbar = new TickChartWindowToolbar(shell, signalId, providerName, signalData, tickFilePath, parentGui, shell);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        toolbar.setCallbacks(this);
        
        shell.layout();
        LOGGER.info("Toolbar erstellt");
    }
    
    /**
     * OPTIMIZED: Erstellt Chart-Panels erst wenn Daten verfügbar sind
     */
    private void createActualChartPanels() {
        LOGGER.info("Erstelle tatsächliche Chart-Panels...");
        
        // Placeholder entfernen
        for (org.eclipse.swt.widgets.Control child : chartsContainer.getChildren()) {
            child.dispose();
        }
        
        // REFACTORED: Chart-Panels als separate Klassen erstellen
        drawdownPanel = new EquityDrawdownChartPanel(chartsContainer, parentGui, signalId, providerName, imageRenderer);
        drawdownPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        drawdownPanel.updateChartDimensions(chartWidth, drawdownChartHeight);
        
        // Trennlinie
        Label separator = new Label(chartsContainer, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        profitPanel = new ProfitDevelopmentChartPanel(chartsContainer, parentGui, signalId, providerName, imageRenderer);
        profitPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        profitPanel.updateChartDimensions(chartWidth, profitChartHeight);
        
        // ScrolledComposite aktualisieren
        scrolledComposite.setMinSize(chartWidth, totalChartHeight);
        
        // Resize-Handler
        scrolledComposite.addListener(SWT.Resize, event -> {
            updateChartDimensions();
            renderBothChartsToImages();
        });
        
        chartsContainer.layout();
        LOGGER.info("Chart-Panels erfolgreich erstellt");
    }
    
    /**
     * Zentriert das Fenster
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
     * Setup Event Handler
     */
    private void setupEventHandlers() {
        shell.addListener(SWT.Close, event -> {
            closeWindow();
        });
    }
    
    /**
     * Wechselt das Zeitintervall
     */
    private void changeTimeScale(TimeScale newScale) {
        if (newScale == currentTimeScale) {
            return;
        }
        
        LOGGER.info("Zeitintervall-Wechsel: " + currentTimeScale.getLabel() + " -> " + newScale.getLabel());
        
        // Buttons aktualisieren
        if (timeScaleButtons != null) {
            for (int i = 0; i < TimeScale.values().length; i++) {
                TimeScale scale = TimeScale.values()[i];
                if (timeScaleButtons[i] != null && !timeScaleButtons[i].isDisposed()) {
                    timeScaleButtons[i].setSelection(scale == newScale);
                }
            }
        }
        
        currentTimeScale = newScale;
        
        if (tickDataSet != null && isInitialSetupComplete) {
            updateChartsWithCurrentData();
        }
    }
    
    /**
     * OPTIMIZED: Aktualisiert beide Chart-Panels - nur wenn alles bereit ist
     */
    private void updateChartsWithCurrentData() {
        if (!isInitialSetupComplete) {
            LOGGER.info("Initial Setup noch nicht abgeschlossen - Chart-Update wird verschoben");
            return;
        }
        
        LOGGER.info("=== UPDATE BEIDE CHART-PANELS (OPTIMIZED) ===");
        
        if (tickDataSet == null) {
            LOGGER.warning("tickDataSet ist NULL - kann Charts nicht aktualisieren");
            return;
        }
        
        try {
            // Daten filtern
            filteredTicks = TickDataFilter.filterTicksForTimeScale(tickDataSet, currentTimeScale);
            
            if (filteredTicks == null || filteredTicks.isEmpty()) {
                filteredTicks = tickDataSet.getTicks();
                LOGGER.info("FALLBACK: Verwende alle verfügbaren Ticks");
            }
            
            // Chart-Panels erstellen falls noch nicht vorhanden
            if (drawdownPanel == null || profitPanel == null) {
                createActualChartPanels();
            }
            
            // REFACTORED: Beide Chart-Panels aktualisieren
            if (drawdownPanel != null && drawdownPanel.isReady()) {
                drawdownPanel.updateChart(filteredTicks, currentTimeScale, chartManager);
                drawdownPanel.setDataLoaded(true);
            }
            
            if (profitPanel != null && profitPanel.isReady()) {
                profitPanel.updateChart(filteredTicks, currentTimeScale, chartManager);
                profitPanel.setDataLoaded(true);
            }
            
            updateInfoPanel();
            
            LOGGER.info("Beide Chart-Panels erfolgreich aktualisiert (OPTIMIZED)");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FEHLER beim Aktualisieren der Chart-Panels", e);
        }
    }
    
    /**
     * OPTIMIZED: Rendert beide Chart-Panels - nur wenn bereit
     */
    private void renderBothChartsToImages() {
        if (chartManager == null || imageRenderer == null || !isInitialSetupComplete) {
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
            
            // Chart-Panels neu zeichnen
            if (drawdownPanel != null && drawdownPanel.isReady()) {
                drawdownPanel.getCanvas().redraw();
            }
            if (profitPanel != null && profitPanel.isReady()) {
                profitPanel.getCanvas().redraw();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern der Chart-Images", e);
        }
    }
    
    /**
     * Aktualisiert Chart-Dimensionen
     */
    private void updateChartDimensions() {
        if (scrolledComposite == null || scrolledComposite.isDisposed()) {
            return;
        }
        
        Point size = scrolledComposite.getSize();
        if (size.x > 0 && size.y > 0) {
            chartWidth = Math.max(400, size.x - 50);
            
            // Chart-Panels aktualisieren
            if (drawdownPanel != null) {
                drawdownPanel.updateChartDimensions(chartWidth, drawdownChartHeight);
            }
            if (profitPanel != null) {
                profitPanel.updateChartDimensions(chartWidth, profitChartHeight);
            }
            
            scrolledComposite.setMinSize(chartWidth, totalChartHeight);
        }
    }
    
    /**
     * OPTIMIZED: Lädt Daten asynchron - besser strukturiert
     */
    private void loadDataAsync() {
        LOGGER.info("=== OPTIMIZED ASYNC DATEN-LOAD START ===");
        
        updateInfoPanelLoading("Lade Tick-Daten...");
        
        new Thread(() -> {
            try {
                LOGGER.info("Background-Thread: Lade Tick-Daten für " + signalId);
                
                // Daten-Loading mit Progress-Updates
                updateLoadingProgress("Prüfe Tick-Datei...");
                Thread.sleep(50); // Kurze Pause für UI-Update
                
                updateLoadingProgress("Lade Tick-Daten...");
                tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    display.asyncExec(() -> {
                        if (!isWindowClosed && !shell.isDisposed()) {
                            showNoDataMessage();
                        }
                    });
                    return;
                }
                
                isDataLoaded = true;
                LOGGER.info("ERFOLG: Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks (OPTIMIZED)");
                
                updateLoadingProgress("Bereite Charts vor...");
                Thread.sleep(100);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateChartsWithCurrentData();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER beim optimierten Laden der Tick-Daten", e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "OptimizedTickDataLoader-" + signalId).start();
    }
    
    /**
     * Aktualisiert Loading-Progress
     */
    private void updateLoadingProgress(String message) {
        display.asyncExec(() -> {
            if (!isWindowClosed && !shell.isDisposed() && infoLabel != null && !infoLabel.isDisposed()) {
                infoLabel.setText("Signal: " + signalId + " - " + message);
            }
        });
    }
    
    /**
     * Zeigt initialen Info-Text
     */
    private void updateInfoPanelInitial() {
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Setup läuft... [OPTIMIZED]";
        infoLabel.setText(info);
        
        if (detailsText != null) {
            detailsText.setText("=== OPTIMIZED CHART WINDOW (ASYNC LOADING) ===\n" +
                               "Chart-Panels: Werden nach Daten-Load erstellt\n" +
                               "Toolbar: Async erstellt\n" +
                               "Manager: Optimiert für bessere Responsiveness\n" +
                               "Tick-Datei: " + tickFilePath + "\n" +
                               "Setup-Phase läuft...");
        }
    }
    
    /**
     * Aktualisiert Info-Panel während Loading
     */
    private void updateInfoPanelLoading(String status) {
        display.asyncExec(() -> {
            if (!isWindowClosed && !shell.isDisposed() && infoLabel != null && !infoLabel.isDisposed()) {
                infoLabel.setText("Signal: " + signalId + " - " + status);
                
                if (detailsText != null && !detailsText.isDisposed()) {
                    detailsText.setText("=== OPTIMIZED ASYNC LOADING ===\n" +
                                       "Status: " + status + "\n" +
                                       "Signal: " + signalId + "\n" +
                                       "Provider: " + providerName + "\n" +
                                       "Setup complete: " + isInitialSetupComplete + "\n" +
                                       "Data loaded: " + isDataLoaded + "\n" +
                                       "Tick-Datei: " + tickFilePath + "\n");
                }
            }
        });
    }
    
    /**
     * Aktualisiert das Info-Panel
     */
    private void updateInfoPanel() {
        StringBuilder info = new StringBuilder();
        info.append("Signal ID: ").append(signalId).append("   ");
        info.append("Provider: ").append(providerName).append("   ");
        info.append("Zeitintervall: ").append(currentTimeScale.getLabel()).append("   ");
        info.append("Refresh: #").append(refreshCounter);
        
        infoLabel.setText(info.toString());
        
        if (detailsText != null && !detailsText.isDisposed()) {
            StringBuilder details = new StringBuilder();
            details.append("=== OPTIMIZED CHART WINDOW - ASYNC LOADED ===\n");
            details.append("Drawdown-Panel: ").append(drawdownPanel != null && drawdownPanel.isReady() ? "OK" : "LOADING").append("\n");
            details.append("Profit-Panel: ").append(profitPanel != null && profitPanel.isReady() ? "OK" : "LOADING").append("\n");
            details.append("Toolbar: ").append(toolbar != null && !toolbar.isDisposed() ? "OK" : "LOADING").append("\n");
            details.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
            details.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
            details.append("Setup Complete: ").append(isInitialSetupComplete).append("\n");
            
            if (tickDataSet != null && filteredTicks != null) {
                details.append("Tick-Daten: ").append(tickDataSet.getTickCount()).append(" gesamt, ").append(filteredTicks.size()).append(" gefiltert\n");
            }
            
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
            
            detailsText.setText(details.toString());
        }
        
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Charts - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel() + " [OPTIMIZED #" + refreshCounter + "]");
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten [OPTIMIZED]");
        
        if (detailsText != null && !detailsText.isDisposed()) {
            detailsText.setText("Keine Tick-Daten verfügbar.\nDas Chart-System ist trotzdem funktional.");
        }
        
        if (drawdownPanel != null) {
            drawdownPanel.setDataLoaded(false);
        }
        if (profitPanel != null) {
            profitPanel.setDataLoaded(false);
        }
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler im Optimized Chart Window");
        errorBox.setMessage("OPTIMIZED CHART WINDOW:\n\n" + message);
        errorBox.open();
    }
    
    /**
     * Schließt das Fenster
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        LOGGER.info("=== SCHLIESSE OPTIMIZED CHART WINDOW ===");
        
        if (imageRenderer != null) {
            imageRenderer.disposeImages();
        }
        
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Öffnet das Fenster
     */
    public void open() {
        shell.open();
        LOGGER.info("Optimized ChartWindow geöffnet für Signal: " + signalId);
    }
    
    // ========== TOOLBAR CALLBACKS IMPLEMENTATION ==========
    
    @Override
    public void onRefresh() {
        refreshCounter++;
        LOGGER.info("Optimized Refresh #" + refreshCounter + " angefordert");
        
        if (toolbar != null) {
            toolbar.setRefreshEnabled(false);
            toolbar.setRefreshText("Lädt...");
        }
        
        isDataLoaded = false;
        if (drawdownPanel != null) drawdownPanel.setDataLoaded(false);
        if (profitPanel != null) profitPanel.setDataLoaded(false);
        
        loadDataAsync();
        
        display.timerExec(3000, () -> {
            if (!isWindowClosed && toolbar != null && !toolbar.isDisposed()) {
                toolbar.setRefreshEnabled(true);
                toolbar.setRefreshText("Aktualisieren");
            }
        });
    }
    
    @Override
    public void onZoomIn() {
        zoomFactor *= 1.2;
        renderBothChartsToImages();
        LOGGER.info("Zoom In: " + zoomFactor);
    }
    
    @Override
    public void onZoomOut() {
        zoomFactor /= 1.2;
        renderBothChartsToImages();
        LOGGER.info("Zoom Out: " + zoomFactor);
    }
    
    @Override
    public void onResetZoom() {
        zoomFactor = 1.0;
        renderBothChartsToImages();
        LOGGER.info("Zoom Reset");
    }
    
    @Override
    public void onClose() {
        closeWindow();
    }
    
    @Override
    public TickDataLoader.TickDataSet getTickDataSet() {
        return tickDataSet;
    }
    
    @Override
    public TimeScale getCurrentTimeScale() {
        return currentTimeScale;
    }
    
    @Override
    public int getRefreshCounter() {
        return refreshCounter;
    }
    
    @Override
    public double getZoomFactor() {
        return zoomFactor;
    }
    
    @Override
    public boolean isDataLoaded() {
        return isDataLoaded;
    }
    
    @Override
    public ChartImageRenderer getImageRenderer() {
        return imageRenderer;
    }
    
    @Override
    public TickChartManager getChartManager() {
        return chartManager;
    }
    
    @Override
    public List<TickDataLoader.TickData> getFilteredTicks() {
        return filteredTicks;
    }
}