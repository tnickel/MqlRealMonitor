package com.mql.realmonitor.gui;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Combo;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.downloader.FavoritesReader;

/**
 * NEU (v1.4.8): "Tickkurse anzeigen"-Fenster — zeigt die 15-Minuten-Daten,
 * die das Monitoring je Signal in realtick/tick/<signalId>.txt ablegt
 * (Datum, Uhrzeit, Equity, FloatingProfit, Profit), komfortabel in EINEM
 * großen geteilten Fenster: oben Chart (Kontostand + Gesamtwert), unten
 * klick-sortierbare Tabelle. Signal oben umschaltbar — das in der
 * Provider-Tabelle markierte Signal ist vorbelegt.
 */
public class TickDataSplitWindow extends AbstractDataSplitWindow {

    private static final DateTimeFormatter DATUM_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ZEIT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String vorwahlSignalId;

    public TickDataSplitWindow(MqlRealMonitorGUI gui, String vorwahlSignalId) {
        super(gui);
        this.vorwahlSignalId = vorwahlSignalId;
    }

    @Override
    protected String windowTitle() {
        return "Tickkurse anzeigen (15-Minuten-Daten)";
    }

    @Override
    protected void fillAuswahlCombo(Combo combo) {
        Map<String, String> signale = ladeSignale();
        for (Map.Entry<String, String> e : signale.entrySet()) {
            combo.add(e.getValue() + " (#" + e.getKey() + ")");
        }
        int vorwahl = -1;
        if (vorwahlSignalId != null) {
            int i = 0;
            for (Map.Entry<String, String> e : signale.entrySet()) {
                if (e.getKey().equals(vorwahlSignalId)) {
                    vorwahl = i;
                    break;
                }
                i++;
            }
        }
        if (vorwahl >= 0) {
            combo.select(vorwahl);
        } else if (combo.getItemCount() > 0) {
            combo.select(0);
        }
    }

    /**
     * Alle überwachten Signale mit Anzeigenamen (Favoritenliste + ID-Übersetzung)
     */
    private Map<String, String> ladeSignale() {
        Map<String, String> signale = new LinkedHashMap<>();
        try {
            List<String> ids = new FavoritesReader(gui.getMonitor().getConfig()).readFavorites();
            IdTranslationManager translation = gui.getProviderTable() != null
                    ? gui.getProviderTable().getIdTranslationManager()
                    : null;
            for (String id : ids) {
                String name = translation != null ? translation.getProviderName(id) : id;
                signale.put(id, name != null && !name.isEmpty() ? name : id);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Signalliste für Tickkurse-Anzeige konnte nicht geladen werden", e);
        }
        return signale;
    }

    @Override
    protected String[] tableHeaders() {
        return new String[] {"Datum", "Uhrzeit", "Equity (Kontostand)", "Floating Profit", "Profit"};
    }

    @Override
    protected int[] columnWidths() {
        return new int[] {120, 100, 180, 160, 160};
    }

    @Override
    protected int[] columnAlignments() {
        return new int[] {SWT.LEFT, SWT.LEFT, SWT.RIGHT, SWT.RIGHT, SWT.RIGHT};
    }

    @Override
    protected int getDefaultSortColumn() {
        return 0; // Datum, absteigend = neueste zuerst
    }

    @Override
    protected String shellTitleFor(String auswahl) {
        return windowTitle() + (auswahl == null || auswahl.isEmpty() ? "" : " — " + auswahl);
    }

    @Override
    protected ViewData loadData(String auswahl, String zeitraum) {
        ViewData view = new ViewData();

        String signalId = extrahiereSignalId(auswahl);
        if (signalId == null) {
            view.statusMessage = "Kein Signal ausgewählt.";
            return view;
        }

        String filePath = gui.getMonitor().getConfig().getTickFilePath(signalId);
        TickDataLoader.TickDataSet dataSet = TickDataLoader.loadTickData(filePath, signalId);
        if (dataSet == null || dataSet.getTickCount() == 0) {
            view.statusMessage = "Keine Tick-Daten für Signal #" + signalId
                    + " gefunden (Datei: " + filePath + ").";
            return view;
        }

        LocalDateTime ab = zeitraumStart(zeitraum);
        List<TickDataLoader.TickData> gefiltert = new ArrayList<>();
        for (TickDataLoader.TickData t : dataSet.getTicks()) {
            if (ab == null || !t.getTimestamp().isBefore(ab)) {
                gefiltert.add(t);
            }
        }
        if (gefiltert.isEmpty()) {
            view.statusMessage = "Keine Tick-Daten im gewählten Zeitraum (" + zeitraum + ").";
            return view;
        }

        double minEquity = Double.MAX_VALUE;
        double maxEquity = -Double.MAX_VALUE;
        double minTotal = Double.MAX_VALUE;
        double maxTotal = -Double.MAX_VALUE;
        for (TickDataLoader.TickData t : gefiltert) {
            view.addRow(
                    new String[] {
                            t.getTimestamp().format(DATUM_FORMATTER),
                            t.getTimestamp().format(ZEIT_FORMATTER),
                            formatZahl(t.getEquity()),
                            formatZahl(t.getFloatingProfit()),
                            formatZahl(t.getProfit())},
                    new Object[] {
                            t.getTimestamp(),
                            t.getTimestamp().toLocalTime(),
                            t.getEquity(),
                            t.getFloatingProfit(),
                            t.getProfit()});
            minEquity = Math.min(minEquity, t.getEquity());
            maxEquity = Math.max(maxEquity, t.getEquity());
            minTotal = Math.min(minTotal, t.getTotalValue());
            maxTotal = Math.max(maxTotal, t.getTotalValue());
        }

        TickDataLoader.TickData erste = gefiltert.get(0);
        TickDataLoader.TickData letzte = gefiltert.get(gefiltert.size() - 1);
        view.chartData = gefiltert;
        view.summary = String.format(
                "%d Punkte  ·  %s %s – %s %s  ·  Equity %s bis %s  ·  Gesamtwert %s bis %s",
                gefiltert.size(),
                erste.getTimestamp().format(DATUM_FORMATTER), erste.getTimestamp().format(ZEIT_FORMATTER),
                letzte.getTimestamp().format(DATUM_FORMATTER), letzte.getTimestamp().format(ZEIT_FORMATTER),
                formatZahl(minEquity), formatZahl(maxEquity),
                formatZahl(minTotal), formatZahl(maxTotal));
        return view;
    }

    /**
     * Löst das Combo-Format "Name (#12345)" auf — ID steht am Zeilenende
     */
    private static String extrahiereSignalId(String auswahl) {
        if (auswahl == null) {
            return null;
        }
        int start = auswahl.lastIndexOf("(#");
        int ende = auswahl.lastIndexOf(')');
        if (start < 0 || ende <= start) {
            return null;
        }
        return auswahl.substring(start + 2, ende).trim();
    }

    @Override
    protected ImageData renderChart(Object chartData, int width, int height) {
        @SuppressWarnings("unchecked")
        List<TickDataLoader.TickData> daten = (List<TickDataLoader.TickData>) chartData;
        if (daten == null || daten.isEmpty()) {
            return null;
        }

        TimeSeries equitySerie = new TimeSeries("Kontostand (Equity)");
        TimeSeries gesamtSerie = new TimeSeries("Gesamtwert (inkl. Floating)");
        for (TickDataLoader.TickData t : sample(daten, MAX_CHART_PUNKTE)) {
            equitySerie.addOrUpdate(toMillisecond(t.getTimestamp()), t.getEquity());
            gesamtSerie.addOrUpdate(toMillisecond(t.getTimestamp()), t.getTotalValue());
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(equitySerie);
        dataset.addSeries(gesamtSerie);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Zeit", "Konto", dataset, true, false, false);
        XYPlot plot = chart.getXYPlot();

        // FIX 25.09.2026: Datumsformat nach der Datenspanne wählen — bei
        // "Gesamt" (Monate) wäre das Standard-Format sonst nur Uhrzeit
        org.jfree.chart.axis.DateAxis timeAxis =
                (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();
        timeAxis.setDateFormatOverride(ChartDateAxisFormat.formatFuerSpanne(
                daten.get(0).getTimestamp(),
                daten.get(daten.size() - 1).getTimestamp()));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new java.awt.Color(0, 128, 0));
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        renderer.setSeriesPaint(1, new java.awt.Color(0, 51, 153));
        renderer.setSeriesStroke(1, new java.awt.BasicStroke(1.5f));
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setDomainGridlinePaint(java.awt.Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
        chart.setBackgroundPaint(new java.awt.Color(250, 250, 250));

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setAutoRangeIncludesZero(false);

        return bufferedImageToImageData(chart.createBufferedImage(width, height));
    }
}
