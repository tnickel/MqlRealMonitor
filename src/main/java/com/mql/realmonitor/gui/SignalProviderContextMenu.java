package com.mql.realmonitor.gui;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.parser.SignalData;

/**
 * Verwaltet das Kontextmenü für die SignalProviderTable
 * Enthält alle Menü-Aktionen und Event-Handler
 */
public class SignalProviderContextMenu {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderContextMenu.class.getName());
    
    private final MqlRealMonitorGUI parentGui;
    private final ProviderTableHelper tableHelper;
    private final TickDataDisplayWindow tickDataWindow;
    
    // Callbacks für Tabellen-Operationen
    private java.util.function.Consumer<String> updateProviderStatus;
    private java.util.function.Supplier<Map<String, SignalData>> getLastSignalData;
    private java.util.function.BiConsumer<String, TableItem> removeFromMapping;
    
    public SignalProviderContextMenu(MqlRealMonitorGUI parentGui) {
        this.parentGui = parentGui;
        this.tableHelper = new ProviderTableHelper(parentGui);
        this.tickDataWindow = new TickDataDisplayWindow(parentGui);
    }
    
    /**
     * Setzt die Callback-Funktionen für Tabellen-Operationen
     * 
     * @param updateProviderStatus Callback für Status-Updates
     * @param getLastSignalData Callback für SignalData-Zugriff
     * @param removeFromMapping Callback für das Entfernen aus der Mapping
     */
    public void setCallbacks(java.util.function.Consumer<String> updateProviderStatus,
                           java.util.function.Supplier<Map<String, SignalData>> getLastSignalData,
                           java.util.function.BiConsumer<String, TableItem> removeFromMapping) {
        this.updateProviderStatus = updateProviderStatus;
        this.getLastSignalData = getLastSignalData;
        this.removeFromMapping = removeFromMapping;
    }
    
    /**
     * Konfiguriert das Tabellenverhalten mit Kontextmenü und Doppelklick
     * 
     * @param table Die Tabelle
     */
    public void setupTableBehavior(Table table) {
        // Doppelklick für Tick-Chart
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
        Menu contextMenu = createContextMenu(table);
        table.setMenu(contextMenu);
    }
    
    /**
     * Erstellt das Kontextmenü für die Tabelle
     * 
     * @param table Die Tabelle
     * @return Das erstellte Kontextmenü
     */
    private Menu createContextMenu(Table table) {
        Menu contextMenu = new Menu(table);
        
        // Menü-Items erstellen
        createRefreshMenuItem(contextMenu, table);
        createDetailsMenuItem(contextMenu, table);
        
        new MenuItem(contextMenu, SWT.SEPARATOR);
        
        createTickDataMenuItem(contextMenu, table);
        createTickChartMenuItem(contextMenu, table);
        
        new MenuItem(contextMenu, SWT.SEPARATOR);
        
        createRemoveMenuItem(contextMenu, table);
        
        return contextMenu;
    }
    
    /**
     * Erstellt das "Ausgewählte aktualisieren" Menü-Item
     */
    private void createRefreshMenuItem(Menu contextMenu, Table table) {
        MenuItem refreshItem = new MenuItem(contextMenu, SWT.PUSH);
        refreshItem.setText("Ausgewählte aktualisieren");
        refreshItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshSelectedProviders(table);
            }
        });
    }
    
    /**
     * Erstellt das "Details anzeigen" Menü-Item
     */
    private void createDetailsMenuItem(Menu contextMenu, Table table) {
        MenuItem detailsItem = new MenuItem(contextMenu, SWT.PUSH);
        detailsItem.setText("Details anzeigen");
        detailsItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showSelectedProviderDetails(table);
            }
        });
    }
    
    /**
     * Erstellt das "Tickdaten anzeigen" Menü-Item
     */
    private void createTickDataMenuItem(Menu contextMenu, Table table) {
        MenuItem tickDataItem = new MenuItem(contextMenu, SWT.PUSH);
        tickDataItem.setText("Tickdaten anzeigen");
        tickDataItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showTickDataForSelected(table);
            }
        });
    }
    
    /**
     * Erstellt das "Tick-Chart öffnen" Menü-Item
     */
    private void createTickChartMenuItem(Menu contextMenu, Table table) {
        MenuItem chartItem = new MenuItem(contextMenu, SWT.PUSH);
        chartItem.setText("Tick-Chart öffnen");
        chartItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openTickChartForSelected(table);
            }
        });
    }
    
    /**
     * Erstellt das "Aus Tabelle entfernen" Menü-Item
     */
    private void createRemoveMenuItem(Menu contextMenu, Table table) {
        MenuItem removeItem = new MenuItem(contextMenu, SWT.PUSH);
        removeItem.setText("Aus Tabelle entfernen");
        removeItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                removeSelectedProviders(table);
            }
        });
    }
    
    /**
     * Zeigt Tickdaten für ausgewählte Provider in einem separaten Textfenster an
     */
    private void showTickDataForSelected(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length > 1) {
            parentGui.showInfo("Mehrfachauswahl", "Bitte wählen Sie nur einen Provider für die Tickdaten-Anzeige aus.");
            return;
        }
        
        TableItem selectedItem = selectedItems[0];
        String signalId = selectedItem.getText(ProviderTableHelper.COL_SIGNAL_ID);
        String providerName = selectedItem.getText(ProviderTableHelper.COL_PROVIDER_NAME);
        
        tickDataWindow.showTickDataWindow(signalId, providerName);
    }
    
    /**
     * Öffnet das Tick-Chart für eine Tabellenzeile
     * 
     * @param item Das Tabellen-Item
     */
    private void openTickChart(TableItem item) {
        String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
        String providerName = item.getText(ProviderTableHelper.COL_PROVIDER_NAME);
        
        Map<String, SignalData> lastSignalData = getLastSignalData.get();
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
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Tick-Charts: " + e.getMessage(), e);
            parentGui.showError("Fehler beim Öffnen des Tick-Charts", 
                               "Konnte Tick-Chart für " + signalId + " nicht öffnen:\n" + e.getMessage());
        }
    }
    
    /**
     * Öffnet Tick-Chart für ausgewählte Provider
     */
    private void openTickChartForSelected(Table table) {
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
     * Aktualisiert ausgewählte Provider
     */
    private void refreshSelectedProviders(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
            return;
        }
        
        for (TableItem item : selectedItems) {
            String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
            updateProviderStatus.accept(signalId + ":Aktualisiere...");
        }
        
        // Manuellen Refresh triggern
        parentGui.getMonitor().manualRefresh();
    }
    
    /**
     * Zeigt Details für ausgewählte Provider
     */
    private void showSelectedProviderDetails(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length == 1) {
            Map<String, SignalData> lastSignalData = getLastSignalData.get();
            tableHelper.showSignalDetails(selectedItems[0], lastSignalData);
        } else {
            showMultipleProvidersSummary(selectedItems);
        }
    }
    
    /**
     * Zeigt eine Zusammenfassung für mehrere ausgewählte Provider
     */
    private void showMultipleProvidersSummary(TableItem[] selectedItems) {
        StringBuilder summary = new StringBuilder();
        summary.append("Ausgewählte Provider (").append(selectedItems.length).append("):\n\n");
        
        for (TableItem item : selectedItems) {
            summary.append("• ").append(item.getText(ProviderTableHelper.COL_SIGNAL_ID))
                   .append(" (").append(item.getText(ProviderTableHelper.COL_PROVIDER_NAME)).append(")")
                   .append(" - ").append(item.getText(ProviderTableHelper.COL_STATUS))
                   .append(" (").append(item.getText(ProviderTableHelper.COL_TOTAL)).append(")")
                   .append(" [DD: ").append(item.getText(ProviderTableHelper.COL_EQUITY_DRAWDOWN)).append("]")
                   .append("\n");
        }
        
        parentGui.showInfo("Ausgewählte Provider", summary.toString());
    }
    
    /**
     * Entfernt ausgewählte Provider aus der Tabelle
     */
    private void removeSelectedProviders(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            parentGui.showInfo("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
            return;
        }
        
        if (tableHelper.confirmRemoveProviders(selectedItems.length)) {
            for (TableItem item : selectedItems) {
                String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
                removeFromMapping.accept(signalId, item);
                item.dispose();
            }
            
            LOGGER.info("Provider aus Tabelle entfernt: " + selectedItems.length + " Einträge");
        }
    }
    
    /**
     * Gibt den ProviderTableHelper zurück
     * 
     * @return Der ProviderTableHelper
     */
    public ProviderTableHelper getTableHelper() {
        return tableHelper;
    }
}