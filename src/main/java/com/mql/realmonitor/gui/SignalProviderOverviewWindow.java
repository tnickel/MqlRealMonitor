package com.mql.realmonitor.gui;

import java.util.ArrayList;
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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * Chart-Übersichtsfenster für alle Signalprovider
 * Zeigt eine scrollbare Tabelle mit Drawdown- und Profit-Charts für jeden Provider
 * Timeframe: M15/H1/H4 umschaltbar, Chart-Größe: 50% der normalen Größe
 */
public class SignalProviderOverviewWindow {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderOverviewWindow.class.getName());
    
    // Chart-Dimensionen (VERGRÖSSERT für bessere Vollflächendarstellung)
    private static final int CHART_WIDTH = 500;  // Erhöht von 400 auf 500
    private static final int CHART_HEIGHT = 280; // Erhöht von 200 auf 280
    
    // UI Komponenten
    private Shell shell;
    private ScrolledComposite scrolledComposite;
    private Composite chartsContainer;
    private Label statusLabel;
    private Label infoLabel;
    private Button refreshButton;
    private Button closeButton;
    private Combo timeframeCombo;
    
    // Parent GUI und Display
    private final MqlRealMonitorGUI parentGui;
    private final Display display;
    
    // Chart-Panels Liste
    private List<SignalOverviewPanel> overviewPanels;
    
    // Status
    private volatile boolean isLoading = false;
    private volatile boolean isWindowClosed = false;
    private int refreshCounter = 0;
    
    // Aktueller Timeframe (umschaltbar)
    private TimeScale currentTimeScale = TimeScale.M15;
    
    /**
     * Konstruktor
     */
    public SignalProviderOverviewWindow(Shell parent, MqlRealMonitorGUI parentGui) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.overviewPanels = new ArrayList<>();
        
        LOGGER.info("=== SIGNAL PROVIDER OVERVIEW WINDOW ERSTELLT ===");
        
        createWindow(parent);
    }
    
    /**
     * Erstellt das Hauptfenster - OPTIMIERT für maximale Chart-Darstellung
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("📊 Chart-Übersicht - Alle Signalprovider (Umschaltbare Timeframes)");
        shell.setSize(1400, 900); // VERGRÖSSERT: Mehr Platz für Charts
        shell.setLayout(new GridLayout(1, false));
        
        centerWindow(parent);
        
        createHeaderPanel();
        createScrollableChartsArea();
        createButtonPanel();
        setupEventHandlers();
        
        LOGGER.info("Chart-Übersichtsfenster UI erstellt (mit umschaltbaren Timeframes)");
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
     * Erstellt das Header-Panel mit Informationen und Timeframe-Auswahl
     */
    private void createHeaderPanel() {
        Group headerGroup = new Group(shell, SWT.NONE);
        headerGroup.setText("Chart-Übersicht - Alle Signalprovider");
        headerGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        headerGroup.setLayout(new GridLayout(1, false));
        
        // Timeframe-Auswahl Panel
        Composite timeframePanel = new Composite(headerGroup, SWT.NONE);
        timeframePanel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        timeframePanel.setLayout(new GridLayout(3, false));
        
        Label timeframeLabel = new Label(timeframePanel, SWT.NONE);
        timeframeLabel.setText("Timeframe:");
        timeframeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        if (parentGui.getBoldFont() != null) {
            timeframeLabel.setFont(parentGui.getBoldFont());
        }
        
        timeframeCombo = new Combo(timeframePanel, SWT.DROP_DOWN | SWT.READ_ONLY);
        timeframeCombo.setItems(new String[] {"M15", "H1", "H4"});
        timeframeCombo.select(0); // M15 als Standard
        timeframeCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        timeframeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onTimeframeChanged();
            }
        });
        
        // Spacer
        Label spacer = new Label(timeframePanel, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        infoLabel = new Label(headerGroup, SWT.WRAP);
        updateInfoLabel();
        infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (parentGui.getBoldFont() != null) {
            infoLabel.setFont(parentGui.getBoldFont());
        }
        
        statusLabel = new Label(headerGroup, SWT.WRAP);
        statusLabel.setText("Lade Provider-Daten...");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        LOGGER.fine("Header-Panel mit Timeframe-Auswahl erstellt");
    }
    
    /**
     * Aktualisiert das Info-Label basierend auf dem aktuellen Timeframe
     */
    private void updateInfoLabel() {
        String timeframeDesc = getTimeframeDescription(currentTimeScale);
        infoLabel.setText("Diese Übersicht zeigt Drawdown- und Profit-Charts für alle verfügbaren Signalprovider.\n" +
                         "Timeframe: " + currentTimeScale.name() + " (" + timeframeDesc + ") | Charts: Vollflächig optimiert | Automatische Aktualisierung alle 5 Minuten");
    }
    
    /**
     * Gibt eine Beschreibung für den Timeframe zurück
     */
    private String getTimeframeDescription(TimeScale timeScale) {
        switch (timeScale) {
            case M15:
                return "letzte 30 Stunden";
            case H1:
                return "letzte 5 Tage";
            case H4:
                return "letzte 20 Tage";
            default:
                return "unbekannt";
        }
    }
    
    /**
     * Event-Handler für Timeframe-Wechsel
     */
    private void onTimeframeChanged() {
        if (isLoading) {
            LOGGER.info("Lädt bereits - Timeframe-Wechsel ignoriert");
            return;
        }
        
        int selectedIndex = timeframeCombo.getSelectionIndex();
        TimeScale newTimeScale;
        
        switch (selectedIndex) {
            case 0:
                newTimeScale = TimeScale.M15;
                break;
            case 1:
                newTimeScale = TimeScale.H1;
                break;
            case 2:
                newTimeScale = TimeScale.H4;
                break;
            default:
                newTimeScale = TimeScale.M15;
                break;
        }
        
        if (newTimeScale != currentTimeScale) {
            currentTimeScale = newTimeScale;
            LOGGER.info("Timeframe geändert zu: " + currentTimeScale.name());
            
            // Info-Label aktualisieren
            updateInfoLabel();
            
            // Charts neu laden mit neuem Timeframe
            refreshAllCharts();
        }
    }
    
    /**
     * Erstellt den scrollbaren Charts-Bereich
     */
    private void createScrollableChartsArea() {
        Group chartsGroup = new Group(shell, SWT.NONE);
        chartsGroup.setText("Provider-Charts (Spalte 1: Drawdown, Spalte 2: Profit)");
        chartsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartsGroup.setLayout(new GridLayout(1, false));
        
        // ScrolledComposite erstellen
        scrolledComposite = new ScrolledComposite(chartsGroup, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        
        // Container für Chart-Panels erstellen
        chartsContainer = new Composite(scrolledComposite, SWT.NONE);
        chartsContainer.setLayout(new GridLayout(1, false)); // Eine Spalte für alle Provider-Panels
        chartsContainer.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // ScrolledComposite konfigurieren - OPTIMIERT für größere Charts
        scrolledComposite.setContent(chartsContainer);
        scrolledComposite.setMinSize(1300, 800); // VERGRÖSSERT: Mindestgröße für bessere Chart-Darstellung
        
        LOGGER.info("Scrollbarer Charts-Bereich erstellt");
    }
    
    /**
     * Erstellt die Button-Panel am unteren Rand
     */
    private void createButtonPanel() {
        Composite buttonPanel = new Composite(shell, SWT.NONE);
        buttonPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonPanel.setLayout(new GridLayout(3, false));
        
        refreshButton = new Button(buttonPanel, SWT.PUSH);
        refreshButton.setText("🔄 Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        refreshButton.setToolTipText("Lädt alle Charts mit dem aktuellen Timeframe neu");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshAllCharts();
            }
        });
        
        // Spacer
        Label spacer = new Label(buttonPanel, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        closeButton = new Button(buttonPanel, SWT.PUSH);
        closeButton.setText("Schließen");
        closeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                closeWindow();
            }
        });
        
        LOGGER.fine("Button-Panel erstellt");
    }
    
    /**
     * Setup Event Handler
     */
    private void setupEventHandlers() {
        shell.addListener(SWT.Close, event -> {
            closeWindow();
        });
        
        // Resize-Handler für Scroll-Update
        shell.addListener(SWT.Resize, event -> {
            updateScrollableArea();
        });
    }
    
    /**
     * Lädt alle Provider-Daten und erstellt Chart-Panels
     */
    private void loadAllProviderCharts() {
        if (isLoading) {
            LOGGER.info("Laden bereits in Bearbeitung - überspringe");
            return;
        }
        
        isLoading = true;
        refreshCounter++;
        
        // UI in Lade-Zustand versetzen
        if (!isWindowClosed && !statusLabel.isDisposed()) {
            statusLabel.setText("Lade Provider-Daten mit " + currentTimeScale.name() + "... (Refresh #" + refreshCounter + ")");
            refreshButton.setEnabled(false);
            refreshButton.setText("Lädt...");
            timeframeCombo.setEnabled(false); // ComboBox während Laden deaktivieren
        }
        
        LOGGER.info("=== LADE ALLE PROVIDER-CHARTS MIT " + currentTimeScale.name() + " (Refresh #" + refreshCounter + ") ===");
        
        // KORRIGIERT: Provider-Daten im UI-Thread laden (SWT-Thread-Sicherheit)
        List<ProviderData> providerDataList = loadProviderDataFromMainTable();
        
        if (providerDataList.isEmpty()) {
            if (!isWindowClosed && !statusLabel.isDisposed()) {
                statusLabel.setText("Keine Provider-Daten gefunden. Bitte starten Sie das Monitoring.");
                refreshButton.setEnabled(true);
                refreshButton.setText("🔄 Aktualisieren");
                timeframeCombo.setEnabled(true);
            }
            isLoading = false;
            return;
        }
        
        LOGGER.info("Gefundene Provider: " + providerDataList.size());
        
        // Alte Panels entfernen (im UI-Thread)
        if (!isWindowClosed && !chartsContainer.isDisposed()) {
            clearExistingPanels();
        }
        
        // Chart-Rendering in separatem Thread
        new Thread(() -> {
            try {
                // Neue Panels erstellen
                int loadedCount = 0;
                for (ProviderData providerData : providerDataList) {
                    if (isWindowClosed) {
                        break;
                    }
                    
                    try {
                        // Tick-Daten laden
                        TickDataLoader.TickDataSet tickDataSet = loadTickDataForProvider(providerData);
                        
                        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
                            // Chart-Panel erstellen (zurück im UI-Thread)
                            final TickDataLoader.TickDataSet finalTickDataSet = tickDataSet;
                            display.syncExec(() -> {
                                if (!isWindowClosed && !chartsContainer.isDisposed()) {
                                    createProviderPanel(providerData, finalTickDataSet);
                                }
                            });
                            loadedCount++;
                        } else {
                            LOGGER.warning("Keine Tick-Daten für Provider: " + providerData.signalId);
                        }
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Fehler beim Laden von Provider: " + providerData.signalId, e);
                    }
                }
                
                final int finalLoadedCount = loadedCount;
                final int totalCount = providerDataList.size();
                
                // UI aktualisieren
                display.asyncExec(() -> {
                    if (!isWindowClosed && !statusLabel.isDisposed()) {
                        statusLabel.setText(String.format("Charts geladen: %d/%d Provider erfolgreich mit %s (Refresh #%d)", 
                                                         finalLoadedCount, totalCount, currentTimeScale.name(), refreshCounter));
                        refreshButton.setEnabled(true);
                        refreshButton.setText("🔄 Aktualisieren");
                        timeframeCombo.setEnabled(true); // ComboBox wieder aktivieren
                        
                        updateScrollableArea();
                        
                        // Auto-Refresh in 5 Minuten
                        scheduleAutoRefresh();
                    }
                });
                
                LOGGER.info("Provider-Charts Laden abgeschlossen: " + finalLoadedCount + "/" + totalCount + " mit " + currentTimeScale.name());
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fataler Fehler beim Laden der Provider-Charts", e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !statusLabel.isDisposed()) {
                        statusLabel.setText("Fehler beim Laden der Provider-Charts: " + e.getMessage());
                        refreshButton.setEnabled(true);
                        refreshButton.setText("🔄 Aktualisieren");
                        timeframeCombo.setEnabled(true);
                        
                        showErrorMessage("Fehler beim Laden", 
                                       "Konnte Provider-Charts nicht laden:\n" + e.getMessage());
                    }
                });
            } finally {
                isLoading = false;
            }
        }, "ProviderChartsLoader-" + refreshCounter).start();
    }
    
    /**
     * KORRIGIERT: Lädt Provider-Daten aus der Haupttabelle (im UI-Thread)
     * ERWEITERT: Jetzt mit Favoritenklasse für Einfärbung des Provider-Info Panels
     */
    private List<ProviderData> loadProviderDataFromMainTable() {
        List<ProviderData> providerDataList = new ArrayList<>();
        
        try {
            // Zugriff auf die Haupttabelle
            SignalProviderTable providerTable = parentGui.getProviderTable();
            if (providerTable == null) {
                LOGGER.warning("Provider-Tabelle ist null");
                return providerDataList;
            }
            
            Table table = providerTable.getTable();
            if (table == null || table.isDisposed()) {
                LOGGER.warning("SWT-Tabelle ist null oder disposed");
                return providerDataList;
            }
            
            // KORRIGIERT: Direkt im UI-Thread - kein syncExec nötig
            TableItem[] items = table.getItems();
            LOGGER.info("Analysiere " + items.length + " Tabellen-Einträge");
            
            for (TableItem item : items) {
                try {
                    String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
                    String favoriteClass = item.getText(ProviderTableHelper.COL_FAVORITE_CLASS);  // NEU: Favoritenklasse
                    String providerName = item.getText(ProviderTableHelper.COL_PROVIDER_NAME);
                    String status = item.getText(ProviderTableHelper.COL_STATUS);
                    
                    if (signalId != null && !signalId.trim().isEmpty() && 
                        providerName != null && !providerName.trim().isEmpty() &&
                        !"Lädt...".equals(providerName)) {
                        
                        ProviderData providerData = new ProviderData(signalId, providerName, status, favoriteClass);
                        providerDataList.add(providerData);
                        
                        String favoriteClassDisplay = (favoriteClass != null && !favoriteClass.trim().isEmpty() && !favoriteClass.equals("-")) 
                                                    ? favoriteClass : "Keine";
                        LOGGER.fine("Provider gefunden: " + signalId + " (" + providerName + ", Klasse: " + favoriteClassDisplay + ")");
                    }
                    
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Fehler beim Verarbeiten von Tabellen-Item", e);
                }
            }
            
            LOGGER.info("Provider-Daten aus Haupttabelle geladen: " + providerDataList.size() + " Einträge");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Laden der Provider-Daten aus Haupttabelle", e);
        }
        
        return providerDataList;
    }
    
    /**
     * Lädt Tick-Daten für einen Provider
     */
    private TickDataLoader.TickDataSet loadTickDataForProvider(ProviderData providerData) {
        try {
            String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(providerData.signalId);
            
            TickDataLoader.TickDataSet tickDataSet = TickDataLoader.loadTickData(tickFilePath, providerData.signalId);
            
            if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
                LOGGER.fine("Tick-Daten geladen für " + providerData.signalId + ": " + tickDataSet.getTickCount() + " Ticks");
                return tickDataSet;
            } else {
                LOGGER.warning("Keine Tick-Daten für Provider: " + providerData.signalId);
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Laden der Tick-Daten für " + providerData.signalId, e);
            return null;
        }
    }
    
    /**
     * Erstellt ein Chart-Panel für einen Provider mit dem aktuellen Timeframe
     * ERWEITERT: Jetzt mit Favoritenklasse-Einfärbung des Provider-Info Panels
     */
    private void createProviderPanel(ProviderData providerData, TickDataLoader.TickDataSet tickDataSet) {
        try {
            LOGGER.info("Erstelle Chart-Panel für Provider: " + providerData.signalId + " mit Timeframe: " + currentTimeScale.name());
            
            SignalOverviewPanel overviewPanel = new SignalOverviewPanel(
                chartsContainer, 
                parentGui, 
                providerData.signalId, 
                providerData.providerName,
                providerData.favoriteClass,  // NEU: Favoritenklasse für Einfärbung
                tickDataSet,
                CHART_WIDTH,
                CHART_HEIGHT,
                currentTimeScale  // Verwende den aktuell gewählten Timeframe
            );
            
            overviewPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
            overviewPanels.add(overviewPanel);
            
            String favoriteClassDisplay = (providerData.favoriteClass != null && !providerData.favoriteClass.trim().isEmpty() && !providerData.favoriteClass.equals("-")) 
                                        ? providerData.favoriteClass : "Keine";
            LOGGER.fine("Chart-Panel erstellt für Provider: " + providerData.signalId + " mit " + currentTimeScale.name() + " (Klasse: " + favoriteClassDisplay + ")");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Erstellen des Chart-Panels für " + providerData.signalId, e);
        }
    }
    
    /**
     * Entfernt alle bestehenden Panels
     */
    private void clearExistingPanels() {
        try {
            LOGGER.info("Entferne bestehende Chart-Panels: " + overviewPanels.size());
            
            for (SignalOverviewPanel panel : overviewPanels) {
                if (!panel.isDisposed()) {
                    panel.dispose();
                }
            }
            overviewPanels.clear();
            
            // Container-Layout aktualisieren
            if (!chartsContainer.isDisposed()) {
                chartsContainer.layout(true);
            }
            
            LOGGER.fine("Bestehende Chart-Panels entfernt");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Entfernen der bestehenden Panels", e);
        }
    }
    
    /**
     * Aktualisiert den scrollbaren Bereich
     */
    private void updateScrollableArea() {
        if (scrolledComposite != null && !scrolledComposite.isDisposed() && 
            chartsContainer != null && !chartsContainer.isDisposed()) {
            
            Point size = chartsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            scrolledComposite.setMinSize(size);
            chartsContainer.layout(true, true);
        }
    }
    
    /**
     * Aktualisiert alle Charts mit dem aktuellen Timeframe
     */
    private void refreshAllCharts() {
        LOGGER.info("Refresh aller Charts angefordert mit Timeframe: " + currentTimeScale.name());
        loadAllProviderCharts();
    }
    
    /**
     * Plant automatischen Refresh
     */
    private void scheduleAutoRefresh() {
        // Auto-Refresh in 5 Minuten (300000 ms)
        display.timerExec(300000, () -> {
            if (!isWindowClosed && !shell.isDisposed() && !isLoading) {
                LOGGER.info("Auto-Refresh gestartet mit Timeframe: " + currentTimeScale.name());
                refreshAllCharts();
            }
        });
    }
    
    /**
     * Zeigt eine Fehlermeldung
     */
    private void showErrorMessage(String title, String message) {
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText(title);
        errorBox.setMessage(message);
        errorBox.open();
    }
    
    /**
     * Schließt das Fenster
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        LOGGER.info("=== SCHLIESSE CHART-ÜBERSICHTSFENSTER ===");
        
        // Alle Chart-Panels schließen
        clearExistingPanels();
        
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Öffnet das Fenster
     */
    public void open() {
        shell.open();
        LOGGER.info("Chart-Übersichtsfenster geöffnet");
        
        // Provider-Charts laden
        loadAllProviderCharts();
    }
    
    /**
     * Hilfsdatenklasse für Provider-Informationen
     * ERWEITERT: Jetzt mit Favoritenklasse für Panel-Einfärbung
     */
    private static class ProviderData {
        final String signalId;
        final String providerName;
        final String status;
        final String favoriteClass;  // NEU: Favoritenklasse
        
        ProviderData(String signalId, String providerName, String status, String favoriteClass) {
            this.signalId = signalId;
            this.providerName = providerName;
            this.status = status;
            this.favoriteClass = favoriteClass;
        }
        
        @Override
        public String toString() {
            String favoriteClassDisplay = (favoriteClass != null && !favoriteClass.trim().isEmpty() && !favoriteClass.equals("-")) 
                                        ? favoriteClass : "Keine";
            return "ProviderData{signalId='" + signalId + "', providerName='" + providerName + 
                   "', status='" + status + "', favoriteClass='" + favoriteClassDisplay + "'}";
        }
    }
}