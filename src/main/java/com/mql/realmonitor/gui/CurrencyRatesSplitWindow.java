package com.mql.realmonitor.gui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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

import com.mql.realmonitor.currency.CurrencyData;
import com.mql.realmonitor.currency.CurrencyDataWriter;
import com.mql.realmonitor.exception.MqlMonitorException;

/**
 * NEU (v1.4.8): "Kurse anzeigen"-Fenster — zeigt die gespeicherten
 * Währungskurse (XAUUSD/BTCUSD aus "💰 Kurse laden"), die bisher nur in
 * den Dateien realtick/tick_kurse/*.txt lagen und nirgends sichtbar waren.
 *
 * Geteiltes Fenster (Basis AbstractDataSplitWindow): oben der Kursverlauf
 * als Chart, unten eine klick-sortierbare Tabelle (Zeitstempel, Symbol,
 * Kurs). Symbol oben umschaltbar, Zeitraum-Auswahl wie im Simulator.
 */
public class CurrencyRatesSplitWindow extends AbstractDataSplitWindow {

    private static final String[] SYMBOLE = {"XAUUSD", "BTCUSD"};

    private static final DateTimeFormatter ZEIT_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public CurrencyRatesSplitWindow(MqlRealMonitorGUI gui) {
        super(gui);
    }

    @Override
    protected String windowTitle() {
        return "Währungskurse anzeigen";
    }

    @Override
    protected void fillAuswahlCombo(Combo combo) {
        for (String symbol : SYMBOLE) {
            combo.add(symbol);
        }
        combo.select(0);
    }

    @Override
    protected String[] tableHeaders() {
        return new String[] {"Zeitstempel", "Symbol", "Kurs"};
    }

    @Override
    protected int[] columnWidths() {
        return new int[] {170, 110, 150};
    }

    @Override
    protected int[] columnAlignments() {
        return new int[] {SWT.LEFT, SWT.LEFT, SWT.RIGHT};
    }

    @Override
    protected int getDefaultSortColumn() {
        return 0; // Zeitstempel, absteigend = neueste zuerst
    }

    @Override
    protected ViewData loadData(String symbol, String zeitraum) {
        ViewData view = new ViewData();

        List<CurrencyData> alle;
        try {
            alle = new CurrencyDataWriter(gui.getMonitor().getConfig()).readCurrencyData(symbol);
        } catch (MqlMonitorException e) {
            LOGGER.warning("Kursdaten für " + symbol + " konnten nicht gelesen werden: " + e.getMessage());
            view.statusMessage = "Fehler beim Lesen der Kursdaten: " + e.getMessage();
            return view;
        }

        if (alle.isEmpty()) {
            view.statusMessage = "Keine Kursdaten für " + symbol + " vorhanden — "
                    + "bitte zuerst '💰 Kurse laden' ausführen.";
            return view;
        }

        LocalDateTime ab = zeitraumStart(zeitraum);
        List<CurrencyData> gefiltert = new java.util.ArrayList<>();
        for (CurrencyData d : alle) {
            if (ab == null || !d.getTimestamp().isBefore(ab)) {
                gefiltert.add(d);
            }
        }
        if (gefiltert.isEmpty()) {
            view.statusMessage = "Keine Kursdaten im gewählten Zeitraum (" + zeitraum + ").";
            return view;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (CurrencyData d : gefiltert) {
            view.addRow(
                    new String[] {d.getTimestamp().format(ZEIT_FORMATTER), d.getSymbol(), formatKurs(d.getPrice())},
                    new Object[] {d.getTimestamp(), d.getSymbol(), d.getPrice()});
            min = Math.min(min, d.getPrice());
            max = Math.max(max, d.getPrice());
        }

        CurrencyData letzte = gefiltert.get(gefiltert.size() - 1);
        CurrencyData erste = gefiltert.get(0);
        view.chartData = gefiltert;
        view.summary = String.format(Locale.GERMANY, "%d Punkte  ·  %s – %s  ·  Min %s  ·  Max %s  ·  Aktuell %s",
                gefiltert.size(),
                erste.getTimestamp().format(ZEIT_FORMATTER),
                letzte.getTimestamp().format(ZEIT_FORMATTER),
                formatKurs(min), formatKurs(max), formatKurs(letzte.getPrice()));
        return view;
    }

    @Override
    protected ImageData renderChart(Object chartData, int width, int height) {
        @SuppressWarnings("unchecked")
        List<CurrencyData> daten = (List<CurrencyData>) chartData;
        if (daten == null || daten.isEmpty()) {
            return null;
        }

        TimeSeries serie = new TimeSeries("Kurs");
        for (CurrencyData d : sample(daten, MAX_CHART_PUNKTE)) {
            serie.addOrUpdate(toMillisecond(d.getTimestamp()), d.getPrice());
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(serie);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Zeit", "Kurs", dataset, false, false, false);
        XYPlot plot = chart.getXYPlot();

        // FIX 25.09.2026: Datumsformat nach der Datenspanne wählen — bei
        // "Gesamt" (Monate) wäre das Standard-Format sonst nur Uhrzeit
        org.jfree.chart.axis.DateAxis timeAxis =
                (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();
        timeAxis.setDateFormatOverride(ChartDateAxisFormat.formatFuerSpanne(
                daten.get(0).getTimestamp(),
                daten.get(daten.size() - 1).getTimestamp()));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new java.awt.Color(0, 51, 153));
        renderer.setSeriesStroke(0, new java.awt.BasicStroke(2.0f));
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setDomainGridlinePaint(java.awt.Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);
        chart.setBackgroundPaint(new java.awt.Color(250, 250, 250));

        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setAutoRangeIncludesZero(false);

        return bufferedImageToImageData(chart.createBufferedImage(width, height));
    }

    /** Kurs ohne unnötige Nachkommastellen (5 gespeicherte Dezimalstellen) */
    private static String formatKurs(double kurs) {
        String s = String.format(Locale.GERMANY, "%.5f", kurs);
        s = s.replaceAll("0+$", "");
        if (s.endsWith(",")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
