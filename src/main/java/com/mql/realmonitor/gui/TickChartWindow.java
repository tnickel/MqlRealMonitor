package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
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
    private final Display display;
    
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
        this.display = parent.getDisplay();
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
        
        // Chart erstellen BEVOR Frame erstellt wird
        createChart();
        
        // AWT Frame für JFreeChart
        Frame chartFrame = SWT_AWT.new_Frame(chartComposite);
        chartFrame.setLayout(new BorderLayout());
        
        // ChartPanel hinzufügen
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setDomainZoomable(true);
        
        // Explizite Hintergrundfarbe für ChartPanel
        chartPanel.setBackground(java.awt.Color.WHITE);
        
        chartFrame.add(chartPanel, BorderLayout.CENTER);
        
        // Frame explizit sichtbar machen
        chartFrame.setVisible(true);
        chartFrame.validate();
        
        LOGGER.info("Chart-Bereich erstellt mit SWT-AWT Bridge");
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
        
        // Debug: Chart-Info ausgeben
        LOGGER.info("Chart erstellt für Signal " + signalId + " mit Provider " + providerName);
    }
    
    /**
     * Konfiguriert das Chart (Farben, Renderer etc.)
     */
    private void configureChart() {
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);  // Equity
        renderer.setSeriesShapesVisible(0, true); // Punkte für Equity anzeigen
        renderer.setSeriesLinesVisible(1, true);  // Floating Profit
        renderer.setSeriesShapesVisible(1, true); // Punkte für Floating anzeigen
        renderer.setSeriesLinesVisible(2, true);  // Total Value
        renderer.setSeriesShapesVisible(2, true); // Punkte auch für Total anzeigen
        
        // Farben setzen
        renderer.setSeriesPaint(0, new Color(255, 200, 0));  // Equity in Gelb
        renderer.setSeriesPaint(1, Color.RED);               // Floating Profit in Rot
        renderer.setSeriesPaint(2, new Color(0, 200, 0));    // Gesamtwert in Grün
        
        // Linienstärke
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));
        renderer.setSeriesStroke(1, new BasicStroke(3.0f));
        renderer.setSeriesStroke(2, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 
                                                    1.0f, new float[]{5.0f, 5.0f}, 0.0f)); // Gestrichelt
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        chart.setBackgroundPaint(new Color(240, 240, 240)); // Hellgrau
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Wert (HKD)");
        plot.getDomainAxis().setLabel("Zeit");
        
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
            
            // Chart-Daten im AWT Event Thread aktualisieren
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    // Chart-Daten aktualisieren
                    updateChartData();
                    
                    // Chart-Komponente neu validieren
                    if (chartPanel != null) {
                        chartPanel.revalidate();
                        chartPanel.repaint();
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren des Charts", e);
                }
            });
            
            // Info-Panel aktualisieren (im SWT Thread)
            display.asyncExec(() -> {
                updateInfoPanel();
            });
            
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
        
        int addedCount = 0;
        
        // Tick-Daten zu den Serien hinzufügen
        for (TickDataLoader.TickData tick : tickDataSet.getTicks()) {
            Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
            Second second = new Second(javaDate);
            
            try {
                equitySeries.add(second, tick.getEquity());
                floatingProfitSeries.add(second, tick.getFloatingProfit());
                totalValueSeries.add(second, tick.getTotalValue());
                addedCount++;
                
                // Debug ersten und letzten Wert
                if (addedCount == 1 || addedCount == tickDataSet.getTickCount()) {
                    LOGGER.info("Tick " + addedCount + ": Zeit=" + tick.getTimestamp() + 
                               ", Equity=" + tick.getEquity() + 
                               ", Floating=" + tick.getFloatingProfit() + 
                               ", Total=" + tick.getTotalValue());
                }
            } catch (Exception e) {
                LOGGER.warning("Fehler beim Hinzufügen von Tick-Daten: " + e.getMessage());
            }
        }
        
        // Chart-Titel aktualisieren
        chart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                      tickDataSet.getTickCount() + " Ticks");
        
        // Y-Achsen-Bereich anpassen bei konstanten Werten
        adjustYAxisRange();
        
        // Chart explizit neu zeichnen
        chart.fireChartChanged();
        
        // Chart-Panel neu zeichnen
        if (chartPanel != null) {
            chartPanel.revalidate();
            chartPanel.repaint();
        }
        
        LOGGER.info("Chart-Daten aktualisiert: " + addedCount + " von " + 
                   tickDataSet.getTickCount() + " Ticks hinzugefügt");
    }
    
    /**
     * Passt den Y-Achsen-Bereich an, besonders bei konstanten Werten
     */
    private void adjustYAxisRange() {
        XYPlot plot = chart.getXYPlot();
        
        // Min/Max-Werte ermitteln
        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;
        
        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
            // Berücksichtige alle drei Datenreihen
            minValue = Math.min(minValue, tickDataSet.getMinEquity());
            minValue = Math.min(minValue, tickDataSet.getMinFloatingProfit());
            minValue = Math.min(minValue, tickDataSet.getMinTotalValue());
            
            maxValue = Math.max(maxValue, tickDataSet.getMaxEquity());
            maxValue = Math.max(maxValue, tickDataSet.getMaxFloatingProfit());
            maxValue = Math.max(maxValue, tickDataSet.getMaxTotalValue());
            
            LOGGER.info("Chart Y-Achse: Min=" + minValue + ", Max=" + maxValue);
        }
        
        // Immer den vollen Bereich anzeigen, inklusive 0
        if (minValue > 0) {
            minValue = 0; // Stelle sicher, dass 0 im Bereich ist
        }
        
        // Füge immer etwas Padding hinzu
        double range = maxValue - minValue;
        double padding = Math.max(range * 0.05, 100); // 5% oder mindestens 100
        
        plot.getRangeAxis().setRange(minValue - padding, maxValue + padding);
        LOGGER.info("Y-Achse eingestellt auf: " + (minValue - padding) + " bis " + (maxValue + padding));
        
        // Auto-Range für X-Achse (Zeit)
        plot.getDomainAxis().setAutoRange(true);
        
        // Stelle sicher, dass die Achsen sichtbar sind
        plot.getRangeAxis().setVisible(true);
        plot.getDomainAxis().setVisible(true);
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
            
            // Hinweis bei konstanten Werten
            if (tickDataSet.getMaxEquity() == tickDataSet.getMinEquity() && 
                tickDataSet.getMaxFloatingProfit() == tickDataSet.getMinFloatingProfit()) {
                details.append("\n=== Hinweis ===\n");
                details.append("Alle Werte sind konstant. Die Linien werden als horizontale Linien angezeigt.\n");
                details.append("Gelbe Linie (Equity): ").append(String.format("%.2f", tickDataSet.getMaxEquity())).append("\n");
                details.append("Rote Linie (Floating): ").append(String.format("%.2f", tickDataSet.getMaxFloatingProfit())).append("\n");
                details.append("Grüne Linie (Gesamt): ").append(String.format("%.2f", tickDataSet.getMaxTotalValue())).append("\n");
            }
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
        
        // Zeige ein Test-Chart mit Dummy-Daten
        createTestChart();
        
        LOGGER.warning("Keine Tick-Daten verfügbar für Signal: " + signalId);
    }
    
    /**
     * Erstellt ein Test-Chart mit Dummy-Daten
     */
    private void createTestChart() {
        // Füge einige Test-Datenpunkte hinzu
        equitySeries.clear();
        floatingProfitSeries.clear();
        totalValueSeries.clear();
        
        try {
            Date now = new Date();
            for (int i = 0; i < 10; i++) {
                Second second = new Second(new Date(now.getTime() - (9 - i) * 60000)); // 1 Minute Intervalle
                
                double equity = 50000 + i * 100;
                double floating = -500 + i * 50;
                double total = equity + floating;
                
                equitySeries.add(second, equity);
                floatingProfitSeries.add(second, floating);
                totalValueSeries.add(second, total);
            }
            
            chart.setTitle("Test-Chart (Keine echten Daten) - " + signalId);
            
            // Y-Achse anpassen
            XYPlot plot = chart.getXYPlot();
            plot.getRangeAxis().setRange(-1000, 51000);
            
            // Chart neu zeichnen
            chart.fireChartChanged();
            
            if (chartPanel != null) {
                chartPanel.revalidate();
                chartPanel.repaint();
            }
            
            LOGGER.info("Test-Chart mit Dummy-Daten erstellt");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Erstellen des Test-Charts: " + e.getMessage());
        }
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
        
        // Test-Chart erstellen zum Debugging
        SwingUtilities.invokeLater(() -> {
            createTestChart();
        });
        
        // Dann echte Daten laden
        display.timerExec(500, () -> {
            loadAndDisplayData();
        });
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
        
        // Force refresh nach dem Öffnen
        display.asyncExec(() -> {
            if (chartPanel != null && !shell.isDisposed()) {
                chartPanel.revalidate();
                chartPanel.repaint();
                
                // Zusätzlicher Refresh nach kurzer Verzögerung
                display.timerExec(100, () -> {
                    if (chartPanel != null && !shell.isDisposed()) {
                        chartPanel.revalidate();
                        chartPanel.repaint();
                    }
                });
            }
        });
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