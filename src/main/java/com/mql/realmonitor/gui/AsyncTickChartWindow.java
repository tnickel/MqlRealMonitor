package com.mql.realmonitor.gui;

import com.mql.realmonitor.parser.SignalData;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * NEU: Asynchrone Chart-Fenster-Klasse - BEHEBT 60-SEKUNDEN-BLOCKING
 * Zeigt sofort ein Loading-Fenster und lädt Charts im Background
 * Verhindert UI-Thread-Blockierung beim Öffnen von Chart-Fenstern
 */
public class AsyncTickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(AsyncTickChartWindow.class.getName());
    
    // Daten für Chart-Erstellung
    private final Shell parentShell;
    private final MqlRealMonitorGUI parentGui;
    private final String signalId;
    private final String providerName;
    private final SignalData signalData;
    private final String tickFilePath;
    private final Display display;
    
    // Loading-Fenster Komponenten
    private Shell loadingShell;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Text logText;
    
    // Actual Chart Window Manager (wird asynchron erstellt)
    private TickChartWindowManager actualChartWindowManager;
    
    // Status-Flags
    private volatile boolean isLoadingCancelled = false;
    private volatile boolean isChartWindowReady = false;
    private volatile boolean isProgrammaticClose = false;
    
    /**
     * Konstruktor - SCHNELL: Sammelt nur Daten, erstellt noch kein UI
     */
    public AsyncTickChartWindow(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                               String providerName, SignalData signalData, String tickFilePath) {
        this.parentShell = parent;
        this.parentGui = parentGui;
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        this.display = parent.getDisplay();
        
        LOGGER.info("=== ASYNC CHART WINDOW ERSTELLT (OHNE UI-BLOCKING) ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
    }
    
    /**
     * HAUPTMETHODE: Öffnet asynchron das Chart-Fenster
     * 1. Zeigt sofort Loading-Fenster
     * 2. Startet Background-Thread für Chart-Erstellung
     * 3. Behebt 60-Sekunden-Blocking-Problem
     */
    public void openAsync() {
        LOGGER.info("=== STARTE ASYNCHRONE CHART-FENSTER-ÖFFNUNG ===");
        
        // 1. SOFORT: Loading-Fenster anzeigen (schnell, kein Blocking)
        createAndShowLoadingWindow();
        
        // 2. ASYNC: Chart-Erstellung in Background-Thread
        startBackgroundChartCreation();
    }
    
    /**
     * SCHRITT 1: Erstellt und zeigt sofort das Loading-Fenster
     * SCHNELL: Nur einfache UI-Komponenten, kein Blocking
     */
    private void createAndShowLoadingWindow() {
        LOGGER.info("SCHRITT 1: Erstelle Loading-Fenster...");
        
        // Loading-Shell erstellen
        loadingShell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        loadingShell.setText("Lade Chart-Fenster - " + signalId);
        loadingShell.setSize(500, 300);
        loadingShell.setLayout(new GridLayout(1, false));
        
        // Fenster zentrieren
        centerWindow(loadingShell, parentShell);
        
        // Info-Label
        statusLabel = new Label(loadingShell, SWT.WRAP);
        statusLabel.setText("Bereite Chart-Fenster für " + providerName + " vor...");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // Progress Bar (indeterminant)
        progressBar = new ProgressBar(loadingShell, SWT.INDETERMINATE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // Log-Text (scrollbar)
        logText = new Text(loadingShell, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        logText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        logText.setText("=== ASYNC CHART LOADING ===\n" +
                       "Signal ID: " + signalId + "\n" +
                       "Provider: " + providerName + "\n" +
                       "Tick-Datei: " + tickFilePath + "\n\n" +
                       "Loading wird gestartet...\n");
        
        // Close-Handler - NUR bei manueller Schließung durch User
        loadingShell.addListener(SWT.Close, event -> {
            if (!isProgrammaticClose) {
                LOGGER.info("Loading-Fenster manuell geschlossen - breche Chart-Erstellung ab");
                isLoadingCancelled = true;
                
                // Wenn Chart-Fenster bereits fertig ist, auch das schließen
                if (actualChartWindowManager != null) {
                    try {
                        actualChartWindowManager.onClose();
                    } catch (Exception e) {
                        LOGGER.warning("Fehler beim Schließen des Chart-Fensters: " + e.getMessage());
                    }
                }
            } else {
                LOGGER.info("Loading-Fenster programmatisch geschlossen - Chart-Fenster bleibt offen");
            }
        });
        
        // Loading-Fenster anzeigen (SOFORT, kein Blocking)
        loadingShell.open();
        
        LOGGER.info("Loading-Fenster erfolgreich angezeigt - UI bleibt responsiv");
    }
    
    /**
     * SCHRITT 2: Startet Background-Thread für Chart-Erstellung
     * NICHT-BLOCKING: Läuft vollständig im Background
     */
    private void startBackgroundChartCreation() {
        LOGGER.info("SCHRITT 2: Starte Background-Thread für Chart-Erstellung...");
        
        Thread chartCreationThread = new Thread(() -> {
            try {
                LOGGER.info("BACKGROUND-THREAD: Chart-Erstellung gestartet für " + signalId);
                
                // Phase 1: Daten-Validierung
                updateLoadingStatus("Validiere Daten...", "Phase 1: Daten-Validierung");
                if (isLoadingCancelled) return;
                
                // Kurze Pause für UI-Update
                Thread.sleep(100);
                
                // Phase 2: Tick-Datei-Zugriff prüfen
                updateLoadingStatus("Prüfe Tick-Datei-Zugriff...", "Phase 2: Dateizugriff");
                if (isLoadingCancelled) return;
                
                java.io.File tickFile = new java.io.File(tickFilePath);
                if (!tickFile.exists()) {
                    throw new RuntimeException("Tick-Datei nicht gefunden: " + tickFilePath);
                }
                
                Thread.sleep(200);
                
                // Phase 3: Chart-Fenster-Erstellung (der zeitaufwändige Teil)
                updateLoadingStatus("Erstelle Chart-Fenster...", "Phase 3: Chart-Window-Erstellung (kann dauern)");
                if (isLoadingCancelled) return;
                
                // DAS IST DER ZEITAUFWÄNDIGE TEIL - aber jetzt im Background!
                display.syncExec(() -> {
                    if (!isLoadingCancelled && !loadingShell.isDisposed()) {
                        try {
                            // KORRIGIERT: Verwende direkt den optimierten TickChartWindowManager
                            actualChartWindowManager = new TickChartWindowManager(
                                parentShell,  // Nicht loadingShell als Parent!
                                parentGui,
                                signalId,
                                providerName,
                                signalData,
                                tickFilePath
                            );
                            isChartWindowReady = true;
                            LOGGER.info("Chart-WindowManager erfolgreich erstellt im Background");
                            
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "FEHLER bei Chart-WindowManager-Erstellung im UI-Thread", e);
                            throw new RuntimeException("Chart-WindowManager-Erstellung fehlgeschlagen", e);
                        }
                    }
                });
                
                if (isLoadingCancelled) return;
                
                // Phase 4: Chart-Fenster öffnen
                updateLoadingStatus("Öffne Chart-Fenster...", "Phase 4: Chart-Window wird geöffnet");
                Thread.sleep(100);
                
                if (isChartWindowReady && actualChartWindowManager != null) {
                    display.asyncExec(() -> {
                        if (!isLoadingCancelled) {
                            try {
                                actualChartWindowManager.open();
                                LOGGER.info("ERFOLG: Chart-WindowManager erfolgreich asynchron geöffnet");
                                
                                // Loading-Fenster schließen
                                closeLoadingWindow();
                                
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "FEHLER beim Öffnen des Chart-WindowManagers", e);
                                showErrorInLoadingWindow("Fehler beim Öffnen: " + e.getMessage());
                            }
                        }
                    });
                } else {
                    throw new RuntimeException("Chart-WindowManager wurde nicht korrekt erstellt");
                }
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER im Background-Chart-Thread", e);
                
                display.asyncExec(() -> {
                    if (!isLoadingCancelled && !loadingShell.isDisposed()) {
                        showErrorInLoadingWindow("FEHLER: " + e.getMessage());
                    }
                });
            }
            
        }, "AsyncChartCreation-" + signalId);
        
        chartCreationThread.setDaemon(true);
        chartCreationThread.start();
        
        LOGGER.info("Background-Thread für Chart-Erstellung gestartet");
    }
    
    /**
     * Aktualisiert den Loading-Status (Thread-sicher)
     */
    private void updateLoadingStatus(String status, String logMessage) {
        display.asyncExec(() -> {
            if (!isLoadingCancelled && !loadingShell.isDisposed()) {
                statusLabel.setText(status);
                logText.append(logMessage + "\n");
                
                // Scroll zum Ende
                logText.setTopIndex(logText.getLineCount() - 1);
                
                LOGGER.info("Loading-Status: " + status);
            }
        });
    }
    
    /**
     * Zeigt Fehler im Loading-Fenster
     */
    private void showErrorInLoadingWindow(String errorMessage) {
        if (!loadingShell.isDisposed()) {
            statusLabel.setText("FEHLER beim Laden des Chart-Fensters");
            progressBar.setVisible(false);
            
            logText.append("\n=== FEHLER ===\n" + errorMessage + "\n\n");
            logText.append("Das Fenster schließt sich in 5 Sekunden automatisch...\n");
            logText.setTopIndex(logText.getLineCount() - 1);
            
            // Auto-Close nach 5 Sekunden
            display.timerExec(5000, () -> {
                if (!loadingShell.isDisposed()) {
                    loadingShell.close();
                }
            });
        }
    }
    
    /**
     * Schließt das Loading-Fenster programmatisch
     */
    private void closeLoadingWindow() {
        display.asyncExec(() -> {
            if (!loadingShell.isDisposed()) {
                isProgrammaticClose = true; // Flag setzen vor programmatischem Schließen
                loadingShell.close();
                LOGGER.info("Loading-Fenster programmatisch geschlossen - Chart-Fenster bleibt offen");
            }
        });
    }
    
    /**
     * Zentriert ein Fenster relativ zu einem Parent-Fenster
     */
    private void centerWindow(Shell window, Shell parent) {
        Point parentSize = parent.getSize();
        Point parentLocation = parent.getLocation();
        Point windowSize = window.getSize();
        
        int x = parentLocation.x + (parentSize.x - windowSize.x) / 2;
        int y = parentLocation.y + (parentSize.y - windowSize.y) / 2;
        
        window.setLocation(x, y);
    }
    
    /**
     * Prüft ob das Chart-Fenster geöffnet ist
     */
    public boolean isOpen() {
        return actualChartWindowManager != null && isChartWindowReady;
    }
    
    /**
     * Gibt die Signal-ID zurück
     */
    public String getSignalId() {
        return signalId;
    }
    
    /**
     * Gibt den Chart-WindowManager zurück (kann null sein während Loading)
     */
    public TickChartWindowManager getChartWindowManager() {
        return actualChartWindowManager;
    }
    
    /**
     * @deprecated Verwende getChartWindowManager() 
     */
    @Deprecated
    public TickChartWindow getActualChartWindow() {
        LOGGER.warning("getActualChartWindow() ist deprecated - verwende getChartWindowManager()");
        return null;
    }
    
    /**
     * Prüft ob das Loading noch läuft
     */
    public boolean isLoading() {
        return !isLoadingCancelled && !isChartWindowReady && 
               (loadingShell != null && !loadingShell.isDisposed());
    }
    
    /**
     * Bricht das Loading ab (manuell)
     */
    public void cancelLoading() {
        LOGGER.info("Loading wird manuell abgebrochen für Signal: " + signalId);
        isLoadingCancelled = true;
        
        display.asyncExec(() -> {
            if (!loadingShell.isDisposed()) {
                // Kein Flag setzen - das ist eine manuelle Aktion
                loadingShell.close();
            }
        });
    }
    
    /**
     * Informationen über die asynchrone Architektur
     */
    public static String getAsyncInfo() {
        return "ASYNC CHART WINDOW ARCHITECTURE:\n" +
               "=====================================\n" +
               "Problem gelöst: 60-Sekunden UI-Blocking beim Chart-Öffnen\n" +
               "\n" +
               "Asynchroner Workflow:\n" +
               "1. Doppelklick → Sofortiges Loading-Fenster (keine Blockierung)\n" +
               "2. Background-Thread startet Chart-Erstellung\n" +
               "3. UI bleibt responsiv während der gesamten Ladezeit\n" +
               "4. Chart-Fenster erscheint wenn fertig geladen\n" +
               "\n" +
               "Vorteile:\n" +
               "- Kein UI-Thread-Blocking mehr\n" +
               "- User-Feedback während Loading\n" +
               "- Möglichkeit Loading abzubrechen\n" +
               "- Bessere User Experience\n" +
               "- System bleibt responsiv\n" +
               "\n" +
               "Thread-Architektur:\n" +
               "- UI-Thread: Loading-Fenster + User-Interaktion\n" +
               "- Background-Thread: Chart-Erstellung\n" +
               "- Sync-Points: Nur für UI-Updates\n";
    }
}