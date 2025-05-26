package com.mql.realmonitor.gui;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.data.TickDataLoader;

/**
 * Separates Fenster für die Anzeige von Tickdaten
 * Zeigt Tickdaten in einem scrollbaren Textfeld an
 */
public class TickDataDisplayWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickDataDisplayWindow.class.getName());
    
    private final MqlRealMonitorGUI parentGui;
    
    public TickDataDisplayWindow(MqlRealMonitorGUI parentGui) {
        this.parentGui = parentGui;
    }
    
    /**
     * Öffnet ein scrollbares Textfenster mit den Tickdaten für einen Provider
     * 
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     */
    public void showTickDataWindow(String signalId, String providerName) {
        try {
            LOGGER.info("Öffne Tickdaten-Fenster für Signal: " + signalId + " (" + providerName + ")");
            
            // Tick-Datei-Pfad ermitteln
            String tickFilePath = parentGui.getMonitor().getConfig().getTickFilePath(signalId);
            
            // Neues Shell-Fenster erstellen
            Shell tickDataShell = new Shell(parentGui.getShell(), SWT.SHELL_TRIM | SWT.MODELESS);
            tickDataShell.setText("Tickdaten - " + signalId + " (" + providerName + ")");
            tickDataShell.setSize(800, 600);
            tickDataShell.setLayout(new GridLayout(1, false));
            
            // Fenster zentrieren
            centerWindow(tickDataShell);
            
            // Info-Label
            Label infoLabel = new Label(tickDataShell, SWT.NONE);
            infoLabel.setText("Tickdaten für Signal " + signalId + " (" + providerName + ")");
            infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            // Status-Label
            Label statusLabel = new Label(tickDataShell, SWT.NONE);
            statusLabel.setText("Lade Tickdaten...");
            statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            // Scrollbares Textfeld für Tickdaten
            Text tickDataText = new Text(tickDataShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
            tickDataText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            tickDataText.setBackground(parentGui.getDisplay().getSystemColor(SWT.COLOR_WHITE));
            
            // Button-Bereich
            Composite buttonComposite = new Composite(tickDataShell, SWT.NONE);
            buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            buttonComposite.setLayout(new GridLayout(3, false));
            
            Button refreshButton = new Button(buttonComposite, SWT.PUSH);
            refreshButton.setText("Aktualisieren");
            refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            
            // Spacer
            Label spacer = new Label(buttonComposite, SWT.NONE);
            spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            
            Button closeButton = new Button(buttonComposite, SWT.PUSH);
            closeButton.setText("Schließen");
            closeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            
            // Event-Handler für Buttons
            refreshButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    loadTickDataIntoText(tickFilePath, signalId, providerName, tickDataText, statusLabel);
                }
            });
            
            closeButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    tickDataShell.close();
                }
            });
            
            // Fenster schließen Handler
            tickDataShell.addListener(SWT.Close, event -> {
                LOGGER.info("Tickdaten-Fenster geschlossen für Signal: " + signalId);
            });
            
            // Fenster anzeigen
            tickDataShell.open();
            
            // Tickdaten laden (asynchron)
            loadTickDataIntoText(tickFilePath, signalId, providerName, tickDataText, statusLabel);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Tickdaten-Fensters für Signal: " + signalId, e);
            parentGui.showError("Fehler beim Öffnen des Tickdaten-Fensters", 
                               "Konnte Tickdaten-Fenster für " + signalId + " nicht öffnen:\n" + e.getMessage());
        }
    }
    
    /**
     * Lädt Tickdaten und zeigt sie im Textfeld an
     * 
     * @param tickFilePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     * @param tickDataText Das Text-Widget für die Anzeige
     * @param statusLabel Das Status-Label für Updates
     */
    private void loadTickDataIntoText(String tickFilePath, String signalId, String providerName, 
                                     Text tickDataText, Label statusLabel) {
        // In separatem Thread laden um GUI nicht zu blockieren
        new Thread(() -> {
            try {
                LOGGER.info("Lade Tickdaten für Signal: " + signalId + " aus: " + tickFilePath);
                
                // Tickdaten laden
                TickDataLoader.TickDataSet tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    parentGui.getDisplay().asyncExec(() -> {
                        if (!statusLabel.isDisposed()) {
                            statusLabel.setText("Keine Tickdaten gefunden in: " + tickFilePath);
                        }
                        if (!tickDataText.isDisposed()) {
                            tickDataText.setText("Keine Tickdaten verfügbar für Signal " + signalId + " (" + providerName + ")\n\n" +
                                                "Datei: " + tickFilePath + "\n" +
                                                "Status: Datei nicht gefunden oder leer");
                        }
                    });
                    return;
                }
                
                // Tickdaten formatieren
                StringBuilder tickDataContent = formatTickData(tickDataSet, signalId, providerName, tickFilePath);
                
                // GUI aktualisieren
                parentGui.getDisplay().asyncExec(() -> {
                    if (!statusLabel.isDisposed()) {
                        statusLabel.setText("Tickdaten geladen: " + tickDataSet.getTickCount() + " Einträge");
                    }
                    if (!tickDataText.isDisposed()) {
                        tickDataText.setText(tickDataContent.toString());
                        // Cursor an den Anfang setzen
                        tickDataText.setSelection(0, 0);
                        tickDataText.setTopIndex(0);
                    }
                });
                
                LOGGER.info("Tickdaten erfolgreich geladen für Signal: " + signalId + " (" + tickDataSet.getTickCount() + " Einträge)");
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden der Tickdaten für Signal: " + signalId, e);
                
                parentGui.getDisplay().asyncExec(() -> {
                    if (!statusLabel.isDisposed()) {
                        statusLabel.setText("Fehler beim Laden der Tickdaten");
                    }
                    if (!tickDataText.isDisposed()) {
                        tickDataText.setText("FEHLER beim Laden der Tickdaten für Signal " + signalId + " (" + providerName + ")\n\n" +
                                           "Datei: " + tickFilePath + "\n" +
                                           "Fehler: " + e.getMessage() + "\n\n" +
                                           "Stacktrace:\n" + getStackTraceAsString(e));
                    }
                });
            }
        }, "TickDataLoader-" + signalId).start();
    }
    
    /**
     * Formatiert die Tickdaten für die Anzeige
     * 
     * @param tickDataSet Das TickDataSet
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     * @param tickFilePath Der Dateipfad
     * @return Formatierter String für die Anzeige
     */
    private StringBuilder formatTickData(TickDataLoader.TickDataSet tickDataSet, String signalId, 
                                       String providerName, String tickFilePath) {
        StringBuilder tickDataContent = new StringBuilder();
        
        // Header
        tickDataContent.append("=== TICKDATEN FÜR SIGNAL ").append(signalId).append(" (").append(providerName).append(") ===\n");
        tickDataContent.append("Datei: ").append(tickFilePath).append("\n");
        tickDataContent.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
        tickDataContent.append("Erstellt: ").append(tickDataSet.getCreatedDate()).append("\n");
        
        if (tickDataSet.getTickCount() > 0) {
            tickDataContent.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                           .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            tickDataContent.append("Equity: ").append(String.format("%.2f - %.2f", 
                                 tickDataSet.getMinEquity(), tickDataSet.getMaxEquity())).append("\n");
            tickDataContent.append("Floating Profit: ").append(String.format("%.2f - %.2f", 
                                 tickDataSet.getMinFloatingProfit(), tickDataSet.getMaxFloatingProfit())).append("\n");
            tickDataContent.append("Gesamtwert: ").append(String.format("%.2f - %.2f", 
                                 tickDataSet.getMinTotalValue(), tickDataSet.getMaxTotalValue())).append("\n");
        }
        
        // Trennlinie
        tickDataContent.append("\n").append("=".repeat(80)).append("\n");
        tickDataContent.append("TICK-DATEN (Format: Zeitstempel | Equity | Floating Profit | Gesamtwert)\n");
        tickDataContent.append("=".repeat(80)).append("\n\n");
        
        // Tick-Daten ausgeben (neueste zuerst)
        var ticks = tickDataSet.getTicks();
        for (int i = ticks.size() - 1; i >= 0; i--) {
            TickDataLoader.TickData tick = ticks.get(i);
            tickDataContent.append(String.format("%s | %8.2f | %8.2f | %8.2f\n",
                                 tick.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")),
                                 tick.getEquity(),
                                 tick.getFloatingProfit(),
                                 tick.getTotalValue()));
        }
        
        // Zusätzliche Statistiken
        tickDataContent.append("\n").append("=".repeat(80)).append("\n");
        tickDataContent.append("STATISTIKEN\n");
        tickDataContent.append("=".repeat(80)).append("\n");
        tickDataContent.append(TickDataLoader.createSummary(tickDataSet));
        
        return tickDataContent;
    }
    
    /**
     * Hilfsmethode zum Konvertieren von Stacktrace zu String
     * 
     * @param e Die Exception
     * @return Stacktrace als String
     */
    private String getStackTraceAsString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Zentriert ein Fenster relativ zum Parent
     * 
     * @param shell Das zu zentrierende Fenster
     */
    private void centerWindow(Shell shell) {
        Shell parent = parentGui.getShell();
        Point parentSize = parent.getSize();
        Point parentLocation = parent.getLocation();
        Point size = shell.getSize();
        
        int x = parentLocation.x + (parentSize.x - size.x) / 2;
        int y = parentLocation.y + (parentSize.y - size.y) / 2;
        
        shell.setLocation(x, y);
    }
}