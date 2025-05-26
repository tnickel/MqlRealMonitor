package com.mql.realmonitor;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.downloader.WebDownloader;
import com.mql.realmonitor.downloader.FavoritesReader;
import com.mql.realmonitor.parser.HTMLParser;
import com.mql.realmonitor.parser.SignalData;
import com.mql.realmonitor.tickdata.TickDataWriter;
import com.mql.realmonitor.gui.MqlRealMonitorGUI;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Hauptklasse für MqlRealMonitor
 * Orchestriert Download, Parsing und GUI-Updates für MQL5 Signalprovider-Monitoring
 * GEÄNDERT: Intervall jetzt in Minuten statt Stunden
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
     * Initialisiert alle Komponenten des MqlRealMonitor
     */
    private void initializeComponents() {
        try {
            LOGGER.info("Initialisiere MqlRealMonitor Komponenten...");
            
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
            
            LOGGER.info("Alle Komponenten erfolgreich initialisiert");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler bei der Initialisierung", e);
            throw new RuntimeException("Initialisierung fehlgeschlagen", e);
        }
    }
    
    /**
     * Startet den Monitor-Prozess
     * GEÄNDERT: Intervall in Minuten statt Stunden
     */
    public void startMonitoring() {
        if (isRunning) {
            LOGGER.warning("Monitoring läuft bereits");
            return;
        }
        
        isRunning = true;
        LOGGER.info("Starte MQL5 Signal Monitoring...");
        
        // Ersten Download sofort starten
        scheduler.submit(this::performMonitoringCycle);
        
        // Wiederkehrende Downloads planen - GEÄNDERT: Minuten statt Stunden
        long intervalMinutes = config.getIntervalMinutes();
        scheduler.scheduleAtFixedRate(
            this::performMonitoringCycle,
            intervalMinutes,
            intervalMinutes,
            TimeUnit.MINUTES  // GEÄNDERT: MINUTES statt vorher umgerechnete Minuten
        );
        
        gui.updateStatus("Monitoring gestartet - Intervall: " + config.getIntervalMinutes() + " Minuten"); // GEÄNDERT
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
        LOGGER.info("Stoppe MQL5 Signal Monitoring...");
        
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
    }
    
    /**
     * Führt einen vollständigen Monitoring-Zyklus durch
     */
    private void performMonitoringCycle() {
        try {
            LOGGER.info("Starte Monitoring-Zyklus...");
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
                                       " - Floating: " + signalData.getFloatingProfit());
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
                           config.getIntervalMinutes() + " Minuten"); // GEÄNDERT
            LOGGER.info("Monitoring-Zyklus erfolgreich abgeschlossen");
            
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
            LOGGER.info("Manueller Refresh angefordert");
            scheduler.submit(this::performMonitoringCycle);
        } else {
            LOGGER.info("Einmaliger manueller Refresh");
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
        LOGGER.info("Beende MqlRealMonitor...");
        stopMonitoring();
        
        if (gui != null) {
            gui.dispose();
        }
        
        LOGGER.info("MqlRealMonitor beendet");
    }
    
    /**
     * Hauptmethode - Startet das MqlRealMonitor GUI
     */
    public static void main(String[] args) {
        try {
            LOGGER.info("Starte MqlRealMonitor...");
            
            MqlRealMonitor monitor = new MqlRealMonitor();
            monitor.gui.open();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fataler Fehler beim Start", e);
            System.exit(1);
        }
    }
}