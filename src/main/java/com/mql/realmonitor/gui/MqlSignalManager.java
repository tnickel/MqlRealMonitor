package com.mql.realmonitor.gui;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

/**
 * Manager für Add/Delete Signal Funktionalität.
 * Verwaltet: Add Signal Dialog, Delete Signal, Tabellen-Selection Listener
 */
public class MqlSignalManager {
    
    private static final Logger LOGGER = Logger.getLogger(MqlSignalManager.class.getName());
    
    private final MqlRealMonitorGUI gui;
    
    // Signal-Komponenten
    private Button deleteSignalButton;
    private Button addSignalButton;
    
    public MqlSignalManager(MqlRealMonitorGUI gui) {
        this.gui = gui;
    }
    
    /**
     * Erstellt die Signal-Buttons (Add, Delete) in der Toolbar
     */
    public void createSignalButtons(Composite parent) {
        // Delete Signal Button
        deleteSignalButton = new Button(parent, SWT.PUSH);
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
        addSignalButton = new Button(parent, SWT.PUSH);
        addSignalButton.setText("➕ Hinzufügen");
        addSignalButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        addSignalButton.setToolTipText("Neues Signal zu Favoriten hinzufügen");
        addSignalButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addNewSignalToFavorites();
            }
        });
        
        LOGGER.info("Signal-Buttons (Add, Delete) erstellt");
        
        // Tabellen-Selection Listener einrichten (verzögert)
        gui.getDisplay().timerExec(1000, () -> {
            setupTableSelectionListener();
            updateDeleteButtonState();
        });
    }
    
    /**
     * Öffnet einen Dialog zum Hinzufügen eines neuen Signals zu den Favoriten
     */
    private void addNewSignalToFavorites() {
        try {
            LOGGER.info("=== ADD NEW SIGNAL DIALOG GEÖFFNET ===");
            
            // Dialog erstellen
            Shell addSignalDialog = new Shell(gui.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
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
                if (!gui.getDisplay().readAndDispatch()) {
                    gui.getDisplay().sleep();
                }
            }
            
            // Nach dem Schließen: Tabelle aktualisieren falls Signal hinzugefügt wurde
            if (dialogResult[0]) {
                refreshTableAfterSignalAdded();
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Add Signal Dialogs", e);
            gui.showError("Fehler", "Konnte Dialog nicht öffnen: " + e.getMessage());
        }
    }
    
    /**
     * Validiert die Eingaben für ein neues Signal
     */
    private boolean validateSignalInput(String signalId, int favoriteClassIndex) {
        // Signal ID Validierung
        if (signalId == null || signalId.isEmpty()) {
            gui.showError("Ungültige Eingabe", "Bitte geben Sie eine Signal-ID ein.");
            return false;
        }
        
        // Prüfe ob Signal ID numerisch ist
        try {
            Long.parseLong(signalId);
        } catch (NumberFormatException e) {
            gui.showError("Ungültige Signal-ID", "Die Signal-ID muss eine Zahl sein.\n\nBeispiel: 1234567");
            return false;
        }
        
        // Favoritenklasse Validierung
        if (favoriteClassIndex < 0 || favoriteClassIndex > 9) {
            gui.showError("Ungültige Favoritenklasse", "Bitte wählen Sie eine Favoritenklasse von 1-10 aus.");
            return false;
        }
        
        // Prüfe ob Signal bereits existiert
        try {
            com.mql.realmonitor.downloader.FavoritesReader favoritesReader = 
                new com.mql.realmonitor.downloader.FavoritesReader(gui.getMonitor().getConfig());
            
            if (favoritesReader.containsSignalId(signalId)) {
                gui.showError("Signal bereits vorhanden", 
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
                new com.mql.realmonitor.downloader.FavoritesReader(gui.getMonitor().getConfig());
            
            boolean success = favoritesReader.addSignal(signalId, favoriteClass);
            
            if (success) {
                LOGGER.info("Signal erfolgreich zu Favoriten hinzugefügt: " + signalId + ":" + favoriteClass);
                
                gui.showInfo("Signal hinzugefügt", 
                        "Das Signal wurde erfolgreich zu den Favoriten hinzugefügt:\n\n" +
                        "Signal ID: " + signalId + "\n" +
                        "Favoritenklasse: " + favoriteClass + "\n\n" +
                        "Das Signal wird beim nächsten Refresh geladen.");
                
                return true;
            } else {
                LOGGER.severe("Fehler beim Hinzufügen des Signals zu favorites.txt: " + signalId);
                
                gui.showError("Fehler beim Hinzufügen", 
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
            
            gui.showError("Schwerwiegender Fehler", 
                     "Unerwarteter Fehler beim Hinzufügen des Signals:\n\n" +
                     "Signal ID: " + signalId + "\n" +
                     "Fehler: " + e.getMessage() + "\n\n" +
                     "Das Signal wurde möglicherweise nicht hinzugefügt.");
            
            return false;
        }
    }
    
    /**
     * Behandelt das Löschen eines Signals über den Toolbar-Button
     */
    private void deleteSelectedSignalFromToolbar() {
        try {
            LOGGER.info("=== DELETE SIGNAL ÜBER TOOLBAR AUSGELÖST ===");
            
            // Prüfen ob SignalProviderTable verfügbar ist
            if (gui.getProviderTable() == null) {
                gui.showError("Fehler", "Signalprovider-Tabelle nicht verfügbar.");
                return;
            }
            
            Table table = gui.getProviderTable().getTable();
            if (table == null || table.isDisposed()) {
                gui.showError("Fehler", "Tabelle nicht verfügbar oder bereits geschlossen.");
                return;
            }
            
            // Prüfen ob genau ein Signal ausgewählt ist
            SignalProviderContextMenu contextMenu = gui.getProviderTable().getContextMenu();
            if (!contextMenu.hasSignalSelectedForDeletion(table)) {
                String selectionInfo = contextMenu.getSelectedSignalInfo(table);
                gui.showInfo("Ungültige Auswahl", 
                       "Bitte wählen Sie genau ein Signal zum Löschen aus.\n\nAktueller Status: " + selectionInfo);
                return;
            }
            
            // Delete-Funktion über das Kontextmenü ausführen
            contextMenu.deleteSelectedSignalFromFavorites(table);
            
            LOGGER.info("Delete Signal über Toolbar erfolgreich ausgeführt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Löschen des Signals über Toolbar", e);
            gui.showError("Unerwarteter Fehler", 
                    "Fehler beim Löschen des Signals:\n\n" + e.getMessage());
        }
    }
    
    /**
     * Zentriert einen Dialog auf dem Hauptfenster
     */
    private void centerDialog(Shell dialog) {
        Point parentLocation = gui.getShell().getLocation();
        Point parentSize = gui.getShell().getSize();
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
            gui.updateStatus("Aktualisiere Favoriten nach Signal-Hinzufügung...");
            
            // Favorites-Cache der Tabelle aktualisieren
            if (gui.getProviderTable() != null) {
                gui.getProviderTable().refreshFavoriteClasses();
                
                // Manuellen Refresh auslösen um neue Daten zu laden
                gui.getDisplay().timerExec(1000, () -> {
                    gui.getMonitor().manualRefresh();
                    gui.updateStatus("Neues Signal hinzugefügt - Tabelle aktualisiert");
                });
            }
            
            LOGGER.info("Tabellen-Aktualisierung nach Signal-Hinzufügung eingeleitet");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Aktualisieren der Tabelle nach Signal-Hinzufügung", e);
            gui.updateStatus("Fehler beim Aktualisieren der Tabelle");
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
            
            if (gui.getProviderTable() != null) {
                Table table = gui.getProviderTable().getTable();
                if (table != null && !table.isDisposed()) {
                    SignalProviderContextMenu contextMenu = gui.getProviderTable().getContextMenu();
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
        if (gui.getProviderTable() == null) {
            return;
        }
        
        Table table = gui.getProviderTable().getTable();
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
            table.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    updateDeleteButtonState();
                }
                
                @Override
                public void focusLost(FocusEvent e) {
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
     * Wird vom GUI aufgerufen wenn sich Provider-Daten ändern
     */
    public void onProviderDataChanged() {
        updateDeleteButtonState();
    }
    
    /**
     * Wird vom GUI aufgerufen wenn sich Provider-Status ändert
     */
    public void onProviderStatusChanged() {
        updateDeleteButtonState();
    }
    
    /**
     * Bereinigt Ressourcen beim Herunterfahren
     */
    public void cleanup() {
        try {
            // Buttons werden automatisch durch SWT disposed
            deleteSignalButton = null;
            addSignalButton = null;
            
            LOGGER.info("SignalManager bereinigt");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Bereinigen des SignalManagers", e);
        }
    }
}