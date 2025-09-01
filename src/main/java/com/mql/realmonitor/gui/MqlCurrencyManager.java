package com.mql.realmonitor.gui;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.MessageBox;

import com.mql.realmonitor.currency.CurrencyDataLoader;

/**
 * Manager für Currency Loading Funktionalität.
 * Verwaltet: Currency Button, Loading-Prozess, Error Handling
 */
public class MqlCurrencyManager {
    
    private static final Logger LOGGER = Logger.getLogger(MqlCurrencyManager.class.getName());
    
    private final MqlRealMonitorGUI gui;
    
    // Currency-Komponenten
    private Button kurseladenButton;
    private CurrencyDataLoader currencyDataLoader;
    
    public MqlCurrencyManager(MqlRealMonitorGUI gui) {
        this.gui = gui;
        initializeCurrencyDataLoader();
    }
    
    /**
     * Initialisiert den CurrencyDataLoader mit detailliertem Exception-Handling
     */
    private void initializeCurrencyDataLoader() {
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG MIT DETAILLIERTEM DEBUG ===");
        
        try {
            // Monitor-Validierung
            if (gui.getMonitor() == null) {
                String errorMsg = "Monitor ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Monitor-Fehler", errorMsg, null, null);
                currencyDataLoader = null;
                return;
            }
            
            // Config-Validierung
            if (gui.getMonitor().getConfig() == null) {
                String errorMsg = "Monitor.getConfig() ist NULL - CurrencyDataLoader kann nicht initialisiert werden";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Config-Fehler", errorMsg, null, null);
                currencyDataLoader = null;
                return;
            }
            
            LOGGER.info("Monitor und Config OK - Erstelle CurrencyDataLoader...");
            LOGGER.info("BasePath: " + gui.getMonitor().getConfig().getBasePath());
            
            // CurrencyDataLoader erstellen
            currencyDataLoader = new CurrencyDataLoader(gui.getMonitor().getConfig());
            
            if (currencyDataLoader != null) {
                LOGGER.info("CurrencyDataLoader erfolgreich erstellt!");
            } else {
                String errorMsg = "CurrencyDataLoader ist NULL nach erfolgreicher Erstellung";
                LOGGER.severe("FEHLER: " + errorMsg);
                showDetailedExceptionDialog("Erstellungs-Fehler", errorMsg, null, null);
            }
            
        } catch (NoClassDefFoundError e) {
            String errorMsg = "KLASSE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoClassDefFoundError: " + errorMsg);
            showDetailedExceptionDialog("Klasse nicht gefunden", errorMsg, e, "NoClassDefFoundError");
            currencyDataLoader = null;
            
        } catch (NoSuchMethodError e) {
            String errorMsg = "METHODE NICHT GEFUNDEN: " + e.getMessage();
            LOGGER.severe("NoSuchMethodError: " + errorMsg); 
            showDetailedExceptionDialog("Methode nicht gefunden", errorMsg, e, "NoSuchMethodError");
            currencyDataLoader = null;
            
        } catch (LinkageError e) {
            String errorMsg = "LINKAGE FEHLER: " + e.getMessage();
            LOGGER.severe("LinkageError: " + errorMsg);
            showDetailedExceptionDialog("Linkage-Fehler", errorMsg, e, "LinkageError");
            currencyDataLoader = null;
            
        } catch (Exception e) {
            String errorMsg = "ALLGEMEINE EXCEPTION: " + e.getMessage();
            LOGGER.log(Level.SEVERE, "Exception beim Initialisieren des CurrencyDataLoader", e);
            showDetailedExceptionDialog("Unerwarteter Fehler", errorMsg, e, e.getClass().getSimpleName());
            currencyDataLoader = null;
            
        } catch (Throwable t) {
            String errorMsg = "SCHWERWIEGENDER FEHLER: " + t.getMessage();
            LOGGER.log(Level.SEVERE, "Throwable beim Initialisieren des CurrencyDataLoader", t);
            showDetailedExceptionDialog("Schwerwiegender Fehler", errorMsg, t, t.getClass().getSimpleName());
            currencyDataLoader = null;
        }
        
        // Status loggen
        boolean success = (currencyDataLoader != null);
        LOGGER.info("=== CURRENCY DATA LOADER INITIALISIERUNG " + (success ? "ERFOLGREICH" : "FEHLGESCHLAGEN") + " ===");
    }
    
    /**
     * Erstellt den "Kurse laden" Button in der Toolbar
     */
    public void createCurrencyButton(Composite parent) {
        if (parent == null || parent.isDisposed()) {
            LOGGER.warning("Parent für Kurse laden Button ist null oder disposed");
            return;
        }
        
        try {
            kurseladenButton = new Button(parent, SWT.PUSH);
            kurseladenButton.setText("💰 Kurse laden");
            kurseladenButton.setToolTipText("Lädt aktuelle Währungskurse (XAUUSD, BTCUSD) von MQL5");
            
            // Layout-Daten setzen
            GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            kurseladenButton.setLayoutData(gridData);
            
            // Event-Handler für Button-Klick
            kurseladenButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    loadCurrencyRates();
                }
            });
            
            // Button initial aktivieren nur wenn CurrencyDataLoader verfügbar
            kurseladenButton.setEnabled(currencyDataLoader != null);
            
            LOGGER.info("Kurse laden Button erfolgreich erstellt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Erstellen des Kurse laden Buttons: " + e.getMessage(), e);
            gui.showError("Button-Erstellungsfehler", 
                "Kurse laden Button konnte nicht erstellt werden: " + e.getMessage());
        }
    }
    
    /**
     * Lädt Währungskurse von MQL5 in einem separaten Thread
     */
    /**
     * Lädt Währungskurse von MQL5 in einem separaten Thread
     * GEÄNDERT: Entfernt störenden Erfolgs-Dialog für automatische Loops
     */
    private void loadCurrencyRates() {
        // Validierung
        if (currencyDataLoader == null) {
            gui.showError("Fehler", "CurrencyDataLoader ist nicht initialisiert.\nBitte Anwendung neu starten.");
            return;
        }
        
        if (kurseladenButton == null || kurseladenButton.isDisposed()) {
            LOGGER.warning("Kurse laden Button ist nicht verfügbar");
            return;
        }
        
        // Prevent multiple simultaneous loads
        if (!kurseladenButton.getEnabled()) {
            LOGGER.info("Currency Loading bereits aktiv - ignoriere weiteren Button-Klick");
            return;
        }
        
        LOGGER.info("=== USER-AKTION: Kurse laden Button geklickt ===");
        
        // Button deaktivieren und Status ändern
        kurseladenButton.setEnabled(false);
        kurseladenButton.setText("Lade...");
        
        // Status-Update
        gui.updateStatus("Lade Währungskurse von MQL5...");
        
        // Loading in separatem Thread um GUI nicht zu blockieren
        Thread loadingThread = new Thread(() -> {
            String diagnosis = null;
            boolean success = false;
            
            try {
                LOGGER.info("Starte Currency Loading Thread...");
                diagnosis = currencyDataLoader.loadCurrencyRatesWithDiagnosis();
                success = true;
                
                LOGGER.info("Currency Loading erfolgreich abgeschlossen");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Currency Loading Thread Fehler: " + e.getMessage(), e);
                diagnosis = "FEHLER beim Laden der Währungskurse:\n" + e.getMessage();
                success = false;
            }
            
            // Ergebnis in UI-Thread verarbeiten
            final String finalDiagnosis = diagnosis;
            final boolean finalSuccess = success;
            
            gui.getDisplay().asyncExec(() -> {
                try {
                    // GEÄNDERT: Dialog-Aufruf entfernt für automatische Loops
                    // showCurrencyLoadingResult(finalDiagnosis, finalSuccess); // <-- ENTFERNT
                    
                    // Stattdessen: Nur in Log und Status ausgeben
                    if (finalSuccess) {
                        LOGGER.info("Currency Loading erfolgreich - Details: " + finalDiagnosis);
                    } else {
                        LOGGER.warning("Currency Loading fehlgeschlagen - Details: " + finalDiagnosis);
                        // Nur bei Fehlern den Dialog zeigen
                        showCurrencyLoadingResult(finalDiagnosis, finalSuccess);
                    }
                    
                    // Button und Status zurücksetzen (wichtig für weitere Aufrufe)
                    resetKurseladenButton();
                    updateStatusAfterCurrencyLoading(finalSuccess);
                    
                    LOGGER.info("Currency Loading UI-Updates abgeschlossen");
                    
                } catch (Exception uiException) {
                    LOGGER.log(Level.SEVERE, "Fehler beim UI-Update nach Currency Loading: " + uiException.getMessage(), uiException);
                }
            });
        });
        
        loadingThread.setName("CurrencyLoadingThread");
        loadingThread.setDaemon(true);
        loadingThread.start();
    }
    
    /**
     * Zeigt das Ergebnis des Currency Loading in einem Dialog
     */
    private void showCurrencyLoadingResult(String diagnosis, boolean success) {
        try {
            if (gui.getShell() == null || gui.getShell().isDisposed()) {
                LOGGER.warning("Shell nicht verfügbar für Currency Result Dialog");
                return;
            }
            
            MessageBox messageBox = new MessageBox(gui.getShell(), 
                success ? SWT.ICON_INFORMATION | SWT.OK : SWT.ICON_WARNING | SWT.OK);
            messageBox.setText(success ? "Currency Loading erfolgreich" : "Currency Loading Probleme");
            messageBox.setMessage(diagnosis != null ? diagnosis : "Unbekanntes Ergebnis");
            messageBox.open();
            
            LOGGER.info("Currency Loading Result Dialog angezeigt: " + (success ? "ERFOLG" : "FEHLER"));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anzeigen des Currency Result Dialogs: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setzt den Kurse laden Button nach dem Loading zurück
     */
    private void resetKurseladenButton() {
        try {
            if (kurseladenButton != null && !kurseladenButton.isDisposed()) {
                kurseladenButton.setEnabled(true);
                kurseladenButton.setText("💰 Kurse laden");
                
                LOGGER.fine("Kurse laden Button zurückgesetzt");
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Zurücksetzen des Kurse laden Buttons: " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert den Status nach dem Currency Loading
     */
    private void updateStatusAfterCurrencyLoading(boolean success) {
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
     * Zeigt einen detaillierten Exception-Dialog mit allen verfügbaren Informationen
     */
    private void showDetailedExceptionDialog(String title, String message, Throwable throwable, String errorType) {
        try {
            if (gui.getShell() == null || gui.getShell().isDisposed()) {
                LOGGER.warning("Shell nicht verfügbar für Exception-Dialog");
                return;
            }
            
            StringBuilder fullMessage = new StringBuilder();
            fullMessage.append("=== DETAILLIERTE FEHLER-INFORMATION ===\n\n");
            
            // Basis-Info
            fullMessage.append("TITEL: ").append(title).append("\n");
            fullMessage.append("NACHRICHT: ").append(message).append("\n\n");
            
            if (errorType != null) {
                fullMessage.append("FEHLER-TYP: ").append(errorType).append("\n\n");
            }
            
            // Exception-Details falls verfügbar
            if (throwable != null) {
                fullMessage.append("EXCEPTION-KLASSE: ").append(throwable.getClass().getName()).append("\n");
                fullMessage.append("EXCEPTION-MESSAGE: ").append(throwable.getMessage()).append("\n\n");
                
                // Cause falls verfügbar
                if (throwable.getCause() != null) {
                    fullMessage.append("URSACHE-KLASSE: ").append(throwable.getCause().getClass().getName()).append("\n");
                    fullMessage.append("URSACHE-MESSAGE: ").append(throwable.getCause().getMessage()).append("\n\n");
                }
                
                // Stack Trace (erste 10 Zeilen)
                fullMessage.append("STACK TRACE (erste 10 Zeilen):\n");
                StackTraceElement[] stackTrace = throwable.getStackTrace();
                int maxLines = Math.min(10, stackTrace.length);
                for (int i = 0; i < maxLines; i++) {
                    fullMessage.append("  ").append(stackTrace[i].toString()).append("\n");
                }
                
                if (stackTrace.length > 10) {
                    fullMessage.append("  ... (").append(stackTrace.length - 10).append(" weitere Zeilen)\n");
                }
            }
            
            // System-Informationen
            fullMessage.append("\n=== SYSTEM-INFORMATIONEN ===\n");
            fullMessage.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
            fullMessage.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
            fullMessage.append("OS Name: ").append(System.getProperty("os.name")).append("\n");
            fullMessage.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
            fullMessage.append("Working Directory: ").append(System.getProperty("user.dir")).append("\n");
            
            // Konfiguration falls verfügbar
            if (gui.getMonitor() != null && gui.getMonitor().getConfig() != null) {
                fullMessage.append("\n=== KONFIGURATION ===\n");
                fullMessage.append("Base Path: ").append(gui.getMonitor().getConfig().getBasePath()).append("\n");
                fullMessage.append("Config-Klasse: ").append(gui.getMonitor().getConfig().getClass().getName()).append("\n");
            }
            
            fullMessage.append("\n=== ENDE DER FEHLER-INFORMATION ===");
            
            // Dialog anzeigen
            MessageBox messageBox = new MessageBox(gui.getShell(), SWT.ICON_ERROR | SWT.OK | SWT.RESIZE);
            messageBox.setText("CurrencyDataLoader Fehler-Details: " + title);
            messageBox.setMessage(fullMessage.toString());
            messageBox.open();
            
            LOGGER.info("Detaillierter Exception-Dialog angezeigt für: " + title);
            
        } catch (Exception dialogException) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anzeigen des Exception-Dialogs", dialogException);
            System.err.println("FATAL: Konnte Exception-Dialog nicht anzeigen: " + dialogException.getMessage());
            System.err.println("Original Fehler war: " + title + " - " + message);
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }
    
    /**
     * Prüft ob Currency Loading verfügbar ist
     */
    public boolean isCurrencyLoadingAvailable() {
        return currencyDataLoader != null;
    }
    
    /**
     * Gibt den CurrencyDataLoader zurück (kann null sein)
     */
    public CurrencyDataLoader getCurrencyDataLoader() {
        return currencyDataLoader;
    }
    
    /**
     * Bereinigt Ressourcen beim Herunterfahren
     */
    public void cleanup() {
        try {
            // Currency Data Loader auträumen
            if (currencyDataLoader != null) {
                currencyDataLoader.cleanup();
                currencyDataLoader = null;
                LOGGER.info("CurrencyDataLoader disposed");
            }
            
            // Button wird automatisch durch SWT disposed
            kurseladenButton = null;
            
            LOGGER.info("CurrencyManager bereinigt");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Dispose der Currency-Funktionalität: " + e.getMessage(), e);
        }
    }
}