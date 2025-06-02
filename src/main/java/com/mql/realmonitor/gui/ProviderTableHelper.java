package com.mql.realmonitor.gui;

import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.parser.SignalData;

/**
 * Hilfsfunktionen für die SignalProviderTable
 * ERWEITERT: Zusätzliche Funktionen für Chart-Übersicht und Favoritenklasse-Spalte
 * NEU: Support für WeeklyProfit und MonthlyProfit Spalten mit Farbkodierung
 * Enthält Berechnungen, Sortierung und Formatierung
 */
public class ProviderTableHelper {
    
    private static final Logger LOGGER = Logger.getLogger(ProviderTableHelper.class.getName());
    
    // Spalten-Indizes (ERWEITERT: Neue Spalten WeeklyProfit und MonthlyProfit)
    public static final int COL_SIGNAL_ID = 0;
    public static final int COL_FAVORITE_CLASS = 1;        // Favoritenklasse
    public static final int COL_PROVIDER_NAME = 2;        // Provider Name
    public static final int COL_STATUS = 3;               // Status
    public static final int COL_EQUITY = 4;               // Kontostand
    public static final int COL_FLOATING = 5;             // Floating Profit
    public static final int COL_EQUITY_DRAWDOWN = 6;      // Equity Drawdown
    public static final int COL_TOTAL = 7;                // Gesamtwert
    public static final int COL_WEEKLY_PROFIT = 8;        // NEU: WeeklyProfit
    public static final int COL_MONTHLY_PROFIT = 9;       // NEU: MonthlyProfit
    public static final int COL_CURRENCY = 10;            // Währung (verschoben von 8 zu 10)
    public static final int COL_LAST_UPDATE = 11;         // Letzte Aktualisierung (verschoben von 9 zu 11)
    public static final int COL_CHANGE = 12;              // Änderung (verschoben von 10 zu 12)
    
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
     * NEU: Bestimmt die Farbe für Profit-Werte (WeeklyProfit und MonthlyProfit)
     * 
     * @param profitPercent Der Profit-Wert in Prozent
     * @return Die entsprechende Farbe (Grün für positive, Rot für negative Werte)
     */
    public Color getProfitColor(double profitPercent) {
        if (profitPercent > 0) {
            return parentGui.getGreenColor();  // Grün für positive Profits
        } else if (profitPercent < 0) {
            return parentGui.getRedColor();    // Rot für negative Profits
        } else {
            return null; // Standard-Farbe für 0%
        }
    }
    
    /**
     * NEU: Bestimmt die Hintergrundfarbe für eine Favoritenklasse
     * 1=grün, 2=gelb, 3=orange, 4-10=rot (helle Farben für bessere Lesbarkeit)
     * 
     * @param favoriteClass Die Favoritenklasse (1-10)
     * @return Die Hintergrundfarbe oder null für keine spezielle Farbe
     */
    public Color getFavoriteClassBackgroundColor(String favoriteClass) {
        if (favoriteClass == null || favoriteClass.trim().isEmpty() || favoriteClass.equals("-")) {
            return null; // Keine spezielle Hintergrundfarbe für Einträge ohne Klasse
        }
        
        try {
            int classNumber = Integer.parseInt(favoriteClass.trim());
            
            switch (classNumber) {
                case 1:
                    return parentGui.getFavoriteClass1Color();         // Hellgrün
                case 2:
                    return parentGui.getFavoriteClass2Color();         // Hellgelb
                case 3:
                    return parentGui.getFavoriteClass3Color();         // Hellorange
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                    return parentGui.getFavoriteClass4To10Color();     // Hellrot (für 4-10)
                default:
                    return null; // Ungültige Klasse - keine Farbe
            }
            
        } catch (NumberFormatException e) {
            return null; // Ungültige Favoritenklasse - keine Farbe
        }
    }
    
    /**
     * NEU: Bestimmt die Farbe für die Favoritenklasse (Standard-Farbe, keine spezielle Kodierung)
     * 
     * @param favoriteClass Die Favoritenklasse
     * @return Die Standard-Farbe (null für normale Textfarbe)
     */
    public Color getFavoriteClassColor(String favoriteClass) {
        // Keine spezielle Farbkodierung - verwende Standard-Textfarbe
        return null;
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
        
        if (lowerStatus.contains("ok") || lowerStatus.contains("erfolg") || lowerStatus.contains("geladen")) {
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
     * ERWEITERT: Bessere Datum/Zeit-Vergleiche und Support für neue Profit-Spalten
     * 
     * @param text1 Erster Text
     * @param text2 Zweiter Text
     * @param columnIndex Spalten-Index für spezielle Behandlung
     * @return Vergleichsresultat
     */
    public int compareTableText(String text1, String text2, int columnIndex) {
        // Für numerische Spalten versuche numerische Sortierung
        if (columnIndex == COL_EQUITY || columnIndex == COL_FLOATING || 
            columnIndex == COL_EQUITY_DRAWDOWN || columnIndex == COL_TOTAL ||
            columnIndex == COL_WEEKLY_PROFIT || columnIndex == COL_MONTHLY_PROFIT) {  // NEU: Profit-Spalten
            try {
                // Extrahiere Zahlen aus formatiertem Text
                double num1 = extractNumber(text1);
                double num2 = extractNumber(text2);
                return Double.compare(num1, num2);
            } catch (Exception e) {
                // Fall back zu String-Vergleich
            }
        }
        
        // NEU: Spezielle Behandlung für Favoritenklasse (numerische Sortierung 1-10, leere Werte am Ende)
        if (columnIndex == COL_FAVORITE_CLASS) {
            boolean text1Empty = (text1 == null || text1.trim().isEmpty() || text1.equals("-"));
            boolean text2Empty = (text2 == null || text2.trim().isEmpty() || text2.equals("-"));
            
            if (text1Empty && text2Empty) return 0;
            if (text1Empty) return 1;  // Leere Werte nach hinten
            if (text2Empty) return -1;
            
            try {
                // Versuche numerische Sortierung für Klassen 1-10
                int num1 = Integer.parseInt(text1.trim());
                int num2 = Integer.parseInt(text2.trim());
                return Integer.compare(num1, num2);
            } catch (NumberFormatException e) {
                // Fallback zu String-Vergleich
                return text1.compareToIgnoreCase(text2);
            }
        }
        
        // NEU: Spezielle Behandlung für Profit-Spalten (N/A Werte am Ende)
        if (columnIndex == COL_WEEKLY_PROFIT || columnIndex == COL_MONTHLY_PROFIT) {
            boolean text1NA = (text1 == null || text1.trim().isEmpty() || text1.equals("N/A"));
            boolean text2NA = (text2 == null || text2.trim().isEmpty() || text2.equals("N/A"));
            
            if (text1NA && text2NA) return 0;
            if (text1NA) return 1;  // N/A Werte nach hinten
            if (text2NA) return -1;
            
            try {
                // Numerische Sortierung für Profit-Werte
                double num1 = extractNumber(text1);
                double num2 = extractNumber(text2);
                return Double.compare(num1, num2);
            } catch (Exception e) {
                // Fallback zu String-Vergleich
                return text1.compareToIgnoreCase(text2);
            }
        }
        
        // Spezielle Behandlung für Datum/Zeit (für Chart-Übersicht relevant)
        if (columnIndex == COL_LAST_UPDATE) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                java.time.LocalDateTime date1 = java.time.LocalDateTime.parse(text1, formatter);
                java.time.LocalDateTime date2 = java.time.LocalDateTime.parse(text2, formatter);
                return date1.compareTo(date2);
            } catch (Exception e) {
                // Fallback zu String-Vergleich
            }
        }
        
        // Standard String-Vergleich
        return text1.compareToIgnoreCase(text2);
    }
    
    /**
     * Extrahiert eine Zahl aus formatiertem Text
     * ERWEITERT: Robustere Zahlen-Extraktion für Profit-Werte
     * 
     * @param text Der formatierte Text
     * @return Die extrahierte Zahl
     */
    public double extractNumber(String text) throws NumberFormatException {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        // Spezielle Behandlung für N/A Werte
        if (text.trim().equals("N/A")) {
            throw new NumberFormatException("N/A value");
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
     * KORRIGIERT: Berechnet Hintergrundfarben neu statt sie nur zu speichern
     * 
     * @param table Die Tabelle
     * @param columns Die Tabellenspalten
     * @param sortedItems Die sortierten Items
     * @param signalIdToItemCallback Callback zum Update der Signal-ID-Mappings
     */
    private void rebuildTableWithSortedItems(Table table, TableColumn[] columns, TableItem[] sortedItems,
                                           java.util.function.BiConsumer<String, TableItem> signalIdToItemCallback) {
        // Speichere Daten inklusive Favoritenklassen für Neuberechnung der Farben
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
            
            // Vordergrundfarben wiederherstellen
            for (int j = 0; j < itemColors[i].length; j++) {
                if (itemColors[i][j] != null) {
                    newItem.setForeground(j, itemColors[i][j]);
                }
            }
            
            // KORRIGIERT: Hintergrundfarbe neu berechnen basierend auf Favoritenklasse
            String favoriteClass = itemData[i][COL_FAVORITE_CLASS];
            Color backgroundColor = getFavoriteClassBackgroundColor(favoriteClass);
            if (backgroundColor != null) {
                newItem.setBackground(backgroundColor);
            }
            
            // Update Mapping über Callback
            String signalId = itemData[i][COL_SIGNAL_ID];
            signalIdToItemCallback.accept(signalId, newItem);
        }
    }
    
    /**
     * ERWEITERT: Zeigt Details für einen Provider an (mit Profit-Informationen)
     * 
     * @param item Das Tabellen-Item
     * @param lastSignalData Map mit den letzten SignalData
     */
    public void showSignalDetails(TableItem item, java.util.Map<String, SignalData> lastSignalData) {
        String signalId = item.getText(COL_SIGNAL_ID);
        String favoriteClass = item.getText(COL_FAVORITE_CLASS);
        String providerName = item.getText(COL_PROVIDER_NAME);
        String weeklyProfit = item.getText(COL_WEEKLY_PROFIT);      // NEU
        String monthlyProfit = item.getText(COL_MONTHLY_PROFIT);    // NEU
        SignalData signalData = lastSignalData.get(signalId);
        
        StringBuilder details = new StringBuilder();
        details.append("=== SIGNAL PROVIDER DETAILS ===\n\n");
        details.append("Signal ID: ").append(signalId).append("\n");
        details.append("Favoritenklasse: ").append(favoriteClass != null && !favoriteClass.trim().isEmpty() && !favoriteClass.equals("-") ? favoriteClass : "Keine Klasse").append("\n");
        details.append("Provider Name: ").append(providerName).append("\n");
        details.append("Status: ").append(item.getText(COL_STATUS)).append("\n");
        details.append("Kontostand: ").append(item.getText(COL_EQUITY)).append("\n");
        details.append("Floating Profit: ").append(item.getText(COL_FLOATING)).append("\n");
        details.append("Equity Drawdown: ").append(item.getText(COL_EQUITY_DRAWDOWN)).append("\n");
        details.append("Gesamtwert: ").append(item.getText(COL_TOTAL)).append("\n");
        details.append("Wochengewinn: ").append(weeklyProfit).append("\n");      // NEU
        details.append("Monatsgewinn: ").append(monthlyProfit).append("\n");    // NEU
        details.append("Währung: ").append(item.getText(COL_CURRENCY)).append("\n");
        details.append("Letzte Aktualisierung: ").append(item.getText(COL_LAST_UPDATE)).append("\n");
        details.append("Änderung: ").append(item.getText(COL_CHANGE)).append("\n");
        
        // NEU: Erweiterte Details für Chart-Übersicht
        if (signalData != null) {
            details.append("\n=== ERWEITERTE INFORMATIONEN ===\n");
            details.append("Vollständige URL: ").append(parentGui.getMonitor().getConfig().buildSignalUrl(signalId)).append("\n");
            details.append("Tick-Datei: ").append(parentGui.getMonitor().getConfig().getTickFilePath(signalId)).append("\n");
            details.append("Vollständige Daten: ").append(signalData.getSummary()).append("\n");
        }
        
        // NEU: Details in eigenem scrollbaren Fenster anzeigen (besser für Chart-Übersicht)
        showDetailedInfoWindow(signalId, details.toString());
    }
    
    /**
     * NEU: Zeigt Details in einem separaten scrollbaren Fenster an
     * (Nützlich für Chart-Übersicht mit vielen Details)
     * 
     * @param signalId Die Signal-ID für den Fenstertitel
     * @param detailsText Der Detail-Text
     */
    private void showDetailedInfoWindow(String signalId, String detailsText) {
        Shell detailsShell = new Shell(parentGui.getShell(), SWT.DIALOG_TRIM | SWT.MODELESS | SWT.RESIZE);
        detailsShell.setText("Signal Details - " + signalId);
        detailsShell.setSize(600, 500);
        detailsShell.setLayout(new GridLayout(1, false));
        
        Text detailsTextWidget = new Text(detailsShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        detailsTextWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        detailsTextWidget.setText(detailsText);
        detailsTextWidget.setBackground(parentGui.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        
        Button closeButton = new Button(detailsShell, SWT.PUSH);
        closeButton.setText("Schließen");
        closeButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        closeButton.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                detailsShell.close();
            }
        });
        
        // Dialog zentrieren
        Point parentLocation = parentGui.getShell().getLocation();
        Point parentSize = parentGui.getShell().getSize();
        Point dialogSize = detailsShell.getSize();
        
        int x = parentLocation.x + (parentSize.x - dialogSize.x) / 2;
        int y = parentLocation.y + (parentSize.y - dialogSize.y) / 2;
        detailsShell.setLocation(x, y);
        
        detailsShell.open();
        
        LOGGER.info("Detailliertes Info-Fenster angezeigt für Signal: " + signalId);
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
    
    /**
     * NEU: Hilfsmethode um Provider-Daten aus der Tabelle zu extrahieren
     * ERWEITERT: Jetzt mit Profit-Informationen
     * 
     * @param table Die Provider-Tabelle
     * @return Liste mit Provider-Daten als einfache Datenklasse
     */
    public java.util.List<ProviderInfo> extractProviderInfoFromTable(Table table) {
        java.util.List<ProviderInfo> providerInfos = new java.util.ArrayList<>();
        
        if (table == null || table.isDisposed()) {
            LOGGER.warning("Tabelle ist null oder disposed - kann Provider-Info nicht extrahieren");
            return providerInfos;
        }
        
        TableItem[] items = table.getItems();
        LOGGER.info("Extrahiere Provider-Info aus " + items.length + " Tabellen-Einträgen");
        
        for (TableItem item : items) {
            try {
                String signalId = item.getText(COL_SIGNAL_ID);
                String favoriteClass = item.getText(COL_FAVORITE_CLASS);
                String providerName = item.getText(COL_PROVIDER_NAME);
                String status = item.getText(COL_STATUS);
                String weeklyProfit = item.getText(COL_WEEKLY_PROFIT);      // NEU
                String monthlyProfit = item.getText(COL_MONTHLY_PROFIT);    // NEU
                
                // Nur gültige Provider hinzufügen
                if (signalId != null && !signalId.trim().isEmpty() && 
                    providerName != null && !providerName.trim().isEmpty() &&
                    !"Lädt...".equals(providerName)) {
                    
                    ProviderInfo info = new ProviderInfo(signalId, providerName, status, favoriteClass, weeklyProfit, monthlyProfit);
                    providerInfos.add(info);
                    
                    LOGGER.fine("Provider-Info extrahiert: " + signalId + " (" + providerName + 
                               ", Klasse: " + (favoriteClass != null && !favoriteClass.trim().isEmpty() && !favoriteClass.equals("-") ? favoriteClass : "Keine") + 
                               ", Weekly: " + weeklyProfit + ", Monthly: " + monthlyProfit + ")");
                }
                
            } catch (Exception e) {
                LOGGER.warning("Fehler beim Extrahieren der Provider-Info: " + e.getMessage());
            }
        }
        
        LOGGER.info("Provider-Info-Extraktion abgeschlossen: " + providerInfos.size() + " gültige Einträge");
        return providerInfos;
    }
    
    /**
     * NEU: Erweiterte Datenklasse für Provider-Informationen (mit Profit-Werten)
     * ERWEITERT: Jetzt mit WeeklyProfit und MonthlyProfit
     */
    public static class ProviderInfo {
        public final String signalId;
        public final String providerName;
        public final String status;
        public final String favoriteClass;
        public final String weeklyProfit;     // NEU
        public final String monthlyProfit;    // NEU
        
        public ProviderInfo(String signalId, String providerName, String status, String favoriteClass, 
                           String weeklyProfit, String monthlyProfit) {
            this.signalId = signalId;
            this.providerName = providerName;
            this.status = status;
            this.favoriteClass = favoriteClass;
            this.weeklyProfit = weeklyProfit;
            this.monthlyProfit = monthlyProfit;
        }
        
        @Override
        public String toString() {
            return "ProviderInfo{signalId='" + signalId + "', providerName='" + providerName + 
                   "', status='" + status + "', favoriteClass='" + favoriteClass + 
                   "', weeklyProfit='" + weeklyProfit + "', monthlyProfit='" + monthlyProfit + "'}";
        }
    }
}