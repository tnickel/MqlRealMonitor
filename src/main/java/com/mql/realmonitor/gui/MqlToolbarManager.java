package com.mql.realmonitor.gui;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Manager für die Toolbar mit allen Buttons und Funktionen.
 * Verwaltet: Start/Stop, Refresh, Chart-Übersicht, Interval, Config, Repair
 */
public class MqlToolbarManager {
    
    private static final Logger LOGGER = Logger.getLogger(MqlToolbarManager.class.getName());
    
    private final MqlRealMonitorGUI gui;
    private final Shell shell;
    
    // Toolbar-Komponenten
    private Button startButton;
    private Button stopButton;
    private Button refreshButton;
    private Button configButton;
    private Button overviewButton;
    private Text intervalText;
    
    // Manager-Referenzen
    private MqlCurrencyManager currencyManager;
    private MqlSignalManager signalManager;
    
    public MqlToolbarManager(MqlRealMonitorGUI gui, Shell shell) {
        this.gui = gui;
        this.shell = shell;
    }
    
    /**
     * Setzt den Currency Manager (für Toolbar-Integration)
     */
    public void setCurrencyManager(MqlCurrencyManager currencyManager) {
        this.currencyManager = currencyManager;
    }
    
    /**
     * Setzt den Signal Manager (für Toolbar-Integration)
     */
    public void setSignalManager(MqlSignalManager signalManager) {
        this.signalManager = signalManager;
    }
    
    /**
     * Erstellt die komplette Toolbar
     */
    public void createToolbar() {
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        toolbar.setLayout(new GridLayout(18, false)); // 18 Spalten für alle Buttons
        
        // Monitoring-Buttons
        createMonitoringButtons(toolbar);
        
        // Separator
        createSeparator(toolbar);
        
        // Action-Buttons
        createActionButtons(toolbar);
        
        // Currency und Signal-Buttons (delegiert an Manager)
        if (currencyManager != null) {
            currencyManager.createCurrencyButton(toolbar);
        }
        if (signalManager != null) {
            signalManager.createSignalButtons(toolbar);
        }
        
        // Interval-Einstellung
        createIntervalControls(toolbar);
        
        // Separator
        createSeparator(toolbar);
        
        // Utility-Buttons
        createUtilityButtons(toolbar);
        
        // Separator
        createSeparator(toolbar);
        
        // Config-Button
        createConfigButton(toolbar);
        
        LOGGER.info("Toolbar mit allen Buttons erstellt");
    }
    
    /**
     * Erstellt Start/Stop Monitoring Buttons
     */
    private void createMonitoringButtons(Composite parent) {
        // Start Button
        startButton = new Button(parent, SWT.PUSH);
        startButton.setText("Start Monitoring");
        startButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        startButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                startMonitoring();
            }
        });
        
        // Stop Button
        stopButton = new Button(parent, SWT.PUSH);
        stopButton.setText("Stop Monitoring");
        stopButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        stopButton.setEnabled(false);
        stopButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                stopMonitoring();
            }
        });
    }
    
    /**
     * Erstellt Action-Buttons (Refresh, Chart-Übersicht)
     */
    private void createActionButtons(Composite parent) {
        // Refresh Button
        refreshButton = new Button(parent, SWT.PUSH);
        refreshButton.setText("Manueller Refresh");
        refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                manualRefresh();
            }
        });
        
        // Chart-Übersicht Button
        overviewButton = new Button(parent, SWT.PUSH);
        overviewButton.setText("📊 Chart-Übersicht");
        overviewButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        overviewButton.setToolTipText("Öffnet eine Übersicht aller Provider mit Drawdown- und Profit-Charts");
        overviewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openChartOverview();
            }
        });
        
        // NEU: Drawdown Analyzer Button
        Button ddAnalyzerButton = new Button(parent, SWT.PUSH);
        ddAnalyzerButton.setText("📉 Drawdown Analyzer");
        ddAnalyzerButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        ddAnalyzerButton.setToolTipText("Öffnet den webbasierten Drawdown Analyzer für das ausgewählte Signal");
        ddAnalyzerButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openDrawdownAnalyzerForSelected();
            }
        });
    }
    
    /**
     * Erstellt Interval-Einstellungen
     */
    private void createIntervalControls(Composite parent) {
        // Interval Label
        Label intervalLabel = new Label(parent, SWT.NONE);
        intervalLabel.setText("Intervall (min):");
        intervalLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        
        // Interval Text
        intervalText = new Text(parent, SWT.BORDER | SWT.RIGHT);
        intervalText.setText(String.valueOf(gui.getMonitor().getConfig().getIntervalMinutes()));
        intervalText.setLayoutData(new GridData(40, SWT.DEFAULT));
        intervalText.addModifyListener(e -> updateInterval());
    }
    
    /**
     * Erstellt Utility-Buttons (Repair)
     */
    private void createUtilityButtons(Composite parent) {
        // Repair Button - Format-Konvertierung
        Button repairButton = new Button(parent, SWT.PUSH);
        repairButton.setText("Tick-Dateien reparieren");
        repairButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        repairButton.setToolTipText("Konvertiert alte Tickdaten (4 Spalten) ins neue Format (5 Spalten mit Profit)");
        repairButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                repairTickFiles();
            }
        });
    }
    
    /**
     * Erstellt Config-Button
     */
    private void createConfigButton(Composite parent) {
        configButton = new Button(parent, SWT.PUSH);
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
     * Erstellt einen Separator
     */
    private void createSeparator(Composite parent) {
        new Label(parent, SWT.SEPARATOR | SWT.VERTICAL)
            .setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false));
    }
    
    // ========================================================================
    // EVENT HANDLER METHODEN
    // ========================================================================
    
    /**
     * Startet das Monitoring
     */
    private void startMonitoring() {
        try {
            LOGGER.info("Starte Monitoring über Toolbar");
            gui.getMonitor().startMonitoring();
            
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            refreshButton.setEnabled(false);
            intervalText.setEnabled(false);
            
            gui.updateStatus("Monitoring gestartet...");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Starten des Monitorings", e);
            gui.showError("Fehler beim Starten", "Monitoring konnte nicht gestartet werden: " + e.getMessage());
        }
    }
    
    /**
     * Stoppt das Monitoring
     */
    private void stopMonitoring() {
        try {
            LOGGER.info("Stoppe Monitoring über Toolbar");
            gui.getMonitor().stopMonitoring();
            
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            refreshButton.setEnabled(true);
            intervalText.setEnabled(true);
            
            gui.updateStatus("Monitoring gestoppt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Stoppen des Monitorings", e);
            gui.showError("Fehler beim Stoppen", "Monitoring konnte nicht gestoppt werden: " + e.getMessage());
        }
    }
    
    /**
     * Führt manuellen Refresh durch
     */
    private void manualRefresh() {
        try {
            LOGGER.info("Manueller Refresh über Toolbar");
            refreshButton.setEnabled(false);
            gui.updateStatus("Führe manuellen Refresh durch...");
            
            gui.getMonitor().manualRefresh();
            
            // Button nach kurzer Zeit wieder aktivieren
            gui.getDisplay().timerExec(2000, () -> {
                if (!refreshButton.isDisposed()) {
                    refreshButton.setEnabled(true);
                }
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim manuellen Refresh", e);
            gui.showError("Fehler beim Refresh", "Manueller Refresh fehlgeschlagen: " + e.getMessage());
            refreshButton.setEnabled(true);
        }
    }
    
    /**
     * Öffnet die Chart-Übersicht
     */
    private void openChartOverview() {
        try {
            LOGGER.info("=== ÖFFNE CHART-ÜBERSICHT ===");
            
            // Prüfe ob Provider-Daten vorhanden sind
            if (gui.getProviderTable() == null || gui.getProviderTable().getProviderCount() == 0) {
                gui.showInfo("Keine Provider", 
                        "Es sind keine Signalprovider verfügbar.\n" +
                        "Bitte starten Sie das Monitoring oder laden Sie Provider-Daten.");
                return;
            }
            
            // Deaktiviere Button temporär
            overviewButton.setEnabled(false);
            overviewButton.setText("Lädt...");
            
            // Übersichtsfenster erstellen und öffnen
            SignalProviderOverviewWindow overviewWindow = new SignalProviderOverviewWindow(shell, gui);
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
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen der Chart-Übersicht", e);
            gui.showError("Fehler beim Öffnen der Chart-Übersicht", 
                     "Konnte Chart-Übersicht nicht öffnen:\n" + e.getMessage());
            
            // Button wieder aktivieren
            if (!overviewButton.isDisposed()) {
                overviewButton.setEnabled(true);
                overviewButton.setText("📊 Chart-Übersicht");
            }
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
                    gui.getMonitor().getConfig().setIntervalMinutes(interval);
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
                      "=== ANWENDUNGSVERSION ===\n" +
                      "Version: " + MqlRealMonitorGUI.getVersion() + "\n" +
                      "Build-Datum: " + MqlRealMonitorGUI.getBuildDate() + "\n\n" +
                      "=== AKTUELLE KONFIGURATION ===\n" +
                      "Basis-Pfad: " + gui.getMonitor().getConfig().getBasePath() + "\n" +
                      "Favoriten-Datei: " + gui.getMonitor().getConfig().getFavoritesFile() + "\n" +
                      "Download-Verzeichnis: " + gui.getMonitor().getConfig().getDownloadDir() + "\n" +
                      "Tick-Verzeichnis: " + gui.getMonitor().getConfig().getTickDir() + "\n" +
                      "Intervall: " + gui.getMonitor().getConfig().getIntervalMinutes() + " Minuten\n\n" +
                      "=== NEU IN VERSION 1.2.1 ===\n" +
                      "• Currency Loading für XAUUSD/BTCUSD von MQL5\n" +
                      "• Kursdaten-Speicherung in realtick/tick_kurse/\n" +
                      "• Thread-sichere Currency-Operationen\n" +
                      "• Automatisches Currency Loading nach Signal-Laden");
        box.setText("Konfiguration - MQL5 Real Monitor v" + MqlRealMonitorGUI.getVersion());
        box.open();
    }
    
    /**
     * Konvertiert Tick-Dateien vom alten 4-Spalten-Format ins neue 5-Spalten-Format
     */
    private void repairTickFiles() {
        MessageBox confirmBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("Tick-Dateien reparieren");
        confirmBox.setMessage("Diese Funktion konvertiert alte Tickdaten ins neue Format.\n\n" +
                             "Format ALT (4 Spalten):  Datum,Zeit,Equity,FloatingProfit\n" +
                             "Format NEU (5 Spalten):  Datum,Zeit,Equity,FloatingProfit,Profit\n\n" +
                             "Die Equity wird als Profit-Wert übernommen.\n\n" +
                             "Es wird automatisch ein Backup erstellt.\n\n" +
                             "Möchten Sie fortfahren?");
        
        if (confirmBox.open() == SWT.YES) {
            try {
                gui.updateStatus("Konvertiere Tick-Dateien ins neue Format...");
                
                // Konvertierung in separatem Thread ausführen
                new Thread(() -> {
                    try {
                        com.mql.realmonitor.tickdata.TickDataWriter writer = 
                            new com.mql.realmonitor.tickdata.TickDataWriter(gui.getMonitor().getConfig());
                        
                        java.util.Map<String, Boolean> results = writer.convertAllTickFilesToNewFormat();
                        
                        long successCount = results.values().stream().filter(v -> v != null && v).count();
                        long skippedCount = results.values().stream().filter(v -> v == null).count();
                        long failedCount = results.values().stream().filter(v -> v != null && !v).count();
                        
                        String message = String.format("Format-Konvertierung abgeschlossen!\n\n" +
                                                     "Gesamt: %d Dateien\n" +
                                                     "Konvertiert: %d\n" +
                                                     "Bereits neues Format: %d\n" +
                                                     "Fehlgeschlagen: %d\n\n" +
                                                     "Alte Dateien wurden als Backup gespeichert.",
                                                     results.size(), successCount, skippedCount, failedCount);
                        
                        gui.getDisplay().asyncExec(() -> {
                            gui.updateStatus("Format-Konvertierung abgeschlossen");
                            gui.showInfo("Konvertierung abgeschlossen", message);
                        });
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Fehler bei der Format-Konvertierung", e);
                        gui.getDisplay().asyncExec(() -> {
                            gui.updateStatus("Format-Konvertierung fehlgeschlagen");
                            gui.showError("Konvertierung fehlgeschlagen", 
                                         "Fehler bei der Format-Konvertierung der Tick-Dateien:\n" + e.getMessage());
                        });
                    }
                }).start();
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Starten der Format-Konvertierung", e);
                gui.showError("Fehler", "Konnte Format-Konvertierung nicht starten: " + e.getMessage());
            }
        }
    }
    
    // ========================================================================
    // ÖFFENTLICHE METHODEN FÜR GUI-UPDATES
    // ========================================================================
    
    /**
     * Aktualisiert den Zustand der Monitoring-Buttons
     */
    public void updateMonitoringButtonStates(boolean isMonitoring) {
        if (startButton != null && !startButton.isDisposed()) {
            startButton.setEnabled(!isMonitoring);
        }
        if (stopButton != null && !stopButton.isDisposed()) {
            stopButton.setEnabled(isMonitoring);
        }
        if (refreshButton != null && !refreshButton.isDisposed()) {
            refreshButton.setEnabled(!isMonitoring);
        }
        if (intervalText != null && !intervalText.isDisposed()) {
            intervalText.setEnabled(!isMonitoring);
        }
    }
    
    /**
     * Öffnet den Drawdown Analyzer für das in der Tabelle ausgewählte Signal
     */
    private void openDrawdownAnalyzerForSelected() {
        try {
            LOGGER.info("=== DRAWDOWN ANALYZER ÜBER TOOLBAR AUSGELÖST ===");
            
            if (gui.getProviderTable() == null) {
                gui.showError("Fehler", "Signalprovider-Tabelle nicht verfügbar.");
                return;
            }
            
            org.eclipse.swt.widgets.Table table = gui.getProviderTable().getTable();
            if (table == null || table.isDisposed()) {
                gui.showError("Fehler", "Tabelle nicht verfügbar.");
                return;
            }
            
            org.eclipse.swt.widgets.TableItem[] selectedItems = table.getSelection();
            if (selectedItems.length == 0) {
                gui.showInfo("Keine Auswahl", "Bitte wählen Sie einen Signalprovider aus der Tabelle aus.");
                return;
            }
            
            org.eclipse.swt.widgets.TableItem item = selectedItems[0];
            String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
            String providerName = item.getText(ProviderTableHelper.COL_PROVIDER_NAME);
            
            openDrawdownAnalyzer(signalId, providerName);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Drawdown Analyzers", e);
            gui.showError("Fehler", "Drawdown Analyzer konnte nicht geöffnet werden: " + e.getMessage());
        }
    }
    
    /**
     * Öffnet den Drawdown Analyzer für die angegebene Signal-ID
     */
    public void openDrawdownAnalyzer(String signalId, String providerName) {
        try {
            LOGGER.info("Erstelle Drawdown-Daten für Signal: " + signalId);
            
            String tickFilePath = gui.getMonitor().getConfig().getTickFilePath(signalId);
            File tickFile = new File(tickFilePath);
            
            if (!tickFile.exists()) {
                gui.showInfo("Keine Tick-Daten", 
                    "Es wurden noch keine historischen Daten für das Signal " + signalId + " gespeichert.\n\n" +
                    "Das Monitoring muss mindestens einmal erfolgreich gelaufen sein, um Tick-Daten zu erfassen.");
                return;
            }
            
            // Lade Ticks
            com.mql.realmonitor.data.TickDataLoader.TickDataSet dataSet = 
                com.mql.realmonitor.data.TickDataLoader.loadTickData(tickFilePath, signalId);
            
            if (dataSet == null || dataSet.getTicks().isEmpty()) {
                gui.showInfo("Keine Ticks geladen", "Die Tick-Datei ist leer oder konnte nicht gelesen werden.");
                return;
            }
            
            // HTML-Datei prüfen
            File htmlFile = new File("doc/drawdown/index.html");
            if (!htmlFile.exists()) {
                gui.showError("Fehler", "Drawdown Analyzer HTML-Datei nicht gefunden unter:\n" + 
                               htmlFile.getAbsolutePath() + "\n\n" +
                               "Bitte stellen Sie sicher, dass der Ordner 'doc/drawdown' im Projektverzeichnis vorhanden ist.");
                return;
            }
            
            // Generiere data.js
            String strategyJson = String.format("{\n  \"strategyName\": \"%s\",\n  \"projectName\": \"MqlRealMonitor\"\n}", providerName);
            
            StringBuilder ordersJson = new StringBuilder();
            ordersJson.append("[\n");
            java.util.List<com.mql.realmonitor.data.TickDataLoader.TickData> ticks = dataSet.getTicks();
            
            if (!ticks.isEmpty()) {
                // Erster Tick als Basis
                com.mql.realmonitor.data.TickDataLoader.TickData firstTick = ticks.get(0);
                long timeMs = firstTick.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                ordersJson.append(String.format(java.util.Locale.US, 
                    "  { \"Type\": 0, \"CloseTime\": %d, \"ProfitLoss\": 0.0, \"Balance\": %.2f }", 
                    timeMs, firstTick.getEquity()));
                
                // Weitere Ticks als Differenzen (virtuelle Trades)
                for (int i = 1; i < ticks.size(); i++) {
                    com.mql.realmonitor.data.TickDataLoader.TickData prev = ticks.get(i - 1);
                    com.mql.realmonitor.data.TickDataLoader.TickData curr = ticks.get(i);
                    double change = curr.getEquity() - prev.getEquity();
                    long currTimeMs = curr.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    ordersJson.append(",\n");
                    ordersJson.append(String.format(java.util.Locale.US, 
                        "  { \"Type\": 0, \"CloseTime\": %d, \"ProfitLoss\": %.2f, \"Balance\": %.2f }", 
                        currTimeMs, change, curr.getEquity()));
                }
            }
            ordersJson.append("\n]");
            
            String dataJsContent = String.format("window.strategyData = %s;\nwindow.ordersData = %s;\n", 
                                                 strategyJson, ordersJson.toString());
            
            // In data.js schreiben
            File dataJsFile = new File("doc/drawdown/data.js");
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(dataJsFile)))) {
                writer.print(dataJsContent);
            }
            
            LOGGER.info("Drawdown-Daten erfolgreich geschrieben nach: " + dataJsFile.getAbsolutePath());
            
            // HTML-Datei im Webbrowser öffnen
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
                LOGGER.info("Drawdown Analyzer im Standard-Browser geöffnet");
                gui.updateStatus("Drawdown Analyzer für " + providerName + " geöffnet");
            } else {
                // Fallback-Methode aus Kontextmenü kopieren
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder processBuilder;
                String url = htmlFile.toURI().toString();
                
                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                } else if (os.contains("mac")) {
                    processBuilder = new ProcessBuilder("open", url);
                } else {
                    processBuilder = new ProcessBuilder("xdg-open", url);
                }
                processBuilder.start();
                LOGGER.info("Drawdown Analyzer mit Fallback OS-Kommando geöffnet");
                gui.updateStatus("Drawdown Analyzer geöffnet (OS Fallback)");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Generieren der Drawdown-Daten oder Öffnen des Browsers", e);
            gui.showError("Fehler", "Drawdown Analyzer konnte nicht gestartet werden:\n" + e.getMessage());
        }
    }
    
    /**
     * Bereinigt Ressourcen beim Herunterfahren
     */
    public void cleanup() {
        try {
            // Buttons werden automatisch durch SWT disposed
            LOGGER.info("ToolbarManager bereinigt");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Bereinigen des ToolbarManagers", e);
        }
    }
}