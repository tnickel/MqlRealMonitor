package com.mql.realmonitor.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.parser.SignalData;
import com.mql.realmonitor.utils.PeriodProfitCalculator;

/**
 * Refactored: Tabelle für die Anzeige der Signalprovider-Daten
 * Kern-Tabellenfunktionen - Kontextmenü und Hilfsfunktionen ausgelagert
 * MODULAR: Verwendet separate Klassen für erweiterte Funktionalität
 * ERWEITERT: Neue Spalte für Favoritenklasse hinzugefügt mit Zeilen-Hintergrundfarben
 * SORTIERUNG: Automatische Sortierung nach Favoritenklasse beim Start
 * NEU: WeeklyProfit und MonthlyProfit Spalten hinzugefügt
 */
public class SignalProviderTable {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderTable.class.getName());
    
    // Spalten-Definitionen (ERWEITERT: Neue Spalten "WeeklyProfit" und "MonthlyProfit")
    private static final String[] COLUMN_TEXTS = {
        "Signal ID", 
        "Favoritenklasse",     // NEU: Spalte 1
        "Provider Name",       // war "Provider Name" 
        "Status",              // war "Status"
        "Kontostand",          // war "Kontostand"
        "Floating Profit",     // war "Floating Profit"
        "Equity Drawdown",     // war "Equity Drawdown"
        "Gesamtwert",          // war "Gesamtwert"
        "WeeklyProfit",        // NEU: Spalte 8
        "MonthlyProfit",       // NEU: Spalte 9
        "Währung",             // war "Währung" (verschoben von 8 zu 10)
        "Letzte Aktualisierung", // war "Letzte Aktualisierung" (verschoben von 9 zu 11)
        "Änderung"             // war "Änderung" (verschoben von 10 zu 12)
    };
    
    private static final int[] COLUMN_WIDTHS = {
        80,   // Signal ID
        120,  // Favoritenklasse (NEU)
        150,  // Provider Name
        100,  // Status
        120,  // Kontostand
        120,  // Floating Profit
        100,  // Equity Drawdown
        120,  // Gesamtwert
        100,  // WeeklyProfit (NEU)
        100,  // MonthlyProfit (NEU)
        70,   // Währung (verschoben)
        150,  // Letzte Aktualisierung (verschoben)
        120   // Änderung (verschoben)
    };
    
    // Komponenten
    private final MqlRealMonitorGUI parentGui;
    private final ProviderTableHelper tableHelper;
    private final SignalProviderContextMenu contextMenu;
    
    // SWT Komponenten
    private Table table;
    private TableColumn[] columns;
    
    // Daten-Management
    private Map<String, TableItem> signalIdToItem;
    private Map<String, SignalData> lastSignalData;
    
    // KORRIGIERT: FavoritesReader als Instanzvariable
    private com.mql.realmonitor.downloader.FavoritesReader favoritesReader;
    
    public SignalProviderTable(Composite parent, MqlRealMonitorGUI parentGui) {
        this.parentGui = parentGui;
        this.signalIdToItem = new HashMap<>();
        this.lastSignalData = new HashMap<>();
        
        // Helfer-Klassen initialisieren
        this.tableHelper = new ProviderTableHelper(parentGui);
        this.contextMenu = new SignalProviderContextMenu(parentGui);
        
        // Callbacks für das Kontextmenü setzen
        setupContextMenuCallbacks();
        
        // Tabelle erstellen
        createTable(parent);
        createColumns();
        
        // Tabellenverhalten mit Kontextmenü konfigurieren
        contextMenu.setupTableBehavior(table);
        
        LOGGER.info("SignalProviderTable (Refactored+Extended+ProfitColumns) initialisiert mit " + COLUMN_TEXTS.length + " Spalten - Modular aufgeteilt mit Favoritenklasse und Profit-Berechnungen");
    }
    
    /**
     * Konfiguriert die Callbacks für das Kontextmenü
     */
    private void setupContextMenuCallbacks() {
        contextMenu.setCallbacks(
            // updateProviderStatus callback
            (statusInfo) -> {
                String[] parts = statusInfo.split(":", 2);
                if (parts.length == 2) {
                    updateProviderStatus(parts[0], parts[1]);
                }
            },
            // getLastSignalData callback
            () -> lastSignalData,
            // removeFromMapping callback
            (signalId, item) -> {
                signalIdToItem.remove(signalId);
                lastSignalData.remove(signalId);
            }
        );
    }
    
    /**
     * Erstellt die Tabelle
     */
    private void createTable(Composite parent) {
        table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
    }
    
    /**
     * Erstellt die Tabellenspalten
     */
    private void createColumns() {
        columns = new TableColumn[COLUMN_TEXTS.length];
        
        for (int i = 0; i < COLUMN_TEXTS.length; i++) {
            columns[i] = new TableColumn(table, SWT.NONE);
            columns[i].setText(COLUMN_TEXTS[i]);
            columns[i].setWidth(COLUMN_WIDTHS[i]);
            columns[i].setResizable(true);
            
            // Sortierung bei Klick auf Header
            final int columnIndex = i;
            columns[i].addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    tableHelper.sortTable(table, columns, columnIndex, signalIdToItem::put);
                }
            });
        }
        
        LOGGER.info("Tabellenspalten erstellt: " + java.util.Arrays.toString(COLUMN_TEXTS));
    }
    
    /**
     * NEU: Holt die Favoritenklasse für eine Signal-ID
     * 
     * @param signalId Die Signal-ID
     * @return Die Favoritenklasse oder "-" wenn nicht vorhanden
     */
    private String getFavoriteClassForSignal(String signalId) {
        try {
            // Lazy Initialization des FavoritesReader
            if (favoritesReader == null) {
                favoritesReader = new com.mql.realmonitor.downloader.FavoritesReader(parentGui.getMonitor().getConfig());
            }
            
            String favoriteClass = favoritesReader.getFavoriteClass(signalId);
            return (favoriteClass != null && !favoriteClass.trim().isEmpty()) ? favoriteClass : "-";
        } catch (Exception e) {
            LOGGER.fine("Konnte Favoritenklasse für Signal " + signalId + " nicht ermitteln: " + e.getMessage());
            return "-";
        }
    }
    
    /**
     * NEU: Berechnet WeeklyProfit und MonthlyProfit für eine Signal-ID
     * 
     * @param signalId Die Signal-ID
     * @return ProfitResult mit den berechneten Werten
     */
    private PeriodProfitCalculator.ProfitResult calculateProfitsForSignal(String signalId) {
        try {
            // Tick-Datei-Pfad aus der Konfiguration ermitteln
            String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(signalId);
            
            // Profits berechnen
            PeriodProfitCalculator.ProfitResult result = PeriodProfitCalculator.calculateProfits(tickFilePath, signalId);
            
            LOGGER.fine("Profit-Berechnung für Signal " + signalId + ": " + result.toString());
            return result;
            
        } catch (Exception e) {
            LOGGER.warning("Fehler bei Profit-Berechnung für Signal " + signalId + ": " + e.getMessage());
            return new PeriodProfitCalculator.ProfitResult(0.0, 0.0, false, false, "Fehler bei Berechnung: " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert die Daten eines Providers (Thread-sicher)
     * ERWEITERT: Setzt auch die Favoritenklasse, Zeilen-Hintergrundfarbe und berechnet Profit-Werte
     * 
     * @param signalData Die aktualisierten Signaldaten
     */
    public void updateProviderData(SignalData signalData) {
        if (signalData == null || !signalData.isValid()) {
            return;
        }
        
        String signalId = signalData.getSignalId();
        TableItem item = signalIdToItem.get(signalId);
        
        if (item == null) {
            // Neuen Provider hinzufügen
            item = new TableItem(table, SWT.NONE);
            signalIdToItem.put(signalId, item);
        }
        
        // Änderung berechnen über Helper
        SignalData lastData = lastSignalData.get(signalId);
        String changeText = tableHelper.calculateChangeText(signalData, lastData);
        Color changeColor = tableHelper.getChangeColor(signalData, lastData);
        
        // NEU: Favoritenklasse ermitteln
        String favoriteClass = getFavoriteClassForSignal(signalId);
        
        // NEU: Profit-Werte berechnen
        PeriodProfitCalculator.ProfitResult profitResult = calculateProfitsForSignal(signalId);
        
        // Tabellendaten setzen (ERWEITERT: Neue Spalten WeeklyProfit und MonthlyProfit)
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_FAVORITE_CLASS, favoriteClass);                     
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, signalData.getProviderName());
        item.setText(ProviderTableHelper.COL_STATUS, "OK");
        item.setText(ProviderTableHelper.COL_EQUITY, signalData.getFormattedEquity());
        item.setText(ProviderTableHelper.COL_FLOATING, signalData.getFormattedFloatingProfit());
        item.setText(ProviderTableHelper.COL_EQUITY_DRAWDOWN, signalData.getFormattedEquityDrawdown());
        item.setText(ProviderTableHelper.COL_TOTAL, signalData.getFormattedTotalValue());
        item.setText(ProviderTableHelper.COL_WEEKLY_PROFIT, profitResult.getFormattedWeeklyProfit());   // NEU
        item.setText(ProviderTableHelper.COL_MONTHLY_PROFIT, profitResult.getFormattedMonthlyProfit()); // NEU
        item.setText(ProviderTableHelper.COL_CURRENCY, signalData.getCurrency());
        item.setText(ProviderTableHelper.COL_LAST_UPDATE, signalData.getFormattedTimestamp());
        item.setText(ProviderTableHelper.COL_CHANGE, changeText);
        
        // Farben setzen über Helper (ERWEITERT: Neue Profit-Spalten-Farben)
        item.setForeground(ProviderTableHelper.COL_FLOATING, tableHelper.getFloatingProfitColor(signalData.getFloatingProfit()));
        item.setForeground(ProviderTableHelper.COL_EQUITY_DRAWDOWN, tableHelper.getEquityDrawdownColor(signalData.getEquityDrawdownPercent()));
        item.setForeground(ProviderTableHelper.COL_CHANGE, changeColor);
        
        // NEU: Farben für Profit-Spalten setzen
        if (profitResult.hasWeeklyData()) {
            item.setForeground(ProviderTableHelper.COL_WEEKLY_PROFIT, 
                              tableHelper.getProfitColor(profitResult.getWeeklyProfitPercent()));
        }
        if (profitResult.hasMonthlyData()) {
            item.setForeground(ProviderTableHelper.COL_MONTHLY_PROFIT, 
                              tableHelper.getProfitColor(profitResult.getMonthlyProfitPercent()));
        }
        
        // Status-Farbe
        item.setForeground(ProviderTableHelper.COL_STATUS, parentGui.getGreenColor());
        
        // NEU: Zeilen-Hintergrundfarbe basierend auf Favoritenklasse setzen
        // 1=hellgrün, 2=hellgelb, 3=hellorange, 4-10=hellrot
        Color backgroundColor = tableHelper.getFavoriteClassBackgroundColor(favoriteClass);
        if (backgroundColor != null) {
            item.setBackground(backgroundColor); // Setzt Hintergrundfarbe für die ganze Zeile
            LOGGER.fine("Hintergrundfarbe gesetzt für Signal " + signalId + " (Klasse " + favoriteClass + ")");
        }
        
        // Daten im Cache speichern
        lastSignalData.put(signalId, signalData);
        
        LOGGER.fine("Provider-Daten aktualisiert (Modular+Extended+ProfitColumns): " + signalData.getSummary() + 
                   " (Klasse: " + favoriteClass + ", WeeklyProfit: " + profitResult.getFormattedWeeklyProfit() + 
                   ", MonthlyProfit: " + profitResult.getFormattedMonthlyProfit() + ")");
    }
    
    /**
     * Aktualisiert den Status eines Providers
     * 
     * @param signalId Die Signal-ID
     * @param status Der neue Status
     */
    public void updateProviderStatus(String signalId, String status) {
        TableItem item = signalIdToItem.get(signalId);
        
        if (item == null) {
            // Neuen Provider mit Status hinzufügen (für Vorladen der Favoriten)
            addEmptyProviderEntry(signalId, status);
            return;
        }
        
        // Bestehenden Status aktualisieren
        item.setText(ProviderTableHelper.COL_STATUS, status);
        
        // Status-Farbe über Helper setzen
        Color statusColor = tableHelper.getStatusColor(status);
        item.setForeground(ProviderTableHelper.COL_STATUS, statusColor);
        
        LOGGER.fine("Provider-Status aktualisiert: " + signalId + " -> " + status);
    }
    
    /**
     * Fügt einen leeren Provider-Eintrag hinzu (nur mit Signal-ID)
     * Wird beim Vorladen der Favoriten verwendet
     * ERWEITERT: Setzt auch die Favoritenklasse und Zeilen-Hintergrundfarbe, Profit-Spalten bleiben leer
     * 
     * @param signalId Die Signal-ID
     * @param initialStatus Der initiale Status
     */
    public void addEmptyProviderEntry(String signalId, String initialStatus) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return;
        }
        
        // Prüfen ob bereits vorhanden
        if (signalIdToItem.containsKey(signalId)) {
            LOGGER.fine("Provider bereits in Tabelle: " + signalId);
            return;
        }
        
        // NEU: Favoritenklasse ermitteln
        String favoriteClass = getFavoriteClassForSignal(signalId);
        
        // Neuen leeren Eintrag erstellen
        TableItem item = new TableItem(table, SWT.NONE);
        
        // Nur Signal-ID, Favoritenklasse und Status setzen, Rest bleibt leer (ERWEITERT: Neue Spalten-Indizes)
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_FAVORITE_CLASS, favoriteClass);                          
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, "Lädt...");  
        item.setText(ProviderTableHelper.COL_STATUS, initialStatus != null ? initialStatus : "Nicht geladen");
        item.setText(ProviderTableHelper.COL_EQUITY, "");
        item.setText(ProviderTableHelper.COL_FLOATING, "");
        item.setText(ProviderTableHelper.COL_EQUITY_DRAWDOWN, "");
        item.setText(ProviderTableHelper.COL_TOTAL, "");
        item.setText(ProviderTableHelper.COL_WEEKLY_PROFIT, "");     // NEU: Leer lassen bis Daten verfügbar
        item.setText(ProviderTableHelper.COL_MONTHLY_PROFIT, "");   // NEU: Leer lassen bis Daten verfügbar
        item.setText(ProviderTableHelper.COL_CURRENCY, "");
        item.setText(ProviderTableHelper.COL_LAST_UPDATE, "");
        item.setText(ProviderTableHelper.COL_CHANGE, "");
        
        // Farben über Helper setzen
        Color statusColor = tableHelper.getStatusColor(initialStatus);
        if (statusColor != null) {
            item.setForeground(ProviderTableHelper.COL_STATUS, statusColor);
        }
        
        // NEU: Zeilen-Hintergrundfarbe basierend auf Favoritenklasse setzen
        // 1=hellgrün, 2=hellgelb, 3=hellorange, 4-10=hellrot
        Color backgroundColor = tableHelper.getFavoriteClassBackgroundColor(favoriteClass);
        if (backgroundColor != null) {
            item.setBackground(backgroundColor); // Setzt Hintergrundfarbe für die ganze Zeile
            LOGGER.fine("Hintergrundfarbe gesetzt für leeren Eintrag " + signalId + " (Klasse " + favoriteClass + ")");
        }
        
        // In Map eintragen
        signalIdToItem.put(signalId, item);
        
        LOGGER.fine("Leerer Provider-Eintrag hinzugefügt (Modular+Extended+ProfitColumns): " + signalId + 
                   " (Klasse: " + favoriteClass + ")");
    }
    
    /**
     * NEU: Sortiert die Tabelle nach Favoritenklasse (1-10, dann ohne Klasse)
     */
    public void sortByFavoriteClass() {
        if (table.getItemCount() <= 1) {
            return; // Nichts zu sortieren
        }
        
        LOGGER.info("Sortiere Tabelle nach Favoritenklasse...");
        
        // Sortierung nach Favoritenklasse-Spalte auslösen
        tableHelper.sortTable(table, columns, ProviderTableHelper.COL_FAVORITE_CLASS, signalIdToItem::put);
        
        // Sortrichtung auf aufsteigend setzen (1, 2, 3, ..., dann "-")
        table.setSortDirection(SWT.UP);
        table.setSortColumn(columns[ProviderTableHelper.COL_FAVORITE_CLASS]);
        
        LOGGER.info("Tabelle nach Favoritenklasse sortiert");
    }
    
    /**
     * NEU: Führt die initiale Sortierung nach Favoritenklasse durch
     * Sollte aufgerufen werden, nachdem alle Provider-Daten geladen wurden
     */
    public void performInitialSort() {
        // Kurze Verzögerung um sicherzustellen, dass alle Daten geladen sind
        parentGui.getDisplay().timerExec(1000, () -> {
            if (!table.isDisposed() && table.getItemCount() > 1) {
                LOGGER.info("Führe initiale Sortierung nach Favoritenklasse durch...");
                sortByFavoriteClass();
            }
        });
    }
    
    /**
     * NEU: Aktualisiert die Favoritenklassen aller Provider
     * Nützlich wenn die favorites.txt geändert wurde
     * ERWEITERT: Führt nach dem Update automatisch eine Sortierung durch
     */
    public void refreshFavoriteClasses() {
        LOGGER.info("Aktualisiere Favoritenklassen für alle Provider...");
        
        // Cache des FavoritesReader leeren
        if (favoritesReader != null) {
            favoritesReader.refreshCache();
        }
        
        for (Map.Entry<String, TableItem> entry : signalIdToItem.entrySet()) {
            String signalId = entry.getKey();
            TableItem item = entry.getValue();
            
            if (item != null && !item.isDisposed()) {
                String favoriteClass = getFavoriteClassForSignal(signalId);
                item.setText(ProviderTableHelper.COL_FAVORITE_CLASS, favoriteClass);
                
                // NEU: Zeilen-Hintergrundfarbe basierend auf Favoritenklasse aktualisieren
                // 1=hellgrün, 2=hellgelb, 3=hellorange, 4-10=hellrot
                Color backgroundColor = tableHelper.getFavoriteClassBackgroundColor(favoriteClass);
                item.setBackground(backgroundColor); // null entfernt die Hintergrundfarbe
                
                LOGGER.fine("Favoritenklasse aktualisiert für " + signalId + ": " + favoriteClass);
            }
        }
        
        // NEU: Nach dem Refresh automatisch nach Favoritenklasse sortieren
        sortByFavoriteClass();
        
        LOGGER.info("Favoritenklassen-Aktualisierung abgeschlossen");
    }
    
    /**
     * NEU: Aktualisiert die Profit-Werte für alle Provider
     * Nützlich für manuelles Refresh der Profit-Berechnungen
     */
    public void refreshProfitValues() {
        LOGGER.info("Aktualisiere Profit-Werte für alle Provider...");
        
        for (Map.Entry<String, TableItem> entry : signalIdToItem.entrySet()) {
            String signalId = entry.getKey();
            TableItem item = entry.getValue();
            
            if (item != null && !item.isDisposed()) {
                // Profit-Werte neu berechnen
                PeriodProfitCalculator.ProfitResult profitResult = calculateProfitsForSignal(signalId);
                
                // Spalten aktualisieren
                item.setText(ProviderTableHelper.COL_WEEKLY_PROFIT, profitResult.getFormattedWeeklyProfit());
                item.setText(ProviderTableHelper.COL_MONTHLY_PROFIT, profitResult.getFormattedMonthlyProfit());
                
                // Farben aktualisieren
                if (profitResult.hasWeeklyData()) {
                    item.setForeground(ProviderTableHelper.COL_WEEKLY_PROFIT, 
                                      tableHelper.getProfitColor(profitResult.getWeeklyProfitPercent()));
                }
                if (profitResult.hasMonthlyData()) {
                    item.setForeground(ProviderTableHelper.COL_MONTHLY_PROFIT, 
                                      tableHelper.getProfitColor(profitResult.getMonthlyProfitPercent()));
                }
                
                LOGGER.fine("Profit-Werte aktualisiert für " + signalId + ": " + profitResult.toString());
            }
        }
        
        LOGGER.info("Profit-Werte-Aktualisierung abgeschlossen");
    }
    
    /**
     * Gibt die Anzahl der Provider in der Tabelle zurück
     * 
     * @return Anzahl der Provider
     */
    public int getProviderCount() {
        return table.getItemCount();
    }
    
    /**
     * Löscht alle Provider aus der Tabelle
     */
    public void clearAllProviders() {
        table.removeAll();
        signalIdToItem.clear();
        lastSignalData.clear();
        
        // FavoritesReader Cache leeren
        if (favoritesReader != null) {
            favoritesReader.refreshCache();
        }
        
        LOGGER.info("Alle Provider aus Tabelle entfernt (Modular+Extended+ProfitColumns)");
    }
    
    /**
     * Gibt die Tabelle zurück
     * 
     * @return Die SWT-Tabelle
     */
    public Table getTable() {
        return table;
    }
    
    /**
     * Gibt den ProviderTableHelper zurück
     * 
     * @return Der ProviderTableHelper
     */
    public ProviderTableHelper getTableHelper() {
        return tableHelper;
    }
    
    /**
     * Gibt das SignalProviderContextMenu zurück
     * 
     * @return Das SignalProviderContextMenu
     */
    public SignalProviderContextMenu getContextMenu() {
        return contextMenu;
    }
}