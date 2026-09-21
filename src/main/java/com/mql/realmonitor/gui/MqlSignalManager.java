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
import org.eclipse.swt.widgets.MessageBox;

import com.mql.realmonitor.downloader.FavoritesReader;
import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.kiscanner.KiScannerClient;
import com.mql.realmonitor.kiscanner.KiScannerFetchResult;
import com.mql.realmonitor.kiscanner.KiScannerSignal;
import com.mql.realmonitor.mql5.Mql5Credentials;
import com.mql.realmonitor.mql5.TradeHistoryManager;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manager für Add/Delete Signal Funktionalität.
 * Verwaltet: Add Signal Dialog, Delete Signal, Tabellen-Selection Listener
 * NEU: KiScanner-Import — ersetzt die überwachten Signale durch die
 * grünen/gelben Signale des MqlKiScanner (REST /api/v1/signals)
 * NEU: Trades laden — lädt die Trade-Historie aller Signale von MQL5
 * (Login via Selenium-Chrome, Rate-Limit, Cache 24h)
 */
public class MqlSignalManager {

    private static final Logger LOGGER = Logger.getLogger(MqlSignalManager.class.getName());

    private final MqlRealMonitorGUI gui;

    // Signal-Komponenten
    private Button deleteSignalButton;
    private Button addSignalButton;
    private Button kiScannerButton;
    private Button tradesButton;
    private Button simulatorButton;

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
        deleteSignalButton.setToolTipText("Ausgewählte(s) Signal(e) aus Favoriten löschen");
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

        // KiScanner Button: Signale vom MqlKiScanner übernehmen (nur grün/gelb)
        kiScannerButton = new Button(parent, SWT.PUSH);
        kiScannerButton.setText("🤖 KiScanner");
        kiScannerButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        kiScannerButton.setToolTipText("Signalliste vom MqlKiScanner holen: nur GRÜN und GELB bewertete "
                + "Signale werden angezeigt und überwacht, alle anderen werden entfernt");
        kiScannerButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                importKiScannerSignals();
            }
        });

        // Trades Button: MQL5-Trade-Historie aller Signale laden (für Chart-Overlay)
        tradesButton = new Button(parent, SWT.PUSH);
        tradesButton.setText("📜 Trades laden");
        tradesButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        tradesButton.setToolTipText("Trade-Historie aller Signale von MQL5 laden (benötigt MQL5-Zugang, "
                + "dauert bei vielen Signalen einige Minuten — bewusst langsam wegen MQL5-Rate-Limit). "
                + "Die Historie erscheint GRAU im Chart (Doppelklick auf eine Zeile).");
        tradesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTrades();
            }
        });

        // Simulator Button: Equity-Simulation aller Strategien ab Startdatum
        simulatorButton = new Button(parent, SWT.PUSH);
        simulatorButton.setText("📊 Simulator");
        simulatorButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        simulatorButton.setToolTipText("Simuliert alle Strategien ab dem Startdatum aus der Konfiguration "
                + "mit dem Startkapital je Strategie (Lot-Skalierung wie beim Signal-Kopieren). "
                + "Zeigt die Equity-Kurven im Scroll-Fenster, unten das Portfolio.");
        simulatorButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openSimulator();
            }
        });

        LOGGER.info("Signal-Buttons (Add, Delete, KiScanner, Trades, Simulator) erstellt");

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
     * Behandelt das Löschen der ausgewählten Signale über den Toolbar-Button
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
            
            // Prüfen ob mindestens ein Signal ausgewählt ist
            SignalProviderContextMenu contextMenu = gui.getProviderTable().getContextMenu();
            if (!contextMenu.hasSignalSelectedForDeletion(table)) {
                String selectionInfo = contextMenu.getSelectedSignalInfo(table);
                gui.showInfo("Ungültige Auswahl", 
                       "Bitte wählen Sie mindestens ein Signal zum Löschen aus.\n\nAktueller Status: " + selectionInfo);
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
            String tooltipText = "Ausgewählte(s) Signal(e) aus Favoriten löschen";
            
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
            kiScannerButton = null;
            tradesButton = null;
            simulatorButton = null;

            LOGGER.info("SignalManager bereinigt");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Bereinigen des SignalManagers", e);
        }
    }

    /**
     * NEU: Holt die Signalliste vom MqlKiScanner und übernimmt sie exklusiv.
     *
     * Nur Signale mit Gesamt-Ampel GRÜN oder GELB werden übernommen: Sie
     * ersetzen die komplette favorites.txt (neu hinzufügen, nicht mehr
     * enthaltene entfernen). Danach wird die Tabelle neu aufgebaut und ein
     * Refresh ausgelöst — die Werte werden wie üblich als Tick-Daten
     * gespeichert und überwacht. Die Favoritenklasse richtet sich nach der
     * Ampel: grün = Klasse 1 (hellgrün), gelb = Klasse 2 (hellgelb).
     */
    private void importKiScannerSignals() {
        if (kiScannerButton == null || kiScannerButton.isDisposed()) {
            return;
        }

        kiScannerButton.setEnabled(false);
        kiScannerButton.setText("Lädt...");
        gui.updateStatus("Hole grüne und gelbe Signale vom MqlKiScanner...");
        gui.updateKiScannerConnectionState("checking", null);

        new Thread(() -> {
            StringBuilder summaryText = new StringBuilder();

            try {
                KiScannerClient client = new KiScannerClient(gui.getMonitor().getConfig());
                KiScannerFetchResult result = client.fetchGreenAndYellowSignals();

                if (!result.isSuccess()) {
                    // Abruf fehlgeschlagen: Favoriten unangetastet lassen
                    gui.updateKiScannerConnectionState("error", result.getErrorMessage());
                    gui.getDisplay().asyncExec(() -> {
                        resetKiScannerButton();
                        gui.updateStatus("KiScanner-Abruf fehlgeschlagen");
                        MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_ERROR | SWT.OK);
                        box.setText("KiScanner-Import fehlgeschlagen");
                        box.setMessage(result.getErrorMessage());
                        box.open();
                    });
                    return;
                }

                // Verbindung steht (der Abruf selbst ist der Verbindungstest)
                gui.updateKiScannerConnectionState("ok", null);

                // NEU: Plattform-Zuordnung (MT4/MT5) dauerhaft speichern — der
                // Browser-Export probiert dann sofort den richtigen Export-Typ
                java.util.Map<String, String> platformen = new java.util.LinkedHashMap<>();
                for (KiScannerSignal signal : result.getSignals()) {
                    if (signal.getPlatform() != null && !signal.getPlatform().isEmpty()) {
                        platformen.put(String.valueOf(signal.getSignalId()), signal.getPlatform());
                    }
                }
                TradeHistoryManager.savePlatformMappings(gui.getMonitor().getConfig(), platformen);

                java.util.List<KiScannerSignal> scannerSignale = result.getSignals();

                if (scannerSignale.isEmpty()) {
                    // Sicherheitsnetz: leere Liste NICHT übernehmen (würde alles löschen)
                    gui.getDisplay().asyncExec(() -> {
                        resetKiScannerButton();
                        gui.updateStatus("KiScanner: keine grünen/gelben Signale");
                        MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_WARNING | SWT.OK);
                        box.setText("KiScanner-Import");
                        box.setMessage("Der MqlKiScanner hat aktuell KEINE Signale mit Ampel grün oder gelb.\n\n"
                                + "Gesamt in der Scanner-Datenbank: " + result.getTotalCount() + " Signale.\n\n"
                                + "Die bestehenden Favoriten bleiben unverändert.");
                        box.open();
                    });
                    return;
                }

                summaryText.append("Vom MqlKiScanner übernommen (nur GRÜN und GELB):\n\n");

                FavoritesReader favoritesReader = new FavoritesReader(gui.getMonitor().getConfig());
                IdTranslationManager translationManager = gui.getProviderTable().getIdTranslationManager();

                // Scanner-IDs in Original-Reihenfolge sammeln
                Set<String> scannerIds = new LinkedHashSet<>();
                for (KiScannerSignal signal : scannerSignale) {
                    scannerIds.add(String.valueOf(signal.getSignalId()));
                }

                // 1. Nicht mehr überwachte Signale entfernen (ein Backup, eine Operation)
                java.util.List<String> aktuelle = favoritesReader.readFavorites();
                Set<String> zuEntfernen = new LinkedHashSet<>();
                for (String id : aktuelle) {
                    if (!scannerIds.contains(id)) {
                        zuEntfernen.add(id);
                    }
                }
                int removedCount = 0;
                if (!zuEntfernen.isEmpty()) {
                    if (favoritesReader.removeSignals(zuEntfernen)) {
                        removedCount = zuEntfernen.size();
                    } else {
                        LOGGER.warning("Konnte Signale nicht aus Favoriten entfernen: " + zuEntfernen);
                    }
                }

                // 2. Fehlende Scanner-Signale hinzufügen; Namen für alle aktualisieren.
                // Die Favoritenklasse folgt IMMER der Ampel (grün = 1 hellgrün,
                // gelb = 2 hellgelb) — auch bei bereits vorhandenen Signalen,
                // damit die Zeilenfarbe der Tabelle der Scanner-Bewertung entspricht.
                int addedCount = 0;
                int classUpdatedCount = 0;
                for (KiScannerSignal signal : scannerSignale) {
                    String id = String.valueOf(signal.getSignalId());
                    String ampelKlasse = KiScannerSignal.AMPEL_GRUEN.equals(signal.getAmpel()) ? "1" : "2";

                    if (translationManager != null && !signal.getName().isEmpty()) {
                        translationManager.addOrUpdateMapping(id, signal.getName());
                    }

                    if (!aktuelle.contains(id)) {
                        if (favoritesReader.addSignal(id, ampelKlasse)) {
                            addedCount++;
                        }
                    } else {
                        // Vorhanden: Klasse auf die Ampel angleichen (z. B. altes
                        // Klasse-1-Signal das jetzt nur noch gelb ist)
                        String aktuelleKlasse = favoritesReader.getFavoriteClass(id);
                        if (aktuelleKlasse != null && !aktuelleKlasse.equals(ampelKlasse)) {
                            if (favoritesReader.updateSignalClass(id, ampelKlasse)) {
                                classUpdatedCount++;
                            }
                        }
                    }

                    summaryText.append(signal.getAmpelEmoji()).append(" ")
                            .append(signal.getName().isEmpty() ? id : signal.getName())
                            .append(" (").append(id).append(")")
                            .append("\n");
                }

                int finalRemovedCount = removedCount;
                int finalAddedCount = addedCount;
                int finalClassUpdatedCount = classUpdatedCount;
                int keptCount = scannerIds.size() - finalAddedCount;

                summaryText.append("\nZusammenfassung:\n");
                summaryText.append("Überwacht (grün/gelb): ").append(scannerIds.size()).append("\n");
                summaryText.append("Neu hinzugefügt: ").append(finalAddedCount).append("\n");
                summaryText.append("Bereits vorhanden: ").append(keptCount).append("\n");
                if (finalClassUpdatedCount > 0) {
                    summaryText.append("Klasse an Ampel angepasst: ").append(finalClassUpdatedCount).append("\n");
                }
                summaryText.append("Entfernt (nicht grün/gelb): ").append(finalRemovedCount).append("\n\n");
                summaryText.append("Hinweis: Die bisher aufgezeichneten Tick-Daten entfernter Signale\n");
                summaryText.append("bleiben erhalten (Realtick\\tick\\<Signal-ID>.txt) - falls ein Signal\n");
                summaryText.append("später wieder grün oder gelb wird, läuft die Historie weiter.\n\n");
                summaryText.append("Die Überwachung startet jetzt für diese Signale.");

                // UI-Thread: Tabelle auf die Scanner-Signale umstellen
                gui.getDisplay().asyncExec(() -> {
                    resetKiScannerButton();

                    if (gui.getProviderTable() != null) {
                        gui.getProviderTable().clearAllProviders();
                        for (KiScannerSignal signal : scannerSignale) {
                            gui.getProviderTable().addEmptyProviderEntry(
                                    String.valueOf(signal.getSignalId()), "Warte auf KiScanner-Daten...");
                        }
                        gui.getProviderTable().refreshFavoriteClasses();
                        gui.getProviderTable().refreshProviderNames();
                    }

                    gui.updateStatus("KiScanner-Import: " + scannerIds.size()
                            + " Signale (grün/gelb) - Starte Überwachung...");

                    // Refresh auslösen: lädt alle Signale und schreibt Tick-Daten
                    gui.getDisplay().timerExec(1000, () -> gui.getMonitor().manualRefresh());

                    MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_INFORMATION | SWT.OK);
                    box.setText("KiScanner-Import");
                    box.setMessage(summaryText.toString());
                    box.open();
                });

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Fehler beim KiScanner-Import", ex);
                gui.updateKiScannerConnectionState("error",
                        "Unerwarteter Fehler: " + ex.getMessage());
                gui.getDisplay().asyncExec(() -> {
                    resetKiScannerButton();
                    gui.updateStatus("KiScanner-Import: Fehler");
                    MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_ERROR | SWT.OK);
                    box.setText("KiScanner-Import fehlgeschlagen");
                    box.setMessage("Unerwarteter Fehler beim Import:\n\n" + ex.getMessage());
                    box.open();
                });
            }
        }).start();
    }

    /**
     * NEU: Öffnet das Simulator-Fenster (scrollbare Liste mit simulierten
     * Equity-Kurven je Strategie + Portfolio am Ende)
     */
    private void openSimulator() {
        try {
            new SimulatorWindow(gui).open();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Simulators", e);
            gui.showError("Simulator", "Konnte Simulator nicht öffnen: " + e.getMessage());
        }
    }

    /**
     * NEU: Setzt den KiScanner-Button nach dem Import zurück
     */
    private void resetKiScannerButton() {
        if (kiScannerButton != null && !kiScannerButton.isDisposed()) {
            kiScannerButton.setEnabled(true);
            kiScannerButton.setText("🤖 KiScanner");
        }
    }

    /**
     * NEU: Lädt die Trade-Historie aller überwachten Signale von MQL5
     * ("Refresh Trades").
     *
     * Ablauf: Login-Check (Zugangsdaten aus den Einstellungen) → für jedes
     * Signal den Export herunterladen und in Realtick\trades speichern
     * (Cache 24 h, Rate-Limit 2-4 s je Request — bewusst langsam, sonst
     * Ärger mit MQL5). Danach erscheint die Historie GRAU im Chart der
     * jeweiligen Zeile (Doppelklick).
     */
    private void refreshTrades() {
        if (tradesButton == null || tradesButton.isDisposed()) {
            return;
        }

        // Zugangsdaten vorher prüfen — mit Hinweis auf die Einstellungen
        Mql5Credentials credentials = new Mql5Credentials(
                gui.getMonitor().getConfig().getConfigDir());
        if (!credentials.isConfigured()) {
            MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_WARNING | SWT.OK);
            box.setText("Keine MQL5-Zugangsdaten");
            box.setMessage("Für den Trade-Historie-Download sind MQL5-Zugangsdaten nötig.\n\n"
                    + "Bitte unter Menü → Einstellungen → Konfiguration MQL5-Login und "
                    + "Passwort hinterlegen.\n\n(Keine Sorge: Die Daten werden außerhalb "
                    + "des Programverzeichnisses gespeichert und nie veröffentlicht.)");
            box.open();
            return;
        }

        tradesButton.setEnabled(false);
        tradesButton.setText("Lädt...");
        gui.updateStatus("Lade Trade-Historie von MQL5 (Login)...");

        java.util.List<String> favoriteIds = new FavoritesReader(gui.getMonitor().getConfig()).readFavorites();
        if (favoriteIds.isEmpty()) {
            tradesButton.setEnabled(true);
            tradesButton.setText("📜 Trades laden");
            gui.updateStatus("Keine Signale in den Favoriten");
            gui.showInfo("Trades laden", "Keine Signale in den Favoriten vorhanden.");
            return;
        }

        final int anzahl = favoriteIds.size();

        new Thread(() -> {
            StringBuilder summary = new StringBuilder();

            try {
                TradeHistoryManager manager = new TradeHistoryManager(gui.getMonitor().getConfig());
                // NEU: Plattform-Zuordnung aus dem KiScanner-Import nutzen —
                // richtige Export-URL je Signal (MT4: history, MT5: positions)
                java.util.Map<String, String> platformen =
                        TradeHistoryManager.readPlatformMappings(gui.getMonitor().getConfig());
                TradeHistoryManager.RefreshResult result = manager.refreshAll(favoriteIds, platformen,
                        meldung -> gui.updateStatus(meldung));

                summary.append("Trade-Historie von MQL5 geladen:\n\n");
                summary.append("Signale: ").append(anzahl).append("\n");
                summary.append("Neu geladen: ").append(result.successCount - result.cacheCount).append("\n");
                summary.append("Aus Cache (< 24 h): ").append(result.cacheCount).append("\n");
                summary.append("Fehler: ").append(result.errorCount).append("\n\n");

                for (TradeHistoryManager.SignalResult r : result.results) {
                    if (r.success && !r.fromCache) {
                        summary.append("✓ ").append(r.signalId).append(": ")
                                .append(r.tradeCount).append(" Trades\n");
                    } else if (r.fromCache) {
                        summary.append("⏱ ").append(r.signalId).append(": aus Cache\n");
                    } else {
                        summary.append("✗ ").append(r.signalId).append(": ")
                                .append(r.error != null && r.error.length() > 120
                                        ? r.error.substring(0, 120) + "..." : r.error).append("\n");
                    }
                }

                summary.append("\nDie Historie erscheint GRAU im Chart (Doppelklick auf eine Zeile).");

                gui.getDisplay().asyncExec(() -> {
                    if (!tradesButton.isDisposed()) {
                        tradesButton.setEnabled(true);
                        tradesButton.setText("📜 Trades laden");
                    }
                    gui.updateStatus("Trade-Historie geladen: " + result.successCount + " OK, "
                            + result.errorCount + " Fehler");
                    MessageBox box = new MessageBox(gui.getShell(),
                            (result.errorCount > 0 && result.successCount == 0
                                    ? SWT.ICON_ERROR : SWT.ICON_INFORMATION) | SWT.OK);
                    box.setText("Trades laden");
                    box.setMessage(summary.toString());
                    box.open();
                });

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden der Trade-Historie", ex);
                gui.getDisplay().asyncExec(() -> {
                    if (!tradesButton.isDisposed()) {
                        tradesButton.setEnabled(true);
                        tradesButton.setText("📜 Trades laden");
                    }
                    gui.updateStatus("Trades laden: Fehler");
                    MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_ERROR | SWT.OK);
                    box.setText("Trades laden fehlgeschlagen");
                    box.setMessage("Unerwarteter Fehler:\n\n" + ex.getMessage());
                    box.open();
                });
            }
        }).start();
    }

}
