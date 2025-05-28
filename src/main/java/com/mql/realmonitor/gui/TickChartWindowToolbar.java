package com.mql.realmonitor.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * Toolbar für das TickChartWindow mit allen Buttons und Event-Handlern
 * Verwaltet: Refresh, Diagnostik, Tickdaten, MQL5 Website, Zoom-Funktionen
 */
public class TickChartWindowToolbar extends Composite {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindowToolbar.class.getName());
    
    // UI Komponenten
    private Button refreshButton;
    private Button diagnosticButton;
    private Button tickDataButton;
    private Button mql5WebsiteButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button resetZoomButton;
    private Button closeButton;
    
    // Daten und Callbacks
    private final String signalId;
    private final String providerName;
    private final SignalData signalData;
    private final String tickFilePath;
    private final MqlRealMonitorGUI parentGui;
    private final Shell parentShell;
    
    // Callback-Interfaces für Aktionen
    public interface ToolbarCallbacks {
        void onRefresh();
        void onZoomIn();
        void onZoomOut();
        void onResetZoom();
        void onClose();
        TickDataLoader.TickDataSet getTickDataSet();
        TimeScale getCurrentTimeScale();
        int getRefreshCounter();
        double getZoomFactor();
        boolean isDataLoaded();
        ChartImageRenderer getImageRenderer();
        TickChartManager getChartManager();
        java.util.List<TickDataLoader.TickData> getFilteredTicks();
    }
    
    private ToolbarCallbacks callbacks;
    
    /**
     * Konstruktor
     */
    public TickChartWindowToolbar(Composite parent, String signalId, String providerName, 
                                 SignalData signalData, String tickFilePath, 
                                 MqlRealMonitorGUI parentGui, Shell parentShell) {
        super(parent, SWT.NONE);
        
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        this.parentGui = parentGui;
        this.parentShell = parentShell;
        
        createButtons();
        setupEventHandlers();
        
        LOGGER.info("TickChartWindowToolbar erstellt für Signal: " + signalId);
    }
    
    /**
     * Setzt die Callback-Schnittstelle
     */
    public void setCallbacks(ToolbarCallbacks callbacks) {
        this.callbacks = callbacks;
    }
    
    /**
     * Erstellt alle Buttons
     */
    private void createButtons() {
        setLayout(new GridLayout(9, false)); // 9 Buttons
        
        refreshButton = new Button(this, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        diagnosticButton = new Button(this, SWT.PUSH);
        diagnosticButton.setText("Diagnostik");
        diagnosticButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        diagnosticButton.setToolTipText("Zeigt detaillierte Diagnostik-Informationen");
        
        tickDataButton = new Button(this, SWT.PUSH);
        tickDataButton.setText("Tickdaten");
        tickDataButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        tickDataButton.setToolTipText("Zeigt die rohen Tickdaten aus der Datei");
        
        mql5WebsiteButton = new Button(this, SWT.PUSH);
        mql5WebsiteButton.setText("🌐 MQL5 Website");
        mql5WebsiteButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        mql5WebsiteButton.setToolTipText("Öffnet die MQL5-Seite dieses Signalproviders im Browser");
        
        zoomInButton = new Button(this, SWT.PUSH);
        zoomInButton.setText("Zoom +");
        zoomInButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        zoomOutButton = new Button(this, SWT.PUSH);
        zoomOutButton.setText("Zoom -");
        zoomOutButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        resetZoomButton = new Button(this, SWT.PUSH);
        resetZoomButton.setText("Reset Zoom");
        resetZoomButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        Label spacer = new Label(this, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        closeButton = new Button(this, SWT.PUSH);
        closeButton.setText("Schließen");
        closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        LOGGER.fine("Toolbar-Buttons erstellt");
    }
    
    /**
     * Setup Event Handler für alle Buttons
     */
    private void setupEventHandlers() {
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (callbacks != null) {
                    callbacks.onRefresh();
                }
            }
        });
        
        diagnosticButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showDiagnosticReport();
            }
        });
        
        tickDataButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showTickDataWindow();
            }
        });
        
        mql5WebsiteButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openMql5Website();
            }
        });
        
        zoomInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (callbacks != null) {
                    callbacks.onZoomIn();
                }
            }
        });
        
        zoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (callbacks != null) {
                    callbacks.onZoomOut();
                }
            }
        });
        
        resetZoomButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (callbacks != null) {
                    callbacks.onResetZoom();
                }
            }
        });
        
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (callbacks != null) {
                    callbacks.onClose();
                }
            }
        });
        
        LOGGER.fine("Event-Handler für Toolbar-Buttons eingerichtet");
    }
    
    /**
     * Öffnet das Tickdaten-Fenster
     */
    private void showTickDataWindow() {
        try {
            LOGGER.info("=== TICKDATEN-FENSTER ANGEFORDERT für Signal: " + signalId + " ===");
            
            TickDataDisplayWindow tickDataWindow = new TickDataDisplayWindow(parentGui);
            tickDataWindow.showTickDataWindow(signalId, providerName);
            
            LOGGER.info("Tickdaten-Fenster erfolgreich geöffnet für Signal: " + signalId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen des Tickdaten-Fensters für Signal: " + signalId, e);
            
            MessageBox errorBox = new MessageBox(parentShell, SWT.ICON_ERROR | SWT.OK);
            errorBox.setText("Fehler beim Öffnen der Tickdaten");
            errorBox.setMessage("Konnte Tickdaten-Fenster für Signal " + signalId + " nicht öffnen:\n\n" + 
                               e.getMessage() + "\n\nTick-Datei: " + tickFilePath);
            errorBox.open();
        }
    }
    
    /**
     * Öffnet die MQL5-Website für diesen Signalprovider
     */
    private void openMql5Website() {
        try {
            LOGGER.info("=== ÖFFNE MQL5 WEBSITE für Signal: " + signalId + " ===");
            
            String websiteUrl = parentGui.getMonitor().getConfig().buildSignalUrl(signalId);
            LOGGER.info("Generierte URL: " + websiteUrl);
            
            if (showWebsiteConfirmationDialog(websiteUrl)) {
                openUrlInBrowser(websiteUrl);
                LOGGER.info("MQL5 Website erfolgreich geöffnet für Signal: " + signalId);
            } else {
                LOGGER.info("Benutzer hat das Öffnen der Website abgebrochen");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Öffnen der MQL5-Website für Signal: " + signalId, e);
            showWebsiteErrorDialog(e);
        }
    }
    
    /**
     * Zeigt Bestätigungsdialog vor dem Öffnen der Website
     */
    private boolean showWebsiteConfirmationDialog(String url) {
        MessageBox confirmBox = new MessageBox(parentShell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("MQL5 Website öffnen");
        
        StringBuilder message = new StringBuilder();
        message.append("MQL5-Seite des Signalproviders im Browser öffnen?\n\n");
        message.append("Signal ID: ").append(signalId).append("\n");
        message.append("Provider: ").append(providerName).append("\n\n");
        message.append("URL: ").append(url).append("\n\n");
        message.append("Diese Aktion öffnet Ihren Standard-Webbrowser.");
        
        confirmBox.setMessage(message.toString());
        
        return confirmBox.open() == SWT.YES;
    }
    
    /**
     * Zeigt Fehlerdialog für Website-Öffnung
     */
    private void showWebsiteErrorDialog(Exception e) {
        MessageBox errorBox = new MessageBox(parentShell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Öffnen der Website");
        
        String url = parentGui.getMonitor().getConfig().buildSignalUrl(signalId);
        
        StringBuilder message = new StringBuilder();
        message.append("Konnte MQL5-Website nicht öffnen:\n\n");
        message.append("Fehler: ").append(e.getMessage()).append("\n\n");
        message.append("Sie können die URL manuell kopieren und im Browser öffnen:\n\n");
        message.append(url);
        
        errorBox.setMessage(message.toString());
        errorBox.open();
    }
    
    /**
     * Öffnet eine URL im Standard-Browser
     */
    private void openUrlInBrowser(String url) throws Exception {
        LOGGER.info("Versuche URL im Browser zu öffnen: " + url);
        
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                URI uri = new URI(url);
                desktop.browse(uri);
                LOGGER.info("URL erfolgreich mit Desktop.browse() geöffnet");
                return;
            }
        }
        
        // Fallback: System-spezifische Kommandos
        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.info("Desktop.browse() nicht verfügbar, verwende OS-spezifischen Ansatz: " + os);
        
        ProcessBuilder processBuilder;
        
        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            processBuilder = new ProcessBuilder("open", url);
        } else {
            processBuilder = new ProcessBuilder("xdg-open", url);
        }
        
        Process process = processBuilder.start();
        
        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        if (finished) {
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                LOGGER.info("URL erfolgreich mit OS-Kommando geöffnet (Exit-Code: " + exitCode + ")");
            } else {
                LOGGER.warning("OS-Kommando beendet mit Exit-Code: " + exitCode);
            }
        } else {
            LOGGER.info("OS-Kommando läuft noch (normal für Browser-Start)");
        }
    }
    
    /**
     * Zeigt einen detaillierten Diagnostik-Bericht
     */
    private void showDiagnosticReport() {
        if (callbacks == null) {
            LOGGER.warning("Callbacks nicht gesetzt - kann Diagnostik-Bericht nicht erstellen");
            return;
        }
        
        LOGGER.info("=== DIAGNOSTIK-BERICHT ANGEFORDERT für Signal: " + signalId + " ===");
        
        StringBuilder report = new StringBuilder();
        report.append("=== DIAGNOSTIK-BERICHT für Signal ").append(signalId).append(" ===\n\n");
        
        // Grunddaten
        report.append("GRUNDDATEN:\n");
        report.append("Signal ID: ").append(signalId).append("\n");
        report.append("Provider Name: ").append(providerName).append("\n");
        report.append("Tick-Datei: ").append(tickFilePath).append("\n");
        report.append("Aktuelle Zeitskala: ").append(callbacks.getCurrentTimeScale() != null ? callbacks.getCurrentTimeScale().getLabel() : "NULL").append("\n");
        report.append("Refresh Counter: ").append(callbacks.getRefreshCounter()).append("\n");
        report.append("Data Loaded: ").append(callbacks.isDataLoaded()).append("\n");
        report.append("Zoom Factor: ").append(callbacks.getZoomFactor()).append("\n");
        report.append("Chart Typ: DRAWDOWN + PROFIT (DUAL-CHART) + TICKDATEN + MQL5 LINK\n\n");
        
        // SignalData Info
        report.append("SIGNALDATA:\n");
        if (signalData != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Details: ").append(signalData.getSummary()).append("\n");
            report.append("MQL5 URL: ").append(parentGui.getMonitor().getConfig().buildSignalUrl(signalId)).append("\n");
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // TickDataSet Info
        TickDataLoader.TickDataSet tickDataSet = callbacks.getTickDataSet();
        report.append("TICK DATASET:\n");
        if (tickDataSet != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            if (tickDataSet.getTickCount() > 0) {
                report.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                       .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            }
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // Gefilterte Daten
        java.util.List<TickDataLoader.TickData> filteredTicks = callbacks.getFilteredTicks();
        report.append("GEFILTERTE DATEN:\n");
        if (filteredTicks != null) {
            report.append("Verfügbar: Ja\n");
            report.append("Anzahl: ").append(filteredTicks.size()).append("\n");
        } else {
            report.append("Verfügbar: Nein\n");
        }
        report.append("\n");
        
        // Chart Status
        ChartImageRenderer imageRenderer = callbacks.getImageRenderer();
        TickChartManager chartManager = callbacks.getChartManager();
        report.append("CHARTS STATUS:\n");
        report.append("ChartManager: ").append(chartManager != null ? "OK" : "NULL").append("\n");
        report.append("ImageRenderer: ").append(imageRenderer != null ? "OK" : "NULL").append("\n");
        report.append("HasValidDrawdownImage: ").append(imageRenderer != null ? imageRenderer.hasValidDrawdownImage() : "N/A").append("\n");
        report.append("HasValidProfitImage: ").append(imageRenderer != null ? imageRenderer.hasValidProfitImage() : "N/A").append("\n");
        
        // Zeige Bericht in einem Dialog
        showDiagnosticDialog(report.toString());
    }
    
    /**
     * Zeigt den Diagnostik-Bericht in einem Dialog
     */
    private void showDiagnosticDialog(String reportText) {
        Shell diagnosticShell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.MODELESS | SWT.RESIZE);
        diagnosticShell.setText("Diagnostik-Bericht - " + signalId);
        diagnosticShell.setSize(600, 500);
        diagnosticShell.setLayout(new GridLayout(1, false));
        
        Text reportTextWidget = new Text(diagnosticShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        reportTextWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        reportTextWidget.setText(reportText);
        reportTextWidget.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
        
        Button closeReportButton = new Button(diagnosticShell, SWT.PUSH);
        closeReportButton.setText("Schließen");
        closeReportButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        closeReportButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                diagnosticShell.close();
            }
        });
        
        // Zentriere Dialog
        Point parentLocation = parentShell.getLocation();
        Point parentSize = parentShell.getSize();
        Point dialogSize = diagnosticShell.getSize();
        
        int x = parentLocation.x + (parentSize.x - dialogSize.x) / 2;
        int y = parentLocation.y + (parentSize.y - dialogSize.y) / 2;
        diagnosticShell.setLocation(x, y);
        
        diagnosticShell.open();
        
        LOGGER.info("Diagnostik-Bericht angezeigt");
    }
    
    /**
     * Aktiviert/Deaktiviert den Refresh-Button
     */
    public void setRefreshEnabled(boolean enabled) {
        if (!refreshButton.isDisposed()) {
            refreshButton.setEnabled(enabled);
        }
    }
    
    /**
     * Setzt den Text des Refresh-Buttons
     */
    public void setRefreshText(String text) {
        if (!refreshButton.isDisposed()) {
            refreshButton.setText(text);
        }
    }
    
    /**
     * Gibt den Refresh-Button zurück
     */
    public Button getRefreshButton() {
        return refreshButton;
    }
    
    @Override
    public void dispose() {
        LOGGER.fine("TickChartWindowToolbar wird disposed für Signal: " + signalId);
        super.dispose();
    }
}