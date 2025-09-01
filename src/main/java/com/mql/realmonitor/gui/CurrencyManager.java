package com.mql.realmonitor.gui;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.mql.realmonitor.MqlRealMonitor;
import com.mql.realmonitor.currency.CurrencyDataLoader;

/**
 * CurrencyManager - Verwaltet die komplette Currency Loading Funktionalität
 * 
 * Zuständig für:
 * - Initialisierung des CurrencyDataLoader
 * - Manuelles Currency Loading (Button-Klick)
 * - Automatisches Currency Loading (nach Signalprovider-Updates)
 * - Thread-sichere Currency-Operationen
 * - Error-Handling für Currency-Operationen
 */
public class CurrencyManager {
    
    private static final Logger LOGGER = Logger.getLogger(CurrencyManager.class.getName());
    
    private final MqlRealMonitorGUI gui;
    private final MqlRealMonitor monitor;
    private final DialogManager dialogManager;
    
    // Currency-Komponenten
    private CurrencyDataLoader currencyDataLoader;
    private volatile boolean isLoading = false;
    
    public CurrencyManager(MqlRealMonitorGUI gui, MqlRealMonitor monitor, DialogManager dialogManager) {
        this.gui = gui;
        this.monitor = monitor;
        this.dialogManager = dialogManager;
        
        initializeCurrencyDataLoader();
    }
    
    /**
     * Initialisiert den CurrencyDataLoader mit detailliertem Error-Handling
     */
    private void initializeCurrencyDataLoader() {
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG ===");
        
        try {
            // Monitor validierung
            if (monitor == null) {
                String errorMsg = "Monitor ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedError("Monitor-Fehler", errorMsg, null);
                return;
            }
            
            // Config validierung  
            if (monitor.getConfig() == null) {
                String errorMsg = "Monitor.getConfig() ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedError("Config-Fehler", errorMsg, null);
                return;
            }
            
            LOGGER.info("Monitor und Config OK - Erstelle CurrencyDataLoader...");
            LOGGER.info("BasePath: " + monitor.getConfig().getBasePath());
            
            // CurrencyDataLoader erstellen
            currencyDataLoader = new CurrencyDataLoader(monitor.getConfig());
            
            if (currencyDataLoader != null) {
                LOGGER.info("CurrencyDataLoader erfolgreich erstellt!");
            } else {
                String errorMsg = "CurrencyDataLoader ist NULL nach erfolgreicher Erstellung";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedError("Erstellungs-Fehler", errorMsg, null);
            }
            
        } catch (NoClassDefFoundError e) {
            String errorMsg = "KLASSE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoClassDefFoundError: " + errorMsg);
            showDetailedError("Klasse nicht gefunden", errorMsg, e);
            currencyDataLoader = null;
            
        } catch (NoSuchMethodError e) {
            String errorMsg = "METHODE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoSuchMethodError: " + errorMsg); 
            showDetailedError("Methode nicht gefunden", errorMsg, e);
            currencyDataLoader = null;
            
        } catch (Exception e) {
            String errorMsg = "ALLGEMEINE EXCEPTION: " + e.getMessage();
            LOGGER.log(Level.SEVERE, "Exception beim Initialisieren des CurrencyDataLoader", e);
            showDetailedError("Unerwarteter Fehler", errorMsg, e);
            currencyDataLoader = null;
            
        } catch (Throwable t) {
            String errorMsg = "SCHWERWIEGENDER FEHLER: " + t.getMessage();
            LOGGER.log(Level.SEVERE, "Throwable beim Initialisieren des CurrencyDataLoader", t);
            showDetailedError("Schwerwiegender Fehler", errorMsg, t);
            currencyDataLoader = null;
        }
        
        // Status loggen
        boolean success = (currencyDataLoader != null);
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG " + (success ? "ERFOLGREICH" : "FEHLGESCHLAGEN") + " ===");
    }
    
    /**
     * Lädt Währungskurse von MQL5 (manueller Button-Klick)
     */
    public void loadCurrencyRatesManually() {
        // Validierung
        if (currencyDataLoader == null) {
            dialogManager.showError("Fehler", "CurrencyDataLoader ist nicht initialisiert.\nBitte Anwendung neu starten.");
            return;
        }
        
        // Prevent multiple simultaneous loads
        if (isLoading) {
            LOGGER.info("Currency Loading bereits aktiv - ignoriere weiteren Aufruf");
            return;
        }
        
        LOGGER.info("=== MANUELLES CURRENCY LOADING GESTARTET ===");
        
        // Status setzen
        isLoading = true;
        gui.updateStatus("Lade Währungskurse von MQL5...");
        
        // Loading in separatem Thread um GUI nicht zu blockieren
        Thread loadingThread = new Thread(() -> {
            String diagnosis = null;
            boolean success = false;
            
            try {
                LOGGER.info("Starte manuelles Currency Loading Thread...");
                diagnosis = currencyDataLoader.loadCurrencyRatesWithDiagnosis();
                success = true;
                
                LOGGER.info("Manuelles Currency Loading erfolgreich abgeschlossen");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Manuelles Currency Loading Fehler: " + e.getMessage(), e);
                diagnosis = "FEHLER beim Laden der Währungskurse:\n" + e.getMessage();
                success = false;
            } finally {
                isLoading = false;
            }
            
            // Ergebnis in UI-Thread verarbeiten
            final String finalDiagnosis = diagnosis;
            final boolean finalSuccess = success;
            
            gui.getDisplay().asyncExec(() -> {
                try {
                    // Status-Update
                    updateStatusAfterLoading(finalSuccess);
                    
                    LOGGER.info("Manuelles Currency Loading UI-Updates abgeschlossen");
                    
                } catch (Exception uiException) {
                    LOGGER.log(Level.SEVERE, "Fehler beim UI-Update nach manuellem Currency Loading: " + uiException.getMessage(), uiException);
                }
            });
        });
        
        loadingThread.setName("ManualCurrencyLoadingThread");
        loadingThread.setDaemon(true);
        loadingThread.start();
    }
    
    /**
     * Lädt Währungskurse automatisch (nach Signalprovider-Updates)
     * Wird vom MqlRealMonitor nach dem Laden aller Signalprovider aufgerufen.
     */
    public void loadCurrencyRatesAutomatically() {
        // Validierung
        if (currencyDataLoader == null) {
            LOGGER.warning("CurrencyDataLoader nicht verfügbar für automatisches Loading");
            return;
        }
        
        // Prevent multiple simultaneous loads
        if (isLoading) {
            LOGGER.info("Currency Loading bereits aktiv - überspringe automatisches Loading");
            return;
        }
        
        LOGGER.info("=== AUTOMATISCHES CURRENCY LOADING GESTARTET ===");
        
        // Loading in separatem Thread um Monitoring-Thread nicht zu blockieren
        Thread automaticLoadingThread = new Thread(() -> {
            String diagnosis = null;
            boolean success = false;
            
            try {
                LOGGER.info("Führe automatisches Currency Loading durch...");
                diagnosis = currencyDataLoader.loadCurrencyRatesWithDiagnosis();
                success = true;
                
                LOGGER.info("Automatisches Currency Loading erfolgreich abgeschlossen");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Automatisches Currency Loading Fehler: " + e.getMessage(), e);
                diagnosis = "FEHLER beim automatischen Laden der Währungskurse: " + e.getMessage();
                success = false;
            }
            
            // Ergebnis in UI-Thread verarbeiten (nur Status-Update, keine Dialoge)
            final boolean finalSuccess = success;
            final String finalDiagnosis = diagnosis;
            
            if (!gui.getDisplay().isDisposed()) {
                gui.getDisplay().asyncExec(() -> {
                    try {
                        // Status-Update basierend auf Erfolg
                        String statusMessage;
                        if (finalSuccess) {
                            statusMessage = "Währungskurse automatisch geladen";
                            LOGGER.info("Status: " + statusMessage);
                        } else {
                            statusMessage = "Automatisches Currency Loading fehlgeschlagen";
                            LOGGER.warning("Status: " + statusMessage + " - " + finalDiagnosis);
                        }
                        
                        // Nur Status updaten, keine Dialoge bei automatischem Loading
                        gui.updateStatus(statusMessage);
                        
                        LOGGER.info("Automatisches Currency Loading UI-Updates abgeschlossen");
                        
                    } catch (Exception uiException) {
                        LOGGER.log(Level.SEVERE, "Fehler beim UI-Update nach automatischem Currency Loading: " + uiException.getMessage(), uiException);
                    }
                });
            }
        });
        
        automaticLoadingThread.setName("AutomaticCurrencyLoadingThread");
        automaticLoadingThread.setDaemon(true); // Daemon Thread, damit er die Anwendung nicht blockiert
        automaticLoadingThread.start();
        
        LOGGER.info("Automatisches Currency Loading Thread gestartet");
    }
    
    /**
     * Aktualisiert den Status nach dem Currency Loading
     */
    private void updateStatusAfterLoading(boolean success) {
        try {
            String statusMessage;
            if (success) {
                statusMessage = "Währungskurse erfolgreich geladen";
            } else {
                statusMessage = "Fehler beim Laden der Währungskurse";
            }
            
            gui.updateStatus(statusMessage);
            
            LOGGER.info("Status nach Currency Loading aktualisiert: " + statusMessage);
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Status-Update nach Currency Loading: " + e.getMessage());
        }
    }
    
    /**
     * Zeigt detaillierte Fehlerinformationen an
     */
    private void showDetailedError(String title, String message, Throwable throwable) {
        if (dialogManager != null) {
            dialogManager.showDetailedError("CurrencyManager: " + title, message, throwable);
        } else {
            LOGGER.severe("DialogManager nicht verfügbar für Fehleranzeige: " + title + " - " + message);
        }
    }
    
    /**
     * Prüft ob Currency Loading verfügbar ist
     */
    public boolean isCurrencyLoadingAvailable() {
        return currencyDataLoader != null;
    }
    
    /**
     * Prüft ob gerade Currency Loading aktiv ist
     */
    public boolean isLoading() {
        return isLoading;
    }
    
    /**
     * Bereinigt Currency-Ressourcen
     */
    public void dispose() {
        try {
            // Loading stoppen falls aktiv
            isLoading = false;
            
            // Currency Data Loader aufräumen
            if (currencyDataLoader != null) {
                currencyDataLoader = null;
                LOGGER.info("CurrencyDataLoader disposed");
            }
            
            LOGGER.info("CurrencyManager erfolgreich bereinigt");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Dispose des CurrencyManager: " + e.getMessage());
        }
    }
}