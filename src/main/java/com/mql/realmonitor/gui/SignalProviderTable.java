package com.mql.realmonitor.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.parser.SignalData;
import com.mql.realmonitor.utils.PeriodProfitCalculator;
import com.mql.realmonitor.data.TickDataLoader;

/**
 * Refactored: Tabelle für die Anzeige der Signalprovider-Daten
 * Kern-Tabellenfunktionen - Kontextmenü und Hilfsfunktionen ausgelagert
 * MODULAR: Verwendet separate Klassen für erweiterte Funktionalität
 * ERWEITERT: Neue Spalte für Favoritenklasse hinzugefügt mit Zeilen-Hintergrundfarben
 * SORTIERUNG: Automatische Sortierung nach Favoritenklasse beim Start
 * NEU: Profit-Spalte zwischen Kontostand und Floating Profit hinzugefügt
 * NEU: WeeklyProfit und MonthlyProfit Spalten hinzugefügt
 * NEU: IdTranslationManager für Provider-Namen beim Start und bei Fehlern
 * KORRIGIERT: Total Value Drawdown für Konsistenz zwischen Chart und Tabelle
 */
public class SignalProviderTable {
    
    private static final Logger LOGGER = Logger.getLogger(SignalProviderTable.class.getName());
    
    // Spalten-Definitionen (KORRIGIERT: Spalte heißt jetzt "Total Value Drawdown")
    private static final String[] COLUMN_TEXTS = {
        "Signal ID", 
        "Favoritenklasse",     // NEU: Spalte 1
        "Provider Name",       // war "Provider Name" 
        "Status",              // war "Status"
        "Kontostand",          // war "Kontostand"
        "Profit",              // NEU: Spalte 5 (zwischen Kontostand und Floating Profit)
        "Floating Profit",     // war "Floating Profit" (verschoben von 5 zu 6)
        "Total Value Drawdown",// KORRIGIERT: war "Equity Drawdown" (verschoben von 6 zu 7)
        "Gesamtwert",          // war "Gesamtwert" (verschoben von 7 zu 8)
        "WeeklyProfit",        // NEU: Spalte 9 (verschoben von 8 zu 9)
        "MonthlyProfit",       // NEU: Spalte 10 (verschoben von 9 zu 10)
        "Währung",             // war "Währung" (verschoben von 10 zu 11)
        "Letzte Aktualisierung", // war "Letzte Aktualisierung" (verschoben von 11 zu 12)
        "Änderung"             // war "Änderung" (verschoben von 12 zu 13)
    };
    
    private static final int[] COLUMN_WIDTHS = {
        80,   // Signal ID
        120,  // Favoritenklasse (NEU)
        150,  // Provider Name
        100,  // Status
        120,  // Kontostand
        100,  // Profit (NEU)
        120,  // Floating Profit (verschoben)
        120,  // Total Value Drawdown (ERWEITERT: breitere Spalte)
        120,  // Gesamtwert (verschoben)
        100,  // WeeklyProfit (verschoben)
        100,  // MonthlyProfit (verschoben)
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
    
    // NEU: IdTranslationManager für Provider-Namen
    private IdTranslationManager idTranslationManager;
    
    // NEU: Peak-Total-Value Cache für konsistente Drawdown-Berechnung
    private Map<String, Double> peakTotalValueCache;
    
    public SignalProviderTable(Composite parent, MqlRealMonitorGUI parentGui) {
        LOGGER.info("=== NEUE SIGNALPROVIDER TABLE MIT TOTAL VALUE DRAWDOWN WIRD GELADEN ===");
        this.parentGui = parentGui;
        this.signalIdToItem = new HashMap<>();
        this.lastSignalData = new HashMap<>();
        this.peakTotalValueCache = new HashMap<>();  // NEU: Peak-Cache initialisieren
        
        // Helfer-Klassen initialisieren
        this.tableHelper = new ProviderTableHelper(parentGui);
        this.contextMenu = new SignalProviderContextMenu(parentGui);
        
        // NEU: IdTranslationManager initialisieren
        try {
            LOGGER.info("=== VERSUCHE ID-TRANSLATION-MANAGER ZU INITIALISIEREN ===");
            this.idTranslationManager = new IdTranslationManager(parentGui.getMonitor().getConfig());
            LOGGER.info("=== ID-TRANSLATION-MANAGER ERFOLGREICH INITIALISIERT ===");
            
            // DEBUG: Test der ID-Translation beim Start
            testIdTranslation();
        } catch (Exception e) {
            LOGGER.severe("=== FEHLER BEIM INITIALISIEREN DES ID-TRANSLATION-MANAGERS ===");
            LOGGER.severe("Fehler: " + e.getMessage());
            e.printStackTrace();
            this.idTranslationManager = null; // Fallback: kein IdTranslationManager
        }
        
        // Callbacks für das Kontextmenü setzen
        setupContextMenuCallbacks();
        
        // Tabelle erstellen
        createTable(parent);
        createColumns();
        
        // Tabellenverhalten mit Kontextmenü konfigurieren
        contextMenu.setupTableBehavior(table);
        
        LOGGER.info("SignalProviderTable (KORRIGIERT - Total Value Drawdown für Chart-Konsistenz) initialisiert mit " + COLUMN_TEXTS.length + " Spalten");
    }
    
    /**
     * DEBUG: Testet die ID-Translation beim Start
     */
    private void testIdTranslation() {
        try {
            LOGGER.info("=== ID-TRANSLATION DEBUG TEST ===");
            
            if (idTranslationManager == null) {
                LOGGER.severe("IdTranslationManager ist null - Test wird übersprungen!");
                return;
            }
            
            LOGGER.info("Translation-Datei-Pfad: " + idTranslationManager.getTranslationFilePath());
            LOGGER.info("Anzahl geladener Zuordnungen: " + idTranslationManager.getMappingCount());
            
            // Teste einige bekannte Signal-IDs aus der idtranslation.txt
            String[] testIds = {"1887334", "2285398", "2296908", "2294111"};
            
            for (String testId : testIds) {
                String name = idTranslationManager.getProviderName(testId);
                LOGGER.info("Test Signal-ID " + testId + " -> " + name);
            }
            
            // Zeige Diagnose-Info
            LOGGER.info("Diagnose-Info:\n" + idTranslationManager.getDiagnosticInfo());
            
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Testen der ID-Translation: " + e.getMessage());
            e.printStackTrace();
        }
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
                peakTotalValueCache.remove(signalId);  // NEU: Auch Peak-Cache leeren
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
        
        LOGGER.info("Tabellenspalten erstellt (KORRIGIERT - Total Value Drawdown): " + java.util.Arrays.toString(COLUMN_TEXTS));
    }
    
    /**
     * NEU: Berechnet den Peak-Total-Value für eine Signal-ID aus den Tick-Daten
     * KONSISTENT MIT CHART: Verwendet die gleiche Peak-Tracking-Logik wie der Chart
     * 
     * @param signalId Die Signal-ID
     * @return Der Peak-Total-Value oder aktueller Total Value als Fallback
     */
    private double calculatePeakTotalValueFromTickData(String signalId, SignalData currentData) {
        try {
            LOGGER.fine("PEAK BERECHNUNG: Starte für Signal " + signalId);
            
            // Prüfe Cache zuerst
            if (peakTotalValueCache.containsKey(signalId)) {
                double cachedPeak = peakTotalValueCache.get(signalId);
                double currentTotalValue = currentData.getTotalValue();
                
                // Aktualisiere Cache falls neuer Peak erreicht
                if (currentTotalValue > cachedPeak) {
                    LOGGER.info("NEUER PEAK ERREICHT für " + signalId + ": " + 
                               String.format("%.6f -> %.6f", cachedPeak, currentTotalValue));
                    peakTotalValueCache.put(signalId, currentTotalValue);
                    return currentTotalValue;
                }
                
                LOGGER.fine("PEAK aus Cache für " + signalId + ": " + String.format("%.6f", cachedPeak));
                return cachedPeak;
            }
            
            // Tick-Datei-Pfad ermitteln
            String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(signalId);
            
            // Prüfen ob Datei existiert
            java.io.File tickFile = new java.io.File(tickFilePath);
            if (!tickFile.exists()) {
                LOGGER.info("PEAK BERECHNUNG: Tick-Datei existiert nicht für " + signalId + " - verwende aktuellen Total Value");
                double currentTotalValue = currentData.getTotalValue();
                peakTotalValueCache.put(signalId, currentTotalValue);
                return currentTotalValue;
            }
            
            // Alle Tick-Daten laden (KORRIGIERT: Statische Methode verwenden)
            TickDataLoader.TickDataSet dataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
            
            if (dataSet == null || dataSet.getTickCount() == 0) {
                LOGGER.info("PEAK BERECHNUNG: Keine Tick-Daten gefunden für " + signalId + " - verwende aktuellen Total Value");
                double currentTotalValue = currentData.getTotalValue();
                peakTotalValueCache.put(signalId, currentTotalValue);
                return currentTotalValue;
            }
            
            // Tick-Daten aus DataSet extrahieren
            List<TickDataLoader.TickData> allTickData = dataSet.getTicks();
            
            if (allTickData.isEmpty()) {
                LOGGER.info("PEAK BERECHNUNG: Keine Tick-Daten gefunden für " + signalId + " - verwende aktuellen Total Value");
                double currentTotalValue = currentData.getTotalValue();
                peakTotalValueCache.put(signalId, currentTotalValue);
                return currentTotalValue;
            }
            
            // KONSISTENT MIT CHART: Peak-Tracking durch alle historischen Daten
            double peakTotalValue = 0.0;
            boolean firstData = true;
            
            // Sortiere chronologisch
            allTickData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
            
            for (TickDataLoader.TickData tick : allTickData) {
                double totalValue = tick.getTotalValue();
                
                if (firstData) {
                    peakTotalValue = totalValue;
                    firstData = false;
                    LOGGER.fine("PEAK INITIALISIERT für " + signalId + ": " + String.format("%.6f", peakTotalValue));
                } else if (totalValue > peakTotalValue) {
                    peakTotalValue = totalValue;
                }
            }
            
            // Auch aktuellen Wert prüfen (falls noch nicht in Datei)
            double currentTotalValue = currentData.getTotalValue();
            if (currentTotalValue > peakTotalValue) {
                peakTotalValue = currentTotalValue;
                LOGGER.fine("PEAK AKTUALISIERT durch aktuellen Wert für " + signalId + ": " + String.format("%.6f", peakTotalValue));
            }
            
            // Cache aktualisieren
            peakTotalValueCache.put(signalId, peakTotalValue);
            
            LOGGER.info("PEAK BERECHNUNG ABGESCHLOSSEN für " + signalId + ": " + 
                       String.format("%.6f", peakTotalValue) + " (aus " + allTickData.size() + " Tick-Datenpunkten)");
            
            return peakTotalValue;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "FEHLER bei Peak-Berechnung für Signal " + signalId + " - verwende aktuellen Total Value als Fallback", e);
            double currentTotalValue = currentData.getTotalValue();
            peakTotalValueCache.put(signalId, currentTotalValue);
            return currentTotalValue;
        }
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
     * NEU: Holt den Provider-Namen für eine Signal-ID aus der ID-Translation
     * 
     * @param signalId Die Signal-ID
     * @return Der Provider-Name oder "Unbekannt" wenn nicht vorhanden
     */
    private String getProviderNameForSignal(String signalId) {
        try {
            LOGGER.info("DEBUG: Suche Provider-Name für Signal-ID: " + signalId);
            
            // Fallback wenn IdTranslationManager nicht verfügbar
            if (idTranslationManager == null) {
                LOGGER.warning("DEBUG: IdTranslationManager ist null - verwende Fallback");
                return "Unbekannt";
            }
            
            String providerName = idTranslationManager.getProviderName(signalId);
            LOGGER.info("DEBUG: Provider-Name für Signal " + signalId + " aus ID-Translation: '" + providerName + "'");
            return providerName;
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Abrufen des Provider-Namens für Signal " + signalId + " aus ID-Translation: " + e.getMessage());
            e.printStackTrace();
            return "Unbekannt";
        }
    }
    
    /**
     * NEU: Speichert oder aktualisiert einen Provider-Namen in der ID-Translation
     * 
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     */
    private void saveProviderNameToTranslation(String signalId, String providerName) {
        try {
            // Nur gültige Provider-Namen speichern (nicht "Lädt...", "Unbekannt", etc.)
            if (providerName != null && !providerName.trim().isEmpty() && 
                !providerName.equals("Lädt...") && !providerName.equals("Unbekannt") &&
                !providerName.equals("Keine Daten") && !providerName.equals("Fehler")) {
                
                boolean isNew = idTranslationManager.addOrUpdateMapping(signalId, providerName);
                if (isNew) {
                    LOGGER.info("Neuer Provider-Name in ID-Translation gespeichert: " + signalId + " -> " + providerName);
                } else {
                    LOGGER.fine("Provider-Name in ID-Translation aktualisiert: " + signalId + " -> " + providerName);
                }
            } else {
                LOGGER.fine("Provider-Name nicht für ID-Translation geeignet: " + signalId + " -> " + providerName);
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Speichern des Provider-Namens in ID-Translation: " + signalId + " -> " + providerName + " - " + e.getMessage());
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
     * KORRIGIERT: Verwendet jetzt Total Value Drawdown für Konsistenz mit Chart
     * ERWEITERT: Setzt auch die Favoritenklasse, Zeilen-Hintergrundfarbe, neue Profit-Spalte und berechnet Profit-Werte
     * NEU: Speichert neue Provider-Namen in der ID-Translation
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
        
        // *** KORRIGIERT: Provider-Name aus ID-Translation bevorzugen ***
        String providerName;
        
        // Erst versuchen aus ID-Translation zu holen
        String nameFromTranslation = getProviderNameForSignal(signalId);
        if (nameFromTranslation != null && !nameFromTranslation.equals("Unbekannt") && !nameFromTranslation.trim().isEmpty()) {
            // ID-Translation hat einen gültigen Namen - verwende diesen
            providerName = nameFromTranslation;
            LOGGER.info("Verwende Provider-Name aus ID-Translation für " + signalId + ": " + providerName);
        } else {
            // Fallback: Verwende Namen aus SignalData (HTML-Parsing)
            providerName = signalData.getProviderName();
            LOGGER.info("Verwende Provider-Name aus SignalData für " + signalId + ": " + providerName + " (ID-Translation nicht verfügbar)");
            
            // Namen in ID-Translation speichern für nächstes Mal
            saveProviderNameToTranslation(signalId, providerName);
        }
        
        // NEU: Profit-Werte berechnen
        PeriodProfitCalculator.ProfitResult profitResult = calculateProfitsForSignal(signalId);
        
        // KORRIGIERT: Total Value Drawdown berechnen (KONSISTENT MIT CHART)
        double peakTotalValue = calculatePeakTotalValueFromTickData(signalId, signalData);
        String totalValueDrawdown = signalData.getFormattedTotalValueDrawdown(peakTotalValue);
        
        LOGGER.info("TOTAL VALUE DRAWDOWN für Tabelle " + signalId + ": " + totalValueDrawdown + 
                   " (Peak: " + String.format("%.6f", peakTotalValue) + 
                   ", Current: " + String.format("%.6f", signalData.getTotalValue()) + ")");
        
        // Tabellendaten setzen - KORRIGIERT: Verwende bevorzugten Provider-Namen und Total Value Drawdown
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_FAVORITE_CLASS, favoriteClass);                     
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, providerName);  // ← KORRIGIERT: Bevorzugte Namen
        item.setText(ProviderTableHelper.COL_STATUS, "OK");
        item.setText(ProviderTableHelper.COL_EQUITY, signalData.getFormattedEquity());
        item.setText(ProviderTableHelper.COL_PROFIT, signalData.getFormattedProfit());
        item.setText(ProviderTableHelper.COL_FLOATING, signalData.getFormattedFloatingProfit());
        item.setText(ProviderTableHelper.COL_TOTAL_VALUE_DRAWDOWN, totalValueDrawdown);  // ← KORRIGIERT: Total Value Drawdown!
        item.setText(ProviderTableHelper.COL_TOTAL, signalData.getFormattedTotalValue());
        item.setText(ProviderTableHelper.COL_WEEKLY_PROFIT, profitResult.getFormattedWeeklyProfit());
        item.setText(ProviderTableHelper.COL_MONTHLY_PROFIT, profitResult.getFormattedMonthlyProfit());
        item.setText(ProviderTableHelper.COL_CURRENCY, signalData.getCurrency());
        item.setText(ProviderTableHelper.COL_LAST_UPDATE, signalData.getFormattedTimestamp());
        item.setText(ProviderTableHelper.COL_CHANGE, changeText);
        
        // Farben setzen über Helper
        item.setForeground(ProviderTableHelper.COL_PROFIT, tableHelper.getProfitColor(signalData.getProfit()));
        item.setForeground(ProviderTableHelper.COL_FLOATING, tableHelper.getFloatingProfitColor(signalData.getFloatingProfit()));
        
        // KORRIGIERT: Verwende Total Value Drawdown für Farbbestimmung und neue Konstante
        double totalValueDrawdownPercent = signalData.getTotalValueDrawdownPercent(peakTotalValue);
        item.setForeground(ProviderTableHelper.COL_TOTAL_VALUE_DRAWDOWN, tableHelper.getTotalValueDrawdownColor(totalValueDrawdownPercent));
        
        item.setForeground(ProviderTableHelper.COL_CHANGE, changeColor);
        
        // Farben für Profit-Spalten setzen
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
        
        // Zeilen-Hintergrundfarbe basierend auf Favoritenklasse setzen
        Color backgroundColor = tableHelper.getFavoriteClassBackgroundColor(favoriteClass);
        if (backgroundColor != null) {
            item.setBackground(backgroundColor);
            LOGGER.fine("Hintergrundfarbe gesetzt für Signal " + signalId + " (Klasse " + favoriteClass + ")");
        }
        
        // Daten im Cache speichern
        lastSignalData.put(signalId, signalData);
        
        LOGGER.fine("Provider-Daten aktualisiert (KORRIGIERT - Total Value Drawdown konsistent mit Chart): " + signalData.getSummary() + 
                   " (Klasse: " + favoriteClass + ", Name: " + providerName + 
                   ", Total Value Drawdown: " + totalValueDrawdown +
                   ", WeeklyProfit: " + profitResult.getFormattedWeeklyProfit() + 
                   ", MonthlyProfit: " + profitResult.getFormattedMonthlyProfit() + ")");
    }
    
    /**
     * Aktualisiert den Status eines Providers
     * NEU: Verwendet Provider-Namen aus ID-Translation auch bei Fehlern
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
        
        // NEU: Bei Fehlerstatus Provider-Namen aus ID-Translation aktualisieren falls verfügbar
        if (status != null && (status.toLowerCase().contains("fehler") || status.toLowerCase().contains("error"))) {
            String knownProviderName = getProviderNameForSignal(signalId);
            if (!knownProviderName.equals("Unbekannt")) {
                item.setText(ProviderTableHelper.COL_PROVIDER_NAME, knownProviderName);
                LOGGER.info("Provider-Name aus ID-Translation bei Fehler gesetzt: " + signalId + " -> " + knownProviderName);
            }
        }
        
        // Status-Farbe über Helper setzen
        Color statusColor = tableHelper.getStatusColor(status);
        item.setForeground(ProviderTableHelper.COL_STATUS, statusColor);
        
        LOGGER.fine("Provider-Status aktualisiert: " + signalId + " -> " + status);
    }
    
    /**
     * Fügt einen leeren Provider-Eintrag hinzu (nur mit Signal-ID)
     * Wird beim Vorladen der Favoriten verwendet
     * ERWEITERT: Setzt auch die Favoritenklasse und Zeilen-Hintergrundfarbe, Profit-Spalten bleiben leer
     * NEU: Verwendet Provider-Namen aus ID-Translation statt "Lädt..."
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
        
        // NEU: Provider-Namen aus ID-Translation holen statt "Lädt..."
        String providerName = getProviderNameForSignal(signalId);
        LOGGER.info("DEBUG: Provider-Name für Signal " + signalId + " aus ID-Translation: '" + providerName + "'");
        if (providerName.equals("Unbekannt")) {
            providerName = "Lädt..."; // Fallback nur wenn wirklich unbekannt
            LOGGER.info("DEBUG: Verwende Fallback 'Lädt...' für Signal " + signalId);
        } else {
            LOGGER.info("DEBUG: Verwende Namen aus ID-Translation für Signal " + signalId + ": " + providerName);
        }
        
        // Neuen leeren Eintrag erstellen
        TableItem item = new TableItem(table, SWT.NONE);
        
        // Nur Signal-ID, Favoritenklasse, Provider-Name und Status setzen, Rest bleibt leer (ERWEITERT: Total Value Drawdown Spalte)
        item.setText(ProviderTableHelper.COL_SIGNAL_ID, signalId);
        item.setText(ProviderTableHelper.COL_FAVORITE_CLASS, favoriteClass);                          
        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, providerName);   // NEU: Aus ID-Translation
        item.setText(ProviderTableHelper.COL_STATUS, initialStatus != null ? initialStatus : "Nicht geladen");
        item.setText(ProviderTableHelper.COL_EQUITY, "");
        item.setText(ProviderTableHelper.COL_PROFIT, "");               // NEU: Leer lassen bis Daten verfügbar
        item.setText(ProviderTableHelper.COL_FLOATING, "");
        item.setText(ProviderTableHelper.COL_TOTAL_VALUE_DRAWDOWN, "");      // KORRIGIERT: Total Value Drawdown
        item.setText(ProviderTableHelper.COL_TOTAL, "");
        item.setText(ProviderTableHelper.COL_WEEKLY_PROFIT, "");        // NEU: Leer lassen bis Daten verfügbar
        item.setText(ProviderTableHelper.COL_MONTHLY_PROFIT, "");       // NEU: Leer lassen bis Daten verfügbar
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
        
        LOGGER.fine("Leerer Provider-Eintrag hinzugefügt (KORRIGIERT - Total Value Drawdown): " + signalId + 
                   " (Klasse: " + favoriteClass + ", Name: " + providerName + ")");
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
     * NEU: Aktualisiert die Provider-Namen aller Provider aus der ID-Translation
     * Nützlich wenn neue Namen in der ID-Translation verfügbar sind
     */
    public void refreshProviderNames() {
        LOGGER.info("Aktualisiere Provider-Namen für alle Provider aus ID-Translation...");
        
        // Cache der ID-Translation neu laden
        idTranslationManager.refreshCache();
        
        for (Map.Entry<String, TableItem> entry : signalIdToItem.entrySet()) {
            String signalId = entry.getKey();
            TableItem item = entry.getValue();
            
            if (item != null && !item.isDisposed()) {
                String currentName = item.getText(ProviderTableHelper.COL_PROVIDER_NAME);
                
                // Nur aktualisieren wenn aktuell "Lädt..." oder "Unbekannt" angezeigt wird
                if (currentName.equals("Lädt...") || currentName.equals("Unbekannt")) {
                    String knownName = getProviderNameForSignal(signalId);
                    if (!knownName.equals("Unbekannt") && !knownName.equals(currentName)) {
                        item.setText(ProviderTableHelper.COL_PROVIDER_NAME, knownName);
                        LOGGER.info("Provider-Name aus ID-Translation aktualisiert: " + signalId + " -> " + knownName);
                    }
                }
            }
        }
        
        LOGGER.info("Provider-Namen-Aktualisierung abgeschlossen");
    }
    
    /**
     * NEU: Leert den Peak-Cache (nützlich bei Problemen oder für Debugging)
     */
    public void clearPeakCache() {
        LOGGER.info("Leere Peak-Total-Value-Cache für alle Signale...");
        peakTotalValueCache.clear();
        LOGGER.info("Peak-Cache geleert - Peaks werden bei nächstem Update neu berechnet");
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
        peakTotalValueCache.clear();  // NEU: Auch Peak-Cache leeren
        
        // FavoritesReader Cache leeren
        if (favoritesReader != null) {
            favoritesReader.refreshCache();
        }
        
        LOGGER.info("Alle Provider aus Tabelle entfernt (KORRIGIERT - Total Value Drawdown für Chart-Konsistenz)");
    }
    
    /**
     * NEU: Gibt den IdTranslationManager zurück
     * 
     * @return Der IdTranslationManager
     */
    public IdTranslationManager getIdTranslationManager() {
        return idTranslationManager;
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