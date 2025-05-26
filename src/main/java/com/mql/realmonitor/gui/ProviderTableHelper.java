package com.mql.realmonitor.gui;

import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.parser.SignalData;

/**
 * Hilfsfunktionen für die SignalProviderTable
 * Enthält Berechnungen, Sortierung und Formatierung
 */
public class ProviderTableHelper {
    
    private static final Logger LOGGER = Logger.getLogger(ProviderTableHelper.class.getName());
    
    // Spalten-Indizes
    public static final int COL_SIGNAL_ID = 0;
    public static final int COL_PROVIDER_NAME = 1;
    public static final int COL_STATUS = 2;
    public static final int COL_EQUITY = 3;
    public static final int COL_FLOATING = 4;
    public static final int COL_EQUITY_DRAWDOWN = 5;
    public static final int COL_TOTAL = 6;
    public static final int COL_CURRENCY = 7;
    public static final int COL_LAST_UPDATE = 8;
    public static final int COL_CHANGE = 9;
    
    private final MqlRealMonitorGUI parentGui;
    
    public ProviderTableHelper(MqlRealMonitorGUI parentGui) {
        this.parentGui = parentGui;
    }
    
    /**
     * Berechnet den Änderungstext
     * 
     * @param current Die aktuellen Daten
     * @param previous Die vorherigen Daten
     * @return Der Änderungstext
     */
    public String calculateChangeText(SignalData current, SignalData previous) {
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
    public Color getChangeColor(SignalData current, SignalData previous) {
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
    public Color getFloatingProfitColor(double floatingProfit) {
        if (floatingProfit > 0) {
            return parentGui.getGreenColor();
        } else if (floatingProfit < 0) {
            return parentGui.getRedColor();
        } else {
            return null; // Standard-Farbe
        }
    }
    
    /**
     * Bestimmt die Farbe für Equity Drawdown
     * 
     * @param equityDrawdownPercent Der Equity Drawdown in Prozent
     * @return Die entsprechende Farbe
     */
    public Color getEquityDrawdownColor(double equityDrawdownPercent) {
        if (equityDrawdownPercent > 0) {
            return parentGui.getGreenColor();  // Grün für positive Drawdowns
        } else if (equityDrawdownPercent < 0) {
            return parentGui.getRedColor();    // Rot für negative Drawdowns
        } else {
            return null; // Standard-Farbe für 0%
        }
    }
    
    /**
     * Bestimmt die Farbe für den Status
     * 
     * @param status Der Status-Text
     * @return Die entsprechende Farbe
     */
    public Color getStatusColor(String status) {
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
     * @param table Die zu sortierende Tabelle
     * @param columns Die Tabellenspalten
     * @param columnIndex Der Index der Spalte
     * @param signalIdToItemCallback Callback zum Update der Signal-ID-Mappings
     */
    public void sortTable(Table table, TableColumn[] columns, int columnIndex, 
                          java.util.function.BiConsumer<String, TableItem> signalIdToItemCallback) {
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
        rebuildTableWithSortedItems(table, columns, items, signalIdToItemCallback);
    }
    
    /**
     * Vergleicht zwei Tabellen-Texte für Sortierung
     * 
     * @param text1 Erster Text
     * @param text2 Zweiter Text
     * @param columnIndex Spalten-Index für spezielle Behandlung
     * @return Vergleichsresultat
     */
    public int compareTableText(String text1, String text2, int columnIndex) {
        // Für numerische Spalten versuche numerische Sortierung
        if (columnIndex == COL_EQUITY || columnIndex == COL_FLOATING || 
            columnIndex == COL_EQUITY_DRAWDOWN || columnIndex == COL_TOTAL) {
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
    public double extractNumber(String text) throws NumberFormatException {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        // Entferne alles außer Zahlen, Dezimalpunkt, Minus und Prozentzeichen
        String cleanText = text.replaceAll("[^0-9.+%-]", "").trim();
        
        // Entferne Prozentzeichen für die Berechnung
        if (cleanText.endsWith("%")) {
            cleanText = cleanText.substring(0, cleanText.length() - 1);
        }
        
        if (cleanText.isEmpty()) {
            return 0.0;
        }
        
        return Double.parseDouble(cleanText);
    }
    
    /**
     * Baut die Tabelle mit sortierten Items neu auf
     * 
     * @param table Die Tabelle
     * @param columns Die Tabellenspalten
     * @param sortedItems Die sortierten Items
     * @param signalIdToItemCallback Callback zum Update der Signal-ID-Mappings
     */
    private void rebuildTableWithSortedItems(Table table, TableColumn[] columns, TableItem[] sortedItems,
                                           java.util.function.BiConsumer<String, TableItem> signalIdToItemCallback) {
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
            
            // Update Mapping über Callback
            String signalId = itemData[i][COL_SIGNAL_ID];
            signalIdToItemCallback.accept(signalId, newItem);
        }
    }
    
    /**
     * Zeigt Details für einen Provider an
     * 
     * @param item Das Tabellen-Item
     * @param lastSignalData Map mit den letzten SignalData
     */
    public void showSignalDetails(TableItem item, java.util.Map<String, SignalData> lastSignalData) {
        String signalId = item.getText(COL_SIGNAL_ID);
        String providerName = item.getText(COL_PROVIDER_NAME);
        SignalData signalData = lastSignalData.get(signalId);
        
        StringBuilder details = new StringBuilder();
        details.append("Signal Provider Details\n\n");
        details.append("Signal ID: ").append(signalId).append("\n");
        details.append("Provider Name: ").append(providerName).append("\n");
        details.append("Status: ").append(item.getText(COL_STATUS)).append("\n");
        details.append("Kontostand: ").append(item.getText(COL_EQUITY)).append("\n");
        details.append("Floating Profit: ").append(item.getText(COL_FLOATING)).append("\n");
        details.append("Equity Drawdown: ").append(item.getText(COL_EQUITY_DRAWDOWN)).append("\n");
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
     * Zeigt eine Bestätigungsdialog für das Entfernen von Providern
     * 
     * @param selectedItemsCount Anzahl der ausgewählten Items
     * @return true wenn bestätigt, false sonst
     */
    public boolean confirmRemoveProviders(int selectedItemsCount) {
        MessageBox confirmBox = new MessageBox(parentGui.getShell(), SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        confirmBox.setText("Provider entfernen");
        confirmBox.setMessage("Möchten Sie " + selectedItemsCount + " Provider aus der Tabelle entfernen?\n\n" +
                             "Dies entfernt sie nur aus der Anzeige, nicht aus der Favoriten-Datei.");
        
        return confirmBox.open() == SWT.YES;
    }
}