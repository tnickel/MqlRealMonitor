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
 * ERWEITERT: TickChartWindowManager mit Scroll-Fix und automatischem Chart-Refresh
 * NEU: Behebt Scroll-Probleme durch Timer-basiertes Auto-Refresh und Manual-Refresh-Button
 * Koordiniert alle Komponenten: Chart-Panels, Toolbar, Layout, Daten-Loading, Scroll-Handling
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
    private int timeScaleChangeCounter = 0;
    
    // NEU: Scroll-Handling Variablen
    private Runnable scrollRefreshTimer = null;
    private volatile boolean isScrolling = false;
    private long lastScrollTime = 0;
    private static final int SCROLL_REFRESH_DELAY = 500; // 500ms nach Scroll-Ende
    
    /**
     * KONSTRUKTOR: Erstellt nur minimales UI, lädt Daten asynchron
     */
    public TickChartWindowManager(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                                 String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        LOGGER.info("=== ENHANCED CHART WINDOW MANAGER ERSTELLT (MIT SCROLL-FIX) ===");
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
        infoGroup.setText("Signal Information (ENHANCED - Scroll-Fix)");
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
        timeScaleGroup.setText("Zeitintervall (Enhanced - Auto-Refresh nach Scroll)");
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
     * ENHANCED: Erstellt scrollbaren Charts-Bereich mit Scroll-Handling
     */
    private void createScrollableChartsAreaAsync() {
        LOGGER.info("Erstelle scrollbaren Charts-Bereich mit Scroll-Fix...");
        
        Group chartsGroup = new Group(shell, SWT.NONE);
        chartsGroup.setText("Charts (Enhanced - Auto-Refresh) - " + currentTimeScale.getLabel());
        chartsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartsGroup.setLayout(new GridLayout(1, false));
        
        // ScrolledComposite erstellen
        scrolledComposite = new ScrolledComposite(chartsGroup, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        
        // NEU: Scroll-Event-Handler hinzufügen
        setupScrollHandling();
        
        // Container für Charts erstellen
        chartsContainer = new Composite(scrolledComposite, SWT.NONE);
        chartsContainer.setLayout(new GridLayout(1, false));
        chartsContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // Chart-Panels werden später erstellt wenn Daten verfügbar sind
        Label placeholderLabel = new Label(chartsContainer, SWT.CENTER);
        placeholderLabel.setText("Charts werden geladen...\nBitte warten Sie einen Moment.\n\nScroll-Fix: Aktiviert");
        placeholderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
        
        // ScrolledComposite konfigurieren
        scrolledComposite.setContent(chartsContainer);
        scrolledComposite.setMinSize(chartWidth, 200); // Minimale Höhe erstmal
        
        shell.layout();
        LOGGER.info("Scrollbarer Charts-Bereich (Enhanced) erstellt");
    }
    
    /**
     * NEU: Setup für Scroll-Event-Handling
     */
    private void setupScrollHandling() {
        LOGGER.info("Setup Scroll-Event-Handling...");
        
        // Mouse Wheel Listener für Scroll-Detection
        scrolledComposite.addListener(SWT.MouseWheel, event -> {
            onScrollEvent("MouseWheel");
        });
        
        // Scroll Bar Listener
        if (scrolledComposite.getVerticalBar() != null) {
            scrolledComposite.getVerticalBar().addListener(SWT.Selection, event -> {
                onScrollEvent("VerticalScrollBar");
            });
        }
        
        if (scrolledComposite.getHorizontalBar() != null) {
            scrolledComposite.getHorizontalBar().addListener(SWT.Selection, event -> {
                onScrollEvent("HorizontalScrollBar");
            });
        }
        
        // Keyboard Scrolling (Arrow keys, Page Up/Down)
        scrolledComposite.addListener(SWT.KeyDown, event -> {
            switch (event.keyCode) {
                case SWT.ARROW_UP:
                case SWT.ARROW_DOWN:
                case SWT.ARROW_LEFT:
                case SWT.ARROW_RIGHT:
                case SWT.PAGE_UP:
                case SWT.PAGE_DOWN:
                case SWT.HOME:
                case SWT.END:
                    onScrollEvent("Keyboard:" + event.keyCode);
                    break;
            }
        });
        
        LOGGER.info("Scroll-Event-Handler eingerichtet");
    }
    
    /**
     * NEU: Behandelt Scroll-Events
     */
    private void onScrollEvent(String source) {
        isScrolling = true;
        lastScrollTime = System.currentTimeMillis();
        
        LOGGER.fine("Scroll-Event erkannt: " + source);
        
        // Timer für Auto-Refresh nach Scroll-Ende
        scheduleScrollRefresh();
    }
    
    /**
     * NEU: Zeitgesteuertes Refresh nach Scroll-Ende
     */
    private void scheduleScrollRefresh() {
        // Vorherigen Timer abbrechen
        if (scrollRefreshTimer != null) {
            display.timerExec(-1, scrollRefreshTimer); // Abbrechen
        }
        
        // Neuen Timer erstellen
        scrollRefreshTimer = () -> {
            long timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime;
            
            if (timeSinceLastScroll >= SCROLL_REFRESH_DELAY) {
                // Scroll ist beendet - Auto-Refresh triggern
                isScrolling = false;
                
                if (!isWindowClosed && !shell.isDisposed()) {
                    LOGGER.info("Auto-Refresh nach Scroll-Ende ausgelöst");
                    refreshChartsAfterScroll();
                }
            } else {
                // Noch am Scrollen - Timer erneut setzen
                scheduleScrollRefresh();
            }
        };
        
        display.timerExec(SCROLL_REFRESH_DELAY, scrollRefreshTimer);
    }
    
    /**
     * NEU: Refresht Charts nach Scroll-Ende (ohne Daten neu zu laden)
     */
    private void refreshChartsAfterScroll() {
        try {
            LOGGER.info("=== AUTO-REFRESH CHARTS NACH SCROLL ===");
            
            if (drawdownPanel != null && drawdownPanel.isReady()) {
                drawdownPanel.getCanvas().redraw();
                LOGGER.fine("Drawdown-Chart nach Scroll neu gezeichnet");
            }
            
            if (profitPanel != null && profitPanel.isReady()) {
                profitPanel.getCanvas().redraw();
                LOGGER.fine("Profit-Chart nach Scroll neu gezeichnet");
            }
            
            // Optional: Chart-Images neu rendern falls nötig
            if (chartManager != null && imageRenderer != null) {
                renderBothChartsToImages();
            }
            
            LOGGER.info("Auto-Refresh nach Scroll erfolgreich abgeschlossen");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Auto-Refresh nach Scroll", e);
        }
    }
    
    /**
     * Erstellt die Toolbar asynchron
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
     * Erstellt Chart-Panels erst wenn Daten verfügbar sind
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
     * Wechselt das Zeitintervall und erzwingt Chart-Neurendering
     */
    private void changeTimeScale(TimeScale newScale) {
        if (newScale == currentTimeScale) {
            LOGGER.info("TimeScale bereits auf " + newScale.getLabel() + " - keine Änderung");
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
        
        // TimeScale wechseln
        TimeScale oldScale = currentTimeScale;
        currentTimeScale = newScale;
        
        // TimeScale-Change-Counter erhöhen für Cache-Invalidierung
        timeScaleChangeCounter++;
        LOGGER.info("TimeScale-Wechsel #" + timeScaleChangeCounter);
        
        // Charts-Group Label aktualisieren
        if (scrolledComposite != null && !scrolledComposite.isDisposed() && 
            scrolledComposite.getParent() instanceof Group) {
            Group chartsGroup = (Group) scrolledComposite.getParent();
            chartsGroup.setText("Charts (Enhanced - Auto-Refresh) - " + currentTimeScale.getLabel());
        }
        
        // Charts nur aktualisieren wenn Daten vorhanden sind
        if (tickDataSet != null && isInitialSetupComplete && isDataLoaded) {
            LOGGER.info("Triggere Chart-Update für neues TimeScale: " + currentTimeScale.getLabel());
            updateChartsWithCurrentData();
        } else {
            LOGGER.info("Charts können noch nicht aktualisiert werden - Daten fehlen oder Setup läuft noch");
        }
    }
    
    /**
     * Aktualisiert beide Chart-Panels ohne refreshCounter zu erhöhen
     */
    public void updateChartsWithCurrentData() {
        if (!isInitialSetupComplete) {
            LOGGER.info("Initial Setup noch nicht abgeschlossen - Chart-Update wird verschoben");
            return;
        }
        
        LOGGER.info("=== UPDATE BEIDE CHART-PANELS (ENHANCED) für TimeScale: " + currentTimeScale.getLabel() + " ===");
        
        if (tickDataSet == null) {
            LOGGER.warning("tickDataSet ist NULL - kann Charts nicht aktualisieren");
            return;
        }
        
        try {
            LOGGER.info("Chart-Update für TimeScale: " + currentTimeScale.getLabel());
            
            // Daten filtern
            filteredTicks = TickDataFilter.filterTicksForTimeScale(tickDataSet, currentTimeScale);
            
            if (filteredTicks == null || filteredTicks.isEmpty()) {
                filteredTicks = tickDataSet.getTicks();
                LOGGER.info("FALLBACK: Verwende alle verfügbaren Ticks");
            }
            
            LOGGER.info("TimeScale " + currentTimeScale.getLabel() + ": " + filteredTicks.size() + " gefilterte Ticks");
            
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
            
            // Chart-Images neu rendern nach Update!
            renderBothChartsToImages();
            
            updateInfoPanel();
            
            LOGGER.info("Beide Chart-Panels erfolgreich aktualisiert (ENHANCED) für TimeScale: " + currentTimeScale.getLabel());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FEHLER beim Aktualisieren der Chart-Panels", e);
        }
    }
    
    /**
     * Rendert beide Chart-Panels mit intelligentem Cache
     */
    private void renderBothChartsToImages() {
        if (chartManager == null || imageRenderer == null || !isInitialSetupComplete) {
            return;
        }
        
        try {
            // Verwende timeScaleChangeCounter statt refreshCounter
            imageRenderer.renderBothChartsToImages(
                chartManager.getDrawdownChart(),
                chartManager.getProfitChart(),
                chartWidth,
                drawdownChartHeight,
                profitChartHeight,
                zoomFactor,
                currentTimeScale,
                timeScaleChangeCounter
            );
            
            // Chart-Panels neu zeichnen
            if (drawdownPanel != null && drawdownPanel.isReady()) {
                drawdownPanel.getCanvas().redraw();
            }
            if (profitPanel != null && profitPanel.isReady()) {
                profitPanel.getCanvas().redraw();
            }
            
            LOGGER.info("Charts gerendert mit TimeScale: " + currentTimeScale.getLabel() + 
                       ", TimeScale-Change #" + timeScaleChangeCounter);
            
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
     * Lädt Daten asynchron
     */
    private void loadDataAsync() {
        LOGGER.info("=== ENHANCED ASYNC DATEN-LOAD START ===");
        
        updateInfoPanelLoading("Lade Tick-Daten...");
        
        new Thread(() -> {
            try {
                LOGGER.info("Background-Thread: Lade Tick-Daten für " + signalId);
                
                updateLoadingProgress("Prüfe Tick-Datei...");
                Thread.sleep(50);
                
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
                LOGGER.info("ERFOLG: Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks (ENHANCED)");
                
                updateLoadingProgress("Bereite Charts vor...");
                Thread.sleep(100);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateChartsWithCurrentData();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER beim enhanced Laden der Tick-Daten", e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "EnhancedTickDataLoader-" + signalId).start();
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
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Setup läuft... [ENHANCED]";
        infoLabel.setText(info);
        
        if (detailsText != null) {
            detailsText.setText("=== ENHANCED CHART WINDOW (SCROLL-FIX) ===\n" +
                               "Chart-Panels: Werden nach Daten-Load erstellt\n" +
                               "Toolbar: Async erstellt mit Chart-Refresh-Button\n" +
                               "Scroll-Handling: Timer-basiertes Auto-Refresh aktiviert\n" +
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
                    detailsText.setText("=== ENHANCED ASYNC LOADING (SCROLL-FIX) ===\n" +
                                       "Status: " + status + "\n" +
                                       "Signal: " + signalId + "\n" +
                                       "Provider: " + providerName + "\n" +
                                       "Setup complete: " + isInitialSetupComplete + "\n" +
                                       "Data loaded: " + isDataLoaded + "\n" +
                                       "Scroll-Handler: Aktiv\n" +
                                       "Auto-Refresh: Nach " + SCROLL_REFRESH_DELAY + "ms\n" +
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
        
        if (isScrolling) {
            info.append("   [SCROLLING...]");
        }
        
        infoLabel.setText(info.toString());
        
        if (detailsText != null && !detailsText.isDisposed()) {
            StringBuilder details = new StringBuilder();
            details.append("=== ENHANCED CHART WINDOW (SCROLL-FIX AKTIV) ===\n");
            details.append("Drawdown-Panel: ").append(drawdownPanel != null && drawdownPanel.isReady() ? "OK" : "LOADING").append("\n");
            details.append("Profit-Panel: ").append(profitPanel != null && profitPanel.isReady() ? "OK" : "LOADING").append("\n");
            details.append("Toolbar: ").append(toolbar != null && !toolbar.isDisposed() ? "OK" : "LOADING").append("\n");
            details.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
            details.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
            details.append("Setup Complete: ").append(isInitialSetupComplete).append("\n");
            details.append("Scroll-Status: ").append(isScrolling ? "SCROLLING" : "IDLE").append("\n");
            details.append("Auto-Refresh-Delay: ").append(SCROLL_REFRESH_DELAY).append("ms\n");
            
            if (tickDataSet != null && filteredTicks != null) {
                details.append("Tick-Daten: ").append(tickDataSet.getTickCount()).append(" gesamt, ").append(filteredTicks.size()).append(" gefiltert\n");
            }
            
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
            details.append("TimeScale-Changes: ").append(timeScaleChangeCounter).append("\n");
            
            detailsText.setText(details.toString());
        }
        
        if (shell != null && !shell.isDisposed()) {
            shell.setText("Charts - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel() + " [ENHANCED #" + refreshCounter + "]");
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten [ENHANCED]");
        
        if (detailsText != null && !detailsText.isDisposed()) {
            detailsText.setText("Keine Tick-Daten verfügbar.\nDas Chart-System ist trotzdem funktional.\nScroll-Fix: Aktiv");
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
        errorBox.setText("Fehler im Enhanced Chart Window");
        errorBox.setMessage("ENHANCED CHART WINDOW (SCROLL-FIX):\n\n" + message);
        errorBox.open();
    }
    
    /**
     * Schließt das Fenster
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        // Scroll-Timer abbrechen
        if (scrollRefreshTimer != null) {
            display.timerExec(-1, scrollRefreshTimer);
        }
        
        LOGGER.info("=== SCHLIESSE ENHANCED CHART WINDOW ===");
        
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
        LOGGER.info("Enhanced ChartWindow geöffnet für Signal: " + signalId);
    }
    
    // ========== TOOLBAR CALLBACKS IMPLEMENTATION ==========
    
    /**
     * onRefresh erhöht nur refreshCounter für Info-Display
     */
    @Override
    public void onRefresh() {
        refreshCounter++;
        LOGGER.info("Enhanced Refresh #" + refreshCounter + " angefordert");
        
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
                toolbar.setRefreshText("🔄 Daten laden");
            }
        });
    }
    
    /**
     * NEU: Chart-only Refresh ohne Daten neu zu laden
     */
    @Override
    public void onChartRefresh() {
        LOGGER.info("Chart-Refresh angefordert (ohne Daten-Reload)");
        
        if (toolbar != null) {
            toolbar.setChartRefreshEnabled(false);
            toolbar.setChartRefreshText("⏳ Refreshing...");
        }
        
        try {
            // Nur Charts neu rendern, keine Daten laden
            if (isDataLoaded && filteredTicks != null) {
                renderBothChartsToImages();
                
                // Canvas neu zeichnen
                if (drawdownPanel != null && drawdownPanel.isReady()) {
                    drawdownPanel.getCanvas().redraw();
                }
                if (profitPanel != null && profitPanel.isReady()) {
                    profitPanel.getCanvas().redraw();
                }
                
                LOGGER.info("Chart-Refresh erfolgreich abgeschlossen");
            } else {
                LOGGER.warning("Chart-Refresh nicht möglich - keine Daten geladen");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Chart-Refresh", e);
        }
        
        // Button wieder aktivieren
        display.timerExec(1000, () -> {
            if (!isWindowClosed && toolbar != null && !toolbar.isDisposed()) {
                toolbar.setChartRefreshEnabled(true);
                toolbar.setChartRefreshText("📊 Charts");
            }
        });
    }
    
    /**
     * Zoom-Operationen invalidieren den Cache durch timeScaleChangeCounter
     */
    @Override
    public void onZoomIn() {
        zoomFactor *= 1.2;
        timeScaleChangeCounter++;
        renderBothChartsToImages();
        LOGGER.info("Zoom In: " + zoomFactor);
    }
    
    @Override
    public void onZoomOut() {
        zoomFactor /= 1.2;
        timeScaleChangeCounter++;
        renderBothChartsToImages();
        LOGGER.info("Zoom Out: " + zoomFactor);
    }
    
    @Override
    public void onResetZoom() {
        zoomFactor = 1.0;
        timeScaleChangeCounter++;
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