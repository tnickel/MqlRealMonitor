package com.mql.realmonitor.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.parser.SignalData;

/**
 * ERWEITERT: Verwaltet das Kontextmenü für die SignalProviderTable
 * NEU: MQL5 Website-Link zum direkten Öffnen der Signalprovider-Seite
 * ASYNCHRON: Doppelklick-Handler jetzt asynchron - behebt 60-Sekunden-Blocking
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
     */
    public void setCallbacks(java.util.function.Consumer<String> updateProviderStatus,
                           java.util.function.Supplier<Map<String, SignalData>> getLastSignalData,
                           java.util.function.BiConsumer<String, TableItem> removeFromMapping) {
        this.updateProviderStatus = updateProviderStatus;
        this.getLastSignalData = getLastSignalData;
        this.removeFromMapping = removeFromMapping;
    }
    
    /**
     * ASYNCHRON: Konfiguriert das Tabellenverhalten mit asynchronem Doppelklick
     * LÖST 60-SEKUNDEN-BLOCKING PROBLEM
     */
    public void setupTableBehavior(Table table) {
        // ASYNCHRONER Doppelklick für Tick-Chart - BEHEBT BLOCKING!
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                TableItem[] selection = table.getSelection();
                if (selection.length > 0) {
                    // ASYNCHRON: Chart-Fenster in Background-Thread öffnen
                    openTickChartAsync(selection[0]);
                }
            }
        });
        
        // Rechtsklick-Kontextmenü
        Menu contextMenu = createContextMenu(table);
        table.setMenu(contextMenu);
    }
    
    /**
     * NEU: ASYNCHRONE Chart-Fenster-Erstellung - BEHEBT 60-SEKUNDEN-BLOCKING
     * Erstellt sofort ein minimales Loading-Fenster und lädt Charts im Background
     */
    private void openTickChartAsync(TableItem item) {
        String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
        String providerName = item.getText(ProviderTableHelper.COL_PROVIDER_NAME);
        
        LOGGER.info("=== ASYNCHRONE CHART-FENSTER-ERSTELLUNG GESTARTET ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        
        // Daten sofort sammeln (schnell)
        Map<String, SignalData> lastSignalData = getLastSignalData.get();
        SignalData signalData = lastSignalData.get(signalId);
        
        if (signalData == null) {
            showErrorMessage("Fehler", "Keine Signaldaten für " + signalId + " verfügbar.");
            return;
        }
        
        String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(signalId);
        
        // Status-Update für User-Feedback
        updateProviderStatus.accept(signalId + ":Chart wird geöffnet...");
        
        // ASYNCHRON: Chart-Fenster-Erstellung in Background-Thread
        new Thread(() -> {
            try {
                LOGGER.info("BACKGROUND-THREAD: Erstelle Chart-Fenster für " + signalId);
                
                // Chart-Fenster im UI-Thread erstellen (aber schnell)
                Display.getDefault().asyncExec(() -> {
                    try {
                        // Erstelle Chart-Fenster mit asynchronem Manager
                        AsyncTickChartWindow chartWindow = new AsyncTickChartWindow(
                            parentGui.getShell(), 
                            parentGui, 
                            signalId, 
                            providerName, 
                            signalData, 
                            tickFilePath
                        );
                        chartWindow.openAsync();
                        
                        LOGGER.info("ASYNC: Chart-Fenster erfolgreich gestartet für " + signalId);
                        
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "FEHLER beim asynchronen Öffnen des Chart-Fensters", e);
                        
                        Display.getDefault().asyncExec(() -> {
                            showErrorMessage("Fehler beim Öffnen des Chart-Fensters", 
                                           "Konnte Chart-Fenster für " + signalId + " nicht öffnen:\n" + e.getMessage());
                            updateProviderStatus.accept(signalId + ":Chart-Fehler");
                        });
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "FATALER FEHLER im Chart-Background-Thread", e);
                
                Display.getDefault().asyncExec(() -> {
                    showErrorMessage("Schwerwiegender Fehler", 
                                   "Unerwarteter Fehler beim Erstellen des Chart-Fensters:\n" + e.getMessage());
                    updateProviderStatus.accept(signalId + ":Schwerer Fehler");
                });
            }
            
        }, "AsyncChartCreator-" + signalId).start();
        
        LOGGER.info("=== ASYNCHRONE CHART-ERSTELLUNG DELEGIERT AN BACKGROUND-THREAD ===");
    }
    
    /**
     * ERWEITERT: Erstellt das Kontextmenü mit MQL5 Website-Link
     */
    private Menu createContextMenu(Table table) {
        Menu contextMenu = new Menu(table);
        
        // NEU: MQL5 Website öffnen - GANZ OBEN für bessere Sichtbarkeit
        createMql5WebsiteMenuItem(contextMenu, table);
        
        new MenuItem(contextMenu, SWT.SEPARATOR);
        
        // Bestehende Menü-Items
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
     * NEU: Erstellt das "MQL5 Website öffnen" Menü-Item
     */
    private void createMql5WebsiteMenuItem(Menu contextMenu, Table table) {
        MenuItem websiteItem = new MenuItem(contextMenu, SWT.PUSH);
        websiteItem.setText("🌐 MQL5 Website öffnen");
        websiteItem.setToolTipText("Öffnet die MQL5-Seite des Signalproviders im Browser");
        
        websiteItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openMql5Website(table);
            }
        });
        
        LOGGER.info("MQL5 Website-Menüeintrag erstellt");
    }
    
    /**
     * NEU: Öffnet die MQL5-Website für den ausgewählten Signalprovider
     */
    private void openMql5Website(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen Signalprovider aus.");
            return;
        }
        
        if (selectedItems.length > 1) {
            showInfoMessage("Mehrfachauswahl", 
                          "Bitte wählen Sie nur einen Signalprovider aus, um dessen MQL5-Seite zu öffnen.");
            return;
        }
        
        TableItem selectedItem = selectedItems[0];
        String signalId = selectedItem.getText(ProviderTableHelper.COL_SIGNAL_ID);
        String providerName = selectedItem.getText(ProviderTableHelper.COL_PROVIDER_NAME);
        
        try {
            LOGGER.info("=== ÖFFNE MQL5 WEBSITE für Signal: " + signalId + " ===");
            
            // URL aus der Konfiguration erstellen
            String websiteUrl = parentGui.getMonitor().getConfig().buildSignalUrl(signalId);
            LOGGER.info("Generierte URL: " + websiteUrl);
            
            // Bestätigungsdialog anzeigen
            if (showWebsiteConfirmationDialog(signalId, providerName, websiteUrl)) {
                openUrlInBrowser(websiteUrl);
                LOGGER.info("MQL5 Website erfolgreich geöffnet für Signal: " + signalId);
            } else {
                LOGGER.info("Benutzer hat das Öffnen der Website abgebrochen");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen der MQL5-Website für Signal: " + signalId, e);
            showErrorMessage("Fehler beim Öffnen der Website", 
                           "Konnte MQL5-Website für Signal " + signalId + " nicht öffnen:\n\n" + 
                           e.getMessage() + "\n\nBitte kopieren Sie die URL manuell:\n" + 
                           parentGui.getMonitor().getConfig().buildSignalUrl(signalId));
        }
    }
    
    /**
     * NEU: Zeigt Bestätigungsdialog vor dem Öffnen der Website
     */
    private boolean showWebsiteConfirmationDialog(String signalId, String providerName, String url) {
        MessageBox confirmBox = new MessageBox(parentGui.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("MQL5 Website öffnen");
        
        StringBuilder message = new StringBuilder();
        message.append("MQL5-Seite des Signalproviders im Browser öffnen?\n\n");
        message.append("Signal ID: ").append(signalId).append("\n");
        message.append("Provider: ").append(providerName).append("\n\n");
        message.append("URL: ").append(url).append("\n\n");
        message.append("Diese Aktion öffnet Ihren Standard-Webbrowser.");
        
        confirmBox.setMessage(message.toString());
        
        return confirmBox.open() == SWT.YES;
    }
    
    /**
     * NEU: Öffnet eine URL im Standard-Browser
     */
    private void openUrlInBrowser(String url) throws Exception {
        LOGGER.info("Versuche URL im Browser zu öffnen: " + url);
        
        // Versuche Desktop.browse() - Moderner Ansatz
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                URI uri = new URI(url);
                desktop.browse(uri);
                LOGGER.info("URL erfolgreich mit Desktop.browse() geöffnet");
                return;
            }
        }
        
        // Fallback: System-spezifische Kommandos
        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.info("Desktop.browse() nicht verfügbar, verwende OS-spezifischen Ansatz: " + os);
        
        ProcessBuilder processBuilder;
        
        if (os.contains("win")) {
            // Windows
            processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            // macOS
            processBuilder = new ProcessBuilder("open", url);
        } else {
            // Linux/Unix
            processBuilder = new ProcessBuilder("xdg-open", url);
        }
        
        Process process = processBuilder.start();
        
        // Warte kurz und prüfe Exit-Code
        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        if (finished) {
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                LOGGER.info("URL erfolgreich mit OS-Kommando geöffnet (Exit-Code: " + exitCode + ")");
            } else {
                LOGGER.warning("OS-Kommando beendet mit Exit-Code: " + exitCode);
            }
        } else {
            LOGGER.info("OS-Kommando läuft noch (normal für Browser-Start)");
        }
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
     * ASYNCHRON: Erstellt das "Tick-Chart öffnen" Menü-Item mit asynchronem Handler
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
     * Zeigt Tickdaten für ausgewählte Provider
     */
    private void showTickDataForSelected(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length > 1) {
            showInfoMessage("Mehrfachauswahl", "Bitte wählen Sie nur einen Provider für die Tickdaten-Anzeige aus.");
            return;
        }
        
        TableItem selectedItem = selectedItems[0];
        String signalId = selectedItem.getText(ProviderTableHelper.COL_SIGNAL_ID);
        String providerName = selectedItem.getText(ProviderTableHelper.COL_PROVIDER_NAME);
        
        tickDataWindow.showTickDataWindow(signalId, providerName);
    }
    
    /**
     * DEPRECATED: Alte synchrone Methode - wird durch openTickChartAsync ersetzt
     * Wird noch von Kontextmenü verwendet, soll aber auch asynchron werden
     */
    private void openTickChart(TableItem item) {
        LOGGER.warning("DEPRECATED: openTickChart() synchron aufgerufen - sollte openTickChartAsync() verwenden");
        
        // Delegate an asynchrone Version
        openTickChartAsync(item);
    }
    
    /**
     * ASYNCHRON: Öffnet Tick-Chart für ausgewählte Provider
     */
    private void openTickChartForSelected(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
            return;
        }
        
        if (selectedItems.length > 1) {
            showInfoMessage("Mehrfachauswahl", "Bitte wählen Sie nur einen Provider für das Tick-Chart aus.");
            return;
        }
        
        // ASYNCHRON: Verwende neue asynchrone Methode
        openTickChartAsync(selectedItems[0]);
    }
    
    /**
     * Aktualisiert ausgewählte Provider
     */
    private void refreshSelectedProviders(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
            return;
        }
        
        for (TableItem item : selectedItems) {
            String signalId = item.getText(ProviderTableHelper.COL_SIGNAL_ID);
            updateProviderStatus.accept(signalId + ":Aktualisiere...");
        }
        
        parentGui.getMonitor().manualRefresh();
    }
    
    /**
     * Zeigt Details für ausgewählte Provider
     */
    private void showSelectedProviderDetails(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen Provider aus.");
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
        
        showInfoMessage("Ausgewählte Provider", summary.toString());
    }
    
    /**
     * Entfernt ausgewählte Provider aus der Tabelle
     */
    private void removeSelectedProviders(Table table) {
        TableItem[] selectedItems = table.getSelection();
        
        if (selectedItems.length == 0) {
            showInfoMessage("Keine Auswahl", "Bitte wählen Sie einen oder mehrere Provider aus.");
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
     * NEU: Hilfsmethode für Info-Nachrichten
     */
    private void showInfoMessage(String title, String message) {
        parentGui.showInfo(title, message);
    }
    
    /**
     * NEU: Hilfsmethode für Fehler-Nachrichten
     */
    private void showErrorMessage(String title, String message) {
        parentGui.showError(title, message);
    }
    
    /**
     * Gibt den ProviderTableHelper zurück
     */
    public ProviderTableHelper getTableHelper() {
        return tableHelper;
    }
}