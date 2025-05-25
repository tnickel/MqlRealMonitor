package com.mql.realmonitor.gui;

import com.mql.realmonitor.parser.SignalData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Tabelle für die Anzeige der Signalprovider-Daten
 * Verwaltet die SWT-Tabelle mit allen Provider-Informationen
 */
public class SignalProviderTable {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderTable.class.getName());
    
    // Spalten-Indizes - NEU: PROVIDER_NAME hinzugefügt
    private static final int COL_SIGNAL_ID = 0;
    private static final int COL_PROVIDER_NAME = 1;  // NEU
    private static final int COL_STATUS = 2;         // Index verschoben
    private static final int COL_EQUITY = 3;         // Index verschoben
    private static final int COL_FLOATING = 4;       // Index verschoben
    private static final int COL_TOTAL = 5;          // Index verschoben
    private static final int COL_CURRENCY = 6;       // Index verschoben
    private static final int COL_LAST_UPDATE = 7;    // Index verschoben
    private static final int COL_CHANGE = 8;         // Index verschoben
    
    // Spalten-Definitionen - NEU: Provider Name hinzugefügt
    private static final String[] COLUMN_TEXTS = {
        "Signal ID", 
        "Provider Name",  // NEU
        "Status", 
        "Kontostand", 
        "Floating Profit", 
        "Gesamtwert", 
        "Währung", 
        "Letzte Aktualisierung", 
        "Änderung"
    };
    
    private static final int[] COLUMN_WIDTHS = {
        80,   // Signal ID
        150,  // Provider Name - NEU
        100,  // Status
        120,  // Kontostand
        120,  // Floating Profit
        120,  // Gesamtwert
        70,   // Währung
        150,  // Letzte Aktualisierung
        120   // Änderung
    };
    
    private final MqlRealMonitorGUI parentGui;
    
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
        
        createTable(parent);
        createColumns();
        setupTableBehavior();
        
        LOGGER.info("SignalProviderTable initialisiert mit " + COLUMN_TEXTS.length + " Spalten");
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
                    sortTable(columnIndex);
                }
            });
        }
        
        LOGGER.info("Tabellenspalten erstellt: " + java.util.Arrays.toString(COLUMN_TEXTS));
    }
    
    /**
     * Konfiguriert das Tabellenverhalten
     */
    private void setupTableBehavior() {
        // NEU: Doppelklick für Tick-Chart
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                TableItem[] selection = table.getSelection();
                if (selection.length > 0) {
                    openTickChart(selection[0]);
                }
            }
        });
        
        // Rechtsklick-Kontextmenü
        Menu contextMenu = new Menu(table);
        table.setMenu(contextMenu);
        
        // Menü-Items
        MenuItem refreshItem = new MenuItem(contextMenu, SWT.PUSH);
        refreshItem.setText("Ausgewählte aktualisieren");
        refreshItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshSelectedProviders();
            }
        });
        
        MenuItem detailsItem = new MenuItem(contextMenu, SWT.PUSH);
        detailsItem.setText("Details anzeigen");
        detailsItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showSelectedProviderDetails();
            }
        });
        
        new MenuItem(contextMenu, SWT.SEPARATOR);
        
        // NEU: Tick-Chart Menü-Item
        MenuItem chartItem = new MenuItem(contextMenu, SWT.PUSH);
        chartItem.setText("Tick-Chart öffnen");
        chartItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openTickChartForSelected();
            }
        });
        
        new MenuItem(contextMenu, SWT.SEPARATOR);
        
        MenuItem removeItem = new MenuItem(contextMenu, SWT.PUSH);
        removeItem.setText("Aus Tabelle entfernen");
        removeItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                removeSelectedProviders();
            }
        });
    }
    
    /**
     * NEU: Öffnet das Tick-Chart für eine Tabellenzeile
     * 
     * @param item Das Tabellen-Item
     */
    private void openTickChart(TableItem item) {
        String signalId = item.getText(COL_SIGNAL_ID);
        String providerName = item.getText(COL_PROVIDER_NAME);
        SignalData signalData = lastSignalData.get(signalId);
        
        if (signalData == null) {
            parentGui.showError("Fehler", "Keine Signaldaten für " + signalId + " verfügbar.");
            return;
        }
        
        // Tick-Datei-Pfad erstellen
        String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(signalId);
        
        try {
            // Tick-Chart-Fenster öffnen
            TickChartWindow chartWindow = new TickChartWindow(
                parentGui.getShell(), 
                parentGui, 
                signalId, 
                providerName, 
                signalData, 
                tickFilePath
            );
            chartWindow.open();
            
            LOGGER.info("Tick-Chart geöffnet für Signal: " + signalId + " (" + providerName + ")");
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Öffnen des Tick-Charts: " + e.getMessage());
            parentGui.showError("Fehler beim Öffnen des Tick-Charts", 
                               "Konnte Tick-Chart für " + signalId + " nicht öffnen:\n" + e.getMessage());
        }
    }
    
    /**
     * NEU: Öffnet Tick-Chart für ausgewählte Provider
     */
    private void openTickChartForSelected() {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length > 1) {
            parentGui.showInfo("Mehrfachauswahl", "Bitte wählen Sie nur einen Provider für das Tick-Chart aus.");
            return;
        }
        
        openTickChart(selectedItems[0]);
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
        
        // Änderung berechnen
        SignalData lastData = lastSignalData.get(signalId);
        String changeText = calculateChangeText(signalData, lastData);
        Color changeColor = getChangeColor(signalData, lastData);
        
        // Tabellendaten setzen - NEU: Provider Name hinzugefügt
        item.setText(COL_SIGNAL_ID, signalId);
        item.setText(COL_PROVIDER_NAME, signalData.getProviderName());  // NEU
        item.setText(COL_STATUS, "OK");
        item.setText(COL_EQUITY, signalData.getFormattedEquity());
        item.setText(COL_FLOATING, signalData.getFormattedFloatingProfit());
        item.setText(COL_TOTAL, signalData.getFormattedTotalValue());
        item.setText(COL_CURRENCY, signalData.getCurrency());
        item.setText(COL_LAST_UPDATE, signalData.getFormattedTimestamp());
        item.setText(COL_CHANGE, changeText);
        
        // Farben setzen
        item.setForeground(COL_FLOATING, getFloatingProfitColor(signalData.getFloatingProfit()));
        item.setForeground(COL_CHANGE, changeColor);
        
        // Status-Farbe
        item.setForeground(COL_STATUS, parentGui.getGreenColor());
        
        // Daten im Cache speichern
        lastSignalData.put(signalId, signalData);
        
        LOGGER.fine("Provider-Daten aktualisiert: " + signalData.getSummary());
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
            // Neuen Provider mit Status hinzufügen
            item = new TableItem(table, SWT.NONE);
            item.setText(COL_SIGNAL_ID, signalId);
            item.setText(COL_PROVIDER_NAME, "Lädt...");  // NEU: Platzhalter
            signalIdToItem.put(signalId, item);
        }
        
        item.setText(COL_STATUS, status);
        
        // Status-Farbe basierend auf Text
        Color statusColor = getStatusColor(status);
        item.setForeground(COL_STATUS, statusColor);
        
        LOGGER.fine("Provider-Status aktualisiert: " + signalId + " -> " + status);
    }
    
    /**
     * Berechnet den Änderungstext
     * 
     * @param current Die aktuellen Daten
     * @param previous Die vorherigen Daten
     * @return Der Änderungstext
     */
    private String calculateChangeText(SignalData current, SignalData previous) {
        if (previous == null) {
            return "Neu";
        }
        
        double equityChange = current.getEquityChange(previous);
        double floatingChange = current.getFloatingProfitChange(previous);
        
        if (equityChange == 0.0 && floatingChange == 0.0) {
            return "Unverändert";
        }
        
        StringBuilder change = new StringBuilder();
        
        if (equityChange != 0.0) {
            change.append(String.format("E: %+.2f", equityChange));
        }
        
        if (floatingChange != 0.0) {
            if (change.length() > 0) {
                change.append(", ");
            }
            change.append(String.format("F: %+.2f", floatingChange));
        }
        
        return change.toString();
    }
    
    /**
     * Bestimmt die Farbe für Änderungen
     * 
     * @param current Die aktuellen Daten
     * @param previous Die vorherigen Daten
     * @return Die Farbe für die Änderung
     */
    private Color getChangeColor(SignalData current, SignalData previous) {
        if (previous == null) {
            return parentGui.getGrayColor();
        }
        
        double totalChange = (current.getEquityChange(previous) + 
                            current.getFloatingProfitChange(previous));
        
        if (totalChange > 0) {
            return parentGui.getGreenColor();
        } else if (totalChange < 0) {
            return parentGui.getRedColor();
        } else {
            return parentGui.getGrayColor();
        }
    }
    
    /**
     * Bestimmt die Farbe für Floating Profit
     * 
     * @param floatingProfit Der Floating Profit Wert
     * @return Die entsprechende Farbe
     */
    private Color getFloatingProfitColor(double floatingProfit) {
        if (floatingProfit > 0) {
            return parentGui.getGreenColor();
        } else if (floatingProfit < 0) {
            return parentGui.getRedColor();
        } else {
            return null; // Standard-Farbe
        }
    }
    
    /**
     * Bestimmt die Farbe für den Status
     * 
     * @param status Der Status-Text
     * @return Die entsprechende Farbe
     */
    private Color getStatusColor(String status) {
        if (status == null) {
            return parentGui.getGrayColor();
        }
        
        String lowerStatus = status.toLowerCase();
        
        if (lowerStatus.contains("ok") || lowerStatus.contains("erfolg")) {
            return parentGui.getGreenColor();
        } else if (lowerStatus.contains("error") || lowerStatus.contains("fehler")) {
            return parentGui.getRedColor();
        } else if (lowerStatus.contains("loading") || lowerStatus.contains("downloading") || lowerStatus.contains("lädt")) {
            return parentGui.getGrayColor();
        } else {
            return null; // Standard-Farbe
        }
    }
    
    /**
     * Sortiert die Tabelle nach der angegebenen Spalte
     * 
     * @param columnIndex Der Index der Spalte
     */
    private void sortTable(int columnIndex) {
        TableItem[] items = table.getItems();
        
        if (items.length <= 1) {
            return;
        }
        
        // Ermittele Sortierrichtung
        boolean ascending = table.getSortDirection() != SWT.UP;
        table.setSortDirection(ascending ? SWT.UP : SWT.DOWN);
        table.setSortColumn(columns[columnIndex]);
        
        // Sortiere Items
        java.util.Arrays.sort(items, (item1, item2) -> {
            String text1 = item1.getText(columnIndex);
            String text2 = item2.getText(columnIndex);
            
            int result = compareTableText(text1, text2, columnIndex);
            return ascending ? result : -result;
        });
        
        // Tabelle neu aufbauen
        rebuildTableWithSortedItems(items);
    }
    
    /**
     * Vergleicht zwei Tabellen-Texte für Sortierung
     * 
     * @param text1 Erster Text
     * @param text2 Zweiter Text
     * @param columnIndex Spalten-Index für spezielle Behandlung
     * @return Vergleichsresultat
     */
    private int compareTableText(String text1, String text2, int columnIndex) {
        // Für numerische Spalten versuche numerische Sortierung
        if (columnIndex == COL_EQUITY || columnIndex == COL_FLOATING || columnIndex == COL_TOTAL) {
            try {
                // Extrahiere Zahlen aus formatiertem Text
                double num1 = extractNumber(text1);
                double num2 = extractNumber(text2);
                return Double.compare(num1, num2);
            } catch (Exception e) {
                // Fall back zu String-Vergleich
            }
        }
        
        // Standard String-Vergleich
        return text1.compareToIgnoreCase(text2);
    }
    
    /**
     * Extrahiert eine Zahl aus formatiertem Text
     * 
     * @param text Der formatierte Text
     * @return Die extrahierte Zahl
     */
    private double extractNumber(String text) throws NumberFormatException {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        // Entferne alles außer Zahlen, Dezimalpunkt und Minus
        String cleanText = text.replaceAll("[^0-9.+-]", "").trim();
        
        if (cleanText.isEmpty()) {
            return 0.0;
        }
        
        return Double.parseDouble(cleanText);
    }
    
    /**
     * Baut die Tabelle mit sortierten Items neu auf
     * 
     * @param sortedItems Die sortierten Items
     */
    private void rebuildTableWithSortedItems(TableItem[] sortedItems) {
        // Speichere Daten
        String[][] itemData = new String[sortedItems.length][];
        Color[][] itemColors = new Color[sortedItems.length][];
        
        for (int i = 0; i < sortedItems.length; i++) {
            TableItem item = sortedItems[i];
            itemData[i] = new String[columns.length];
            itemColors[i] = new Color[columns.length];
            
            for (int j = 0; j < columns.length; j++) {
                itemData[i][j] = item.getText(j);
                itemColors[i][j] = item.getForeground(j);
            }
            
            item.dispose();
        }
        
        // Neue Items erstellen
        for (int i = 0; i < itemData.length; i++) {
            TableItem newItem = new TableItem(table, SWT.NONE);
            newItem.setText(itemData[i]);
            
            for (int j = 0; j < itemColors[i].length; j++) {
                if (itemColors[i][j] != null) {
                    newItem.setForeground(j, itemColors[i][j]);
                }
            }
            
            // Update Mapping
            String signalId = itemData[i][COL_SIGNAL_ID];
            signalIdToItem.put(signalId, newItem);
        }
    }
    
    /**
     * Zeigt Details für einen Provider an
     * 
     * @param item Das Tabellen-Item
     */
    private void showSignalDetails(TableItem item) {
        String signalId = item.getText(COL_SIGNAL_ID);
        String providerName = item.getText(COL_PROVIDER_NAME);  // NEU
        SignalData signalData = lastSignalData.get(signalId);
        
        StringBuilder details = new StringBuilder();
        details.append("Signal Provider Details\n\n");
        details.append("Signal ID: ").append(signalId).append("\n");
        details.append("Provider Name: ").append(providerName).append("\n");  // NEU
        details.append("Status: ").append(item.getText(COL_STATUS)).append("\n");
        details.append("Kontostand: ").append(item.getText(COL_EQUITY)).append("\n");
        details.append("Floating Profit: ").append(item.getText(COL_FLOATING)).append("\n");
        details.append("Gesamtwert: ").append(item.getText(COL_TOTAL)).append("\n");
        details.append("Währung: ").append(item.getText(COL_CURRENCY)).append("\n");
        details.append("Letzte Aktualisierung: ").append(item.getText(COL_LAST_UPDATE)).append("\n");
        details.append("Änderung: ").append(item.getText(COL_CHANGE)).append("\n");
        
        if (signalData != null) {
            details.append("\nZusätzliche Informationen:\n");
            details.append("Vollständige URL: ").append(parentGui.getMonitor().getConfig().buildSignalUrl(signalId)).append("\n");
            details.append("Tick-Datei: ").append(parentGui.getMonitor().getConfig().getTickFilePath(signalId)).append("\n");
        }
        
        parentGui.showInfo("Signal Provider Details", details.toString());
    }
    
    /**
     * Aktualisiert ausgewählte Provider
     */
    private void refreshSelectedProviders() {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
            return;
        }
        
        for (TableItem item : selectedItems) {
            String signalId = item.getText(COL_SIGNAL_ID);
            updateProviderStatus(signalId, "Aktualisiere...");
        }
        
        // Manuellen Refresh triggern
        parentGui.getMonitor().manualRefresh();
    }
    
    /**
     * Zeigt Details für ausgewählte Provider
     */
    private void showSelectedProviderDetails() {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length == 1) {
            showSignalDetails(selectedItems[0]);
        } else {
            StringBuilder summary = new StringBuilder();
            summary.append("Ausgewählte Provider (").append(selectedItems.length).append("):\n\n");
            
            for (TableItem item : selectedItems) {
                summary.append("• ").append(item.getText(COL_SIGNAL_ID))
                       .append(" (").append(item.getText(COL_PROVIDER_NAME)).append(")")  // NEU: Provider Name
                       .append(" - ").append(item.getText(COL_STATUS))
                       .append(" (").append(item.getText(COL_TOTAL)).append(")")
                       .append("\n");
            }
            
            parentGui.showInfo("Ausgewählte Provider", summary.toString());
        }
    }
    
    /**
     * Entfernt ausgewählte Provider aus der Tabelle
     */
    private void removeSelectedProviders() {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
            return;
        }
        
        MessageBox confirmBox = new MessageBox(parentGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        confirmBox.setText("Provider entfernen");
        confirmBox.setMessage("Möchten Sie " + selectedItems.length + " Provider aus der Tabelle entfernen?\n\n" +
                             "Dies entfernt sie nur aus der Anzeige, nicht aus der Favoriten-Datei.");
        
        if (confirmBox.open() == SWT.YES) {
            for (TableItem item : selectedItems) {
                String signalId = item.getText(COL_SIGNAL_ID);
                signalIdToItem.remove(signalId);
                lastSignalData.remove(signalId);
                item.dispose();
            }
            
            LOGGER.info("Provider aus Tabelle entfernt: " + selectedItems.length + " Einträge");
        }
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
        
        LOGGER.info("Alle Provider aus Tabelle entfernt");
    }
    
    /**
     * Gibt die Tabelle zurück
     * 
     * @return Die SWT-Tabelle
     */
    public Table getTable() {
        return table;
    }
}
