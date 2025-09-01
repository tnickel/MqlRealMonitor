package com.mql.realmonitor.gui;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import com.mql.realmonitor.MqlRealMonitor;
import com.mql.realmonitor.parser.SignalData;

/**
 * REFACTORED: Haupt-GUI für MqlRealMonitor - Aufgeteilt in modulare Manager-Klassen
 * 
 * Diese Klasse ist jetzt deutlich kleiner und fokussiert sich nur auf:
 * - Shell-Management und Layout
 * - Ressourcen-Management (Farben, Fonts)
 * - Koordination der Manager-Klassen
 * - Status-Updates
 * 
 * Die Funktionalitäten sind aufgeteilt in:
 * - MqlToolbarManager: Toolbar mit allen Buttons
 * - MqlCurrencyManager: Currency-Funktionalität 
 * - MqlSignalManager: Add/Delete Signal-Funktionalität
 */
public class MqlRealMonitorGUI {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitorGUI.class.getName());
    
    // VERSION INFORMATION
    private static final String VERSION = "1.2.1";
    private static final String BUILD_DATE = "2025-08-27";
    private static final String APPLICATION_TITLE = "MQL5 Real Monitor - Signal Provider Überwachung";
    
    private final MqlRealMonitor monitor;
    
    // SWT Komponenten
    private Display display;
    private Shell shell;
    private SignalProviderTable providerTable;
    private StatusUpdater statusUpdater;
    
    // UI Komponenten
    private Label statusLabel;
    private Label countLabel;
    
    // Manager-Klassen (modular)
    private MqlToolbarManager toolbarManager;
    private MqlCurrencyManager currencyManager;
    private MqlSignalManager signalManager;
    
    // Farben und Fonts
    private Color greenColor;
    private Color redColor;
    private Color grayColor;
    private Font boldFont;
    private Font statusFont;
    
    // Favoritenklasse-Farben
    private Color favoriteClass1Color;
    private Color favoriteClass2Color;
    private Color favoriteClass3Color;
    private Color favoriteClass4To10Color;
    
    public MqlRealMonitorGUI(MqlRealMonitor monitor) {
        this.monitor = monitor;
        this.display = Display.getDefault();
        
        initializeResources();
        createShell();
        initializeManagers();  // KORRIGIERT: Manager ZUERST initialisieren
        createWidgets();       // DANN erst Widgets erstellen
        
        this.statusUpdater = new StatusUpdater(this);
        
        // Tabelle sofort beim Erstellen initialisieren
        initializeTableWithSavedData();
    }
    
    /**
     * Initialisiert alle Ressourcen (Farben und Fonts)
     */
    private void initializeResources() {
        initializeColors();
        initializeFonts();
    }
    
    /**
     * Initialisiert alle Manager-Klassen
     */
    private void initializeManagers() {
        try {
            LOGGER.info("=== INITIALISIERE MANAGER-KLASSEN ===");
            
            // Toolbar Manager
            this.toolbarManager = new MqlToolbarManager(this, shell);
            LOGGER.info("ToolbarManager initialisiert");
            
            // Currency Manager
            this.currencyManager = new MqlCurrencyManager(this);
            LOGGER.info("CurrencyManager initialisiert");
            
            // Signal Manager
            this.signalManager = new MqlSignalManager(this);
            LOGGER.info("SignalManager initialisiert");
            
            // Manager miteinander verknüpfen
            toolbarManager.setCurrencyManager(currencyManager);
            toolbarManager.setSignalManager(signalManager);
            
            LOGGER.info("Alle Manager erfolgreich initialisiert und verknüpft");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Initialisieren der Manager", e);
            throw new RuntimeException("Manager-Initialisierung fehlgeschlagen", e);
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
        favoriteClass1Color = new Color(display, 200, 255, 200);    // 1 = Hellgrün
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
            shutdown();
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
     * Erstellt alle GUI-Komponenten (delegiert an Manager)
     */
    private void createWidgets() {
        // Toolbar wird vom ToolbarManager erstellt
        toolbarManager.createToolbar();
        
        createProviderTable();
        createStatusBar();
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
     * Initialisiert die Tabelle beim Start mit gespeicherten Daten (vereinfacht)
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
            java.util.List<String> favoriteIds = favoritesReader.readFavorites();
            LOGGER.info("Favoriten geladen - Anzahl: " + favoriteIds.size());
            
            if (favoriteIds.isEmpty()) {
                LOGGER.warning("Keine Favoriten gefunden!");
                return;
            }
            
            // Leere Provider-Einträge erstellen
            int createdEntries = 0;
            for (String signalId : favoriteIds) {
                if (signalId == null || signalId.trim().isEmpty()) continue;
                
                providerTable.addEmptyProviderEntry(signalId, "Lade Daten...");
                createdEntries++;
            }
            
            LOGGER.info(createdEntries + " leere Provider-Einträge erstellt");
            updateProviderCount();
            updateStatus(createdEntries + " Provider erstellt, lade Tick-Daten...");
            
            // Tick-Daten in separatem Thread laden
            new Thread(() -> {
                int loadedCount = 0;
                for (String signalId : favoriteIds) {
                    if (signalId == null || signalId.trim().isEmpty()) continue;
                    
                    try {
                        String tickFilePath = monitor.getConfig().getTickFilePath(signalId);
                        java.io.File tickFile = new java.io.File(tickFilePath);
                        
                        if (tickFile.exists()) {
                            SignalData lastSignalData = tickDataWriter.readLastTickEntry(tickFilePath, signalId);
                            
                            if (lastSignalData != null && lastSignalData.isValid()) {
                                display.asyncExec(() -> providerTable.updateProviderData(lastSignalData));
                                loadedCount++;
                            } else {
                                display.asyncExec(() -> providerTable.updateProviderStatus(signalId, "Keine gültigen Daten"));
                            }
                        } else {
                            display.asyncExec(() -> providerTable.updateProviderStatus(signalId, "Keine Daten"));
                        }
                        
                    } catch (Exception e) {
                        LOGGER.warning("Fehler beim Laden der Tick-Daten für " + signalId + ": " + e.getMessage());
                        display.asyncExec(() -> providerTable.updateProviderStatus(signalId, "Fehler beim Laden"));
                    }
                }
                
                final int finalLoadedCount = loadedCount;
                display.asyncExec(() -> {
                    updateProviderCount();
                    updateStatus("Tabelle initialisiert - " + favoriteIds.size() + " Provider erstellt, " + finalLoadedCount + " mit Daten gefüllt");
                    if (providerTable != null) {
                        providerTable.performInitialSort();
                    }
                });
                
            }).start();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Initialisieren der Tabelle", e);
            updateStatus("Fehler beim Laden der Daten - " + e.getMessage());
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
                
                // Signal Manager benachrichtigen über Datenänderung
                if (signalManager != null) {
                    signalManager.onProviderDataChanged();
                }
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
                
                // Signal Manager benachrichtigen über Statusänderung
                if (signalManager != null) {
                    signalManager.onProviderStatusChanged();
                }
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
        LOGGER.info("MqlRealMonitor GUI geöffnet (Refactored mit Managern)");
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
     * Beendet die Anwendung ordnungsgemäß
     */
    private void shutdown() {
        monitor.shutdown();
        
        // Manager bereinigen
        if (currencyManager != null) {
            currencyManager.cleanup();
        }
        if (signalManager != null) {
            signalManager.cleanup();
        }
        if (toolbarManager != null) {
            toolbarManager.cleanup();
        }
        
        disposeResources();
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
    
    // ========================================================================
    // GETTER-METHODEN FÜR MANAGER UND KOMPONENTEN
    // ========================================================================
    
    public Display getDisplay() { return display; }
    public Shell getShell() { return shell; }
    public MqlRealMonitor getMonitor() { return monitor; }
    public SignalProviderTable getProviderTable() { return providerTable; }
    
    // Farben
    public Color getGreenColor() { return greenColor; }
    public Color getRedColor() { return redColor; }
    public Color getGrayColor() { return grayColor; }
    public Font getBoldFont() { return boldFont; }
    
    // Favoritenklasse-Farben
    public Color getFavoriteClass1Color() { return favoriteClass1Color; }
    public Color getFavoriteClass2Color() { return favoriteClass2Color; }
    public Color getFavoriteClass3Color() { return favoriteClass3Color; }
    public Color getFavoriteClass4To10Color() { return favoriteClass4To10Color; }
    
    // Manager
    public MqlToolbarManager getToolbarManager() { return toolbarManager; }
    public MqlCurrencyManager getCurrencyManager() { return currencyManager; }
    public MqlSignalManager getSignalManager() { return signalManager; }
    
    // Version Information
    public static String getVersion() { return VERSION; }
    public static String getBuildDate() { return BUILD_DATE; }
    public static String getFullTitle() { return APPLICATION_TITLE + " v" + VERSION; }
    public static String getVersionInfo() { return "MQL5 Real Monitor Version " + VERSION + " (Build: " + BUILD_DATE + ")"; }
}