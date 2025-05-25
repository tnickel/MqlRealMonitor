package com.mql.realmonitor.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.parser.SignalData;

/**
 * OHNE SWT-AWT BRIDGE: JFreeChart als Image-Rendering in SWT Canvas
 * 
 * VORTEILE:
 * - Keine Threading-Konflikte
 * - Keine Bridge-Probleme
 * - Stabiles Rendering
 * - Vollständige SWT-Integration
 * - Bessere Performance
 */
public class TickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindow.class.getName());
    
    // UI Komponenten
    private Shell shell;
    private Canvas chartCanvas;
    private Label infoLabel;
    private Text detailsText;
    private Button refreshButton;
    private Button closeButton;
    private Button zoomInButton;
    private Button zoomOutButton;
    private Button resetZoomButton;
    
    // Chart Komponenten (nur für Rendering, nicht für Display)
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
    
    // Chart-Image Verwaltung
    private Image chartImage;
    private int chartWidth = 800;
    private int chartHeight = 400;
    private double zoomFactor = 1.0;
    
    // Status-Flags
    private volatile boolean isDataLoaded = false;
    private volatile boolean isWindowClosed = false;
    
    /**
     * Konstruktor
     */
    public TickChartWindow(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                          String providerName, SignalData signalData, String tickFilePath) {
        this.parentGui = parentGui;
        this.display = parent.getDisplay();
        this.signalId = signalId;
        this.providerName = providerName;
        this.signalData = signalData;
        this.tickFilePath = tickFilePath;
        
        LOGGER.info("Erstelle TickChartWindow OHNE Bridge für Signal: " + signalId + " (" + providerName + ")");
        
        createWindow(parent);
        createChart();
        loadDataAsync();
    }
    
    /**
     * Erstellt das Hauptfenster
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
        
        // Chart-Canvas erstellen (KEIN AWT!)
        createChartCanvas();
        
        // Button-Panel erstellen
        createButtonPanel();
        
        // Event Handler
        setupEventHandlers();
        
        LOGGER.info("TickChartWindow UI erstellt (OHNE Bridge) für Signal: " + signalId);
    }
    
    /**
     * Zentriert das Fenster relativ zum Parent
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
        
        // Initialer Info-Text
        updateInfoPanelInitial();
    }
    
    /**
     * KERN-INNOVATION: Chart-Canvas OHNE SWT-AWT Bridge
     */
    private void createChartCanvas() {
        Group chartGroup = new Group(shell, SWT.NONE);
        chartGroup.setText("Tick Data Chart");
        chartGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartGroup.setLayout(new GridLayout(1, false));
        
        // Reiner SWT Canvas für Chart-Anzeige
        chartCanvas = new Canvas(chartGroup, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        chartCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // Paint-Listener: Zeichnet das Chart-Image
        chartCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintChart(e.gc);
            }
        });
        
        // Resize-Listener: Chart bei Größenänderung neu rendern
        chartCanvas.addListener(SWT.Resize, event -> {
            Point size = chartCanvas.getSize();
            if (size.x > 0 && size.y > 0) {
                chartWidth = size.x;
                chartHeight = size.y;
                LOGGER.info("Canvas Größe geändert: " + chartWidth + "x" + chartHeight);
                renderChartToImage();
            }
        });
        
        LOGGER.info("Chart-Canvas erstellt (SWT-only, keine Bridge)");
    }
    
    /**
     * Erstellt das Button-Panel mit Zoom-Funktionen
     */
    private void createButtonPanel() {
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        buttonComposite.setLayout(new GridLayout(6, false));
        
        // Refresh-Button
        refreshButton = new Button(buttonComposite, SWT.PUSH);
        refreshButton.setText("Aktualisieren");
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        // Zoom-Buttons
        zoomInButton = new Button(buttonComposite, SWT.PUSH);
        zoomInButton.setText("Zoom +");
        zoomInButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        zoomOutButton = new Button(buttonComposite, SWT.PUSH);
        zoomOutButton.setText("Zoom -");
        zoomOutButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
        resetZoomButton = new Button(buttonComposite, SWT.PUSH);
        resetZoomButton.setText("Reset Zoom");
        resetZoomButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        
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
        
        // Zoom-Buttons
        zoomInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor *= 1.2;
                renderChartToImage();
                LOGGER.info("Zoom In: " + zoomFactor);
            }
        });
        
        zoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor /= 1.2;
                renderChartToImage();
                LOGGER.info("Zoom Out: " + zoomFactor);
            }
        });
        
        resetZoomButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor = 1.0;
                renderChartToImage();
                LOGGER.info("Zoom Reset");
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
     * Erstellt das JFreeChart (nur für Rendering, nicht für Display)
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
        
        LOGGER.info("JFreeChart (für Rendering) erstellt für Signal: " + signalId);
    }
    
    /**
     * Konfiguriert das Chart (Farben, Renderer etc.)
     */
    private void configureChart() {
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);  // Equity
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesLinesVisible(1, true);  // Floating Profit
        renderer.setSeriesShapesVisible(1, true);
        renderer.setSeriesLinesVisible(2, true);  // Total Value
        renderer.setSeriesShapesVisible(2, true);
        
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
        chart.setBackgroundPaint(new Color(240, 240, 240));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Wert (HKD)");
        plot.getDomainAxis().setLabel("Zeit");
    }
    
    /**
     * KERN: Rendert JFreeChart als BufferedImage und konvertiert zu SWT Image
     */
    private void renderChartToImage() {
        if (chart == null || chartWidth <= 0 || chartHeight <= 0) {
            return;
        }
        
        try {
            // JFreeChart als BufferedImage rendern (reines AWT, kein Display nötig)
            BufferedImage bufferedImage = chart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(chartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (chartImage != null && !chartImage.isDisposed()) {
                chartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            chartImage = new Image(display, imageData);
            
            // Canvas neu zeichnen
            if (!chartCanvas.isDisposed()) {
                chartCanvas.redraw();
            }
            
            LOGGER.fine("Chart als Image gerendert: " + chartWidth + "x" + chartHeight + " (Zoom: " + zoomFactor + ")");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Charts als Image", e);
        }
    }
    
    /**
     * Konvertiert BufferedImage zu SWT ImageData
     */
    private ImageData convertBufferedImageToImageData(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        
        // RGB-Daten extrahieren
        int[] rgbArray = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        
        // ImageData erstellen
        ImageData imageData = new ImageData(width, height, 24, new org.eclipse.swt.graphics.PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        
        // Pixel-Daten kopieren
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = rgbArray[y * width + x];
                imageData.setPixel(x, y, rgb & 0xFFFFFF);
            }
        }
        
        return imageData;
    }
    
    /**
     * Zeichnet das Chart-Image auf dem Canvas
     */
    private void paintChart(GC gc) {
        // Canvas leeren
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(chartCanvas.getBounds());
        
        if (chartImage != null && !chartImage.isDisposed()) {
            // Chart-Image zeichnen
            org.eclipse.swt.graphics.Rectangle imageBounds = chartImage.getBounds();
            org.eclipse.swt.graphics.Rectangle canvasBounds = chartCanvas.getBounds();
            
            // Image zentriert zeichnen
            int x = (canvasBounds.width - imageBounds.width) / 2;
            int y = (canvasBounds.height - imageBounds.height) / 2;
            
            gc.drawImage(chartImage, Math.max(0, x), Math.max(0, y));
            
        } else if (!isDataLoaded) {
            // Loading-Message zeichnen
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Chart wird geladen...", 20, 20, true);
            
        } else {
            // Error-Message zeichnen
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("Fehler beim Laden des Charts", 20, 20, true);
        }
    }
    
    /**
     * Lädt Daten asynchron
     */
    private void loadDataAsync() {
        new Thread(() -> {
            try {
                LOGGER.info("Lade Tick-Daten für Signal: " + signalId + " von " + tickFilePath);
                
                // Tick-Daten laden
                tickDataSet = TickDataLoader.loadTickData(tickFilePath, signalId);
                isDataLoaded = true;
                
                if (tickDataSet == null || tickDataSet.getTickCount() == 0) {
                    LOGGER.warning("Keine Tick-Daten gefunden für Signal: " + signalId);
                    
                    display.asyncExec(() -> {
                        if (!isWindowClosed && !shell.isDisposed()) {
                            showNoDataMessage();
                        }
                    });
                    return;
                }
                
                LOGGER.info("Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks für Signal: " + signalId);
                
                // Chart-Daten aktualisieren
                updateChartData();
                
                // UI-Updates im SWT Thread
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateInfoPanel();
                        renderChartToImage();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden der Tick-Daten für Signal: " + signalId, e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "TickDataLoader-" + signalId).start();
    }
    
    /**
     * Aktualisiert die Chart-Daten
     */
    private void updateChartData() {
        if (tickDataSet == null || chart == null) {
            return;
        }
        
        try {
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
            
            // Y-Achsen-Bereich anpassen
            adjustYAxisRange();
            
            LOGGER.info("Chart-Daten aktualisiert: " + tickDataSet.getTickCount() + " Ticks hinzugefügt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren der Chart-Daten", e);
        }
    }
    
    /**
     * Passt den Y-Achsen-Bereich an
     */
    private void adjustYAxisRange() {
        if (chart == null || tickDataSet == null) {
            return;
        }
        
        XYPlot plot = chart.getXYPlot();
        
        double minValue = Math.min(Math.min(tickDataSet.getMinEquity(), tickDataSet.getMinFloatingProfit()), 
                                  tickDataSet.getMinTotalValue());
        double maxValue = Math.max(Math.max(tickDataSet.getMaxEquity(), tickDataSet.getMaxFloatingProfit()), 
                                  tickDataSet.getMaxTotalValue());
        
        if (minValue > 0) {
            minValue = 0;
        }
        
        double range = maxValue - minValue;
        double padding = Math.max(range * 0.05, 100);
        
        plot.getRangeAxis().setRange(minValue - padding, maxValue + padding);
        plot.getDomainAxis().setAutoRange(true);
    }
    
    /**
     * Zeigt initialen Info-Text
     */
    private void updateInfoPanelInitial() {
        String info = "Signal ID: " + signalId + "   Provider: " + providerName + "   Status: Lädt...";
        infoLabel.setText(info);
        detailsText.setText("Tick-Daten werden geladen...\nBitte warten Sie einen Moment.");
    }
    
    /**
     * Aktualisiert das Info-Panel
     */
    private void updateInfoPanel() {
        StringBuilder info = new StringBuilder();
        
        info.append("Signal ID: ").append(signalId).append("   ");
        info.append("Provider: ").append(providerName).append("   ");
        
        if (signalData != null) {
            info.append("Aktuell: ").append(signalData.getFormattedTotalValue())
                .append(" ").append(signalData.getCurrency()).append("   ");
            info.append("Stand: ").append(signalData.getFormattedTimestamp());
        }
        
        infoLabel.setText(info.toString());
        
        StringBuilder details = new StringBuilder();
        
        if (tickDataSet != null && tickDataSet.getTickCount() > 0) {
            details.append("=== Tick-Daten Statistik ===\n");
            details.append("Rendering: JFreeChart ohne SWT-AWT Bridge\n");
            details.append("Datei: ").append(tickFilePath).append("\n");
            details.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            details.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                   .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
        } else {
            details.append("Keine Tick-Daten verfügbar oder noch nicht geladen.\n");
        }
        
        detailsText.setText(details.toString());
    }
    
    /**
     * Zeigt "Keine Daten"-Nachricht
     */
    private void showNoDataMessage() {
        infoLabel.setText("Signal ID: " + signalId + " - Keine Tick-Daten verfügbar");
        detailsText.setText("Keine Tick-Daten gefunden in: " + tickFilePath);
        chartCanvas.redraw();
    }
    
    /**
     * Zeigt Fehlermeldung
     */
    private void showErrorMessage(String message) {
        infoLabel.setText("Signal ID: " + signalId + " - Fehler beim Laden");
        detailsText.setText("FEHLER: " + message);
        
        MessageBox errorBox = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        errorBox.setText("Fehler beim Laden der Tick-Daten");
        errorBox.setMessage(message);
        errorBox.open();
    }
    
    /**
     * Aktualisiert die Daten
     */
    private void refreshData() {
        LOGGER.info("Manueller Refresh für Signal: " + signalId);
        
        refreshButton.setEnabled(false);
        refreshButton.setText("Lädt...");
        
        isDataLoaded = false;
        chartCanvas.redraw();
        
        loadDataAsync();
        
        display.timerExec(3000, () -> {
            if (!isWindowClosed && !refreshButton.isDisposed()) {
                refreshButton.setEnabled(true);
                refreshButton.setText("Aktualisieren");
            }
        });
    }
    
    /**
     * Schließt das Fenster sauber
     */
    private void closeWindow() {
        isWindowClosed = true;
        
        LOGGER.info("Schließe TickChartWindow (OHNE Bridge) für Signal: " + signalId);
        
        // Image-Ressourcen freigeben
        if (chartImage != null && !chartImage.isDisposed()) {
            chartImage.dispose();
        }
        
        // SWT-Ressourcen freigeben
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }
    
    /**
     * Zeigt das Fenster an
     */
    public void open() {
        shell.open();
        LOGGER.info("TickChartWindow geöffnet (OHNE Bridge) für Signal: " + signalId);
    }
    
    /**
     * Prüft ob das Fenster noch geöffnet ist
     */
    public boolean isOpen() {
        return shell != null && !shell.isDisposed() && !isWindowClosed;
    }
    
    /**
     * Gibt die Signal-ID zurück
     */
    public String getSignalId() {
        return signalId;
    }
}