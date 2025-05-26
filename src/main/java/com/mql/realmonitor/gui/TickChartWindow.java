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
 * ERWEITERTE TICK CHART WINDOW MIT EQUITY DRAWDOWN
 * 
 * Zeigt zwei Charts:
 * 1. Hauptchart mit Equity, Floating Profit und Gesamtwert (obere Hälfte)
 * 2. Drawdown-Chart mit Floating Profit in Prozent (untere Hälfte)
 * 
 * OHNE SWT-AWT BRIDGE: JFreeChart als Image-Rendering in SWT Canvas
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
    
    // Haupt-Chart Komponenten (obere Hälfte)
    private JFreeChart mainChart;
    private TimeSeries equitySeries;
    private TimeSeries floatingProfitSeries;
    private TimeSeries totalValueSeries;
    
    // NEU: Drawdown-Chart Komponenten (untere Hälfte)
    private JFreeChart drawdownChart;
    private TimeSeries drawdownPercentSeries;
    
    // Daten
    private final String signalId;
    private final String providerName;
    private final SignalData signalData;
    private final String tickFilePath;
    private TickDataLoader.TickDataSet tickDataSet;
    
    // Parent GUI für Callbacks
    private final MqlRealMonitorGUI parentGui;
    private final Display display;
    
    // Chart-Image Verwaltung (beide Charts)
    private Image mainChartImage;
    private Image drawdownChartImage;
    private int chartWidth = 800;
    private int mainChartHeight = 300;  // Halb so groß wie vorher
    private int drawdownChartHeight = 200; // Neuer Drawdown-Chart
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
        
        LOGGER.info("Erstelle erweiterte TickChartWindow mit Drawdown für Signal: " + signalId + " (" + providerName + ")");
        
        createWindow(parent);
        createCharts();
        loadDataAsync();
    }
    
    /**
     * Erstellt das Hauptfenster (erweiterte Höhe für beide Charts)
     */
    private void createWindow(Shell parent) {
        shell = new Shell(parent, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Tick Chart + Drawdown - " + signalId + " (" + providerName + ")");
        shell.setSize(1000, 900); // Erhöhte Höhe für beide Charts
        shell.setLayout(new GridLayout(1, false));
        
        // Fenster zentrieren
        centerWindow(parent);
        
        // Info-Panel erstellen
        createInfoPanel();
        
        // Chart-Canvas erstellen (für beide Charts)
        createChartCanvas();
        
        // Button-Panel erstellen
        createButtonPanel();
        
        // Event Handler
        setupEventHandlers();
        
        LOGGER.info("Erweiterte TickChartWindow UI erstellt für Signal: " + signalId);
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
     * ERWEITERT: Chart-Canvas für beide Charts
     */
    private void createChartCanvas() {
        Group chartGroup = new Group(shell, SWT.NONE);
        chartGroup.setText("Tick Charts (Haupt-Chart + Equity Drawdown)");
        chartGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartGroup.setLayout(new GridLayout(1, false));
        
        // Reiner SWT Canvas für beide Charts
        chartCanvas = new Canvas(chartGroup, SWT.BORDER | SWT.DOUBLE_BUFFERED);
        chartCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartCanvas.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        
        // Paint-Listener: Zeichnet beide Chart-Images
        chartCanvas.addPaintListener(new PaintListener() {
            @Override
            public void paintControl(PaintEvent e) {
                paintBothCharts(e.gc);
            }
        });
        
        // Resize-Listener: Charts bei Größenänderung neu rendern
        chartCanvas.addListener(SWT.Resize, event -> {
            Point size = chartCanvas.getSize();
            if (size.x > 0 && size.y > 0) {
                chartWidth = size.x;
                // Höhe aufteilen: 60% für Haupt-Chart, 40% für Drawdown-Chart
                int totalHeight = size.y;
                mainChartHeight = (int) (totalHeight * 0.6);
                drawdownChartHeight = (int) (totalHeight * 0.4);
                
                LOGGER.info("Canvas Größe geändert: " + chartWidth + "x" + totalHeight + 
                           " (Main: " + mainChartHeight + ", Drawdown: " + drawdownChartHeight + ")");
                renderBothChartsToImages();
            }
        });
        
        LOGGER.info("Chart-Canvas für beide Charts erstellt");
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
                renderBothChartsToImages();
                LOGGER.info("Zoom In: " + zoomFactor);
            }
        });
        
        zoomOutButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor /= 1.2;
                renderBothChartsToImages();
                LOGGER.info("Zoom Out: " + zoomFactor);
            }
        });
        
        resetZoomButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                zoomFactor = 1.0;
                renderBothChartsToImages();
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
     * ERWEITERT: Erstellt beide JFreeCharts (Haupt-Chart + Drawdown-Chart)
     */
    private void createCharts() {
        createMainChart();
        createDrawdownChart();
        LOGGER.info("Beide Charts (Haupt + Drawdown) erstellt für Signal: " + signalId);
    }
    
    /**
     * Erstellt den Haupt-Chart (Equity, Floating Profit, Gesamtwert)
     */
    private void createMainChart() {
        // TimeSeries für die verschiedenen Datenreihen
        equitySeries = new TimeSeries("Equity (Kontostand)");
        floatingProfitSeries = new TimeSeries("Floating Profit");
        totalValueSeries = new TimeSeries("Gesamtwert");
        
        // TimeSeriesCollection erstellen
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(equitySeries);
        dataset.addSeries(floatingProfitSeries);
        dataset.addSeries(totalValueSeries);
        
        // Haupt-Chart erstellen
        mainChart = ChartFactory.createTimeSeriesChart(
            "Tick Daten - " + signalId + " (" + providerName + ")",
            "Zeit",
            "Wert",
            dataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Haupt-Chart konfigurieren
        configureMainChart();
    }
    
    /**
     * NEU: Erstellt den Drawdown-Chart (Floating Profit in Prozent)
     */
    private void createDrawdownChart() {
        // TimeSeries für Drawdown-Prozentsatz
        drawdownPercentSeries = new TimeSeries("Equity Drawdown (%)");
        
        // TimeSeriesCollection für Drawdown
        TimeSeriesCollection drawdownDataset = new TimeSeriesCollection();
        drawdownDataset.addSeries(drawdownPercentSeries);
        
        // Drawdown-Chart erstellen
        drawdownChart = ChartFactory.createTimeSeriesChart(
            "Equity Drawdown (%)",
            "Zeit",
            "Prozent (%)",
            drawdownDataset,
            true,  // Legend
            true,  // Tooltips
            false  // URLs
        );
        
        // Drawdown-Chart konfigurieren
        configureDrawdownChart();
    }
    
    /**
     * Konfiguriert den Haupt-Chart (Farben, Renderer etc.)
     */
    private void configureMainChart() {
        XYPlot plot = mainChart.getXYPlot();
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
        mainChart.setBackgroundPaint(new Color(240, 240, 240));
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
     * NEU: Konfiguriert den Drawdown-Chart
     */
    /**
     * NEU: Konfiguriert den Drawdown-Chart (KORRIGIERT)
     * Klasse: TickChartWindow
     */
    private void configureDrawdownChart() {
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        // Linien-Renderer konfigurieren
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true);
        
        // Dynamische Farbe basierend auf Werten wird später in updateDrawdownChartColors() gesetzt
        renderer.setSeriesPaint(0, Color.BLUE); // Standard-Farbe
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        
        plot.setRenderer(renderer);
        
        // Hintergrund-Farben
        drawdownChart.setBackgroundPaint(new Color(250, 250, 250));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // Grid sichtbar machen
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);
        
        // Nulllinie hervorheben
        plot.setRangeZeroBaselineVisible(true);
        plot.setRangeZeroBaselinePaint(Color.BLACK);
        plot.setRangeZeroBaselineStroke(new BasicStroke(1.0f));
        
        // Achsen-Labels
        plot.getRangeAxis().setLabel("Drawdown (%)");
        plot.getDomainAxis().setLabel("Zeit");
        
        // Y-Achse manuell konfigurieren (statt setAutoRangeIncludesZero)
        plot.getRangeAxis().setAutoRange(true);
        plot.getRangeAxis().setLowerMargin(0.1); // 10% Margin unten
        plot.getRangeAxis().setUpperMargin(0.1); // 10% Margin oben
    }
    
    /**
     * NEU: Rendert beide Charts als BufferedImages
     */
    private void renderBothChartsToImages() {
        if (mainChart == null || drawdownChart == null || chartWidth <= 0) {
            return;
        }
        
        try {
            // Haupt-Chart rendern
            renderMainChartToImage();
            
            // Drawdown-Chart rendern
            renderDrawdownChartToImage();
            
            // Canvas neu zeichnen
            if (!chartCanvas.isDisposed()) {
                chartCanvas.redraw();
            }
            
            LOGGER.fine("Beide Charts gerendert: Main=" + mainChartHeight + ", Drawdown=" + drawdownChartHeight);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern der Charts", e);
        }
    }
    
    /**
     * Rendert den Haupt-Chart als Image
     */
    private void renderMainChartToImage() {
        if (mainChart == null || mainChartHeight <= 0) {
            return;
        }
        
        try {
            // JFreeChart als BufferedImage rendern
            BufferedImage bufferedImage = mainChart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(mainChartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (mainChartImage != null && !mainChartImage.isDisposed()) {
                mainChartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            mainChartImage = new Image(display, imageData);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Haupt-Charts", e);
        }
    }
    
    /**
     * NEU: Rendert den Drawdown-Chart als Image
     */
    private void renderDrawdownChartToImage() {
        if (drawdownChart == null || drawdownChartHeight <= 0) {
            return;
        }
        
        try {
            // JFreeChart als BufferedImage rendern
            BufferedImage bufferedImage = drawdownChart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(drawdownChartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
                drawdownChartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            drawdownChartImage = new Image(display, imageData);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Drawdown-Charts", e);
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
     * ERWEITERT: Zeichnet beide Charts untereinander auf dem Canvas
     */
    private void paintBothCharts(GC gc) {
        // Canvas leeren
        gc.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(chartCanvas.getBounds());
        
        org.eclipse.swt.graphics.Rectangle canvasBounds = chartCanvas.getBounds();
        
        if (mainChartImage != null && !mainChartImage.isDisposed()) {
            // Haupt-Chart oben zeichnen
            org.eclipse.swt.graphics.Rectangle mainImageBounds = mainChartImage.getBounds();
            int mainX = (canvasBounds.width - mainImageBounds.width) / 2;
            int mainY = 5; // Kleiner Abstand oben
            
            gc.drawImage(mainChartImage, Math.max(0, mainX), mainY);
            
            // Trennlinie zwischen den Charts
            int separatorY = mainY + mainImageBounds.height + 5;
            gc.setForeground(display.getSystemColor(SWT.COLOR_GRAY));
            gc.drawLine(10, separatorY, canvasBounds.width - 10, separatorY);
        }
        
        if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
            // Drawdown-Chart unten zeichnen
            org.eclipse.swt.graphics.Rectangle drawdownImageBounds = drawdownChartImage.getBounds();
            int drawdownX = (canvasBounds.width - drawdownImageBounds.width) / 2;
            
            // Y-Position: Nach dem Haupt-Chart + Separator
            int drawdownY = 15; // Abstand für Separator
            if (mainChartImage != null && !mainChartImage.isDisposed()) {
                drawdownY += mainChartImage.getBounds().height + 15;
            }
            
            gc.drawImage(drawdownChartImage, Math.max(0, drawdownX), drawdownY);
            
        } else if (!isDataLoaded) {
            // Loading-Message zeichnen
            gc.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
            gc.drawText("Charts werden geladen...", 20, 20, true);
            
        } else {
            // Error-Message zeichnen
            gc.setForeground(display.getSystemColor(SWT.COLOR_RED));
            gc.drawText("Fehler beim Laden der Charts", 20, 20, true);
        }
    }
    
    /**
     * Lädt Daten asynchron
     */
    private void loadDataAsync() {
        new Thread(() -> {
            try {
                LOGGER.info("Lade Tick-Daten für erweiterte Charts - Signal: " + signalId + " von " + tickFilePath);
                
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
                
                LOGGER.info("Tick-Daten geladen: " + tickDataSet.getTickCount() + " Ticks für erweiterte Charts - Signal: " + signalId);
                
                // Chart-Daten aktualisieren (beide Charts)
                updateBothChartsData();
                
                // UI-Updates im SWT Thread
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        updateInfoPanel();
                        renderBothChartsToImages();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Fehler beim Laden der Tick-Daten für erweiterte Charts - Signal: " + signalId, e);
                
                display.asyncExec(() -> {
                    if (!isWindowClosed && !shell.isDisposed()) {
                        showErrorMessage("Fehler beim Laden der Tick-Daten: " + e.getMessage());
                    }
                });
            }
        }, "TickDataLoader-Extended-" + signalId).start();
    }
    
    /**
     * ERWEITERT: Aktualisiert die Daten für beide Charts
     */
    private void updateBothChartsData() {
        if (tickDataSet == null || mainChart == null || drawdownChart == null) {
            return;
        }
        
        try {
            // Haupt-Chart Serien leeren
            equitySeries.clear();
            floatingProfitSeries.clear();
            totalValueSeries.clear();
            
            // NEU: Drawdown-Serie leeren
            drawdownPercentSeries.clear();
            
            // Tick-Daten zu beiden Chart-Serien hinzufügen
            for (TickDataLoader.TickData tick : tickDataSet.getTicks()) {
                Date javaDate = Date.from(tick.getTimestamp().atZone(ZoneId.systemDefault()).toInstant());
                Second second = new Second(javaDate);
                
                // Haupt-Chart Daten
                equitySeries.add(second, tick.getEquity());
                floatingProfitSeries.add(second, tick.getFloatingProfit());
                totalValueSeries.add(second, tick.getTotalValue());
                
                // NEU: Drawdown-Prozentsatz berechnen und hinzufügen
                double drawdownPercent = calculateDrawdownPercent(tick.getEquity(), tick.getFloatingProfit());
                drawdownPercentSeries.add(second, drawdownPercent);
            }
            
            // Chart-Titel aktualisieren
            mainChart.setTitle("Tick Daten - " + signalId + " (" + providerName + ") - " + 
                              tickDataSet.getTickCount() + " Ticks");
            
            drawdownChart.setTitle("Equity Drawdown (%) - " + signalId + " (" + providerName + ")");
            
            // Y-Achsen-Bereiche anpassen
            adjustMainChartYAxisRange();
            adjustDrawdownChartYAxisRange();
            
            // Drawdown-Chart Farben aktualisieren
            updateDrawdownChartColors();
            
            LOGGER.info("Beide Charts aktualisiert: " + tickDataSet.getTickCount() + " Ticks hinzugefügt");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Aktualisieren der Chart-Daten", e);
        }
    }
    
    /**
     * NEU: Berechnet den Drawdown-Prozentsatz
     * Drawdown (%) = (Floating Profit / Equity) * 100
     * 
     * @param equity Der Kontostand
     * @param floatingProfit Der Floating Profit
     * @return Der Drawdown-Prozentsatz
     */
    private double calculateDrawdownPercent(double equity, double floatingProfit) {
        if (equity == 0) {
            return 0.0;
        }
        
        return (floatingProfit / equity) * 100.0;
    }
    
    /**
     * Passt den Y-Achsen-Bereich des Haupt-Charts an
     */
    private void adjustMainChartYAxisRange() {
        if (mainChart == null || tickDataSet == null) {
            return;
        }
        
        XYPlot plot = mainChart.getXYPlot();
        
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
     * NEU: Passt den Y-Achsen-Bereich des Drawdown-Charts an
     */
    private void adjustDrawdownChartYAxisRange() {
        if (drawdownChart == null || tickDataSet == null || drawdownPercentSeries.getItemCount() == 0) {
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        
        // Min/Max Drawdown-Prozentsätze finden
        double minDrawdown = Double.MAX_VALUE;
        double maxDrawdown = Double.MIN_VALUE;
        
        for (int i = 0; i < drawdownPercentSeries.getItemCount(); i++) {
            double value = drawdownPercentSeries.getValue(i).doubleValue();
            minDrawdown = Math.min(minDrawdown, value);
            maxDrawdown = Math.max(maxDrawdown, value);
        }
        
        // Symmetrischer Bereich um 0
        double maxAbsValue = Math.max(Math.abs(minDrawdown), Math.abs(maxDrawdown));
        double padding = Math.max(maxAbsValue * 0.1, 1.0); // Mindestens 1% Padding
        
        plot.getRangeAxis().setRange(-maxAbsValue - padding, maxAbsValue + padding);
        plot.getDomainAxis().setAutoRange(true);
        
        LOGGER.fine("Drawdown Y-Achse angepasst: " + (-maxAbsValue - padding) + " bis " + (maxAbsValue + padding));
    }
    
    /**
     * NEU: Aktualisiert die Farben des Drawdown-Charts basierend auf positiven/negativen Werten
     */
    private void updateDrawdownChartColors() {
        if (drawdownChart == null || drawdownPercentSeries.getItemCount() == 0) {
            return;
        }
        
        XYPlot plot = drawdownChart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        
        // Prüfe ob mehr positive oder negative Werte vorhanden sind
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (int i = 0; i < drawdownPercentSeries.getItemCount(); i++) {
            double value = drawdownPercentSeries.getValue(i).doubleValue();
            if (value > 0) {
                positiveCount++;
            } else if (value < 0) {
                negativeCount++;
            }
        }
        
        // Farbe basierend auf Mehrheit setzen
        if (positiveCount > negativeCount) {
            renderer.setSeriesPaint(0, new Color(0, 150, 0)); // Grün für überwiegend positive Werte
            LOGGER.fine("Drawdown-Chart Farbe: Grün (mehr positive Werte)");
        } else if (negativeCount > positiveCount) {
            renderer.setSeriesPaint(0, new Color(200, 0, 0)); // Rot für überwiegend negative Werte
            LOGGER.fine("Drawdown-Chart Farbe: Rot (mehr negative Werte)");
        } else {
            renderer.setSeriesPaint(0, new Color(0, 0, 200)); // Blau für ausgeglichen
            LOGGER.fine("Drawdown-Chart Farbe: Blau (ausgeglichen)");
        }
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
     * ERWEITERT: Aktualisiert das Info-Panel mit Drawdown-Informationen
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
            details.append("=== Erweiterte Tick-Daten Statistik ===\n");
            details.append("Rendering: JFreeChart ohne SWT-AWT Bridge (Dual-Chart)\n");
            details.append("Charts: Haupt-Chart (Equity/Floating/Total) + Drawdown-Chart (%)\n");
            details.append("Datei: ").append(tickFilePath).append("\n");
            details.append("Anzahl Ticks: ").append(tickDataSet.getTickCount()).append("\n");
            details.append("Zeitraum: ").append(tickDataSet.getFirstTick().getTimestamp())
                   .append(" bis ").append(tickDataSet.getLatestTick().getTimestamp()).append("\n");
            details.append("Zoom-Faktor: ").append(String.format("%.2f", zoomFactor)).append("\n");
            
            // NEU: Drawdown-Statistiken
            if (drawdownPercentSeries.getItemCount() > 0) {
                double minDrawdown = Double.MAX_VALUE;
                double maxDrawdown = Double.MIN_VALUE;
                double avgDrawdown = 0.0;
                
                for (int i = 0; i < drawdownPercentSeries.getItemCount(); i++) {
                    double value = drawdownPercentSeries.getValue(i).doubleValue();
                    minDrawdown = Math.min(minDrawdown, value);
                    maxDrawdown = Math.max(maxDrawdown, value);
                    avgDrawdown += value;
                }
                avgDrawdown /= drawdownPercentSeries.getItemCount();
                
                details.append("\n=== Drawdown Statistik ===\n");
                details.append("Min. Drawdown: ").append(String.format("%.2f%%", minDrawdown)).append("\n");
                details.append("Max. Drawdown: ").append(String.format("%.2f%%", maxDrawdown)).append("\n");
                details.append("Durchschn. Drawdown: ").append(String.format("%.2f%%", avgDrawdown)).append("\n");
            }
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
        LOGGER.info("Manueller Refresh für erweiterte Charts - Signal: " + signalId);
        
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
        
        LOGGER.info("Schließe erweiterte TickChartWindow für Signal: " + signalId);
        
        // Image-Ressourcen freigeben
        if (mainChartImage != null && !mainChartImage.isDisposed()) {
            mainChartImage.dispose();
        }
        if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
            drawdownChartImage.dispose();
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
        LOGGER.info("Erweiterte TickChartWindow geöffnet für Signal: " + signalId);
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