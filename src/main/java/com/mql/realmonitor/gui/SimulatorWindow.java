package com.mql.realmonitor.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.downloader.FavoritesReader;
import com.mql.realmonitor.simulator.SimulatorEngine;
import com.mql.realmonitor.simulator.SimulatorEngine.SimulationResult;
import com.mql.realmonitor.simulator.SimulatorEngine.StrategyResult;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: Simulator-Fenster — scrollbare Liste mit simulierten Equity-Kurven.
 *
 * Je Strategie: Startkapital aus der Config (Standard 10.000), Startdatum aus
 * der Config (Menü → Einstellungen → Konfiguration). Lot-Skalierung wie beim
 * echten MT5-Signal-Kopieren (Compounding). Am Ende der Liste das PORTFOLIO
 * (alle Strategien vereint). Sortierung: beste Strategie zuerst.
 *
 * Charts werden im Hintergrund gebaut und nach und nach eingeblendet — das
 * Fenster öffnet sofort.
 */
public class SimulatorWindow {

    private static final Logger LOGGER = Logger.getLogger(SimulatorWindow.class.getName());

    private static final int CHART_WIDTH = 960;
    private static final int CHART_HEIGHT = 200;
    private static final PaletteData PALETTE = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);

    private final MqlRealMonitorGUI gui;
    private Shell shell;
    private Composite content;
    private Label statusLabel;
    private Font titleFont;
    private Font headerFont;

    // Parameter des Laufs (open() ohne Parameter nutzt die globale Config)
    private String fensterTitel = "Simulator";
    private java.util.List<String> signalIds;
    private LocalDate startDatum;
    private double startKapital;

    public SimulatorWindow(MqlRealMonitorGUI gui) {
        this.gui = gui;
    }

    /**
     * Öffnet das Simulator-Fenster mit den globalen Config-Werten über
     * ALLE Favoriten (Toolbar-Button 📊 Simulator).
     */
    public void open() {
        open("Alle Strategien",
                new FavoritesReader(gui.getMonitor().getConfig()).readFavorites(),
                gui.getMonitor().getConfig().getSimulatorStartDateParsed(),
                gui.getMonitor().getConfig().getSimulatorStartCapital());
    }

    /**
     * NEU: Öffnet das Simulator-Fenster für eine konkrete Signal-Auswahl —
     * so öffnen die Portfolio-Simulatoren des Seitenpanels ihre Ansicht.
     *
     * @param titel      Fenster-/Kopfzeilen-Titel (z. B. Portfolio-Name)
     * @param signalIds  Die zu simulierenden Signal-IDs
     * @param start      Startdatum der Simulation
     * @param kapital    Startkapital je Strategie
     */
    public void open(String titel, java.util.List<String> signalIds, LocalDate start, double kapital) {
        this.fensterTitel = titel != null ? titel : "Simulator";
        this.signalIds = signalIds;
        this.startDatum = start;
        this.startKapital = kapital;
        doOpen();
    }

    private void doOpen() {
        Display display = gui.getDisplay();
        shell = new Shell(display, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText("Simulator — " + fensterTitel + " — ab " + startDatum
                + " · " + String.format("%.0f", startKapital) + " Startkapital je Strategie");
        shell.setSize(1020, 860);
        shell.setLayout(new GridLayout(1, false));

        FontData[] base = display.getSystemFont().getFontData();
        FontData titleData = base[0];
        titleData.setStyle(SWT.BOLD);
        titleData.setHeight(titleData.getHeight() + 2);
        titleFont = new Font(display, titleData);
        FontData headerData = base[0];
        headerData.setStyle(SWT.BOLD);
        headerData.setHeight(headerData.getHeight() + 4);
        headerFont = new Font(display, headerData);

        // Kopfzeile
        Composite header = new Composite(shell, SWT.NONE);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        header.setLayout(new GridLayout(1, false));
        Label titel = new Label(header, SWT.NONE);
        titel.setText(fensterTitel + ": Jede Strategie startet am " + startDatum + " mit "
                + String.format("%.0f", startKapital)
                + " (Lot-Skalierung wie beim Signal-Kopieren)");
        titel.setFont(headerFont);
        titel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Label hinweis = new Label(header, SWT.WRAP);
        hinweis.setText("Grün = Gewinn · Rot = Verlust · Grau = keine Historie geladen "
                + "(zuerst \uD83D\uDCDD Trades laden). Dunkelgelb = Open Equity (live aus Tick-Daten, "
                + "nur ab Monitoring-Start verfügbar — zeigt die Floating-Schwankungen). "
                + "Sortiert nach Ergebnis. Unten: Portfolio (alle vereint).");
        hinweis.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Scrollbare Liste
        ScrolledComposite scrolled = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.BORDER);
        scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        content = new Composite(scrolled, SWT.NONE);
        content.setLayout(new GridLayout(1, false));
        content.setBackgroundMode(SWT.INHERIT_NONE);
        scrolled.setContent(content);

        scrolled.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                applyMinSize(scrolled);
            }
        });

        statusLabel = new Label(content, SWT.NONE);
        statusLabel.setText("Lade Simulation...");
        statusLabel.setFont(titleFont);
        statusLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        applyMinSize(scrolled);

        shell.open();

        // Aufbau im Hintergrund
        new Thread(() -> {
            try {
                buildChartsAsync(display, scrolled);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Simulator-Aufbau fehlgeschlagen", e);
                display.asyncExec(() -> {
                    if (!statusLabel.isDisposed()) {
                        statusLabel.setText("Fehler beim Aufbau: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void applyMinSize(ScrolledComposite scrolled) {
        org.eclipse.swt.graphics.Point size = content.computeSize(
                scrolled.getParent().getClientArea().x > 0
                        ? Math.max(scrolled.getParent().getClientArea().x - 30, 400)
                        : 1000,
                SWT.DEFAULT);
        scrolled.setMinSize(size);
    }

    /**
     * Baut alle Charts im Hintergrund und blendet sie nacheinander ein
     */
    private void buildChartsAsync(Display display, ScrolledComposite scrolled) {
        SimulatorEngine engine = new SimulatorEngine(gui.getMonitor().getConfig());

        java.util.List<String> ids = signalIds != null ? signalIds : java.util.List.of();
        Map<String, String> namen = new LinkedHashMap<>();
        IdTranslationManager translation = gui.getProviderTable() != null
                ? gui.getProviderTable().getIdTranslationManager() : null;
        for (String id : ids) {
            namen.put(id, translation != null ? translation.getProviderName(id) : id);
        }

        if (ids.isEmpty()) {
            display.asyncExec(() -> {
                if (!statusLabel.isDisposed()) {
                    statusLabel.setText("Keine Signale ausgewählt.");
                }
            });
            return;
        }

        // FIX: Startkapital des Aufrufs nutzen (Portfolio-Simulatoren haben
        // ein eigenes Kapital) — vorher wurde still die globale Config verwendet
        SimulationResult result = engine.simulate(ids, namen, startDatum, startKapital);

        // NEU: Open-Equity-Overlays (Floating-Schwankungen) aus den Tick-Daten.
        // Portfolio-Open-Equity = Summe der vereinigten Kurven (Sim-Verlauf +
        // Overlay) ALLER Strategien — nicht nur der Overlay-Segmente, sonst
        // trägt jede Strategie vor ihrem Overlay-Start 0 bei und das Portfolio
        // fiele fälschlich auf ein einzelnes Sim-Konto (~10K statt ~30K).
        var config = gui.getMonitor().getConfig();
        Map<String, java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint>> openEquity =
                new LinkedHashMap<>();
        java.util.List<java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint>> kurvenFuerPortfolio =
                new ArrayList<>();
        int strategienMitOverlay = 0;
        for (StrategyResult s : result.strategien) {
            java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> kurve =
                    SimulatorEngine.ladeOpenEquityKurve(config, s, startDatum);
            openEquity.put(s.signalId, kurve);
            if (s.hatHistorie) {
                if (!kurve.isEmpty()) {
                    strategienMitOverlay++;
                }
                kurvenFuerPortfolio.add(SimulatorEngine.vereineKurven(s.punkte, kurve));
            }
        }
        java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> portfolioOpenEquity =
                SimulatorEngine.mergeKurven(kurvenFuerPortfolio);

        // Status-Label ersetzen
        display.asyncExec(() -> {
            if (!statusLabel.isDisposed()) {
                statusLabel.dispose();
            }
        });

        // Strategie-Charts (beste zuerst)
        int index = 1;
        for (StrategyResult s : result.strategien) {
            if (shell.isDisposed()) {
                return;
            }
            final int nr = index++;
            final ImageData bild = renderStrategyImage(s, nr, openEquity.get(s.signalId));
            final java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> overlay =
                    openEquity.get(s.signalId);
            display.asyncExec(() -> {
                if (shell.isDisposed() || content.isDisposed()) {
                    return;
                }
                addChartPanel(display, s, bild, overlay);
                applyMinSize(scrolled);
                content.layout();
            });
        }

        // Portfolio am Ende
        if (shell.isDisposed()) {
            return;
        }
        final ImageData portfolioBild = renderPortfolioImage(result, portfolioOpenEquity);
        display.asyncExec(() -> {
            if (shell.isDisposed() || content.isDisposed()) {
                return;
            }
            addPortfolioPanel(display, result, portfolioBild);
            applyMinSize(scrolled);
            content.layout();
        });

        LOGGER.info("Simulator-Fenster aufgebaut: " + result.strategien.size() + " Strategien, "
                + "Open-Equity-Overlays für " + strategienMitOverlay + " Strategien");
    }

    // ------------------------------------------------------------- Panels

    private void addChartPanel(Display display, StrategyResult s, ImageData bildData,
                               java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> openEquity) {
        Composite panel = new Composite(content, SWT.NONE);
        panel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        panel.setLayout(new GridLayout(1, false));

        Label titel = new Label(panel, SWT.NONE);
        titel.setFont(titleFont);
        boolean gewinn = s.endwert >= s.startkapital;
        String openEquityInfo = (openEquity != null && !openEquity.isEmpty())
                ? String.format("  ·  Open Equity (live) ab %ta.%tm.",
                        openEquity.get(0).getTime(), openEquity.get(0).getTime())
                : "";
        if (!s.hatHistorie) {
            titel.setText("— " + s.name + " (" + s.signalId + ")  ·  keine Historie geladen");
        } else if (s.punkte.size() <= 1) {
            titel.setText(s.name + " (" + s.signalId + ")  ·  keine Trades seit Startdatum  ·  "
                    + String.format("%.0f", s.endwert) + " (±0,0 %)" + openEquityInfo);
        } else {
            titel.setText(String.format("%s (#%s)  ·  Endstand %.0f  ·  %+.1f %%%s",
                    s.name, s.signalId, s.endwert, s.prozent(), openEquityInfo));
        }
        titel.setForeground(display.getSystemColor(
                !s.hatHistorie ? SWT.COLOR_GRAY : gewinn ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_RED));
        titel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

        if (bildData != null) {
            Image bild = new Image(display, bildData);
            Canvas canvas = new Canvas(panel, SWT.NONE);
            canvas.setLayoutData(new GridData(CHART_WIDTH, CHART_HEIGHT));
            canvas.addPaintListener(e -> {
                if (!bild.isDisposed()) {
                    e.gc.drawImage(bild, 0, 0);
                }
            });
            canvas.addDisposeListener(e -> bild.dispose());
        }
    }

    private void addPortfolioPanel(Display display, SimulationResult result, ImageData bildData) {
        // Abstand
        Label abstand = new Label(content, SWT.SEPARATOR | SWT.HORIZONTAL);
        abstand.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite panel = new Composite(content, SWT.NONE);
        panel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        panel.setLayout(new GridLayout(1, false));

        Label titel = new Label(panel, SWT.NONE);
        titel.setFont(headerFont);
        boolean gewinn = result.portfolioEndwert >= result.portfolioStartwert;
        titel.setText(String.format("PORTFOLIO — alle Strategien vereint  ·  Start %.0f  ·  Endstand %.0f  ·  %+.1f %%",
                result.portfolioStartwert, result.portfolioEndwert, result.portfolioProzent()));
        titel.setForeground(display.getSystemColor(gewinn ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_RED));
        titel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

        if (bildData != null) {
            Image bild = new Image(display, bildData);
            Canvas canvas = new Canvas(panel, SWT.NONE);
            canvas.setLayoutData(new GridData(CHART_WIDTH, CHART_HEIGHT + 40));
            canvas.addPaintListener(e -> {
                if (!bild.isDisposed()) {
                    e.gc.drawImage(bild, 0, 0);
                }
            });
            canvas.addDisposeListener(e -> bild.dispose());
        }
    }

    // ------------------------------------------------------------- Rendering

    /** Rendert das Chart einer Strategie zu ImageData (Hintergrund-Thread) */
    private ImageData renderStrategyImage(StrategyResult s, int nr,
                                          java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> openEquity) {
        if (!s.hatHistorie || s.punkte.isEmpty()) {
            return null;
        }

        TimeSeries serie = new TimeSeries("Sim-Konto");
        for (com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint p : s.punkte) {
            serie.addOrUpdate(new Millisecond(toDate(p.getTime())), p.getCumulatedProfit());
        }
        // OPTIK-FIX: grüne Kurve flach bis zum letzten Open-Equity-Zeitpunkt
        // führen — sonst wirkt sie "abgebrochen" (nur CLOSED Trades erzeugen Punkte)
        if (openEquity != null && !openEquity.isEmpty()) {
            com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint simEnde =
                    s.punkte.get(s.punkte.size() - 1);
            com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint oeEnde =
                    openEquity.get(openEquity.size() - 1);
            if (oeEnde.getTime().isAfter(simEnde.getTime())) {
                serie.addOrUpdate(new Millisecond(toDate(oeEnde.getTime())),
                        simEnde.getCumulatedProfit());
            }
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(serie);

        // NEU: Open Equity (live) als zweite Serie — Floating-Schwankungen
        if (openEquity != null && !openEquity.isEmpty()) {
            TimeSeries oe = new TimeSeries("Open Equity (live)");
            for (com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint p : openEquity) {
                oe.addOrUpdate(new Millisecond(toDate(p.getTime())), p.getCumulatedProfit());
            }
            dataset.addSeries(oe);
        }

        boolean gewinn = s.endwert >= s.startkapital;
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Zeit", "Sim-Konto", dataset, false, false, false);

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, gewinn ? new Color(0, 128, 0) : new Color(200, 0, 0));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        // Dunkelgelb = Open Equity (wie im Haupt-Chart)
        renderer.setSeriesPaint(1, new Color(204, 153, 0));
        renderer.setSeriesStroke(1, new BasicStroke(1.2f));
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        chart.setBackgroundPaint(new Color(250, 250, 250));

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setAutoRangeIncludesZero(false);

        // Startkapital-Linie als Referenz
        double start = s.startkapital;
        org.jfree.chart.axis.ValueAxis rangeAxis = plot.getRangeAxis();
        if (rangeAxis.getRange().getLowerBound() > start) {
            rangeAxis.setRange(start, rangeAxis.getRange().getUpperBound());
        }
        if (rangeAxis.getRange().getUpperBound() < start) {
            rangeAxis.setRange(rangeAxis.getRange().getLowerBound(), start);
        }

        return bufferedImageToImageData(chart.createBufferedImage(CHART_WIDTH, CHART_HEIGHT));
    }

    /** Rendert das Portfolio-Chart zu ImageData (Hintergrund-Thread) */
    private ImageData renderPortfolioImage(SimulationResult result,
                                           java.util.List<com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint> openEquity) {
        if (result.portfolio.isEmpty()) {
            return null;
        }

        TimeSeries serie = new TimeSeries("Sim-Konto (Portfolio)");
        for (com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint p : result.portfolio) {
            serie.addOrUpdate(new Millisecond(toDate(p.getTime())), p.getCumulatedProfit());
        }
        // OPTIK-FIX: Portfolio-Kurve flach bis zum letzten Open-Equity-Zeitpunkt
        if (openEquity != null && !openEquity.isEmpty()) {
            com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint simEnde =
                    result.portfolio.get(result.portfolio.size() - 1);
            com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint oeEnde =
                    openEquity.get(openEquity.size() - 1);
            if (oeEnde.getTime().isAfter(simEnde.getTime())) {
                serie.addOrUpdate(new Millisecond(toDate(oeEnde.getTime())),
                        simEnde.getCumulatedProfit());
            }
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(serie);

        // NEU: Open Equity (live) für das gesamte Portfolio
        if (openEquity != null && !openEquity.isEmpty()) {
            TimeSeries oe = new TimeSeries("Open Equity (live)");
            for (com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint p : openEquity) {
                oe.addOrUpdate(new Millisecond(toDate(p.getTime())), p.getCumulatedProfit());
            }
            dataset.addSeries(oe);
        }

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Zeit", "Gesamtwert (alle Strategien)", dataset, true, false, false);

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(0, 51, 153));
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        // Dunkelgelb = Open Equity (wie im Haupt-Chart)
        renderer.setSeriesPaint(1, new Color(204, 153, 0));
        renderer.setSeriesStroke(1, new BasicStroke(1.2f));
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        chart.setBackgroundPaint(new Color(250, 250, 250));

        return bufferedImageToImageData(chart.createBufferedImage(CHART_WIDTH, CHART_HEIGHT + 40));
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** BufferedImage → SWT ImageData (gleiche Konvertierung wie ChartImageRenderer) */
    private static ImageData bufferedImageToImageData(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = new int[width * height];
        image.getRGB(0, 0, width, height, rgb, 0, width);
        ImageData data = new ImageData(width, height, 24, PALETTE);
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                data.setPixel(x, y, rgb[offset + x] & 0xFFFFFF);
            }
        }
        return data;
    }
}
