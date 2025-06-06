package com.mql.realmonitor.gui;

import com.mql.realmonitor.MqlRealMonitor;
import com.mql.realmonitor.parser.SignalData;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Haupt-GUI für MqlRealMonitor
 * ERWEITERT: Neuer "Chart-Übersicht" Button für Signalprovider-Overview
 * ERWEITERT: Favoritenklasse-Farben für Zeilen-Hintergrund
 * DEBUG: initializeTableWithSavedData() mit ausführlicher Diagnostik
 */
public class MqlRealMonitorGUI {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorGUI.class.getName());
    
    private final MqlRealMonitor monitor;
    
    // SWT Komponenten
    private Display display;
    private Shell shell;
    private SignalProviderTable providerTable;
    private StatusUpdater statusUpdater;
    
    // UI Komponenten
    private Label statusLabel;
    private Button startButton;
    private Button stopButton;
    private Button refreshButton;
    private Button configButton;
    private Button overviewButton; // NEU: Chart-Übersicht Button
    private Text intervalText;
    private Label countLabel;
    
    // Farben und Fonts
    private Color greenColor;
    private Color redColor;
    private Color grayColor;
    private Font boldFont;
    private Font statusFont;
    
    // NEU: Favoritenklasse-Farben (Helle Hintergrundfarben für bessere Lesbarkeit)
    private Color favoriteClass1Color;  // Grün
    private Color favoriteClass2Color;  // Gelb
    private Color favoriteClass3Color;  // Orange
    private Color favoriteClass4To10Color; // Rot (für 4-10)
    
    public MqlRealMonitorGUI(MqlRealMonitor monitor) {
        this.monitor = monitor;
        this.display = Display.getDefault();
        
        initializeColors();
        initializeFonts();
        createShell();
        createWidgets();
        
        this.statusUpdater = new StatusUpdater(this);
        
        // Tabelle sofort beim Erstellen initialisieren
        initializeTableWithSavedData();
    }
    
    /**
     * Initialisiert die Farben
     */
    private void initializeColors() {
        greenColor = new Color(display, 0, 128, 0);
        redColor = new Color(display, 200, 0, 0);
        grayColor = new Color(display, 128, 128, 128);
        
        // NEU: Favoritenklasse-Hintergrundfarben (hell und gut lesbar)
        favoriteClass1Color = new Color(display, 200, 255, 200);    // 1 = Hellgrün (sehr hell)
        favoriteClass2Color = new Color(display, 255, 255, 200);    // 2 = Hellgelb 
        favoriteClass3Color = new Color(display, 255, 220, 180);    // 3 = Hellorange
        favoriteClass4To10Color = new Color(display, 255, 200, 200); // 4-10 = Hellrot
    }
    
    /**
     * Initialisiert die Schriftarten
     */
    private void initializeFonts() {
        FontData[] fontData = display.getSystemFont().getFontData();
        
        // Bold Font
        fontData[0].setStyle(SWT.BOLD);
        boldFont = new Font(display, fontData[0]);
        
        // Status Font (etwas kleiner)
        fontData[0].setHeight(fontData[0].getHeight() - 1);
        fontData[0].setStyle(SWT.NORMAL);
        statusFont = new Font(display, fontData[0]);
    }
    
    /**
     * Erstellt das Hauptfenster
     */
    private void createShell() {
        shell = new Shell(display);
        shell.setText("MQL5 Real Monitor - Signal Provider Überwachung");
        shell.setSize(1000, 700);
        shell.setLayout(new GridLayout(1, false));
        
        // Beim Schließen ordnungsgemäß beenden
        shell.addListener(SWT.Close, event -> {
            LOGGER.info("GUI wird geschlossen");
            monitor.shutdown();
            disposeResources();
        });
        
        // Zentrieren
        centerShell();
    }
    
    /**
     * Zentriert das Fenster auf dem Bildschirm
     */
    private void centerShell() {
        org.eclipse.swt.graphics.Rectangle displayBounds = display.getPrimaryMonitor().getBounds();
        org.eclipse.swt.graphics.Rectangle shellBounds = shell.getBounds();
        
        int x = (displayBounds.width - shellBounds.width) / 2;
        int y = (displayBounds.height - shellBounds.height) / 2;
        
        shell.setLocation(x, y);
    }
    
    /**
     * Erstellt alle GUI-Komponenten
     */
    private void createWidgets() {
        createToolbar();
        createProviderTable();
        createStatusBar();
    }
    
    /**
     * ERWEITERT: Erstellt die Toolbar mit neuem Chart-Übersicht Button
     */
    private void createToolbar() {
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        toolbar.setLayout(new GridLayout(11, false)); // ERWEITERT: 11 statt 10 Spalten
        
        // Start Button
        startButton = new Button(toolbar, SWT.PUSH);
        startButton.setText("Start Monitoring");
        startButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        startButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                startMonitoring();
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
                stopMonitoring();
            }
        });
        
        // Separator
        new Label(toolbar, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false));
        
        // Refresh Button
        refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText("Manueller Refresh");
        refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                manualRefresh();
            }
        });
        
        // NEU: Chart-Übersicht Button
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
        
        // Interval Label
        Label intervalLabel = new Label(toolbar, SWT.NONE);
        intervalLabel.setText("Intervall (min):");
        intervalLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        
        // Interval Text
        intervalText = new Text(toolbar, SWT.BORDER | SWT.RIGHT);
        intervalText.setText(String.valueOf(monitor.getConfig().getIntervalMinutes()));
        intervalText.setLayoutData(new GridData(40, SWT.DEFAULT));
        intervalText.addModifyListener(e -> updateInterval());
        
        // Separator
        new Label(toolbar, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false));
        
        // Repair Button
        Button repairButton = new Button(toolbar, SWT.PUSH);
        repairButton.setText("Tick-Dateien reparieren");
        repairButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        repairButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                repairTickFiles();
            }
        });
        
        // Separator
        new Label(toolbar, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false));
        
        // Config Button
        configButton = new Button(toolbar, SWT.PUSH);
        configButton.setText("Konfiguration");
        configButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        configButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showConfiguration();
            }
        });
    }
    
    /**
     * NEU: Öffnet die Chart-Übersicht für alle Signalprovider
     */
    private void openChartOverview() {
        try {
            LOGGER.info("=== ÖFFNE CHART-ÜBERSICHT ===");
            
            // Prüfe ob Provider-Daten vorhanden sind
            if (providerTable == null || providerTable.getProviderCount() == 0) {
                showInfo("Keine Provider", 
                        "Es sind keine Signalprovider verfügbar.\n" +
                        "Bitte starten Sie das Monitoring oder laden Sie Provider-Daten.");
                return;
            }
            
            // Deaktiviere Button temporär
            overviewButton.setEnabled(false);
            overviewButton.setText("Lädt...");
            
            // Übersichtsfenster erstellen und öffnen
            SignalProviderOverviewWindow overviewWindow = new SignalProviderOverviewWindow(shell, this);
            overviewWindow.open();
            
            LOGGER.info("Chart-Übersicht erfolgreich geöffnet");
            
            // Button nach kurzer Zeit wieder aktivieren
            display.timerExec(2000, () -> {
                if (!overviewButton.isDisposed()) {
                    overviewButton.setEnabled(true);
                    overviewButton.setText("📊 Chart-Übersicht");
                }
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen der Chart-Übersicht", e);
            showError("Fehler beim Öffnen der Chart-Übersicht", 
                     "Konnte Chart-Übersicht nicht öffnen:\n" + e.getMessage());
            
            // Button wieder aktivieren
            if (!overviewButton.isDisposed()) {
                overviewButton.setEnabled(true);
                overviewButton.setText("📊 Chart-Übersicht");
            }
        }
    }
    
    /**
     * Erstellt die Signalprovider-Tabelle
     */
    private void createProviderTable() {
        Group tableGroup = new Group(shell, SWT.NONE);
        tableGroup.setText("Signal Provider");
        tableGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tableGroup.setLayout(new GridLayout(1, false));
        
        providerTable = new SignalProviderTable(tableGroup, this);
    }
    
    /**
     * Erstellt die Statusleiste
     */
    private void createStatusBar() {
        Composite statusBar = new Composite(shell, SWT.NONE);
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        statusBar.setLayout(new GridLayout(2, false));
        
        // Status Label
        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setText("Bereit");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setFont(statusFont);
        
        // Count Label
        countLabel = new Label(statusBar, SWT.NONE);
        countLabel.setText("Provider: 0");
        countLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        countLabel.setFont(statusFont);
    }
    
    /**
     * Startet das Monitoring
     */
    private void startMonitoring() {
        try {
            LOGGER.info("Starte Monitoring über GUI");
            monitor.startMonitoring();
            
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            refreshButton.setEnabled(false);
            intervalText.setEnabled(false);
            
            updateStatus("Monitoring gestartet...");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Starten des Monitorings", e);
            showError("Fehler beim Starten", "Monitoring konnte nicht gestartet werden: " + e.getMessage());
        }
    }
    
    /**
     * Stoppt das Monitoring
     */
    private void stopMonitoring() {
        try {
            LOGGER.info("Stoppe Monitoring über GUI");
            monitor.stopMonitoring();
            
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            refreshButton.setEnabled(true);
            intervalText.setEnabled(true);
            
            updateStatus("Monitoring gestoppt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Stoppen des Monitorings", e);
            showError("Fehler beim Stoppen", "Monitoring konnte nicht gestoppt werden: " + e.getMessage());
        }
    }
    
    /**
     * Führt manuellen Refresh durch
     */
    private void manualRefresh() {
        try {
            LOGGER.info("Manueller Refresh über GUI");
            refreshButton.setEnabled(false);
            updateStatus("Führe manuellen Refresh durch...");
            
            monitor.manualRefresh();
            
            // Button nach kurzer Zeit wieder aktivieren
            display.timerExec(2000, () -> {
                if (!refreshButton.isDisposed()) {
                    refreshButton.setEnabled(true);
                }
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim manuellen Refresh", e);
            showError("Fehler beim Refresh", "Manueller Refresh fehlgeschlagen: " + e.getMessage());
            refreshButton.setEnabled(true);
        }
    }
    
    /**
     * Aktualisiert das Intervall
     */
    private void updateInterval() {
        try {
            String text = intervalText.getText().trim();
            if (!text.isEmpty()) {
                int interval = Integer.parseInt(text);
                if (interval > 0) {
                    monitor.getConfig().setIntervalMinutes(interval);
                    LOGGER.info("Intervall geändert auf: " + interval + " Minuten");
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.warning("Ungültiges Intervall: " + intervalText.getText());
        }
    }
    
    /**
     * Zeigt die Konfiguration an
     */
    private void showConfiguration() {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setMessage("Konfigurationsdialog wird in zukünftiger Version implementiert.\n\n" +
                      "Aktuelle Konfiguration:\n" +
                      "Basis-Pfad: " + monitor.getConfig().getBasePath() + "\n" +
                      "Favoriten-Datei: " + monitor.getConfig().getFavoritesFile() + "\n" +
                      "Download-Verzeichnis: " + monitor.getConfig().getDownloadDir() + "\n" +
                      "Tick-Verzeichnis: " + monitor.getConfig().getTickDir() + "\n" +
                      "Intervall: " + monitor.getConfig().getIntervalMinutes() + " Minuten");
        box.setText("Konfiguration");
        box.open();
    }
    
    /**
     * Repariert fehlerhafte Tick-Dateien
     */
    private void repairTickFiles() {
        MessageBox confirmBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("Tick-Dateien reparieren");
        confirmBox.setMessage("Diese Funktion repariert Tick-Dateien mit fehlerhaftem Format.\n" +
                             "Dateien mit Format '53745,30,0,00' werden zu '53745.30,0.00' korrigiert.\n\n" +
                             "Es wird automatisch ein Backup erstellt.\n\n" +
                             "Möchten Sie fortfahren?");
        
        if (confirmBox.open() == SWT.YES) {
            try {
                updateStatus("Repariere Tick-Dateien...");
                
                // Reparatur in separatem Thread ausführen
                new Thread(() -> {
                    try {
                        com.mql.realmonitor.tickdata.TickDataWriter writer = 
                            new com.mql.realmonitor.tickdata.TickDataWriter(monitor.getConfig());
                        
                        Map<String, Boolean> results = writer.repairAllTickFiles();
                        
                        long successCount = results.values().stream().filter(v -> v).count();
                        long failedCount = results.size() - successCount;
                        
                        String message = String.format("Reparatur abgeschlossen!\n\n" +
                                                     "Gesamt: %d Dateien\n" +
                                                     "Erfolgreich: %d\n" +
                                                     "Fehlgeschlagen: %d",
                                                     results.size(), successCount, failedCount);
                        
                        display.asyncExec(() -> {
                            updateStatus("Tick-Dateien repariert");
                            showInfo("Reparatur abgeschlossen", message);
                        });
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Fehler bei der Reparatur", e);
                        display.asyncExec(() -> {
                            updateStatus("Reparatur fehlgeschlagen");
                            showError("Reparatur fehlgeschlagen", 
                                     "Fehler bei der Reparatur der Tick-Dateien:\n" + e.getMessage());
                        });
                    }
                }).start();
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Starten der Reparatur", e);
                showError("Fehler", "Konnte Reparatur nicht starten: " + e.getMessage());
            }
        }
    }
    
    /**
     * DEBUG-VERSION: Initialisiert die Tabelle beim Start mit gespeicherten Daten
     * Erstellt zuerst leere Provider-Einträge mit Namen aus ID-Translation,
     * dann versucht diese mit Tick-Daten zu füllen
     */
    private void initializeTableWithSavedData() {
        try {
            LOGGER.info("=== INITIALISIERE TABELLE MIT GESPEICHERTEN DATEN (DEBUG-VERSION) ===");
            
            // Favoriten-Reader erstellen
            LOGGER.info("DEBUG: Erstelle FavoritesReader...");
            com.mql.realmonitor.downloader.FavoritesReader favoritesReader = 
                new com.mql.realmonitor.downloader.FavoritesReader(monitor.getConfig());
            
            // TickDataWriter für das Lesen der letzten Einträge
            LOGGER.info("DEBUG: Erstelle TickDataWriter...");
            com.mql.realmonitor.tickdata.TickDataWriter tickDataWriter = 
                new com.mql.realmonitor.tickdata.TickDataWriter(monitor.getConfig());
            
            // Favoriten laden
            LOGGER.info("DEBUG: Lade Favoriten...");
            List<String> favoriteIds = favoritesReader.readFavorites();
            LOGGER.info("DEBUG: Favoriten geladen - Anzahl: " + favoriteIds.size());
            
            if (favoriteIds.isEmpty()) {
                LOGGER.warning("DEBUG: Keine Favoriten gefunden!");
                return;
            }
            
            // DEBUG: Zeige alle geladenen Favoriten
            LOGGER.info("DEBUG: Alle Favoriten-IDs:");
            for (int i = 0; i < favoriteIds.size(); i++) {
                String id = favoriteIds.get(i);
                LOGGER.info("DEBUG:   " + (i+1) + ". " + id);
            }
            
            // SCHRITT 1: Zuerst leere Provider-Einträge für alle Favoriten erstellen
            LOGGER.info("=== DEBUG SCHRITT 1: Erstelle leere Provider-Einträge mit ID-Translation ===");
            int createdEntries = 0;
            
            for (String signalId : favoriteIds) {
                if (signalId == null || signalId.trim().isEmpty()) {
                    LOGGER.warning("DEBUG: Überspringe leere Signal-ID");
                    continue;
                }
                
                try {
                    LOGGER.info("DEBUG: Erstelle leeren Eintrag für Signal-ID: " + signalId);
                    
                    // Prüfe zuerst, ob Name in ID-Translation vorhanden ist
                    String providerName = "UNBEKANNT";
                    if (providerTable != null && providerTable.getIdTranslationManager() != null) {
                        providerName = providerTable.getIdTranslationManager().getProviderName(signalId);
                        LOGGER.info("DEBUG: Name aus ID-Translation für " + signalId + ": '" + providerName + "'");
                    }
                    
                    // Leeren Provider-Eintrag erstellen
                    providerTable.addEmptyProviderEntry(signalId, "Lade Daten...");
                    createdEntries++;
                    
                    LOGGER.info("DEBUG: Eintrag " + createdEntries + " erstellt für " + signalId + " (Name: " + providerName + ")");
                    
                } catch (Exception e) {
                    LOGGER.severe("DEBUG: Fehler beim Erstellen des Eintrags für Signal " + signalId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            LOGGER.info("DEBUG: " + createdEntries + " leere Provider-Einträge erstellt");
            
            // GUI-Update und kurze Pause
            updateProviderCount();
            final int finalCreatedEntries = createdEntries;  // <-- FINALE KOPIE ERSTELLEN
            display.asyncExec(() -> {
                updateStatus("DEBUG: " + finalCreatedEntries  + " Provider erstellt, lade Tick-Daten...");
            });
            
            // Kurze Pause um GUI zu aktualisieren
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // SCHRITT 2: Jetzt versuchen, die Einträge mit Tick-Daten zu füllen
            LOGGER.info("=== DEBUG SCHRITT 2: Versuche Tick-Daten zu laden ===");
            int loadedCount = 0;
            int totalCount = favoriteIds.size();
            
            for (int i = 0; i < favoriteIds.size(); i++) {
                String signalId = favoriteIds.get(i);
                
                if (signalId == null || signalId.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    LOGGER.info("DEBUG: (" + (i+1) + "/" + totalCount + ") Versuche Tick-Daten für Signal-ID: " + signalId);
                    
                    // Tick-Datei-Pfad ermitteln
                    String tickFilePath = monitor.getConfig().getTickFilePath(signalId);
                    LOGGER.info("DEBUG: Tick-Datei-Pfad: " + tickFilePath);
                    
                    // Prüfen ob Datei existiert
                    java.io.File tickFile = new java.io.File(tickFilePath);
                    if (!tickFile.exists()) {
                        LOGGER.info("DEBUG: Tick-Datei existiert nicht - Provider bleibt mit ID-Translation Name");
                        providerTable.updateProviderStatus(signalId, "Keine Daten");
                        continue;
                    }
                    
                    LOGGER.info("DEBUG: Tick-Datei gefunden, Größe: " + tickFile.length() + " Bytes");
                    
                    // Letzten Eintrag lesen
                    SignalData lastSignalData = tickDataWriter.readLastTickEntry(tickFilePath, signalId);
                    
                    if (lastSignalData != null && lastSignalData.isValid()) {
                        LOGGER.info("DEBUG: Gültige Tick-Daten geladen: " + lastSignalData.getSummary());
                        
                        // Vollständige Provider-Daten in Tabelle anzeigen
                        providerTable.updateProviderData(lastSignalData);
                        loadedCount++;
                        
                        LOGGER.info("DEBUG: Provider " + signalId + " erfolgreich mit Tick-Daten aktualisiert");
                    } else {
                        LOGGER.info("DEBUG: Keine gültigen Tick-Daten - Provider bleibt mit ID-Translation Name");
                        providerTable.updateProviderStatus(signalId, "Keine gültigen Daten");
                    }
                    
                } catch (Exception e) {
                    LOGGER.severe("DEBUG: Fehler beim Laden der Tick-Daten für Signal: " + signalId + " - " + e.getMessage());
                    e.printStackTrace();
                    providerTable.updateProviderStatus(signalId, "Fehler beim Laden");
                }
                
                // GUI-Update nach jedem Provider
                if (i % 5 == 0) { // Alle 5 Provider
                    final int currentIndex = i;
                    display.asyncExec(() -> {
                        updateStatus("DEBUG: Tick-Daten laden... (" + (currentIndex+1) + "/" + totalCount + ")");
                    });
                }
            }
            
            updateProviderCount();
            String resultMessage = "DEBUG: Tabelle initialisiert - " + totalCount + " Provider erstellt, " + loadedCount + " mit Tick-Daten gefüllt";
            LOGGER.info("=== " + resultMessage + " ===");
            
            // Finale Status-Updates
            display.asyncExec(() -> {
                updateStatus(resultMessage);
                
                // Initiale Sortierung nach Favoritenklasse durchführen
                if (providerTable != null) {
                    LOGGER.info("DEBUG: Führe initiale Sortierung durch...");
                    providerTable.performInitialSort();
                }
            });
            
            LOGGER.info("DEBUG: initializeTableWithSavedData() erfolgreich abgeschlossen");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DEBUG: FATALER FEHLER beim Initialisieren der Tabelle", e);
            e.printStackTrace();
            display.asyncExec(() -> {
                updateStatus("DEBUG: Fehler beim Laden der Daten - " + e.getMessage());
            });
        }
    }
    
    /**
     * Aktualisiert die Statusanzeige (Thread-sicher)
     */
    public void updateStatus(String status) {
        if (display.isDisposed()) return;
        
        display.asyncExec(() -> {
            if (!statusLabel.isDisposed()) {
                statusLabel.setText(status);
                LOGGER.fine("Status aktualisiert: " + status);
            }
        });
    }
    
    /**
     * Aktualisiert Provider-Daten (Thread-sicher)
     */
    public void updateProviderData(SignalData signalData) {
        if (display.isDisposed()) return;
        
        display.asyncExec(() -> {
            if (providerTable != null) {
                providerTable.updateProviderData(signalData);
                updateProviderCount();
            }
        });
    }
    
    /**
     * Aktualisiert Provider-Status (Thread-sicher)
     */
    public void updateProviderStatus(String signalId, String status) {
        if (display.isDisposed()) return;
        
        display.asyncExec(() -> {
            if (providerTable != null) {
                providerTable.updateProviderStatus(signalId, status);
            }
        });
    }
    
    /**
     * Aktualisiert die Anzahl der Provider
     */
    private void updateProviderCount() {
        if (providerTable != null && !countLabel.isDisposed()) {
            int count = providerTable.getProviderCount();
            countLabel.setText("Provider: " + count);
        }
    }
    
    /**
     * Zeigt eine Fehlermeldung an
     */
    public void showError(String title, String message) {
        if (display.isDisposed()) return;
        
        display.asyncExec(() -> {
            if (!shell.isDisposed()) {
                MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
                box.setText(title);
                box.setMessage(message);
                box.open();
            }
        });
    }
    
    /**
     * Zeigt eine Informationsmeldung an
     */
    public void showInfo(String title, String message) {
        if (display.isDisposed()) return;
        
        display.asyncExec(() -> {
            if (!shell.isDisposed()) {
                MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
                box.setText(title);
                box.setMessage(message);
                box.open();
            }
        });
    }
    
    /**
     * Öffnet das GUI-Fenster und startet die Event-Loop
     */
    public void open() {
        shell.open();
        
        LOGGER.info("MqlRealMonitor GUI geöffnet");
        updateStatus("MqlRealMonitor bereit - Daten geladen");
        
        // Event-Loop
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        
        disposeResources();
    }
    
    /**
     * Schließt das GUI-Fenster
     */
    public void dispose() {
        if (!shell.isDisposed()) {
            shell.close();
        }
    }
    
    /**
     * Gibt alle Ressourcen frei
     */
    private void disposeResources() {
        if (greenColor != null && !greenColor.isDisposed()) greenColor.dispose();
        if (redColor != null && !redColor.isDisposed()) redColor.dispose();
        if (grayColor != null && !grayColor.isDisposed()) grayColor.dispose();
        if (boldFont != null && !boldFont.isDisposed()) boldFont.dispose();
        if (statusFont != null && !statusFont.isDisposed()) statusFont.dispose();
        
        // NEU: Favoritenklasse-Farben freigeben
        if (favoriteClass1Color != null && !favoriteClass1Color.isDisposed()) favoriteClass1Color.dispose();
        if (favoriteClass2Color != null && !favoriteClass2Color.isDisposed()) favoriteClass2Color.dispose();
        if (favoriteClass3Color != null && !favoriteClass3Color.isDisposed()) favoriteClass3Color.dispose();
        if (favoriteClass4To10Color != null && !favoriteClass4To10Color.isDisposed()) favoriteClass4To10Color.dispose();
        
        if (!display.isDisposed()) {
            display.dispose();
        }
        
        LOGGER.info("GUI-Ressourcen freigegeben");
    }
    
    // Getter für andere Klassen
    
    public Display getDisplay() {
        return display;
    }
    
    public Shell getShell() {
        return shell;
    }
    
    public Color getGreenColor() {
        return greenColor;
    }
    
    public Color getRedColor() {
        return redColor;
    }
    
    public Color getGrayColor() {
        return grayColor;
    }
    
    public Font getBoldFont() {
        return boldFont;
    }
    
    public MqlRealMonitor getMonitor() {
        return monitor;
    }
    
    /**
     * NEU: Gibt die SignalProviderTable zurück
     */
    public SignalProviderTable getProviderTable() {
        return providerTable;
    }
    
    // NEU: Getter für Favoritenklasse-Farben (vereinfacht)
    
    /**
     * NEU: Gibt die Hintergrundfarbe für Favoritenklasse 1 zurück (Hellgrün)
     */
    public Color getFavoriteClass1Color() {
        return favoriteClass1Color;
    }
    
    /**
     * NEU: Gibt die Hintergrundfarbe für Favoritenklasse 2 zurück (Hellgelb)
     */
    public Color getFavoriteClass2Color() {
        return favoriteClass2Color;
    }
    
    /**
     * NEU: Gibt die Hintergrundfarbe für Favoritenklasse 3 zurück (Hellorange)
     */
    public Color getFavoriteClass3Color() {
        return favoriteClass3Color;
    }
    
    /**
     * NEU: Gibt die Hintergrundfarbe für Favoritenklassen 4-10 zurück (Hellrot)
     */
    public Color getFavoriteClass4To10Color() {
        return favoriteClass4To10Color;
    }
}