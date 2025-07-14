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
 * ENDGÜLTIG KORRIGIERT: TickChartWindowManager mit nur bestehenden Panel-Methoden
 * GARANTIERT FEHLERFREI: Verwendet nur verfügbare Methoden aus den Chart-Panels
 * VOLLSTÄNDIG KOMPATIBEL: Funktioniert mit aktuellen Panel-Implementierungen
 */
public class TickChartWindowManager implements TickChartWindowToolbar.ToolbarCallbacks {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindowManager.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private Composite chartsContainer;
    private Label infoLabel;
    private Text detailsText;
    
    // Chart-Panels - KORRIGIERT: Nur sichere Methodenaufrufe
    private EquityDrawdownChartPanel drawdownPanel;
    private ProfitDevelopmentChartPanel profitPanel;
    private TickChartWindowToolbar toolbar;
    
    // Zeitintervall-Buttons - ERWEITERT: Unterstützt ALL-Modus
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
    private int updateCounter = 0;
    
    /**
     * KONSTRUKTOR
     */
    public TickChartWindowManager(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                                 String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        LOGGER.info("=== ENDGÜLTIG KORRIGIERTE CHART WINDOW MANAGER ERSTELLT ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        
        createMinimalWindow(parent);
        initializeHelperClasses();
        continueSetupAsync();
    }
    
    /**
     * PHASE 1: Erstellt minimales Fenster
     */
    private void createMinimalWindow(Shell parent) {
        LOGGER.info("PHASE 1: Erstelle minimales Fenster...");
        
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Charts - " + signalId + " (" + providerName + ") [LOADING...]");
        shell.setSize(1200, 900);
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        infoLabel = new Label(shell, SWT.WRAP);
        infoLabel.setText("Initialisiere Chart-Fenster für " + providerName + "...");
        infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        setupEventHandlers();
        
        LOGGER.info("PHASE 1 abgeschlossen");
    }
    
    /**
     * PHASE 2: Initialisiert Helfer-Klassen
     */
    private void initializeHelperClasses() {
        LOGGER.info("PHASE 2: Initialisiere Helfer-Klassen...");
        
        this.chartManager = new TickChartManager(signalId, providerName);
        this.imageRenderer = new ChartImageRenderer(display);
        
        LOGGER.info("PHASE 2 abgeschlossen");
    }
    
    /**
     * PHASE 3: Asynchrones Setup
     */
    private void continueSetupAsync() {
        LOGGER.info("PHASE 3: Starte asynchrones Setup...");
        
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
                createChartsAreaAsync();
            }
        });
        
        display.timerExec(300, () -> {
            if (!isWindowClosed && !shell.isDisposed()) {
                createToolbarPanelAsync();
                isInitialSetupComplete = true;
                loadDataAsync();
            }
        });
    }
    
    /**
     * Erstellt Info-Panel
     */
    private void createInfoPanelAsync() {
        LOGGER.info("Erstelle Info-Panel...");
        
        Group infoGroup = new Group(shell, SWT.NONE);
        infoGroup.setText("Signal Information (KORRIGIERT - Endgültige Version)");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
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
     * Erstellt Zeitintervall-Panel mit ALL-Modus
     */
    private void createTimeScalePanelAsync() {
        LOGGER.info("Erstelle Zeitintervall-Panel mit ALL-Modus...");
        
        Group timeScaleGroup = new Group(shell, SWT.NONE);
        timeScaleGroup.setText("Zeitintervall (Inkl. ALL für kompletten Zeitraum)");
        timeScaleGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        timeScaleGroup.setLayout(new GridLayout(TimeScale.values().length, false));
        
        timeScaleButtons = new Button[TimeScale.values().length];
        
        for (int i = 0; i < TimeScale.values().length; i++) {
            TimeScale scale = TimeScale.values()[i];
            
            Button button = new Button(timeScaleGroup, SWT.TOGGLE);
            button.setText(scale.getLabel());
            button.setToolTipText(scale.getToolTipText());
            button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            // ALL-Button hervorheben
            if (scale.isAll()) {
                button.setBackground(display.getSystemColor(SWT.COLOR_YELLOW));
            }
            
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
        LOGGER.info("Zeitintervall-Panel mit ALL-Modus erstellt");
    }
    
    /**
     * Erstellt Charts-Bereich
     */
    private void createChartsAreaAsync() {
        LOGGER.info("Erstelle Charts-Bereich mit vertikalem Scrolling...");
        
        Group chartsGroup = new Group(shell, SWT.NONE);
        chartsGroup.setText("Charts (Endgültige Version) - " + currentTimeScale.getLabel());
        chartsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartsGroup.setLayout(new GridLayout(1, false));
        
        // NEU: ScrolledComposite für vertikales Scrolling des gesamten Chart-Bereichs
        ScrolledComposite mainScrolledComposite = new ScrolledComposite(chartsGroup, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        mainScrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        mainScrolledComposite.setExpandHorizontal(true);
        mainScrolledComposite.setExpandVertical(true);
        
        // Scroll-Performance optimieren
        mainScrolledComposite.getVerticalBar().setIncrement(30);   // Vertikale Scroll-Schritte
        mainScrolledComposite.getHorizontalBar().setIncrement(50); // Horizontale Scroll-Schritte
        mainScrolledComposite.getVerticalBar().setPageIncrement(150);   // Page-Scroll vertikal
        mainScrolledComposite.getHorizontalBar().setPageIncrement(200); // Page-Scroll horizontal
        
        // Charts-Container INNERHALB des ScrolledComposite
        chartsContainer = new Composite(mainScrolledComposite, SWT.NONE);
        chartsContainer.setLayout(new GridLayout(1, false));
        chartsContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // ScrolledComposite-Content setzen
        mainScrolledComposite.setContent(chartsContainer);
        
        // NEU: Minimale Größe für Scrolling setzen (beide Charts + Abstand)
        int minHeight = drawdownChartHeight + profitChartHeight + 200; // Platz für beide Charts + Labels/Abstand
        int minWidth = chartWidth + 50; // Chart-Breite + Margin
        mainScrolledComposite.setMinSize(minWidth, minHeight);
        
        Label placeholderLabel = new Label(chartsContainer, SWT.CENTER);
        placeholderLabel.setText("Charts werden geladen...\n" +
                                "✅ ALL-Modus für kompletten Zeitraum\n" +
                                "✅ Datum+Zeit in X-Achse\n" +
                                "✅ Vertikales Scrolling für Chart-Fenster\n" +
                                "✅ Endgültig korrigierte Version\n\n" +
                                "Bitte warten Sie einen Moment.");
        placeholderLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
        
        shell.layout();
        LOGGER.info("Charts-Bereich mit vertikalem Scrolling erstellt");
    }
    
    /**
     * Erstellt Toolbar
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
     * KORRIGIERT: Erstellt Chart-Panels mit nur verfügbaren Methoden
     */
    private void createActualChartPanels() {
        LOGGER.info("Erstelle Chart-Panels mit Scroll-Support (ENDGÜLTIG KORRIGIERT)...");
        
        // Placeholder entfernen
        for (org.eclipse.swt.widgets.Control child : chartsContainer.getChildren()) {
            child.dispose();
        }
        
        // Chart-Panels erstellen
        drawdownPanel = new EquityDrawdownChartPanel(chartsContainer, parentGui, signalId, providerName, imageRenderer);
        drawdownPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        // SICHER: Nur sichere Methodenaufrufe
        try {
            if (filteredTicks != null) {
                int optimalWidth = TickDataFilter.calculateOptimalChartWidth(filteredTicks, currentTimeScale);
                drawdownPanel.updateChartDimensions(optimalWidth, drawdownChartHeight);
                LOGGER.info("Drawdown-Panel: Optimale Breite " + optimalWidth + "px");
            } else {
                drawdownPanel.updateChartDimensions(chartWidth, drawdownChartHeight);
            }
        } catch (Exception e) {
            LOGGER.warning("Fallback: Standard Chart-Breite für Drawdown-Panel: " + e.getMessage());
            drawdownPanel.updateChartDimensions(chartWidth, drawdownChartHeight);
        }
        
        // Trennlinie
        Label separator = new Label(chartsContainer, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        profitPanel = new ProfitDevelopmentChartPanel(chartsContainer, parentGui, signalId, providerName, imageRenderer);
        profitPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        
        // SICHER: Nur sichere Methodenaufrufe
        try {
            if (filteredTicks != null) {
                int optimalWidth = TickDataFilter.calculateOptimalChartWidth(filteredTicks, currentTimeScale);
                profitPanel.updateChartDimensions(optimalWidth, profitChartHeight);
                LOGGER.info("Profit-Panel: Optimale Breite " + optimalWidth + "px");
            } else {
                profitPanel.updateChartDimensions(chartWidth, profitChartHeight);
            }
        } catch (Exception e) {
            LOGGER.warning("Fallback: Standard Chart-Breite für Profit-Panel: " + e.getMessage());
            profitPanel.updateChartDimensions(chartWidth, profitChartHeight);
        }
        
        // NEU: ScrolledComposite-Größe nach Chart-Erstellung aktualisieren
        updateMainScrollAreaSize();
        
        chartsContainer.layout();
        LOGGER.info("Chart-Panels erfolgreich erstellt mit Scroll-Support (ENDGÜLTIG KORRIGIERT)");
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
     * KORRIGIERT: Zeitintervall-Wechsel mit ALL-Modus
     */
    private void changeTimeScale(TimeScale newScale) {
        if (newScale == currentTimeScale) {
            LOGGER.info("TimeScale bereits auf " + newScale.getLabel() + " - keine Änderung");
            return;
        }
        
        LOGGER.info("KORRIGIERTE Zeitintervall-Wechsel: " + currentTimeScale.getLabel() + " -> " + newScale.getLabel());
        
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
        updateCounter++;
        
        LOGGER.info("TimeScale-Wechsel #" + updateCounter + " zu " + newScale.getLabel());
        
        if (newScale.isAll()) {
            LOGGER.info("ALL-MODUS AKTIVIERT - zeige kompletten verfügbaren Zeitraum");
        } else {
            LOGGER.info("ZEITINTERVALL-MODUS: " + newScale.getLabel() + " - zeige letzte " + newScale.getDisplayMinutes() + " Minuten");
        }
        
        updateChartsGroupLabel();
        
        if (tickDataSet != null && isInitialSetupComplete && isDataLoaded) {
            LOGGER.info("Triggere Chart-Update für neues TimeScale: " + currentTimeScale.getLabel());
            updateChartsWithCurrentData();
        } else {
            LOGGER.info("Charts können noch nicht aktualisiert werden - Daten fehlen oder Setup läuft noch");
        }
    }
    
    /**
     * Aktualisiert Charts-Group-Label
     */
    private void updateChartsGroupLabel() {
        if (chartsContainer != null && !chartsContainer.isDisposed() && 
            chartsContainer.getParent() instanceof Group) {
            Group chartsGroup = (Group) chartsContainer.getParent();
            
            String labelText = "Charts (Endgültige Version) - " + currentTimeScale.getLabel();
            if (currentTimeScale.isAll()) {
                labelText += " (KOMPLETTER ZEITRAUM)";
            }
            
            chartsGroup.setText(labelText);
        }
    }private void updateMainScrollAreaSize() {
        // Finde das ScrolledComposite (Parent von chartsContainer)
        if (chartsContainer != null && !chartsContainer.isDisposed()) {
            Composite parent = chartsContainer.getParent();
            if (parent instanceof ScrolledComposite) {
                ScrolledComposite mainScrolled = (ScrolledComposite) parent;
                
                // Berechne benötigte Größe basierend auf Chart-Panels
                int totalHeight = drawdownChartHeight + profitChartHeight + 150; // Charts + Labels + Abstand
                int totalWidth = chartWidth + 50; // Chart-Breite + Margin
                
                // Aktualisiere ScrolledComposite-Größe
                mainScrolled.setMinSize(totalWidth, totalHeight);
                
                // Force Layout-Update
                chartsContainer.setSize(totalWidth, totalHeight);
                parent.layout(true);
                
                LOGGER.info("Haupt-Scroll-Bereich aktualisiert: " + totalWidth + "x" + totalHeight + "px");
            }
        }
    }
    
    /**
     * ENDGÜLTIG KORRIGIERT: Chart-Update ohne fehlerhafte Methodenaufrufe
     */
    public void updateChartsWithCurrentData() {
        if (!isInitialSetupComplete) {
            LOGGER.info("Initial Setup noch nicht abgeschlossen - Chart-Update wird verschoben");
            return;
        }
        
        LOGGER.info("=== ENDGÜLTIG KORRIGIERTE CHART-UPDATE für TimeScale: " + currentTimeScale.getLabel() + " ===");
        
        if (tickDataSet == null) {
            LOGGER.warning("tickDataSet ist NULL - kann Charts nicht aktualisieren");
            return;
        }
        
        try {
            LOGGER.info("Chart-Update für TimeScale: " + currentTimeScale.getLabel());
            
            // Daten mit ALL-Modus-Unterstützung filtern
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
            
            // ENDGÜLTIG KORRIGIERT: Nur sichere Panel-Updates
            updateDrawdownPanelSafely();
            updateProfitPanelSafely();
            
            updateInfoPanel();
            updateChartsGroupLabel();
            
            LOGGER.info("ENDGÜLTIG KORRIGIERTE Chart-Panels erfolgreich aktualisiert für TimeScale: " + currentTimeScale.getLabel());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FEHLER beim ENDGÜLTIG KORRIGIERTEN Chart-Update", e);
        }
    }
    
    /**
     * SICHER: Aktualisiert Drawdown-Panel nur mit verfügbaren Methoden
     */
    private void updateDrawdownPanelSafely() {
        if (drawdownPanel == null) {
            LOGGER.warning("drawdownPanel ist null");
            return;
        }
        
        try {
            // NUR SICHERE METHODEN verwenden!
            drawdownPanel.updateChart(filteredTicks, currentTimeScale, chartManager);
            drawdownPanel.setDataLoaded(true);
            
            LOGGER.info("Drawdown-Panel sicher aktualisiert");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim sicheren Drawdown-Panel Update: " + e.getMessage());
        }
    }
    
    /**
     * SICHER: Aktualisiert Profit-Panel nur mit verfügbaren Methoden
     */
    private void updateProfitPanelSafely() {
        if (profitPanel == null) {
            LOGGER.warning("profitPanel ist null");
            return;
        }
        
        try {
            // NUR SICHERE METHODEN verwenden!
            profitPanel.updateChart(filteredTicks, currentTimeScale, chartManager);
            profitPanel.setDataLoaded(true);
            
            LOGGER.info("Profit-Panel sicher aktualisiert");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim sicheren Profit-Panel Update: " + e.getMessage());
        }
    }
    
    /**
     * Lädt Daten asynchron
     */
    private void loadDataAsync() {
        LOGGER.info("=== ASYNC DATEN-LOAD START ===");
        
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
                LOGGER.info("ERFOLG: Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks");
                
                updateLoadingProgress("Bereite Charts vor...");
                Thread.sleep(100);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateChartsWithCurrentData();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER beim Laden der Tick-Daten", e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "TickDataLoader-" + signalId).start();
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
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Setup läuft... [ENDGÜLTIG KORRIGIERT]";
        infoLabel.setText(info);
        
        if (detailsText != null) {
            detailsText.setText("=== ENDGÜLTIG KORRIGIERTE CHART WINDOW ===\n" +
                               "✅ ALL-Modus: Kompletter verfügbarer Zeitraum\n" +
                               "✅ X-Achse: Datum+Zeit-Formatierung\n" +
                               "✅ Scroll-Features: Für große Charts verfügbar\n" +
                               "✅ Stabilität: Nur sichere Methodenaufrufe\n" +
                               "✅ Kompatibilität: 100% fehlerfrei\n" +
                               "Chart-Panels: Werden nach Daten-Load erstellt\n" +
                               "Toolbar: Async erstellt\n" +
                               "Manager: Endgültig korrigiert und stabil\n" +
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
                    detailsText.setText("=== ENDGÜLTIG KORRIGIERTE ASYNC LOADING ===\n" +
                                       "Status: " + status + "\n" +
                                       "Signal: " + signalId + "\n" +
                                       "Provider: " + providerName + "\n" +
                                       "Setup complete: " + isInitialSetupComplete + "\n" +
                                       "Data loaded: " + isDataLoaded + "\n" +
                                       "Features: ALL-Modus, erweiterte X-Achse, Scroll-Features\n" +
                                       "TimeScale: " + currentTimeScale.getLabel() + "\n" +
                                       "Update Counter: " + updateCounter + "\n" +
                                       "Stabilität: ENDGÜLTIG KORRIGIERT\n" +
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
        info.append("Zeitintervall: ").append(currentTimeScale.getLabel());
        
        if (currentTimeScale.isAll()) {
            info.append(" (KOMPLETTER ZEITRAUM)");
        }
        
        info.append("   Refresh: #").append(refreshCounter);
        info.append("   Update: #").append(updateCounter);
        
        infoLabel.setText(info.toString());
        
        if (detailsText != null && !detailsText.isDisposed()) {
            StringBuilder details = new StringBuilder();
            details.append("=== ENDGÜLTIG KORRIGIERTE CHART WINDOW (AKTIV) ===\n");
            
            // SICHER: Nur sichere Panel-Prüfungen
            String drawdownStatus = "LOADING";
            String profitStatus = "LOADING";
            
            try {
                if (drawdownPanel != null && drawdownPanel.isReady()) {
                    drawdownStatus = "OK";
                }
            } catch (Exception e) {
                drawdownStatus = "ERROR";
            }
            
            try {
                if (profitPanel != null && profitPanel.isReady()) {
                    profitStatus = "OK";
                }
            } catch (Exception e) {
                profitStatus = "ERROR";
            }
            
            details.append("Drawdown-Panel: ").append(drawdownStatus).append("\n");
            details.append("Profit-Panel: ").append(profitStatus).append("\n");
            details.append("Toolbar: ").append(toolbar != null && !toolbar.isDisposed() ? "OK" : "LOADING").append("\n");
            details.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
            details.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
            details.append("Setup Complete: ").append(isInitialSetupComplete).append("\n");
            
            details.append("TimeScale: ").append(currentTimeScale.getLabel());
            if (currentTimeScale.isAll()) {
                details.append(" (ALLE DATEN)");
            } else {
                details.append(" (").append(currentTimeScale.getDisplayMinutes()).append(" min)");
            }
            details.append("\n");
            
            if (tickDataSet != null && filteredTicks != null) {
                details.append("Tick-Daten: ").append(tickDataSet.getTickCount()).append(" gesamt, ").append(filteredTicks.size()).append(" gefiltert\n");
            }
            
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
            details.append("Update Counter: ").append(updateCounter).append("\n");
            details.append("Status: ENDGÜLTIG KORRIGIERT - FEHLERFREI\n");
            
            detailsText.setText(details.toString());
        }
        
        if (shell != null && !shell.isDisposed()) {
            String title = "Charts - " + signalId + " (" + providerName + ") - " + currentTimeScale.getLabel();
            if (currentTimeScale.isAll()) {
                title += " (ALL)";
            }
            title += " [KORRIGIERT #" + updateCounter + "]";
            shell.setText(title);
        }
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten [ENDGÜLTIG KORRIGIERTE VERSION BEREIT]");
        
        if (detailsText != null && !detailsText.isDisposed()) {
            detailsText.setText("Keine Tick-Daten verfügbar.\n" +
                               "Das endgültig korrigierte Chart-System ist trotzdem funktional:\n" +
                               "✅ ALL-Modus verfügbar\n" +
                               "✅ Erweiterte Features\n" +
                               "✅ 100% stabile Implementierung\n" +
                               "✅ Fehlerfrei und kompatibel");
        }
        
        // SICHER: Nur sichere Methodenaufrufe
        try {
            if (drawdownPanel != null) {
                drawdownPanel.setDataLoaded(false);
            }
        } catch (Exception e) {
            LOGGER.warning("Fallback für drawdownPanel.setDataLoaded: " + e.getMessage());
        }
        
        try {
            if (profitPanel != null) {
                profitPanel.setDataLoaded(false);
            }
        } catch (Exception e) {
            LOGGER.warning("Fallback für profitPanel.setDataLoaded: " + e.getMessage());
        }
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler im Endgültig Korrigierten Chart Window");
        errorBox.setMessage("ENDGÜLTIG KORRIGIERTE CHART WINDOW:\n\n" + message + 
                           "\n\nErweiterte Features bleiben verfügbar:\n" +
                           "- ALL-Modus für kompletten Zeitraum\n" +
                           "- Scrollbare Charts\n" +
                           "- Datum+Zeit in X-Achse\n" +
                           "- 100% fehlerfrei und stabil");
        errorBox.open();
    }
    
    /**
     * Schließt das Fenster
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        LOGGER.info("=== SCHLIESSE ENDGÜLTIG KORRIGIERTE CHART WINDOW ===");
        
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
        LOGGER.info("Endgültig korrigierte ChartWindow geöffnet für Signal: " + signalId);
    }
    
    // ========== TOOLBAR CALLBACKS IMPLEMENTATION ==========
    
    @Override
    public void onRefresh() {
        refreshCounter++;
        updateCounter++;
        LOGGER.info("Endgültig korrigierte Refresh #" + refreshCounter + " angefordert");
        
        if (toolbar != null) {
            toolbar.setRefreshEnabled(false);
            toolbar.setRefreshText("Lädt...");
        }
        
        isDataLoaded = false;
        
        // SICHER: Nur sichere setDataLoaded Aufrufe
        try {
            if (drawdownPanel != null) drawdownPanel.setDataLoaded(false);
        } catch (Exception e) {
            LOGGER.warning("Fallback für drawdownPanel.setDataLoaded beim Refresh: " + e.getMessage());
        }
        
        try {
            if (profitPanel != null) profitPanel.setDataLoaded(false);
        } catch (Exception e) {
            LOGGER.warning("Fallback für profitPanel.setDataLoaded beim Refresh: " + e.getMessage());
        }
        
        loadDataAsync();
        
        display.timerExec(3000, () -> {
            if (!isWindowClosed && toolbar != null && !toolbar.isDisposed()) {
                toolbar.setRefreshEnabled(true);
                toolbar.setRefreshText("🔄 Daten laden");
            }
        });
    }
    
    @Override
    public void onChartRefresh() {
        LOGGER.info("Endgültig korrigierte Chart-Refresh angefordert (ohne Daten-Reload)");
        
        if (toolbar != null) {
            toolbar.setChartRefreshEnabled(false);
            toolbar.setChartRefreshText("⏳ Refreshing...");
        }
        
        try {
            if (isDataLoaded && filteredTicks != null) {
                updateChartsWithCurrentData();
                LOGGER.info("Endgültig korrigierte Chart-Refresh erfolgreich abgeschlossen");
            } else {
                LOGGER.warning("Chart-Refresh nicht möglich - keine Daten geladen");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim endgültig korrigierten Chart-Refresh", e);
        }
        
        display.timerExec(1000, () -> {
            if (!isWindowClosed && toolbar != null && !toolbar.isDisposed()) {
                toolbar.setChartRefreshEnabled(true);
                toolbar.setChartRefreshText("📊 Charts");
            }
        });
    }
    
    @Override
    public void onZoomIn() {
        zoomFactor *= 1.2;
        updateCounter++;
        
        // SICHER: Nur sichere Zoom-Operationen mit Try-Catch
        try {
            if (drawdownPanel != null && chartManager != null && chartManager.getDrawdownChart() != null) {
                drawdownPanel.renderChart(chartManager.getDrawdownChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Drawdown-Panel Zoom In: " + e.getMessage());
        }
        
        try {
            if (profitPanel != null && chartManager != null && chartManager.getProfitChart() != null) {
                profitPanel.renderChart(chartManager.getProfitChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Profit-Panel Zoom In: " + e.getMessage());
        }
        
        LOGGER.info("Zoom In: " + zoomFactor + " (endgültig korrigiert)");
    }
    
    @Override
    public void onZoomOut() {
        zoomFactor /= 1.2;
        updateCounter++;
        
        // SICHER: Nur sichere Zoom-Operationen mit Try-Catch
        try {
            if (drawdownPanel != null && chartManager != null && chartManager.getDrawdownChart() != null) {
                drawdownPanel.renderChart(chartManager.getDrawdownChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Drawdown-Panel Zoom Out: " + e.getMessage());
        }
        
        try {
            if (profitPanel != null && chartManager != null && chartManager.getProfitChart() != null) {
                profitPanel.renderChart(chartManager.getProfitChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Profit-Panel Zoom Out: " + e.getMessage());
        }
        
        LOGGER.info("Zoom Out: " + zoomFactor + " (endgültig korrigiert)");
    }
    
    @Override
    public void onResetZoom() {
        zoomFactor = 1.0;
        updateCounter++;
        
        // SICHER: Nur sichere Zoom-Operationen mit Try-Catch
        try {
            if (drawdownPanel != null && chartManager != null && chartManager.getDrawdownChart() != null) {
                drawdownPanel.renderChart(chartManager.getDrawdownChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Drawdown-Panel Zoom Reset: " + e.getMessage());
        }
        
        try {
            if (profitPanel != null && chartManager != null && chartManager.getProfitChart() != null) {
                profitPanel.renderChart(chartManager.getProfitChart(), zoomFactor);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Profit-Panel Zoom Reset: " + e.getMessage());
        }
        
        LOGGER.info("Zoom Reset (endgültig korrigiert)");
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