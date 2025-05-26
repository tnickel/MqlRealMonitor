package com.mql.realmonitor;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
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
    
    public MqlRealMonitor() {
        initializeComponents();
    }
    
    /**
     * VERBESSERT: Initialisiert alle Komponenten des MqlRealMonitor mit korrektem Logging
     */
    private void initializeComponents() {
        try {
            // KRITISCH: Logging-Level für umfassende Diagnostik setzen
            setupDiagnosticLogging();
            
            LOGGER.info("=== MQL REAL MONITOR INITIALISIERUNG (VERBESSERT) ===");
            LOGGER.info("Aktiviere umfassende Diagnostik für Chart-Probleme...");
            
            // Konfiguration laden
            config = new MqlRealMonitorConfig();
            config.loadConfig();
            
            // Komponenten initialisieren
            downloader = new WebDownloader(config);
            htmlParser = new HTMLParser();
            tickDataWriter = new TickDataWriter(config);
            favoritesReader = new FavoritesReader(config);
            
            // GUI initialisieren
            gui = new MqlRealMonitorGUI(this);
            
            // Scheduler für automatische Updates
            scheduler = Executors.newSingleThreadScheduledExecutor();
            
            LOGGER.info("Alle Komponenten erfolgreich initialisiert - Diagnostik aktiviert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler bei der Initialisierung", e);
            throw new RuntimeException("Initialisierung fehlgeschlagen", e);
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
        LOGGER.info("=== STARTE MQL5 SIGNAL MONITORING (VERBESSERT) ===");
        
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
        
        gui.updateStatus("Monitoring gestartet (DIAGNOSEMODUS) - Intervall: " + config.getIntervalMinutes() + " Minuten");
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
     * Führt einen vollständigen Monitoring-Zyklus durch
     */
    private void performMonitoringCycle() {
        try {
            LOGGER.info("=== MONITORING-ZYKLUS START ===");
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
            
            // Downloads durchführen
            for (int i = 0; i < favoriteIds.size(); i++) {
                String id = favoriteIds.get(i);
                
                try {
                    gui.updateStatus("Download " + (i + 1) + "/" + favoriteIds.size() + ": " + id);
                    gui.updateProviderStatus(id, "Downloading...");
                    
                    // HTML herunterladen
                    String htmlContent = downloader.downloadSignalPage(id);
                    
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
            
            gui.updateStatus("Monitoring-Zyklus abgeschlossen - Nächster in " + 
                           config.getIntervalMinutes() + " Minuten");
            LOGGER.info("=== MONITORING-ZYKLUS ERFOLGREICH ABGESCHLOSSEN ===");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler im Monitoring-Zyklus", e);
            gui.updateStatus("Fehler: " + e.getMessage());
        }
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
        
        if (gui != null) {
            gui.dispose();
        }
        
        LOGGER.info("MqlRealMonitor ordnungsgemäß beendet");
    }
    
    /**
     * VERBESSERT: Hauptmethode - Startet das MqlRealMonitor GUI mit Diagnostik
     */
    public static void main(String[] args) {
        try {
            // Initialisiere Logging mit File-Logging für bessere Diagnostik
            MqlUtils.initializeLogging(Level.INFO, true);
            
            LOGGER.info("=== STARTE MQL REAL MONITOR (VERBESSERTE VERSION) ===");
            LOGGER.info("Version: " + MqlUtils.getVersion());
            LOGGER.info("Diagnostik-Modus: AKTIVIERT für Chart-Debugging");
            
            MqlRealMonitor monitor = new MqlRealMonitor();
            monitor.gui.open();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fataler Fehler beim Start", e);
            System.err.println("KRITISCHER FEHLER beim Start von MqlRealMonitor:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}