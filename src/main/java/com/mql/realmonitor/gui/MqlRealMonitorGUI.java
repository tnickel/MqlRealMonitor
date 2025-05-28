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
     * Initialisiert die Tabelle beim Start mit gespeicherten Daten
     */
    private void initializeTableWithSavedData() {
        try {
            LOGGER.info("=== INITIALISIERE TABELLE MIT GESPEICHERTEN DATEN ===");
            
            // Favoriten-Reader erstellen
            com.mql.realmonitor.downloader.FavoritesReader favoritesReader = 
                new com.mql.realmonitor.downloader.FavoritesReader(monitor.getConfig());
            
            // TickDataWriter für das Lesen der letzten Einträge
            com.mql.realmonitor.tickdata.TickDataWriter tickDataWriter = 
                new com.mql.realmonitor.tickdata.TickDataWriter(monitor.getConfig());
            
            // Favoriten laden
            List<String> favoriteIds = favoritesReader.readFavorites();
            LOGGER.info("Gefundene Favoriten: " + favoriteIds.size());
            
            if (favoriteIds.isEmpty()) {
                LOGGER.warning("Keine Favoriten gefunden!");
                return;
            }
            
            // Jede Signal-ID verarbeiten
            int loadedCount = 0;
            int totalCount = favoriteIds.size();
            
            for (String signalId : favoriteIds) {
                if (signalId == null || signalId.trim().isEmpty()) {
                    continue;
                }
                
                LOGGER.info("Verarbeite Signal-ID: " + signalId);
                
                try {
                    // Tick-Datei-Pfad ermitteln
                    String tickFilePath = monitor.getConfig().getTickFilePath(signalId);
                    LOGGER.info("Tick-Datei-Pfad: " + tickFilePath);
                    
                    // Prüfen ob Datei existiert
                    java.io.File tickFile = new java.io.File(tickFilePath);
                    if (!tickFile.exists()) {
                        LOGGER.warning("Tick-Datei existiert nicht: " + tickFilePath);
                        providerTable.updateProviderStatus(signalId, "Keine Datei");
                        continue;
                    }
                    
                    LOGGER.info("Tick-Datei gefunden, Größe: " + tickFile.length() + " Bytes");
                    
                    // Letzten Eintrag lesen
                    LOGGER.info("Versuche letzten Tick-Eintrag zu lesen...");
                    SignalData lastSignalData = tickDataWriter.readLastTickEntry(tickFilePath, signalId);
                    
                    if (lastSignalData != null) {
                        LOGGER.info("SignalData erhalten: " + lastSignalData.toString());
                        LOGGER.info("IsValid: " + lastSignalData.isValid());
                        
                        if (lastSignalData.isValid()) {
                            LOGGER.info("Gültige Daten geladen: " + lastSignalData.getSummary());
                            
                            // Vollständige Provider-Daten in Tabelle anzeigen
                            providerTable.updateProviderData(lastSignalData);
                            providerTable.updateProviderStatus(signalId, "Geladen - " + lastSignalData.getFormattedTimestamp());
                            loadedCount++;
                        } else {
                            LOGGER.warning("SignalData ist ungültig für Signal: " + signalId);
                            LOGGER.warning("Details: SignalId=" + lastSignalData.getSignalId() + 
                                         ", Currency=" + lastSignalData.getCurrency() + 
                                         ", Equity=" + lastSignalData.getEquity() + 
                                         ", Timestamp=" + lastSignalData.getTimestamp());
                            providerTable.updateProviderStatus(signalId, "Ungültige Daten");
                        }
                    } else {
                        LOGGER.warning("readLastTickEntry gab null zurück für Signal: " + signalId);
                        
                        // Versuche die Datei direkt zu lesen um zu sehen was drin steht
                        try {
                            java.nio.file.Path path = java.nio.file.Paths.get(tickFilePath);
                            java.util.List<String> lines = java.nio.file.Files.readAllLines(path);
                            LOGGER.info("Datei-Inhalt (" + lines.size() + " Zeilen):");
                            for (int i = Math.max(0, lines.size() - 5); i < lines.size(); i++) {
                                LOGGER.info("Zeile " + (i+1) + ": " + lines.get(i));
                            }
                        } catch (Exception fileEx) {
                            LOGGER.log(Level.WARNING, "Konnte Datei nicht direkt lesen", fileEx);
                        }
                        
                        providerTable.updateProviderStatus(signalId, "Keine Daten");
                    }
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Fehler beim Verarbeiten von Signal: " + signalId, e);
                    providerTable.updateProviderStatus(signalId, "Fehler: " + e.getMessage());
                }
            }
            
            updateProviderCount();
            String resultMessage = "Tabelle initialisiert: " + loadedCount + "/" + totalCount + " Provider geladen";
            LOGGER.info("=== " + resultMessage + " ===");
            
            // Status in GUI setzen
            display.asyncExec(() -> {
                updateStatus(resultMessage);
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER beim Initialisieren der Tabelle", e);
            display.asyncExec(() -> {
                updateStatus("Fehler beim Laden der Daten");
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
}