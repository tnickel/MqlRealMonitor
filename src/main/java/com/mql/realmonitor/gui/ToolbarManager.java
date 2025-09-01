package com.mql.realmonitor.gui;

import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * ToolbarManager - Verwaltet die Toolbar-Erstellung und Button-Funktionalität
 * 
 * Zuständig für:
 * - Erstellung der kompletten Toolbar
 * - Verwaltung aller Toolbar-Buttons
 * - Koordination zwischen verschiedenen Managern
 * - Button-Event-Handling
 * - Button-Status-Management
 */
public class ToolbarManager {
    
    private static final Logger LOGGER = Logger.getLogger(ToolbarManager.class.getName());
    
    private final Shell parentShell;
    private final MqlRealMonitorGUI gui;
    private final CurrencyManager currencyManager;
    private final SignalManager signalManager;
    private final MonitoringController monitoringController;
    
    // Toolbar-Komponenten
    private Composite toolbar;
    private Button startButton;
    private Button stopButton;
    private Button refreshButton;
    private Button overviewButton;
    private Button deleteSignalButton;
    private Button addSignalButton;
    private Button kurseladenButton;
    private Button repairButton;
    private Button configButton;
    private Text intervalText;
    
    public ToolbarManager(Shell parentShell, MqlRealMonitorGUI gui, 
                         CurrencyManager currencyManager, SignalManager signalManager, 
                         MonitoringController monitoringController) {
        this.parentShell = parentShell;
        this.gui = gui;
        this.currencyManager = currencyManager;
        this.signalManager = signalManager;
        this.monitoringController = monitoringController;
    }
    
    /**
     * Erstellt die komplette Toolbar mit allen Buttons
     */
    public void createToolbar() {
        toolbar = new Composite(parentShell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        toolbar.setLayout(new GridLayout(16, false)); // 16 Spalten für alle Buttons
        
        createMonitoringButtons();
        addSeparator();
        createActionButtons();
        addSeparator();
        createCurrencyButton();
        createIntervalControls();
        addSeparator();
        createUtilityButtons();
        addSeparator();
        createConfigButton();
        
        LOGGER.info("Toolbar mit allen Buttons erfolgreich erstellt");
    }
    
    /**
     * Erstellt die Monitoring-Buttons (Start/Stop/Refresh)
     */
    private void createMonitoringButtons() {
        // Start Button
        startButton = new Button(toolbar, SWT.PUSH);
        startButton.setText("Start Monitoring");
        startButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        startButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                monitoringController.startMonitoring();
                updateMonitoringButtonStates(true);
            }
        });
        
        // Stop Button
        stopButton = new Button(toolbar, SWT.PUSH);
        stopButton.setText("Stop Monitoring");
        stopButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        stopButton.setEnabled(false);
        stopButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                monitoringController.stopMonitoring();
                updateMonitoringButtonStates(false);
            }
        });
        
        // Refresh Button
        refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText("Manueller Refresh");
        refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                monitoringController.manualRefresh();
                // Button wird temporär deaktiviert
                refreshButton.setEnabled(false);
                gui.getDisplay().timerExec(2000, () -> {
                    if (!refreshButton.isDisposed()) {
                        refreshButton.setEnabled(true);
                    }
                });
            }
        });
    }
    
    /**
     * Erstellt die Action-Buttons (Chart-Übersicht, Add/Delete Signal)
     */
    private void createActionButtons() {
        // Chart-Übersicht Button
        overviewButton = new Button(toolbar, SWT.PUSH);
        overviewButton.setText("📊 Chart-Übersicht");
        overviewButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        overviewButton.setToolTipText("Öffnet eine Übersicht aller Provider mit Drawdown- und Profit-Charts");
        overviewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openChartOverview();
            }
        });
        
        // Delete Signal Button
        deleteSignalButton = new Button(toolbar, SWT.PUSH);
        deleteSignalButton.setText("🗑️ Löschen");
        deleteSignalButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        deleteSignalButton.setToolTipText("Ausgewähltes Signal aus Favoriten löschen");
        deleteSignalButton.setEnabled(false); // Anfangs deaktiviert
        deleteSignalButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                signalManager.deleteSelectedSignal();
            }
        });
        
        // Add Signal Button
        addSignalButton = new Button(toolbar, SWT.PUSH);
        addSignalButton.setText("➕ Hinzufügen");
        addSignalButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        addSignalButton.setToolTipText("Neues Signal zu Favoriten hinzufügen");
        addSignalButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                signalManager.addNewSignal();
            }
        });
        
        // Signal Manager über Button informieren
        signalManager.setDeleteButton(deleteSignalButton);
    }
    
    /**
     * Erstellt den Currency-Button
     */
    private void createCurrencyButton() {
        kurseladenButton = new Button(toolbar, SWT.PUSH);
        kurseladenButton.setText("💰 Kurse laden");
        kurseladenButton.setToolTipText("Lädt aktuelle Währungskurse (XAUUSD, BTCUSD) von MQL5");
        kurseladenButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        
        kurseladenButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // Button temporär deaktivieren
                kurseladenButton.setEnabled(false);
                kurseladenButton.setText("Lade...");
                
                // Currency Loading ausführen
                currencyManager.loadCurrencyRatesManually();
                
                // Button nach kurzer Zeit wieder aktivieren
                gui.getDisplay().timerExec(2000, () -> {
                    if (!kurseladenButton.isDisposed()) {
                        kurseladenButton.setEnabled(currencyManager.isCurrencyLoadingAvailable());
                        kurseladenButton.setText("💰 Kurse laden");
                    }
                });
            }
        });
        
        // Button initial aktivieren nur wenn CurrencyManager verfügbar
        kurseladenButton.setEnabled(currencyManager.isCurrencyLoadingAvailable());
    }
    
    /**
     * Erstellt die Intervall-Kontrollen
     */
    private void createIntervalControls() {
        // Interval Label
        Label intervalLabel = new Label(toolbar, SWT.NONE);
        intervalLabel.setText("Intervall (min):");
        intervalLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        
        // Interval Text
        intervalText = new Text(toolbar, SWT.BORDER | SWT.RIGHT);
        intervalText.setText(String.valueOf(gui.getMonitor().getConfig().getIntervalMinutes()));
        intervalText.setLayoutData(new GridData(40, SWT.DEFAULT));
        intervalText.addModifyListener(e -> updateInterval());
    }
    
    /**
     * Erstellt Utility-Buttons (Repair)
     */
    private void createUtilityButtons() {
        // Repair Button - Format-Konvertierung
        repairButton = new Button(toolbar, SWT.PUSH);
        repairButton.setText("Tick-Dateien reparieren");
        repairButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        repairButton.setToolTipText("Konvertiert alte Tickdaten (4 Spalten) ins neue Format (5 Spalten mit Profit)");
        repairButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                monitoringController.repairTickFiles();
            }
        });
    }
    
    /**
     * Erstellt den Config-Button
     */
    private void createConfigButton() {
        configButton = new Button(toolbar, SWT.PUSH);
        configButton.setText("Konfiguration");
        configButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        configButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                monitoringController.showConfiguration();
            }
        });
    }
    
    /**
     * Fügt einen visuellen Separator hinzu
     */
    private void addSeparator() {
        new Label(toolbar, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false));
    }
    
    /**
     * Öffnet die Chart-Übersicht
     */
    private void openChartOverview() {
        try {
            LOGGER.info("=== ÖFFNE CHART-ÜBERSICHT ÜBER TOOLBAR ===");
            
            // Prüfe ob Provider-Daten vorhanden sind
            if (gui.getProviderTable() == null || gui.getProviderTable().getProviderCount() == 0) {
                gui.getDialogManager().showInfo("Keine Provider", 
                        "Es sind keine Signalprovider verfügbar.\n" +
                        "Bitte starten Sie das Monitoring oder laden Sie Provider-Daten.");
                return;
            }
            
            // Button temporär deaktivieren
            overviewButton.setEnabled(false);
            overviewButton.setText("Lädt...");
            
            // Übersichtsfenster erstellen und öffnen
            SignalProviderOverviewWindow overviewWindow = new SignalProviderOverviewWindow(parentShell, gui);
            overviewWindow.open();
            
            LOGGER.info("Chart-Übersicht erfolgreich geöffnet");
            
            // Button nach kurzer Zeit wieder aktivieren
            gui.getDisplay().timerExec(2000, () -> {
                if (!overviewButton.isDisposed()) {
                    overviewButton.setEnabled(true);
                    overviewButton.setText("📊 Chart-Übersicht");
                }
            });
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Öffnen der Chart-Übersicht: " + e.getMessage());
            gui.getDialogManager().showError("Fehler beim Öffnen der Chart-Übersicht", 
                     "Konnte Chart-Übersicht nicht öffnen:\n" + e.getMessage());
            
            // Button wieder aktivieren
            if (!overviewButton.isDisposed()) {
                overviewButton.setEnabled(true);
                overviewButton.setText("📊 Chart-Übersicht");
            }
        }
    }
    
    /**
     * Aktualisiert das Monitoring-Intervall
     */
    private void updateInterval() {
        try {
            String text = intervalText.getText().trim();
            if (!text.isEmpty()) {
                int interval = Integer.parseInt(text);
                if (interval > 0) {
                    gui.getMonitor().getConfig().setIntervalMinutes(interval);
                    LOGGER.info("Intervall geändert auf: " + interval + " Minuten");
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Ungültiges Intervall: " + intervalText.getText());
        }
    }
    
    /**
     * Aktualisiert den Status der Monitoring-Buttons
     */
    public void updateMonitoringButtonStates(boolean isMonitoringActive) {
        if (!startButton.isDisposed()) {
            startButton.setEnabled(!isMonitoringActive);
        }
        if (!stopButton.isDisposed()) {
            stopButton.setEnabled(isMonitoringActive);
        }
        if (!refreshButton.isDisposed()) {
            refreshButton.setEnabled(!isMonitoringActive);
        }
        if (!intervalText.isDisposed()) {
            intervalText.setEnabled(!isMonitoringActive);
        }
        
        LOGGER.fine("Monitoring-Button-Status aktualisiert - Aktiv: " + isMonitoringActive);
    }
    
    /**
     * Aktualisiert den Status des Delete-Buttons
     */
    public void updateDeleteButtonState(boolean enabled, String tooltipText) {
        if (deleteSignalButton != null && !deleteSignalButton.isDisposed()) {
            deleteSignalButton.setEnabled(enabled);
            deleteSignalButton.setToolTipText(tooltipText != null ? tooltipText : "Ausgewähltes Signal aus Favoriten löschen");
            
            LOGGER.fine("Delete-Button Zustand aktualisiert: " + (enabled ? "AKTIVIERT" : "DEAKTIVIERT"));
        }
    }
    
    /**
     * Gibt alle Ressourcen frei
     */
    public void dispose() {
        try {
            // Buttons werden automatisch durch SWT disposed
            startButton = null;
            stopButton = null;
            refreshButton = null;
            overviewButton = null;
            deleteSignalButton = null;
            addSignalButton = null;
            kurseladenButton = null;
            repairButton = null;
            configButton = null;
            intervalText = null;
            
            // Toolbar wird automatisch durch SWT disposed
            toolbar = null;
            
            LOGGER.info("ToolbarManager erfolgreich bereinigt");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Dispose des ToolbarManager: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // GETTER METHODS
    // ========================================================================
    
    public Button getStartButton() {
        return startButton;
    }
    
    public Button getStopButton() {
        return stopButton;
    }
    
    public Button getRefreshButton() {
        return refreshButton;
    }
    
    public Button getDeleteSignalButton() {
        return deleteSignalButton;
    }
    
    public Button getKurseladenButton() {
        return kurseladenButton;
    }
}