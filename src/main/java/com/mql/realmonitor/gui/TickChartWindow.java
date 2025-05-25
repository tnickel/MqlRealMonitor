package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Label;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * Fenster für die graphische Anzeige der Tick-Daten
 * Zeigt Equity-Kurve in Gelb und Floating Profit in Rot
 */
public class TickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindow.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private Composite chartComposite;
    private Label infoLabel;
    private Text detailsText;
    private Button refreshButton;
    private Button closeButton;
    
    // Chart Komponenten
    private ChartPanel chartPanel;
    private JFreeChart chart;
    private TimeSeries equitySeries;
    private TimeSeries floatingProfitSeries;
    private TimeSeries totalValueSeries;
    
    // Daten
    private final String signalId;
    private final String providerName;
    private final SignalData signalData;
    private final String tickFilePath;
    private TickDataLoader.TickDataSet tickDataSet;
    
    // Parent GUI für Callbacks
    private final MqlRealMonitorGUI parentGui;
    
    /**
     * Konstruktor
     * 
     * @param parent Das Parent-Shell
     * @param parentGui Die Parent-GUI für Callbacks
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     * @param signalData Die aktuellen Signal-Daten
     * @param tickFilePath Pfad zur Tick-Datei
     */
    public TickChartWindow(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                          String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        createWindow(parent);
        loadAndDisplayData();
    }
    
    /**
     * Erstellt das Hauptfenster
     * 
     * @param parent Das Parent-Shell
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Tick Chart - " + signalId + " (" + providerName + ")");
        shell.setSize(1000, 700);
        shell.setLayout(new GridLayout(1, false));
        
        // Fenster zentrieren
        centerWindow(parent);
        
        // Info-Panel erstellen
        createInfoPanel();
        
        // Chart-Bereich erstellen
        createChartArea();
        
        // Button-Panel erstellen
        createButtonPanel();
        
        // Event Handler
        setupEventHandlers();
        
        LOGGER.info("Tick Chart Fenster erstellt für Signal: " + signalId);
    }
    
    /**
     * Zentriert das Fenster relativ zum Parent
     * 
     * @param parent Das Parent-Shell
     */
    private void centerWindow(Shell parent) {
        Point parentSize = parent.getSize();
        Point parentLocation = parent.getLocation();
        Point size = shell.getSize();
        
        int x = parentLocation.x + (parentSize.x - size.x) / 2;
        int y = parentLocation.y + (parentSize.y - size.y) / 2;
        
        shell.setLocation(x, y);
    }
    
    /**
     * Erstellt das Info-Panel
     */
    private void createInfoPanel() {
        Group infoGroup = new Group(shell, SWT.NONE);
        infoGroup.setText("Signal Information");
        infoGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        infoGroup.setLayout(new GridLayout(2, false));
        
        // Info-Label für Grunddaten
        infoLabel = new Label(infoGroup, SWT.WRAP);
        GridData infoLabelData = new GridData(SWT.FILL, SWT.FILL, true, false);
        infoLabelData.horizontalSpan = 2;
        infoLabel.setLayoutData(infoLabelData);
        
        // Details-Text für erweiterte Informationen
        detailsText = new Text(infoGroup, SWT.BORDER | SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsData.horizontalSpan = 2;
        detailsData.heightHint = 80;
        detailsText.setLayoutData(detailsData);
        
        updateInfoPanel();
    }
    
    /**
     * Erstellt den Chart-Bereich
     */
    private void createChartArea() {
        Group chartGroup = new Group(shell, SWT.NONE);
        chartGroup.setText("Tick Data Chart");
        chartGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartGroup.setLayout(new GridLayout(1, false));
        
        // SWT-AWT Bridge für JFreeChart
        chartComposite = new Composite(chartGroup, SWT.EMBEDDED | SWT.NO_BACKGROUND);
        chartComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        // AWT Frame für JFreeChart
        Frame chartFrame = SWT_AWT.new_Frame(chartComposite);
        chartFrame.setLayout(new BorderLayout());
        
        // Chart erstellen
        createChart();
        
        // ChartPanel hinzufügen
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setDomainZoomable(true);
        
        chartFrame.add(chartPanel, BorderLayout.CENTER);
        chartFrame.validate();
    }
    
    /**
     * Erstellt das JFreeChart
     */
    private void createChart() {
        // TimeSeries für die verschiedenen Datenreihen
        equitySeries = new TimeSeries("Equity (Kontostand)");
        floatingProfitSeries = new TimeSeries("Floating Profit");
        totalValueSeries = new TimeSeries("Gesamtwert");
        
        // TimeSeriesCollection erstellen
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(equitySeries);
        dataset.addSeries(floatingProfitSeries);
        dataset.addSeries(totalValueSeries);
        
        // Chart erstellen
        chart = ChartFactory.createTimeSeriesChart(
            "Tick Daten - " + signalId + " (" + providerName + ")",
            "Zeit",
            "Wert",
            dataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Chart konfigurieren
        configureChart();
    }
    
    /**
     * Konfiguriert das Chart (Farben, Renderer etc.)
     */
    private void configureChart() {
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);  // Equity
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesLinesVisible(1, true);  // Floating Profit
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesLinesVisible(2, true);  // Total Value
        renderer.setSeriesShapesVisible(2, false);
        
        // Farben setzen
        renderer.setSeriesPaint(0, Color.YELLOW);      // Equity in Gelb
        renderer.setSeriesPaint(1, Color.RED);         // Floating Profit in Rot
        renderer.setSeriesPaint(2, Color.GREEN);       // Gesamtwert in Grün
        
        // Linienstärke
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        renderer.setSeriesStroke(2, new BasicStroke(2.0f));
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        LOGGER.info("Chart konfiguriert mit 3 Datenreihen");
    }
    
    /**
     * Erstellt das Button-Panel
     */
    private void createButtonPanel() {
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonComposite.setLayout(new GridLayout(3, false));
        
        // Refresh-Button
        refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        // Spacer
        Label spacer = new Label(buttonComposite, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        
        // Close-Button
        closeButton = new Button(buttonComposite, SWT.PUSH);
        closeButton.setText("Schließen");
        closeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    }
    
    /**
     * Setup Event Handler
     */
    private void setupEventHandlers() {
        // Refresh-Button
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshData();
            }
        });
        
        // Close-Button
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                closeWindow();
            }
        });
        
        // Shell-Close Event
        shell.addListener(SWT.Close, event -> {
            closeWindow();
        });
    }
    
    /**
     * Lädt und zeigt die Tick-Daten an
     */
    private void loadAndDisplayData() {
        try {
            // Tick-Daten laden
            tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
            
            if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                showNoDataMessage();
                return;
            }
            
            // Chart-Daten aktualisieren
            updateChartData();
            
            // Info-Panel aktualisieren
            updateInfoPanel();
            
            LOGGER.info("Tick-Daten geladen und angezeigt: " + tickDataSet.getTickCount() + " Ticks");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Laden der Tick-Daten", e);
            showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert die Chart-Daten
     */
    private void updateChartData() {
        if (tickDataSet == null) {
            return;
        }
        
        // Serien leeren
        equitySeries.clear();
        floatingProfitSeries.clear();
        totalValueSeries.clear();
        
        // Tick-Daten zu den Serien hinzufügen
        for (TickDataLoader.TickData tick : tickDataSet.getTicks()) {
            Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            Second second = new Second(javaDate);
            
            equitySeries.add(second, tick.getEquity());
            floatingProfitSeries.add(second, tick.getFloatingProfit());
            totalValueSeries.add(second, tick.getTotalValue());
        }
        
        // Chart-Titel aktualisieren
        chart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                      tickDataSet.getTickCount() + " Ticks");
        
        // Chart neu zeichnen
        if (chartPanel != null) {
            chartPanel.repaint();
        }
        
        LOGGER.info("Chart-Daten aktualisiert mit " + tickDataSet.getTickCount() + " Ticks");
    }
    
    /**
     * Aktualisiert das Info-Panel
     */
    private void updateInfoPanel() {
        StringBuilder info = new StringBuilder();
        
        // Grundinformationen
        info.append("Signal ID: ").append(signalId).append("   ");
        info.append("Provider: ").append(providerName).append("   ");
        
        if (signalData != null) {
            info.append("Aktuell: ").append(signalData.getFormattedTotalValue())
                .append(" ").append(signalData.getCurrency()).append("   ");
            info.append("Stand: ").append(signalData.getFormattedTimestamp());
        }
        
        infoLabel.setText(info.toString());
        
        // Detaillierte Informationen
        StringBuilder details = new StringBuilder();
        
        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
            details.append("=== Tick-Daten Statistik ===\n");
            details.append("Datei: ").append(tickFilePath).append("\n");
            details.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            details.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                   .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            details.append("Equity-Bereich: ").append(String.format("%.2f - %.2f", 
                         tickDataSet.getMinEquity(), tickDataSet.getMaxEquity())).append("\n");
            details.append("Floating Profit-Bereich: ").append(String.format("%.2f - %.2f", 
                         tickDataSet.getMinFloatingProfit(), tickDataSet.getMaxFloatingProfit())).append("\n");
            details.append("Gesamtwert-Bereich: ").append(String.format("%.2f - %.2f", 
                         tickDataSet.getMinTotalValue(), tickDataSet.getMaxTotalValue())).append("\n");
            
            // Letzte Werte
            TickDataLoader.TickData latestTick = tickDataSet.getLatestTick();
            details.append("\n=== Aktuelle Werte ===\n");
            details.append("Letzter Tick: ").append(latestTick.getTimestamp()).append("\n");
            details.append("Equity: ").append(String.format("%.2f", latestTick.getEquity())).append("\n");
            details.append("Floating Profit: ").append(String.format("%.2f", latestTick.getFloatingProfit())).append("\n");
            details.append("Gesamtwert: ").append(String.format("%.2f", latestTick.getTotalValue())).append("\n");
        } else {
            details.append("Keine Tick-Daten verfügbar.\n");
            details.append("Datei: ").append(tickFilePath).append("\n");
            details.append("Bitte überprüfen Sie, ob die Datei existiert und gültige Daten enthält.");
        }
        
        detailsText.setText(details.toString());
    }
    
    /**
     * Zeigt eine "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten verfügbar");
        detailsText.setText("Keine Tick-Daten gefunden.\n\n" +
                           "Mögliche Ursachen:\n" +
                           "• Die Tick-Datei existiert nicht: " + tickFilePath + "\n" +
                           "• Die Datei ist leer oder enthält keine gültigen Daten\n" +
                           "• Der Dateiformat ist ungültig\n\n" +
                           "Erwartetes Format: Datum,Uhrzeit,Equity,FloatingProfit\n" +
                           "Beispiel: 24.05.2025,15:13:36,2000.00,-479.54");
        
        // Chart-Titel aktualisieren
        chart.setTitle("Keine Tick-Daten verfügbar - " + signalId);
        
        LOGGER.warning("Keine Tick-Daten verfügbar für Signal: " + signalId);
    }
    
    /**
     * Zeigt eine Fehlermeldung
     * 
     * @param message Die Fehlermeldung
     */
    private void showErrorMessage(String message) {
        infoLabel.setText("Signal ID: " + signalId + " - Fehler beim Laden");
        detailsText.setText("FEHLER: " + message + "\n\n" +
                           "Datei: " + tickFilePath + "\n\n" +
                           "Bitte überprüfen Sie:\n" +
                           "• Existiert die Datei?\n" +
                           "• Haben Sie Leserechte?\n" +
                           "• Ist das Dateiformat korrekt?");
        
        chart.setTitle("Fehler beim Laden - " + signalId);
        
        // Fehlermeldung auch als MessageBox anzeigen
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Laden der Tick-Daten");
        errorBox.setMessage(message);
        errorBox.open();
    }
    
    /**
     * Aktualisiert die Daten
     */
    private void refreshData() {
        LOGGER.info("Tick-Daten werden aktualisiert für Signal: " + signalId);
        loadAndDisplayData();
    }
    
    /**
     * Schließt das Fenster
     */
    private void closeWindow() {
        LOGGER.info("Tick Chart Fenster wird geschlossen für Signal: " + signalId);
        
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Zeigt das Fenster an
     */
    public void open() {
        shell.open();
        LOGGER.info("Tick Chart Fenster geöffnet für Signal: " + signalId);
    }
    
    /**
     * Prüft ob das Fenster noch geöffnet ist
     * 
     * @return true wenn das Fenster geöffnet ist
     */
    public boolean isOpen() {
        return shell != null && !shell.isDisposed();
    }
    
    /**
     * Gibt die Signal-ID zurück
     * 
     * @return Die Signal-ID
     */
    public String getSignalId() {
        return signalId;
    }
}
