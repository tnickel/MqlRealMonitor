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

/**
 * Refactored: Tabelle für die Anzeige der Signalprovider-Daten
 * Kern-Tabellenfunktionen - Kontextmenü und Hilfsfunktionen ausgelagert
 * MODULAR: Verwendet separate Klassen für erweiterte Funktionalität
 */
public class SignalProviderTable {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderTable.class.getName());
    
    // Spalten-Definitionen
    private static final String[] COLUMN_TEXTS = {
        "Signal ID", 
        "Provider Name",
        "Status", 
        "Kontostand", 
        "Floating Profit", 
        "Equity Drawdown",
        "Gesamtwert", 
        "Währung", 
        "Letzte Aktualisierung", 
        "Änderung"
    };
    
    private static final int[] COLUMN_WIDTHS = {
        80,   // Signal ID
        150,  // Provider Name
        100,  // Status
        120,  // Kontostand
        120,  // Floating Profit
        100,  // Equity Drawdown
        120,  // Gesamtwert
        70,   // Währung
        150,  // Letzte Aktualisierung
        120   // Änderung
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
        
        LOGGER.info("SignalProviderTable (Refactored) initialisiert mit " + COLUMN_TEXTS.length + " Spalten - Modular aufgeteilt");
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
     * Aktualisiert die Daten eines Providers (Thread-sicher)
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
        
        // Tabellendaten setzen
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, signalData.getProviderName());
        item.setText(ProviderTableHelper.COL_STATUS, "OK");
        item.setText(ProviderTableHelper.COL_EQUITY, signalData.getFormattedEquity());
        item.setText(ProviderTableHelper.COL_FLOATING, signalData.getFormattedFloatingProfit());
        item.setText(ProviderTableHelper.COL_EQUITY_DRAWDOWN, signalData.getFormattedEquityDrawdown());
        item.setText(ProviderTableHelper.COL_TOTAL, signalData.getFormattedTotalValue());
        item.setText(ProviderTableHelper.COL_CURRENCY, signalData.getCurrency());
        item.setText(ProviderTableHelper.COL_LAST_UPDATE, signalData.getFormattedTimestamp());
        item.setText(ProviderTableHelper.COL_CHANGE, changeText);
        
        // Farben setzen über Helper
        item.setForeground(ProviderTableHelper.COL_FLOATING, tableHelper.getFloatingProfitColor(signalData.getFloatingProfit()));
        item.setForeground(ProviderTableHelper.COL_EQUITY_DRAWDOWN, tableHelper.getEquityDrawdownColor(signalData.getEquityDrawdownPercent()));
        item.setForeground(ProviderTableHelper.COL_CHANGE, changeColor);
        
        // Status-Farbe
        item.setForeground(ProviderTableHelper.COL_STATUS, parentGui.getGreenColor());
        
        // Daten im Cache speichern
        lastSignalData.put(signalId, signalData);
        
        LOGGER.fine("Provider-Daten aktualisiert (Modular): " + signalData.getSummary());
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
        
        // Neuen leeren Eintrag erstellen
        TableItem item = new TableItem(table, SWT.NONE);
        
        // Nur Signal-ID und Status setzen, Rest bleibt leer
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, "Lädt...");  
        item.setText(ProviderTableHelper.COL_STATUS, initialStatus != null ? initialStatus : "Nicht geladen");
        item.setText(ProviderTableHelper.COL_EQUITY, "");
        item.setText(ProviderTableHelper.COL_FLOATING, "");
        item.setText(ProviderTableHelper.COL_EQUITY_DRAWDOWN, "");
        item.setText(ProviderTableHelper.COL_TOTAL, "");
        item.setText(ProviderTableHelper.COL_CURRENCY, "");
        item.setText(ProviderTableHelper.COL_LAST_UPDATE, "");
        item.setText(ProviderTableHelper.COL_CHANGE, "");
        
        // Status-Farbe über Helper setzen
        Color statusColor = tableHelper.getStatusColor(initialStatus);
        if (statusColor != null) {
            item.setForeground(ProviderTableHelper.COL_STATUS, statusColor);
        }
        
        // In Map eintragen
        signalIdToItem.put(signalId, item);
        
        LOGGER.fine("Leerer Provider-Eintrag hinzugefügt (Modular): " + signalId);
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
        
        LOGGER.info("Alle Provider aus Tabelle entfernt (Modular)");
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