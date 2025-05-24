package com.mql.realmonitor.gui;

import org.eclipse.swt.widgets.Display;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Status-Update Manager für die GUI
 * Verwaltet periodische Status-Updates und Thread-sichere GUI-Aktualisierungen
 */
public class StatusUpdater {
    
    private static final Logger LOGGER = Logger.getLogger(StatusUpdater.class.getName());
    
    private final MqlRealMonitorGUI gui;
    private final Display display;
    private final ScheduledExecutorService scheduler;
    
    private volatile boolean isRunning = false;
    private long lastUpdateTime = 0;
    private int updateCounter = 0;
    
    public StatusUpdater(MqlRealMonitorGUI gui) {
        this.gui = gui;
        this.display = gui.getDisplay();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StatusUpdater");
            t.setDaemon(true);
            return t;
        });
        
        startPeriodicUpdates();
    }
    
    /**
     * Startet periodische Status-Updates
     */
    private void startPeriodicUpdates() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        // Alle 30 Sekunden Status aktualisieren
        scheduler.scheduleAtFixedRate(this::performPeriodicUpdate, 30, 30, TimeUnit.SECONDS);
        
        LOGGER.info("StatusUpdater gestartet - Updates alle 30 Sekunden");
    }
    
    /**
     * Führt periodische Updates durch
     */
    private void performPeriodicUpdate() {
        if (!isRunning || display.isDisposed()) {
            return;
        }
        
        try {
            updateCounter++;
            lastUpdateTime = System.currentTimeMillis();
            
            display.asyncExec(() -> {
                if (!display.isDisposed()) {
                    updateRuntimeInformation();
                }
            });
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim periodischen Update: " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert Laufzeit-Informationen in der GUI
     */
    private void updateRuntimeInformation() {
        try {
            // Memory Usage
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            String memoryInfo = String.format("Memory: %.1f%% (%d MB / %d MB)", 
                                            memoryUsagePercent, 
                                            usedMemory / (1024 * 1024),
                                            maxMemory / (1024 * 1024));
            
            // Uptime berechnen
            long uptimeMinutes = (System.currentTimeMillis() - getStartTime()) / (1000 * 60);
            String uptimeInfo = String.format("Uptime: %d Min", uptimeMinutes);
            
            // Status zusammensetzen
            String currentStatus = getCurrentDisplayStatus();
            String extendedStatus = String.format("%s | %s | %s | Updates: %d", 
                                                currentStatus, memoryInfo, uptimeInfo, updateCounter);
            
            // Memory Warning bei hoher Auslastung
            if (memoryUsagePercent > 80) {
                LOGGER.warning("Hohe Memory-Auslastung: " + memoryUsagePercent + "%");
                gui.updateStatus("WARNUNG: Hohe Memory-Auslastung (" + memoryUsagePercent + "%) - " + currentStatus);
            } else {
                gui.updateStatus(extendedStatus);
            }
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Update der Laufzeit-Informationen: " + e.getMessage());
        }
    }
    
    /**
     * Gibt den aktuellen Anzeige-Status zurück
     */
    private String getCurrentDisplayStatus() {
        if (gui.getMonitor().isMonitoringActive()) {
            return "Monitoring aktiv";
        } else {
            return "Bereit";
        }
    }
    
    /**
     * Gibt die Startzeit der Anwendung zurück
     */
    private long getStartTime() {
        // Verwende System Property oder feste Zeit - in produktiver Umgebung 
        // sollte dies beim Start der Anwendung gesetzt werden
        String startTimeProperty = System.getProperty("mql.start.time");
        if (startTimeProperty != null) {
            try {
                return Long.parseLong(startTimeProperty);
            } catch (NumberFormatException e) {
                // Fallback
            }
        }
        
        // Fallback: Ungefähre Startzeit basierend auf aktueller Zeit minus Update-Counter
        return System.currentTimeMillis() - (updateCounter * 30 * 1000L);
    }
    
    /**
     * Aktualisiert den Status mit Priorität (unterbricht periodische Updates kurzzeitig)
     * 
     * @param status Der prioritäre Status
     * @param durationSeconds Wie lange der Status angezeigt werden soll
     */
    public void updateStatusWithPriority(String status, int durationSeconds) {
        if (display.isDisposed()) {
            return;
        }
        
        display.asyncExec(() -> {
            if (!display.isDisposed()) {
                gui.updateStatus(status);
                
                // Nach der angegebenen Zeit wieder zu normalem Status wechseln
                display.timerExec(durationSeconds * 1000, () -> {
                    if (!display.isDisposed()) {
                        updateRuntimeInformation();
                    }
                });
            }
        });
    }
    
    /**
     * Zeigt eine temporäre Erfolgs-Nachricht an
     * 
     * @param message Die Erfolgs-Nachricht
     */
    public void showSuccessMessage(String message) {
        updateStatusWithPriority("✓ " + message, 5);
        LOGGER.info("Erfolg: " + message);
    }
    
    /**
     * Zeigt eine temporäre Warn-Nachricht an
     * 
     * @param message Die Warn-Nachricht
     */
    public void showWarningMessage(String message) {
        updateStatusWithPriority("⚠ " + message, 10);
        LOGGER.warning("Warnung: " + message);
    }
    
    /**
     * Zeigt eine temporäre Fehler-Nachricht an
     * 
     * @param message Die Fehler-Nachricht
     */
    public void showErrorMessage(String message) {
        updateStatusWithPriority("✗ " + message, 15);
        LOGGER.severe("Fehler: " + message);
    }
    
    /**
     * Zeigt eine Fortschritts-Nachricht an
     * 
     * @param message Die Fortschritts-Nachricht
     * @param current Aktueller Wert
     * @param total Gesamt-Wert
     */
    public void showProgressMessage(String message, int current, int total) {
        double percentage = total > 0 ? (double) current / total * 100 : 0;
        String progressStatus = String.format("%s (%d/%d - %.1f%%)", message, current, total, percentage);
        
        if (display.isDisposed()) {
            return;
        }
        
        display.asyncExec(() -> {
            if (!display.isDisposed()) {
                gui.updateStatus(progressStatus);
            }
        });
    }
    
    /**
     * Erstellt einen Status-Bericht
     * 
     * @return Status-Bericht als String
     */
    public String createStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MqlRealMonitor Status-Bericht ===\n\n");
        
        // Allgemeine Informationen
        report.append("Zeitstempel: ").append(new java.util.Date()).append("\n");
        report.append("Monitoring aktiv: ").append(gui.getMonitor().isMonitoringActive()).append("\n");
        report.append("Provider in Tabelle: ").append(gui.getClass().getSimpleName()).append("\n");
        report.append("Update Counter: ").append(updateCounter).append("\n");
        
        // Memory Information
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        report.append("\n=== Memory Status ===\n");
        report.append("Verwendet: ").append(usedMemory / (1024 * 1024)).append(" MB\n");
        report.append("Frei: ").append(freeMemory / (1024 * 1024)).append(" MB\n");
        report.append("Total: ").append(totalMemory / (1024 * 1024)).append(" MB\n");
        report.append("Maximum: ").append(maxMemory / (1024 * 1024)).append(" MB\n");
        report.append("Auslastung: ").append(String.format("%.1f%%", (double) usedMemory / maxMemory * 100)).append("\n");
        
        // Konfiguration
        report.append("\n=== Konfiguration ===\n");
        report.append("Intervall: ").append(gui.getMonitor().getConfig().getIntervalHour()).append(" Stunden\n");
        report.append("Timeout: ").append(gui.getMonitor().getConfig().getTimeoutSeconds()).append(" Sekunden\n");
        report.append("Basis-Pfad: ").append(gui.getMonitor().getConfig().getBasePath()).append("\n");
        
        return report.toString();
    }
    
    /**
     * Führt Garbage Collection durch und zeigt Memory-Status
     */
    public void performGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long beforeGC = runtime.totalMemory() - runtime.freeMemory();
        
        System.gc();
        
        // Kurz warten bis GC abgeschlossen
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterGC = runtime.totalMemory() - runtime.freeMemory();
        long freedMemory = beforeGC - afterGC;
        
        String gcMessage = String.format("Garbage Collection: %.1f MB freigegeben", 
                                       freedMemory / (1024.0 * 1024.0));
        
        showSuccessMessage(gcMessage);
        
        LOGGER.info("Garbage Collection durchgeführt: " + freedMemory + " Bytes freigegeben");
    }
    
    /**
     * Stoppt den StatusUpdater
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        LOGGER.info("StatusUpdater gestoppt");
    }
    
    /**
     * Prüft ob der StatusUpdater läuft
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Gibt die Zeit des letzten Updates zurück
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * Gibt die Anzahl der Updates zurück
     */
    public int getUpdateCount() {
        return updateCounter;
    }
}