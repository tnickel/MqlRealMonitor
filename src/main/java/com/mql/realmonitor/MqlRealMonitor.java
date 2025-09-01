package com.mql.realmonitor;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.currency.CurrencyDataLoader;
import com.mql.realmonitor.downloader.WebDownloader;
import com.mql.realmonitor.downloader.FavoritesReader;
import com.mql.realmonitor.parser.HTMLParser;
import com.mql.realmonitor.parser.SignalData;
import com.mql.realmonitor.tickdata.TickDataWriter;
import com.mql.realmonitor.gui.MqlRealMonitorGUI;
import com.mql.realmonitor.utils.MqlUtils;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * VERBESSERT: Hauptklasse für MqlRealMonitor mit korrektem Logging für Diagnostik
 * Orchestriert Download, Parsing und GUI-Updates für MQL5 Signalprovider-Monitoring
 * ALLE CHART-PROBLEME BEHOBEN: Robuste Diagnostik und Fehlerbehandlung aktiviert
 * NEU: Unterstützt konfigurierbaren BASE_PATH über Kommandozeilen-Parameter
 * ERWEITERT: Automatisches Currency Loading nach Signalprovider-Laden
 */
public class MqlRealMonitor {
    
    private static final Logger LOGGER = Logger.getLogger(MqlRealMonitor.class.getName());
    
    private MqlRealMonitorConfig config;
    private WebDownloader downloader;
    private HTMLParser htmlParser;
    private TickDataWriter tickDataWriter;
    private MqlRealMonitorGUI gui;
    private FavoritesReader favoritesReader;
    private ScheduledExecutorService scheduler;
    private volatile boolean isRunning = false;
    
    // NEU: Currency Data Loader für automatisches Kurse-Laden
    private CurrencyDataLoader currencyDataLoader;
    
    /**
     * Standard-Konstruktor mit Default-Pfad
     */
    public MqlRealMonitor() {
        this(null);
    }
    
    /**
     * NEU: Konstruktor mit konfigurierbarem BASE_PATH
     * 
     * @param basePath Der zu verwendende Basis-Pfad oder null für Standard
     */
    public MqlRealMonitor(String basePath) {
        initializeComponents(basePath);
    }
    
    /**
     * VERBESSERT: Initialisiert alle Komponenten des MqlRealMonitor mit korrektem Logging
     * NEU: Unterstützt konfigurierbaren BASE_PATH
     * ERWEITERT: Initialisiert auch CurrencyDataLoader
     * 
     * @param basePath Der zu verwendende Basis-Pfad oder null für Standard
     */
    private void initializeComponents(String basePath) {
        try {
            // KRITISCH: Logging-Level für umfassende Diagnostik setzen
            setupDiagnosticLogging();
            
            LOGGER.info("=== MQL REAL MONITOR INITIALISIERUNG (VERBESSERT MIT CURRENCY) ===");
            LOGGER.info("Aktiviere umfassende Diagnostik für Chart-Probleme...");
            
            // NEU: Konfiguration mit optionalem BASE_PATH laden
            if (basePath != null && !basePath.trim().isEmpty()) {
                LOGGER.info("Verwende benutzerdefinierten BASE_PATH: " + basePath);
                config = new MqlRealMonitorConfig(basePath.trim());
            } else {
                LOGGER.info("Verwende Standard-BASE_PATH");
                config = new MqlRealMonitorConfig();
            }
            config.loadConfig();
            
            // Komponenten initialisieren
            downloader = new WebDownloader(config);
            htmlParser = new HTMLParser();
            tickDataWriter = new TickDataWriter(config);
            favoritesReader = new FavoritesReader(config);
            
            // NEU: Currency Data Loader initialisieren
            initializeCurrencyDataLoader();
            
            // GUI initialisieren
            gui = new MqlRealMonitorGUI(this);
            
            // Scheduler für automatische Updates
            scheduler = Executors.newSingleThreadScheduledExecutor();
            
            LOGGER.info("Alle Komponenten erfolgreich initialisiert - Diagnostik aktiviert");
            LOGGER.info("Aktuelle Konfiguration:");
            LOGGER.info("  BASE_PATH: " + config.getBasePath());
            LOGGER.info("  Favoriten-Datei: " + config.getFavoritesFile());
            LOGGER.info("  Currency Loading: " + (currencyDataLoader != null ? "AKTIVIERT" : "DEAKTIVIERT"));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler bei der Initialisierung", e);
            throw new RuntimeException("Initialisierung fehlgeschlagen", e);
        }
    }
    
    /**
     * NEU: Initialisiert den CurrencyDataLoader für automatisches Kurse-Laden
     */
    private void initializeCurrencyDataLoader() {
        try {
            LOGGER.info("Initialisiere CurrencyDataLoader...");
            currencyDataLoader = new CurrencyDataLoader(config);
            LOGGER.info("CurrencyDataLoader erfolgreich initialisiert");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CurrencyDataLoader konnte nicht initialisiert werden: " + e.getMessage(), e);
            currencyDataLoader = null;
            LOGGER.warning("Currency Loading wird nicht verfügbar sein");
        }
    }
    
    /**
     * NEU: Setzt das Logging-Level für umfassende Chart-Diagnostik
     */
    private void setupDiagnosticLogging() {
        try {
            // Root Logger Level auf INFO setzen für umfassende Diagnostik
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);
            
            // Spezielle Logger für Chart-Komponenten auf INFO Level setzen
            Logger.getLogger("com.mql.realmonitor.gui.TickChartManager").setLevel(Level.INFO);
            Logger.getLogger("com.mql.realmonitor.gui.TickDataFilter").setLevel(Level.INFO);
            Logger.getLogger("com.mql.realmonitor.gui.TickChartWindow").setLevel(Level.INFO);
            Logger.getLogger("com.mql.realmonitor.data.TickDataLoader").setLevel(Level.INFO);
            
            // Console Handler auch auf INFO Level setzen
            for (var handler : rootLogger.getHandlers()) {
                handler.setLevel(Level.INFO);
            }
            
            LOGGER.info("Diagnostik-Logging aktiviert für Chart-Debugging");
            
        } catch (Exception e) {
            System.err.println("Warnung: Konnte Logging-Level nicht setzen: " + e.getMessage());
        }
    }
    
    /**
     * Startet den Monitor-Prozess
     */
    public void startMonitoring() {
        if (isRunning) {
            LOGGER.warning("Monitoring läuft bereits");
            return;
        }
        
        isRunning = true;
        LOGGER.info("=== STARTE MQL5 SIGNAL MONITORING (VERBESSERT MIT CURRENCY) ===");
        
        // Ersten Download sofort starten
        scheduler.submit(this::performMonitoringCycle);
        
        // Wiederkehrende Downloads planen
        long intervalMinutes = config.getIntervalMinutes();
        scheduler.scheduleAtFixedRate(
            this::performMonitoringCycle,
            intervalMinutes,
            intervalMinutes,
            TimeUnit.MINUTES
        );
        
        gui.updateStatus("Monitoring gestartet (DIAGNOSEMODUS + CURRENCY) - Intervall: " + config.getIntervalMinutes() + " Minuten");
        LOGGER.info("Monitoring erfolgreich gestartet mit Intervall: " + intervalMinutes + " Minuten");
    }
    
    /**
     * Stoppt den Monitor-Prozess
     */
    public void stopMonitoring() {
        if (!isRunning) {
            LOGGER.warning("Monitoring läuft nicht");
            return;
        }
        
        isRunning = false;
        LOGGER.info("=== STOPPE MQL5 SIGNAL MONITORING ===");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        gui.updateStatus("Monitoring gestoppt");
        LOGGER.info("Monitoring erfolgreich gestoppt");
    }
    
    /**
     * ERWEITERT: Führt einen vollständigen Monitoring-Zyklus durch
     * NEU: Lädt automatisch Währungskurse nach dem Laden aller Signalprovider
     */
    private void performMonitoringCycle() {
        try {
            LOGGER.info("=== MONITORING-ZYKLUS START (MIT CURRENCY LOADING) ===");
            gui.updateStatus("Lade Favoriten...");
            
            // Favoriten laden
            List<String> favoriteIds = favoritesReader.readFavorites();
            if (favoriteIds.isEmpty()) {
                LOGGER.warning("Keine Favoriten gefunden");
                gui.updateStatus("Keine Favoriten gefunden");
                return;
            }
            
            LOGGER.info("Gefundene Favoriten: " + favoriteIds.size());
            gui.updateStatus("Starte Downloads für " + favoriteIds.size() + " Provider...");
            
            // SCHRITT 1: Downloads für alle Signalprovider durchführen
            for (int i = 0; i < favoriteIds.size(); i++) {
                String id = favoriteIds.get(i);
                
                try {
                    gui.updateStatus("Download " + (i + 1) + "/" + favoriteIds.size() + ": " + id);
                    gui.updateProviderStatus(id, "Downloading...");
                    
                    // HTML herunterladen
                    String url = "https://www.mql5.com/en/signals/" + id;  // oder /de/, falls gewünscht
                    String htmlContent = downloader.downloadSignalPage(id, url);
                    
                    if (htmlContent != null && !htmlContent.trim().isEmpty()) {
                        // HTML parsen
                        SignalData signalData = htmlParser.parseSignalData(htmlContent, id);
                        
                        if (signalData != null && signalData.isValid()) {
                            // Tick-Daten speichern
                            tickDataWriter.writeTickData(signalData);
                            
                            // GUI aktualisieren
                            gui.updateProviderData(signalData);
                            gui.updateProviderStatus(id, "OK - " + signalData.getTimestamp());
                            
                            LOGGER.info("Erfolgreich verarbeitet: " + id + 
                                       " - Kontostand: " + signalData.getEquity() + 
                                       " - Floating: " + signalData.getFloatingProfit() +
                                       " - Drawdown: " + signalData.getFormattedEquityDrawdown());
                        } else {
                            gui.updateProviderStatus(id, "Parse Error");
                            LOGGER.warning("Parse-Fehler für ID: " + id);
                        }
                    } else {
                        gui.updateProviderStatus(id, "Download Error");
                        LOGGER.warning("Download-Fehler für ID: " + id);
                    }
                    
                } catch (Exception e) {
                    gui.updateProviderStatus(id, "Error: " + e.getMessage());
                    LOGGER.log(Level.WARNING, "Fehler bei Verarbeitung von ID: " + id, e);
                }
                
                // Random Sleep zwischen Downloads
                if (i < favoriteIds.size() - 1) {
                    Thread.sleep(1000 + (int)(Math.random() * 2000)); // 1-3 Sekunden
                }
            }
            
            // SCHRITT 2: NEU - Automatisches Currency Loading nach allen Signalprovidernale
            performAutomaticCurrencyLoading();
            
            gui.updateStatus("Monitoring-Zyklus abgeschlossen - Nächster in " + 
                           config.getIntervalMinutes() + " Minuten");
            LOGGER.info("=== MONITORING-ZYKLUS ERFOLGREICH ABGESCHLOSSEN (INKL. CURRENCY) ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler im Monitoring-Zyklus", e);
            gui.updateStatus("Fehler: " + e.getMessage());
        }
    }
    
    /**
     * NEU: Führt automatisches Currency Loading nach dem Laden aller Signalprovider durch
     */
    private void performAutomaticCurrencyLoading() {
        // Prüfen ob Currency Loading verfügbar ist
        if (currencyDataLoader == null) {
            LOGGER.info("Currency Loading übersprungen - CurrencyDataLoader nicht verfügbar");
            return;
        }
        
        try {
            LOGGER.info("=== AUTOMATISCHES CURRENCY LOADING START ===");
            gui.updateStatus("Lade Währungskurse von MQL5...");
            
            // Currency Loading im aktuellen Thread (bereits im Background)
            String diagnosis = currencyDataLoader.loadCurrencyRatesWithDiagnosis();
            
            // Erfolg prüfen
            boolean success = !diagnosis.toLowerCase().contains("fehler");
            
            if (success) {
                LOGGER.info("Automatisches Currency Loading erfolgreich abgeschlossen");
                gui.updateStatus("Währungskurse erfolgreich geladen");
            } else {
                LOGGER.warning("Automatisches Currency Loading mit Fehlern: " + diagnosis);
                gui.updateStatus("Currency Loading: Teilweise erfolgreich");
            }
            
            // Kurze Diagnose loggen (ohne UI-Dialog da automatisch)
            LOGGER.info("Currency Diagnose: " + diagnosis.replaceAll("\n", " | "));
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim automatischen Currency Loading: " + e.getMessage(), e);
            gui.updateStatus("Currency Loading: Fehler aufgetreten");
        }
        
        LOGGER.info("=== AUTOMATISCHES CURRENCY LOADING ENDE ===");
    }
    
    /**
     * Führt manuellen Refresh durch
     */
    public void manualRefresh() {
        if (isRunning) {
            LOGGER.info("Manueller Refresh angefordert (Monitoring läuft)");
            scheduler.submit(this::performMonitoringCycle);
        } else {
            LOGGER.info("Einmaliger manueller Refresh (Monitoring gestoppt)");
            new Thread(this::performMonitoringCycle).start();
        }
    }
    
    /**
     * Gibt die aktuelle Konfiguration zurück
     */
    public MqlRealMonitorConfig getConfig() {
        return config;
    }
    
    /**
     * NEU: Gibt den CurrencyDataLoader zurück
     */
    public CurrencyDataLoader getCurrencyDataLoader() {
        return currencyDataLoader;
    }
    
    /**
     * Prüft ob Monitoring läuft
     */
    public boolean isMonitoringActive() {
        return isRunning;
    }
    
    /**
     * Beendet das Programm ordnungsgemäß
     */
    public void shutdown() {
        LOGGER.info("=== BEENDE MQL REAL MONITOR ===");
        stopMonitoring();
        
        // Currency Data Loader bereinigen
        if (currencyDataLoader != null) {
            try {
                currencyDataLoader.cleanup();
                LOGGER.info("CurrencyDataLoader bereinigt");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Fehler beim Bereinigen des CurrencyDataLoader", e);
            }
        }
        
        if (gui != null) {
            gui.dispose();
        }
        
        LOGGER.info("MqlRealMonitor ordnungsgemäß beendet");
    }
    
    /**
     * NEU: Parst Kommandozeilen-Argumente
     * 
     * @param args Die Kommandozeilen-Argumente
     * @return Der extrahierte BASE_PATH oder null wenn nicht angegeben
     */
    private static String parseCommandLineArgs(String[] args) {
        String basePath = null;
        
        if (args == null || args.length == 0) {
            LOGGER.info("Keine Kommandozeilen-Parameter übergeben");
            return null;
        }
        
        LOGGER.info("Verarbeite Kommandozeilen-Parameter: " + java.util.Arrays.toString(args));
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            // --base-path oder -b Parameter
            if (("--base-path".equals(arg) || "-b".equals(arg)) && i + 1 < args.length) {
                basePath = args[i + 1];
                LOGGER.info("BASE_PATH aus Parameter: " + basePath);
                i++; // Nächsten Parameter überspringen (ist der Wert)
            }
            // --base-path=VALUE Format
            else if (arg.startsWith("--base-path=")) {
                basePath = arg.substring("--base-path=".length());
                LOGGER.info("BASE_PATH aus Parameter (= Format): " + basePath);
            }
            // Direkter Pfad als erstes Argument (legacy)
            else if (i == 0 && !arg.startsWith("-")) {
                basePath = arg;
                LOGGER.info("BASE_PATH als erstes Argument: " + basePath);
            }
            // Hilfe anzeigen
            else if ("--help".equals(arg) || "-h".equals(arg)) {
                showUsageAndExit();
            }
            // Unbekannter Parameter
            else if (arg.startsWith("-")) {
                LOGGER.warning("Unbekannter Parameter: " + arg);
                System.err.println("Warnung: Unbekannter Parameter: " + arg);
                System.err.println("Verwende --help für Hilfe");
            }
        }
        
        return basePath;
    }
    
    /**
     * NEU: Zeigt die Verwendung und beendet das Programm
     */
    private static void showUsageAndExit() {
        System.out.println("MQL Real Monitor - MQL5 Signal Provider Monitor");
        System.out.println();
        System.out.println("Verwendung:");
        System.out.println("  java -jar MqlRealMonitor.jar [OPTIONS]");
        System.out.println();
        System.out.println("Optionen:");
        System.out.println("  --base-path <pfad>   Setzt den Basis-Pfad für alle Dateien");
        System.out.println("  -b <pfad>            Kurz-Version von --base-path");
        System.out.println("  --base-path=<pfad>   Alternative Syntax für BASE_PATH");
        System.out.println("  --help               Zeigt diese Hilfe");
        System.out.println("  -h                   Kurz-Version von --help");
        System.out.println();
        System.out.println("Beispiele:");
        System.out.println("  java -jar MqlRealMonitor.jar --base-path \"C:\\Forex\\MyMql\"");
        System.out.println("  java -jar MqlRealMonitor.jar -b \"D:\\Trading\\MqlMonitor\"");
        System.out.println("  java -jar MqlRealMonitor.jar --base-path=\"/home/user/mql\"");
        System.out.println("  java -jar MqlRealMonitor.jar \"C:\\Forex\\MqlAnalyzer\" (Legacy)");
        System.out.println();
        System.out.println("Standard BASE_PATH: C:\\Forex\\MqlAnalyzer");
        System.out.println();
        System.out.println("Der BASE_PATH wird für folgende Unterverzeichnisse verwendet:");
        System.out.println("  config\\      - Konfigurationsdateien (favorites.txt, etc.)");
        System.out.println("  Realtick\\download\\  - Heruntergeladene HTML-Dateien");
        System.out.println("  Realtick\\tick\\      - Tick-Daten (CSV-Dateien)");
        System.out.println("  Realtick\\tick_kurse\\ - Währungskurse (XAUUSD, BTCUSD)");
        
        System.exit(0);
    }
    
    /**
     * NEU: Validiert den übergebenen BASE_PATH
     * 
     * @param basePath Der zu validierende BASE_PATH
     * @return true wenn gültig oder null, false wenn ungültig
     */
    private static boolean validateBasePath(String basePath) {
        if (basePath == null) {
            return true; // null ist OK (Standard wird verwendet)
        }
        
        String trimmed = basePath.trim();
        if (trimmed.isEmpty()) {
            LOGGER.severe("BASE_PATH ist leer");
            return false;
        }
        
        // Prüfe auf ungültige Zeichen (einfache Prüfung)
        if (trimmed.contains("\"") || trimmed.contains("*") || trimmed.contains("?") || trimmed.contains("<") || trimmed.contains(">") || trimmed.contains("|")) {
            LOGGER.severe("BASE_PATH enthält ungültige Zeichen: " + trimmed);
            return false;
        }
        
        try {
            // Prüfe ob Pfad erstellt werden kann
            java.nio.file.Path path = java.nio.file.Paths.get(trimmed);
            if (!java.nio.file.Files.exists(path)) {
                LOGGER.info("BASE_PATH existiert noch nicht, wird bei Bedarf erstellt: " + trimmed);
            } else {
                LOGGER.info("BASE_PATH existiert bereits: " + trimmed);
            }
            return true;
        } catch (Exception e) {
            LOGGER.severe("BASE_PATH ist ungültig: " + trimmed + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * VERBESSERT: Hauptmethode - Startet das MqlRealMonitor GUI mit Diagnostik
     * NEU: Unterstützt BASE_PATH Kommandozeilen-Parameter
     * 
     * @param args Kommandozeilen-Argumente: [--base-path <pfad>] [--help]
     */
    public static void main(String[] args) {
        try {
            // Initialisiere Logging mit File-Logging für bessere Diagnostik
            MqlUtils.initializeLogging(Level.INFO, true);
            
            LOGGER.info("=== STARTE MQL REAL MONITOR (VERBESSERTE VERSION MIT CURRENCY) ===");
            LOGGER.info("Version: " + MqlUtils.getVersion());
            LOGGER.info("Diagnostik-Modus: AKTIVIERT für Chart-Debugging");
            LOGGER.info("Currency Loading: AKTIVIERT für automatisches Kurse-Laden");
            LOGGER.info("Kommandozeilen-Argumente: " + java.util.Arrays.toString(args));
            
            // NEU: Kommandozeilen-Parameter parsen
            String basePath = parseCommandLineArgs(args);
            
            // BASE_PATH validieren
            if (!validateBasePath(basePath)) {
                System.err.println("FEHLER: Ungültiger BASE_PATH übergeben!");
                System.err.println("Verwende --help für Hilfe");
                System.exit(1);
            }
            
            // Monitor mit optionalem BASE_PATH starten
            MqlRealMonitor monitor = new MqlRealMonitor(basePath);
            
            // Konfigurations-Zusammenfassung loggen
            LOGGER.info("=== KONFIGURATIONSZUSAMMENFASSUNG ===");
            LOGGER.info(monitor.getConfig().getConfigSummary());
            LOGGER.info("=====================================");
            
            // GUI starten
            monitor.gui.open();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fataler Fehler beim Start", e);
            System.err.println("KRITISCHER FEHLER beim Start von MqlRealMonitor:");
            e.printStackTrace();
            System.err.println();
            System.err.println("Mögliche Ursachen:");
            System.err.println("- Ungültiger BASE_PATH angegeben");
            System.err.println("- Keine Berechtigung zum Erstellen von Verzeichnissen");
            System.err.println("- Konfigurationsfehler");
            System.err.println();
            System.err.println("Verwende --help für Hilfe");
            System.exit(1);
        }
    }
}