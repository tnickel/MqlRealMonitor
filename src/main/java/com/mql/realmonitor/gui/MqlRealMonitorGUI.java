package com.mql.realmonitor.gui;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
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
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.MqlRealMonitor;
import com.mql.realmonitor.currency.CurrencyDataLoader; // NEU: Currency Import
import com.mql.realmonitor.parser.SignalData;

/**
 * Haupt-GUI für MqlRealMonitor
 * ERWEITERT: Neuer "Chart-Übersicht" Button für Signalprovider-Overview
 * ERWEITERT: Favoritenklasse-Farben für Zeilen-Hintergrund
 * DEBUG: initializeTableWithSavedData() mit ausführlicher Diagnostik
 * AKTUALISIERT: Format-Konvertierung statt Tausendertrennzeichen-Reparatur
 * NEU: Versionsnummer in der Titelleiste
 * NEU: Delete Signal Funktionalität mit Toolbar-Button
 * NEU: Currency-Loading für XAUUSD/BTCUSD von MQL5 (v1.2.1)
 */
public class MqlRealMonitorGUI {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorGUI.class.getName());
    
    // VERSION INFORMATION
    private static final String VERSION = "1.2.1"; // AKTUALISIERT: Version wegen Currency-Feature
    private static final String BUILD_DATE = "2025-08-26"; // AKTUALISIERT: Aktuelles Datum
    private static final String APPLICATION_TITLE = "MQL5 Real Monitor - Signal Provider Überwachung";
    
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
    private Button overviewButton; // Chart-Übersicht Button
    private Button deleteSignalButton; // Delete Signal Button
    private Button kurseladenButton; // NEU: Currency Button
    private Text intervalText;
    private Label countLabel;
    
    // NEU: Currency-Funktionalität
    private CurrencyDataLoader currencyDataLoader; // NEU: Currency Data Loader
    
    // Farben und Fonts
    private Color greenColor;
    private Color redColor;
    private Color grayColor;
    private Font boldFont;
    private Font statusFont;
    
    // Favoritenklasse-Farben (Helle Hintergrundfarben für bessere Lesbarkeit)
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
        
        // NEU: Currency Data Loader initialisieren
        initializeCurrencyDataLoader();
        
        // Tabelle sofort beim Erstellen initialisieren
        initializeTableWithSavedData();
        
        // Delete Signal Funktionalität initialisieren
        initializeDeleteSignalFunctionality();
    }
    
    /**
     * NEU: Initialisiert den CurrencyDataLoader.
     */
    /**
     * ERWEITERTE DEBUG-VERSION: Initialisiert den CurrencyDataLoader mit detaillierter Diagnose.
     * Gehört in die MqlRealMonitorGUI Klasse.
     */
    /**
     * Initialisiert den CurrencyDataLoader mit detailliertem Exception-Dialog.
     * Zeigt bei jeder Exception ein PopUp-Fenster mit allen Details.
     * Gehört in die MqlRealMonitorGUI Klasse.
     */
    private void initializeCurrencyDataLoader() {
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG MIT DETAILLIERTEM DEBUG ===");
        
        try {
            // Schritt 1: Monitor validierung
            if (monitor == null) {
                String errorMsg = "Monitor ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Monitor-Fehler", errorMsg, null, null);
                currencyDataLoader = null;
                return;
            }
            
            // Schritt 2: Config validierung  
            if (monitor.getConfig() == null) {
                String errorMsg = "Monitor.getConfig() ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Config-Fehler", errorMsg, null, null);
                currencyDataLoader = null;
                return;
            }
            
            LOGGER.info("Monitor und Config OK - Erstelle CurrencyDataLoader...");
            LOGGER.info("BasePath: " + monitor.getConfig().getBasePath());
            
            // Schritt 3: CurrencyDataLoader erstellen - HIER passiert wahrscheinlich der Fehler
            currencyDataLoader = new CurrencyDataLoader(monitor.getConfig());
            
            if (currencyDataLoader != null) {
                LOGGER.info("CurrencyDataLoader erfolgreich erstellt!");
            } else {
                String errorMsg = "CurrencyDataLoader ist NULL nach erfolgreicher Erstellung";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Erstellungs-Fehler", errorMsg, null, null);
            }
            
        } catch (NoClassDefFoundError e) {
            String errorMsg = "KLASSE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoClassDefFoundError: " + errorMsg);
            showDetailedExceptionDialog("Klasse nicht gefunden", errorMsg, e, "NoClassDefFoundError");
            currencyDataLoader = null;
            
        } catch (NoSuchMethodError e) {
            String errorMsg = "METHODE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoSuchMethodError: " + errorMsg); 
            showDetailedExceptionDialog("Methode nicht gefunden", errorMsg, e, "NoSuchMethodError");
            currencyDataLoader = null;
            
        } catch (LinkageError e) {
            String errorMsg = "LINKAGE FEHLER: " + e.getMessage();
            LOGGER.severe("LinkageError: " + errorMsg);
            showDetailedExceptionDialog("Linkage-Fehler", errorMsg, e, "LinkageError");
            currencyDataLoader = null;
            
        } catch (Exception e) {
            String errorMsg = "ALLGEMEINE EXCEPTION: " + e.getMessage();
            LOGGER.log(Level.SEVERE, "Exception beim Initialisieren des CurrencyDataLoader", e);
            showDetailedExceptionDialog("Unerwarteter Fehler", errorMsg, e, e.getClass().getSimpleName());
            currencyDataLoader = null;
            
        } catch (Throwable t) {
            String errorMsg = "SCHWERWIEGENDER FEHLER: " + t.getMessage();
            LOGGER.log(Level.SEVERE, "Throwable beim Initialisieren des CurrencyDataLoader", t);
            showDetailedExceptionDialog("Schwerwiegender Fehler", errorMsg, t, t.getClass().getSimpleName());
            currencyDataLoader = null;
        }
        
        // Status loggen
        boolean success = (currencyDataLoader != null);
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG " + (success ? "ERFOLGREICH" : "FEHLGESCHLAGEN") + " ===");
        
        // Button Status setzen
        if (kurseladenButton != null && !kurseladenButton.isDisposed()) {
            kurseladenButton.setEnabled(success);
            LOGGER.info("Kurse laden Button " + (success ? "AKTIVIERT" : "DEAKTIVIERT"));
        }
    }

    /**
     * Zeigt einen detaillierten Exception-Dialog mit allen verfügbaren Informationen.
     * 
     * @param title Titel des Dialogs
     * @param message Haupt-Fehlermeldung  
     * @param throwable Die Exception/Throwable (kann null sein)
     * @param errorType Typ des Fehlers als String
     */
    private void showDetailedExceptionDialog(String title, String message, Throwable throwable, String errorType) {
        try {
            if (shell == null || shell.isDisposed()) {
                LOGGER.warning("Shell nicht verfügbar für Exception-Dialog");
                return;
            }
            
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append("=== DETAILLIERTE FEHLER-INFORMATION ===\n\n");
            
            // Basis-Info
            fullMessage.append("TITEL: ").append(title).append("\n");
            fullMessage.append("NACHRICHT: ").append(message).append("\n\n");
            
            if (errorType != null) {
                fullMessage.append("FEHLER-TYP: ").append(errorType).append("\n\n");
            }
            
            // Exception-Details falls verfügbar
            if (throwable != null) {
                fullMessage.append("EXCEPTION-KLASSE: ").append(throwable.getClass().getName()).append("\n");
                fullMessage.append("EXCEPTION-MESSAGE: ").append(throwable.getMessage()).append("\n\n");
                
                // Cause falls verfügbar
                if (throwable.getCause() != null) {
                    fullMessage.append("URSACHE-KLASSE: ").append(throwable.getCause().getClass().getName()).append("\n");
                    fullMessage.append("URSACHE-MESSAGE: ").append(throwable.getCause().getMessage()).append("\n\n");
                }
                
                // Stack Trace (erste 10 Zeilen)
                fullMessage.append("STACK TRACE (erste 10 Zeilen):\n");
                StackTraceElement[] stackTrace = throwable.getStackTrace();
                int maxLines = Math.min(10, stackTrace.length);
                for (int i = 0; i < maxLines; i++) {
                    fullMessage.append("  ").append(stackTrace[i].toString()).append("\n");
                }
                
                if (stackTrace.length > 10) {
                    fullMessage.append("  ... (").append(stackTrace.length - 10).append(" weitere Zeilen)\n");
                }
            }
            
            // System-Informationen
            fullMessage.append("\n=== SYSTEM-INFORMATIONEN ===\n");
            fullMessage.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
            fullMessage.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
            fullMessage.append("OS Name: ").append(System.getProperty("os.name")).append("\n");
            fullMessage.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
            fullMessage.append("Working Directory: ").append(System.getProperty("user.dir")).append("\n");
            
            // Konfiguration falls verfügbar
            if (monitor != null && monitor.getConfig() != null) {
                fullMessage.append("\n=== KONFIGURATION ===\n");
                fullMessage.append("Base Path: ").append(monitor.getConfig().getBasePath()).append("\n");
                fullMessage.append("Config-Klasse: ").append(monitor.getConfig().getClass().getName()).append("\n");
            }
            
            fullMessage.append("\n=== ENDE DER FEHLER-INFORMATION ===");
            
            // Dialog anzeigen
            MessageBox messageBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK | SWT.RESIZE);
            messageBox.setText("CurrencyDataLoader Fehler-Details: " + title);
            messageBox.setMessage(fullMessage.toString());
            messageBox.open();
            
            LOGGER.info("Detaillierter Exception-Dialog angezeigt für: " + title);
            
        } catch (Exception dialogException) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anzeigen des Exception-Dialogs", dialogException);
            System.err.println("FATAL: Konnte Exception-Dialog nicht anzeigen: " + dialogException.getMessage());
            System.err.println("Original Fehler war: " + title + " - " + message);
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }
    
    /**
     * Initialisiert die Farben
     */
    private void initializeColors() {
        greenColor = new Color(display, 0, 128, 0);
        redColor = new Color(display, 200, 0, 0);
        grayColor = new Color(display, 128, 128, 128);
        
        // Favoritenklasse-Hintergrundfarben (hell und gut lesbar)
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
        shell.setText(APPLICATION_TITLE + " v" + VERSION);
        shell.setSize(1000, 700);
        shell.setLayout(new GridLayout(1, false));
        
        // Beim Schließen ordnungsgemäß beenden
        shell.addListener(SWT.Close, event -> {
            LOGGER.info("GUI wird geschlossen (Version " + VERSION + ")");
            monitor.shutdown();
            
            // Delete Signal Funktionalität bereinigen
            cleanupDeleteSignalFunctionality();
            
            // NEU: Currency-Funktionalität bereinigen
            disposeCurrencyFunctionality();
            
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
     * ERWEITERT: Erstellt die Toolbar mit Currency-Button, Chart-Übersicht Button, Delete Signal Button und Add Signal Button
     */
    private void createToolbar() {
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        toolbar.setLayout(new GridLayout(16, false)); // ERWEITERT: 16 statt 15 Spalten (für Currency Button)
        
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
                deleteSelectedSignalFromToolbar();
            }
        });
        
        // Add Signal Button
        Button addSignalButton = new Button(toolbar, SWT.PUSH);
        addSignalButton.setText("➕ Hinzufügen");
        addSignalButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        addSignalButton.setToolTipText("Neues Signal zu Favoriten hinzufügen");
        addSignalButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addNewSignalToFavorites();
            }
        });
        
        // NEU: Currency Button (Kurse laden)
        createKurseladenButton(toolbar);
        
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
        
        // Repair Button - Format-Konvertierung
        Button repairButton = new Button(toolbar, SWT.PUSH);
        repairButton.setText("Tick-Dateien reparieren");
        repairButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        repairButton.setToolTipText("Konvertiert alte Tickdaten (4 Spalten) ins neue Format (5 Spalten mit Profit)");
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
        
        LOGGER.info("Toolbar mit Currency Button, Delete Signal Button und Add Signal Button erstellt");
    }
    
    // ========================================================================
    // NEU: CURRENCY-FUNKTIONALITÄT
    // ========================================================================
    
    /**
     * NEU: Erstellt den "Kurse laden" Button in der Toolbar.
     * 
     * @param parent Der Parent-Composite (Toolbar)
     */
    private void createKurseladenButton(Composite parent) {
        if (parent == null || parent.isDisposed()) {
            LOGGER.warning("Parent für Kurse laden Button ist null oder disposed");
            return;
        }
        
        try {
            kurseladenButton = new Button(parent, SWT.PUSH);
            kurseladenButton.setText("💰 Kurse laden");
            kurseladenButton.setToolTipText("Lädt aktuelle Währungskurse (XAUUSD, BTCUSD) von MQL5");
            
            // Layout-Daten setzen
            GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            kurseladenButton.setLayoutData(gridData);
            
            // Event-Handler für Button-Klick
            kurseladenButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    loadCurrencyRates();
                }
            });
            
            // Button initial aktivieren nur wenn CurrencyDataLoader verfügbar
            kurseladenButton.setEnabled(currencyDataLoader != null);
            
            LOGGER.info("Kurse laden Button erfolgreich erstellt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Erstellen des Kurse laden Buttons: " + e.getMessage(), e);
            showErrorMessage("Button-Erstellungsfehler", 
                "Kurse laden Button konnte nicht erstellt werden: " + e.getMessage());
        }
    }
    
    /**
     * NEU: Lädt Währungskurse von MQL5 in einem separaten Thread.
     */
    private void loadCurrencyRates() {
        // Validierung
        if (currencyDataLoader == null) {
            showErrorMessage("Fehler", "CurrencyDataLoader ist nicht initialisiert.\nBitte Anwendung neu starten.");
            return;
        }
        
        if (kurseladenButton == null || kurseladenButton.isDisposed()) {
            LOGGER.warning("Kurse laden Button ist nicht verfügbar");
            return;
        }
        
        // Prevent multiple simultaneous loads
        if (!kurseladenButton.getEnabled()) {
            LOGGER.info("Currency Loading bereits aktiv - ignoriere weiteren Button-Klick");
            return;
        }
        
        LOGGER.info("=== USER-AKTION: Kurse laden Button geklickt ===");
        
        // Button deaktivieren und Status ändern
        kurseladenButton.setEnabled(false);
        kurseladenButton.setText("Lade...");
        
        // Status-Update
        updateStatus("Lade Währungskurse von MQL5...");
        
        // Loading in separatem Thread um GUI nicht zu blockieren
        Thread loadingThread = new Thread(() -> {
            String diagnosis = null;
            boolean success = false;
            
            try {
                LOGGER.info("Starte Currency Loading Thread...");
                diagnosis = currencyDataLoader.loadCurrencyRatesWithDiagnosis();
                success = true;
                
                LOGGER.info("Currency Loading erfolgreich abgeschlossen");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Currency Loading Thread Fehler: " + e.getMessage(), e);
                diagnosis = "FEHLER beim Laden der Währungskurse:\n" + e.getMessage();
                success = false;
            }
            
            // Ergebnis in UI-Thread verarbeiten
            final String finalDiagnosis = diagnosis;
            final boolean finalSuccess = success;
            
            display.asyncExec(() -> {
                try {
                    // Ergebnis-Dialog anzeigen
                    showCurrencyLoadingResult(finalDiagnosis, finalSuccess);
                    
                    // Button und Status zurücksetzen
                    resetKurseladenButton();
                    updateStatusAfterCurrencyLoading(finalSuccess);
                    
                    LOGGER.info("Currency Loading UI-Updates abgeschlossen");
                    
                } catch (Exception uiException) {
                    LOGGER.log(Level.SEVERE, "Fehler beim UI-Update nach Currency Loading: " + uiException.getMessage(), uiException);
                }
            });
        });
        
        loadingThread.setName("CurrencyLoadingThread");
        loadingThread.setDaemon(true);
        loadingThread.start();
    }
    
    /**
     * NEU: Setzt den Kurse laden Button nach dem Loading zurück.
     */
    private void resetKurseladenButton() {
        try {
            if (kurseladenButton != null && !kurseladenButton.isDisposed()) {
                kurseladenButton.setEnabled(true);
                kurseladenButton.setText("💰 Kurse laden");
                
                LOGGER.fine("Kurse laden Button zurückgesetzt");
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Zurücksetzen des Kurse laden Buttons: " + e.getMessage());
        }
    }
    
    /**
     * NEU: Aktualisiert den Status nach dem Currency Loading.
     * 
     * @param success true wenn erfolgreich
     */
    private void updateStatusAfterCurrencyLoading(boolean success) {
        try {
            String statusMessage;
            if (success) {
                statusMessage = "Währungskurse erfolgreich geladen";
            } else {
                statusMessage = "Fehler beim Laden der Währungskurse";
            }
            
            updateStatus(statusMessage);
            
            LOGGER.info("Status nach Currency Loading aktualisiert: " + statusMessage);
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Status-Update nach Currency Loading: " + e.getMessage());
        }
    }
    
    /**
     * NEU: Zeigt das Ergebnis des Currency Loading in einem Dialog.
     * 
     * @param message Die anzuzeigende Nachricht
     * @param success true wenn erfolgreich
     */
    private void showCurrencyLoadingResult(String message, boolean success) {
        try {
            if (shell == null || shell.isDisposed()) {
                LOGGER.warning("Shell nicht verfügbar für Currency Loading Result Dialog");
                return;
            }
            
            int style = success ? (SWT.ICON_INFORMATION | SWT.OK) : (SWT.ICON_ERROR | SWT.OK);
            MessageBox messageBox = new MessageBox(shell, style);
            
            String title = success ? "Währungskurse geladen" : "Fehler beim Laden";
            messageBox.setText(title);
            
            // Nachricht formatieren
            String displayMessage = message;
            if (message != null && message.length() > 500) {
                displayMessage = message.substring(0, 500) + "\n\n... (gekürzt)";
            }
            messageBox.setMessage(displayMessage != null ? displayMessage : "Unbekannter Fehler");
            
            messageBox.open();
            
            LOGGER.info("Currency Loading Result Dialog angezeigt - " + (success ? "Erfolg" : "Fehler"));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anzeigen des Currency Loading Result Dialogs: " + e.getMessage(), e);
        }
    }
    
    /**
     * NEU: Zeigt eine allgemeine Fehlermeldung in einem Dialog.
     * 
     * @param title Titel des Dialogs
     * @param message Fehlermeldung
     */
    private void showErrorMessage(String title, String message) {
        try {
            if (shell == null || shell.isDisposed()) {
                LOGGER.warning("Shell nicht verfügbar für Error Dialog");
                return;
            }
            
            MessageBox messageBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            messageBox.setText(title != null ? title : "Fehler");
            messageBox.setMessage(message != null ? message : "Unbekannter Fehler");
            messageBox.open();
            
            LOGGER.info("Error Dialog angezeigt: " + title);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anzeigen des Error Dialogs: " + e.getMessage(), e);
        }
    }
    
    /**
     * NEU: Currency-Funktionalität beim Dispose aufräumen.
     */
    private void disposeCurrencyFunctionality() {
        try {
            // Currency Data Loader aufräumen
            if (currencyDataLoader != null) {
                currencyDataLoader = null;
                LOGGER.info("CurrencyDataLoader disposed");
            }
            
            // Button wird automatisch durch SWT disposed
            kurseladenButton = null;
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Dispose der Currency-Funktionalität: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // BESTEHENDE FUNKTIONALITÄT (Add Signal, Delete Signal, etc.)
    // ========================================================================
    
    /**
     * Öffnet einen Dialog zum Hinzufügen eines neuen Signals zu den Favoriten
     */
    private void addNewSignalToFavorites() {
        try {
            LOGGER.info("=== ADD NEW SIGNAL DIALOG GEÖFFNET ===");
            
            // Dialog erstellen
            Shell addSignalDialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
            addSignalDialog.setText("Neues Signal zu Favoriten hinzufügen");
            addSignalDialog.setSize(400, 200);
            addSignalDialog.setLayout(new GridLayout(2, false));
            
            // Signal ID Label und Eingabefeld
            Label signalIdLabel = new Label(addSignalDialog, SWT.NONE);
            signalIdLabel.setText("Signal ID (Magic):");
            signalIdLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            
            Text signalIdText = new Text(addSignalDialog, SWT.BORDER);
            signalIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            signalIdText.setToolTipText("Geben Sie die Signal-ID (Magic Number) ein, z.B. 1234567");
            
            // Favoritenklasse Label und Combo
            Label favoriteClassLabel = new Label(addSignalDialog, SWT.NONE);
            favoriteClassLabel.setText("Favoritenklasse (1-10):");
            favoriteClassLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            
            Combo favoriteClassCombo = new Combo(addSignalDialog, SWT.DROP_DOWN | SWT.READ_ONLY);
            favoriteClassCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            favoriteClassCombo.setItems(new String[]{"1 (Hellgrün - Beste)", "2 (Hellgelb - Gut)", "3 (Hellorange - Mittel)", 
                                                   "4 (Hellrot)", "5 (Hellrot)", "6 (Hellrot)", "7 (Hellrot)", 
                                                   "8 (Hellrot)", "9 (Hellrot)", "10 (Hellrot - Schlechteste)"});
            favoriteClassCombo.select(0); // Standard: Klasse 1
            favoriteClassCombo.setToolTipText("Wählen Sie die Favoritenklasse (1=beste, 10=schlechteste)");
            
            // Separator
            Label separator = new Label(addSignalDialog, SWT.SEPARATOR | SWT.HORIZONTAL);
            GridData separatorData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            separatorData.horizontalSpan = 2;
            separator.setLayoutData(separatorData);
            
            // Button Container
            Composite buttonContainer = new Composite(addSignalDialog, SWT.NONE);
            GridData buttonContainerData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            buttonContainerData.horizontalSpan = 2;
            buttonContainer.setLayoutData(buttonContainerData);
            buttonContainer.setLayout(new GridLayout(2, true));
            
            // OK Button
            Button okButton = new Button(buttonContainer, SWT.PUSH);
            okButton.setText("OK");
            okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            // Cancel Button
            Button cancelButton = new Button(buttonContainer, SWT.PUSH);
            cancelButton.setText("Abbrechen");
            cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            // Event Handlers
            final boolean[] dialogResult = {false};
            
            okButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    String signalId = signalIdText.getText().trim();
                    int selectedIndex = favoriteClassCombo.getSelectionIndex();
                    
                    if (validateSignalInput(signalId, selectedIndex)) {
                        String favoriteClass = String.valueOf(selectedIndex + 1); // 1-10
                        if (addSignalToFavoritesFile(signalId, favoriteClass)) {
                            dialogResult[0] = true;
                            addSignalDialog.close();
                        }
                    }
                }
            });
            
            cancelButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    dialogResult[0] = false;
                    addSignalDialog.close();
                }
            });
            
            // Dialog zentrieren
            centerDialog(addSignalDialog);
            
            // Dialog öffnen
            addSignalDialog.open();
            
            // Event Loop für modalen Dialog
            while (!addSignalDialog.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
            
            // Nach dem Schließen: Tabelle aktualisieren falls Signal hinzugefügt wurde
            if (dialogResult[0]) {
                refreshTableAfterSignalAdded();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Add Signal Dialogs", e);
            showError("Fehler", "Konnte Dialog nicht öffnen: " + e.getMessage());
        }
    }
    
    /**
     * Validiert die Eingaben für ein neues Signal
     */
    private boolean validateSignalInput(String signalId, int favoriteClassIndex) {
        // Signal ID Validierung
        if (signalId == null || signalId.isEmpty()) {
            showError("Ungültige Eingabe", "Bitte geben Sie eine Signal-ID ein.");
            return false;
        }
        
        // Prüfe ob Signal ID numerisch ist
        try {
            Long.parseLong(signalId);
        } catch (NumberFormatException e) {
            showError("Ungültige Signal-ID", "Die Signal-ID muss eine Zahl sein.\n\nBeispiel: 1234567");
            return false;
        }
        
        // Favoritenklasse Validierung
        if (favoriteClassIndex < 0 || favoriteClassIndex > 9) {
            showError("Ungültige Favoritenklasse", "Bitte wählen Sie eine Favoritenklasse von 1-10 aus.");
            return false;
        }
        
        // Prüfe ob Signal bereits existiert
        try {
            com.mql.realmonitor.downloader.FavoritesReader favoritesReader = 
                new com.mql.realmonitor.downloader.FavoritesReader(monitor.getConfig());
            
            if (favoritesReader.containsSignalId(signalId)) {
                showError("Signal bereits vorhanden", 
                         "Das Signal " + signalId + " ist bereits in den Favoriten vorhanden.\n\n" +
                         "Verwenden Sie den 'Löschen' Button um es zuerst zu entfernen, " +
                         "falls Sie die Favoritenklasse ändern möchten.");
                return false;
            }
        } catch (Exception e) {
            LOGGER.warning("Konnte nicht prüfen ob Signal bereits existiert: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * Fügt ein Signal zur favorites.txt hinzu
     */
    private boolean addSignalToFavoritesFile(String signalId, String favoriteClass) {
        try {
            LOGGER.info("=== FÜGE NEUES SIGNAL ZU FAVORITES HINZU ===");
            LOGGER.info("Signal ID: " + signalId + ", Favoritenklasse: " + favoriteClass);
            
            // FavoritesReader verwenden um Signal hinzuzufügen
            com.mql.realmonitor.downloader.FavoritesReader favoritesReader = 
                new com.mql.realmonitor.downloader.FavoritesReader(monitor.getConfig());
            
            boolean success = favoritesReader.addSignal(signalId, favoriteClass);
            
            if (success) {
                LOGGER.info("Signal erfolgreich zu Favoriten hinzugefügt: " + signalId + ":" + favoriteClass);
                
                showInfo("Signal hinzugefügt", 
                        "Das Signal wurde erfolgreich zu den Favoriten hinzugefügt:\n\n" +
                        "Signal ID: " + signalId + "\n" +
                        "Favoritenklasse: " + favoriteClass + "\n\n" +
                        "Das Signal wird beim nächsten Refresh geladen.");
                
                return true;
            } else {
                LOGGER.severe("Fehler beim Hinzufügen des Signals zu favorites.txt: " + signalId);
                
                showError("Fehler beim Hinzufügen", 
                         "Das Signal konnte nicht zu den Favoriten hinzugefügt werden:\n\n" +
                         "Signal ID: " + signalId + "\n" +
                         "Favoritenklasse: " + favoriteClass + "\n\n" +
                         "Mögliche Ursachen:\n" +
                         "• Datei ist schreibgeschützt\n" +
                         "• Unzureichende Berechtigungen\n" +
                         "• Signal bereits vorhanden\n\n" +
                         "Prüfen Sie die Logs für Details.");
                
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unerwarteter Fehler beim Hinzufügen des Signals: " + signalId, e);
            
            showError("Schwerwiegender Fehler", 
                     "Unerwarteter Fehler beim Hinzufügen des Signals:\n\n" +
                     "Signal ID: " + signalId + "\n" +
                     "Fehler: " + e.getMessage() + "\n\n" +
                     "Das Signal wurde möglicherweise nicht hinzugefügt.");
            
            return false;
        }
    }
    
    /**
     * Zentriert einen Dialog auf dem Hauptfenster
     */
    private void centerDialog(Shell dialog) {
        Point parentLocation = shell.getLocation();
        Point parentSize = shell.getSize();
        Point dialogSize = dialog.getSize();
        
        int x = parentLocation.x + (parentSize.x - dialogSize.x) / 2;
        int y = parentLocation.y + (parentSize.y - dialogSize.y) / 2;
        
        dialog.setLocation(x, y);
    }
    
    /**
     * Aktualisiert die Tabelle nach dem Hinzufügen eines Signals
     */
    private void refreshTableAfterSignalAdded() {
        try {
            LOGGER.info("=== AKTUALISIERE TABELLE NACH SIGNAL-HINZUFÜGUNG ===");
            
            // Status anzeigen
            updateStatus("Aktualisiere Favoriten nach Signal-Hinzufügung...");
            
            // Favorites-Cache der Tabelle aktualisieren
            if (providerTable != null) {
                providerTable.refreshFavoriteClasses();
                
                // Manuellen Refresh auslösen um neue Daten zu laden
                display.timerExec(1000, () -> {
                    manualRefresh();
                    updateStatus("Neues Signal hinzugefügt - Tabelle aktualisiert");
                });
            }
            
            LOGGER.info("Tabellen-Aktualisierung nach Signal-Hinzufügung eingeleitet");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Aktualisieren der Tabelle nach Signal-Hinzufügung", e);
            updateStatus("Fehler beim Aktualisieren der Tabelle");
        }
    }
    
    /**
     * Behandelt das Löschen eines Signals über den Toolbar-Button
     */
    private void deleteSelectedSignalFromToolbar() {
        try {
            LOGGER.info("=== DELETE SIGNAL ÜBER TOOLBAR AUSGELÖST ===");
            
            // Prüfen ob SignalProviderTable verfügbar ist
            if (providerTable == null) {
                showError("Fehler", "Signalprovider-Tabelle nicht verfügbar.");
                return;
            }
            
            Table table = providerTable.getTable();
            if (table == null || table.isDisposed()) {
                showError("Fehler", "Tabelle nicht verfügbar oder bereits geschlossen.");
                return;
            }
            
            // Prüfen ob genau ein Signal ausgewählt ist
            SignalProviderContextMenu contextMenu = providerTable.getContextMenu();
            if (!contextMenu.hasSignalSelectedForDeletion(table)) {
                String selectionInfo = contextMenu.getSelectedSignalInfo(table);
                showInfo("Ungültige Auswahl", 
                       "Bitte wählen Sie genau ein Signal zum Löschen aus.\n\nAktueller Status: " + selectionInfo);
                return;
            }
            
            // Delete-Funktion über das Kontextmenü ausführen
            contextMenu.deleteSelectedSignalFromFavorites(table);
            
            LOGGER.info("Delete Signal über Toolbar erfolgreich ausgeführt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Löschen des Signals über Toolbar", e);
            showError("Unerwarteter Fehler", 
                    "Fehler beim Löschen des Signals:\n\n" + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert den Zustand des Delete-Buttons basierend auf der Tabellenauswahl
     */
    private void updateDeleteButtonState() {
        if (deleteSignalButton == null || deleteSignalButton.isDisposed()) {
            return;
        }
        
        try {
            boolean hasValidSelection = false;
            String tooltipText = "Ausgewähltes Signal aus Favoriten löschen";
            
            if (providerTable != null) {
                Table table = providerTable.getTable();
                if (table != null && !table.isDisposed()) {
                    SignalProviderContextMenu contextMenu = providerTable.getContextMenu();
                    hasValidSelection = contextMenu.hasSignalSelectedForDeletion(table);
                    
                    if (!hasValidSelection) {
                        String selectionInfo = contextMenu.getSelectedSignalInfo(table);
                        tooltipText = "Signal löschen nicht möglich: " + selectionInfo;
                    }
                }
            }
            
            deleteSignalButton.setEnabled(hasValidSelection);
            deleteSignalButton.setToolTipText(tooltipText);
            
            LOGGER.fine("Delete-Button Zustand aktualisiert: " + (hasValidSelection ? "AKTIVIERT" : "DEAKTIVIERT"));
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Aktualisieren des Delete-Button Zustands", e);
            deleteSignalButton.setEnabled(false);
            deleteSignalButton.setToolTipText("Fehler bei Zustandsüberprüfung");
        }
    }
    
    /**
     * Setzt einen Listener für Tabellenauswahl-Änderungen
     */
    private void setupTableSelectionListener() {
        if (providerTable == null) {
            return;
        }
        
        Table table = providerTable.getTable();
        if (table == null || table.isDisposed()) {
            return;
        }
        
        try {
            // Listener für Auswahl-Änderungen
            table.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    // Delete-Button Zustand aktualisieren
                    updateDeleteButtonState();
                }
            });
            
            // Auch bei Fokus-Änderungen aktualisieren
            table.addFocusListener(new org.eclipse.swt.events.FocusListener() {
                @Override
                public void focusGained(org.eclipse.swt.events.FocusEvent e) {
                    updateDeleteButtonState();
                }
                
                @Override
                public void focusLost(org.eclipse.swt.events.FocusEvent e) {
                    // Optional: Button deaktivieren wenn Tabelle Fokus verliert
                    // updateDeleteButtonState();
                }
            });
            
            LOGGER.info("Tabellen-Auswahl-Listener für Delete-Button erfolgreich eingerichtet");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Einrichten der Tabellen-Listener", e);
        }
    }
    
    /**
     * Initialisiert die komplette Delete-Signal-Funktionalität
     */
    private void initializeDeleteSignalFunctionality() {
        try {
            LOGGER.info("=== INITIALISIERE DELETE SIGNAL FUNKTIONALITÄT ===");
            
            // Tabellen-Listener einrichten (verzögert, falls Tabelle noch nicht erstellt)
            display.timerExec(1000, () -> {
                setupTableSelectionListener();
                updateDeleteButtonState();
            });
            
            LOGGER.info("Delete Signal Funktionalität erfolgreich initialisiert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Initialisieren der Delete Signal Funktionalität", e);
        }
    }
    
    /**
     * Cleanup-Methode für das Schließen der Anwendung
     */
    private void cleanupDeleteSignalFunctionality() {
        try {
            if (deleteSignalButton != null && !deleteSignalButton.isDisposed()) {
                deleteSignalButton.dispose();
                deleteSignalButton = null;
            }
            
            LOGGER.info("Delete Signal Funktionalität erfolgreich bereinigt");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Bereinigen der Delete Signal Funktionalität", e);
        }
    }
    
    /**
     * Öffnet die Chart-Übersicht für alle Signalprovider
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
                      "=== ANWENDUNGSVERSION ===\n" +
                      "Version: " + VERSION + "\n" +
                      "Build-Datum: " + BUILD_DATE + "\n\n" +
                      "=== AKTUELLE KONFIGURATION ===\n" +
                      "Basis-Pfad: " + monitor.getConfig().getBasePath() + "\n" +
                      "Favoriten-Datei: " + monitor.getConfig().getFavoritesFile() + "\n" +
                      "Download-Verzeichnis: " + monitor.getConfig().getDownloadDir() + "\n" +
                      "Tick-Verzeichnis: " + monitor.getConfig().getTickDir() + "\n" +
                      "Intervall: " + monitor.getConfig().getIntervalMinutes() + " Minuten\n\n" +
                      "=== NEU IN VERSION 1.2.1 ===\n" +
                      "• Currency Loading für XAUUSD/BTCUSD von MQL5\n" +
                      "• Kursdaten-Speicherung in realtick/tick_kurse/\n" +
                      "• Thread-sichere Currency-Operationen");
        box.setText("Konfiguration - MQL5 Real Monitor v" + VERSION);
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
                updateStatus("Konvertiere Tick-Dateien ins neue Format...");
                
                // Konvertierung in separatem Thread ausführen
                new Thread(() -> {
                    try {
                        com.mql.realmonitor.tickdata.TickDataWriter writer = 
                            new com.mql.realmonitor.tickdata.TickDataWriter(monitor.getConfig());
                        
                        Map<String, Boolean> results = writer.convertAllTickFilesToNewFormat();
                        
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
                        
                        display.asyncExec(() -> {
                            updateStatus("Format-Konvertierung abgeschlossen");
                            showInfo("Konvertierung abgeschlossen", message);
                        });
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Fehler bei der Format-Konvertierung", e);
                        display.asyncExec(() -> {
                            updateStatus("Format-Konvertierung fehlgeschlagen");
                            showError("Konvertierung fehlgeschlagen", 
                                     "Fehler bei der Format-Konvertierung der Tick-Dateien:\n" + e.getMessage());
                        });
                    }
                }).start();
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Starten der Format-Konvertierung", e);
                showError("Fehler", "Konnte Format-Konvertierung nicht starten: " + e.getMessage());
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
            final int finalCreatedEntries = createdEntries;
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
                
                // Delete-Button Zustand aktualisieren wenn Daten sich ändern
                updateDeleteButtonState();
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
                
                // Delete-Button Zustand aktualisieren wenn Status sich ändert
                updateDeleteButtonState();
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
        
        LOGGER.info("=== " + getVersionInfo() + " ===");
        LOGGER.info("MqlRealMonitor GUI geöffnet");
        updateStatus("MqlRealMonitor bereit - Daten geladen (v" + VERSION + ")");
        
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
        
        // Favoritenklasse-Farben freigeben
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
     * Gibt die SignalProviderTable zurück
     */
    public SignalProviderTable getProviderTable() {
        return providerTable;
    }
    
    // Getter für Favoritenklasse-Farben (vereinfacht)
    
    /**
     * Gibt die Hintergrundfarbe für Favoritenklasse 1 zurück (Hellgrün)
     */
    public Color getFavoriteClass1Color() {
        return favoriteClass1Color;
    }
    
    /**
     * Gibt die Hintergrundfarbe für Favoritenklasse 2 zurück (Hellgelb)
     */
    public Color getFavoriteClass2Color() {
        return favoriteClass2Color;
    }
    
    /**
     * Gibt die Hintergrundfarbe für Favoritenklasse 3 zurück (Hellorange)
     */
    public Color getFavoriteClass3Color() {
        return favoriteClass3Color;
    }
    
    /**
     * Gibt die Hintergrundfarbe für Favoritenklassen 4-10 zurück (Hellrot)
     */
    public Color getFavoriteClass4To10Color() {
        return favoriteClass4To10Color;
    }
    
    // Version Information Getter
    
    /**
     * Gibt die aktuelle Anwendungsversion zurück
     */
    public static String getVersion() {
        return VERSION;
    }
    
    /**
     * Gibt das Build-Datum zurück
     */
    public static String getBuildDate() {
        return BUILD_DATE;
    }
    
    /**
     * Gibt den vollständigen Anwendungstitel mit Version zurück
     */
    public static String getFullTitle() {
        return APPLICATION_TITLE + " v" + VERSION;
    }
    
    /**
     * Gibt eine vollständige Versions-Information zurück
     */
    public static String getVersionInfo() {
        return "MQL5 Real Monitor Version " + VERSION + " (Build: " + BUILD_DATE + ")";
    }
}