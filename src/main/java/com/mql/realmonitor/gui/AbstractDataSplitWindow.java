package com.mql.realmonitor.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import org.jfree.data.time.Millisecond;

import com.mql.realmonitor.simulator.SimulatorEngine;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * NEU (v1.4.8): Gemeinsame Basis für geteilte Daten-Fenster — großes,
 * nicht-blockierendes Fenster mit Chart oben und klick-sortierbarer
 * Tabelle unten (SashForm, Trennlinie verschiebbar).
 *
 * Unterklassen liefern nur die Teile, die sich unterscheiden:
 *  - fillAuswahlCombo(): Auswahl oben (Symbol bzw. Signal) befüllen
 *  - loadData(): Daten im Hintergrund laden und in Zeilen/Schlüssel packen
 *  - renderChart(): Chart für die geladenen Daten als Bild rendern
 *  - tableHeaders()/columnWidths()/columnAlignments()/getDefaultSortColumn()
 *
 * Alles Gemeinsame regelt diese Klasse: Zeitraum-Auswahl (Gesamt bis
 * 12 Monate, gleiche Regeln wie im Simulator-Fenster), Aktualisieren-
 * Button, Hintergrund-Laden mit Generations-Schutz, Sortierung per
 * Klick auf den Spaltenkopf (Zahlen sortieren nach Wert, nicht Text)
 * und Chart-Neurendern beim Fenster-Vergrößern (gedeckelt per Timer).
 */
public abstract class AbstractDataSplitWindow {

    protected static final Logger LOGGER = Logger.getLogger(AbstractDataSplitWindow.class.getName());

    private static final PaletteData PALETTE = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);

    /** Maximal gerenderte Chart-Punkte (größere Datenmengen werden ausgedünnt) */
    protected static final int MAX_CHART_PUNKTE = 2000;

    /** Wählbare Anzeige-Zeiträume (wie im Simulator-Fenster) */
    protected static final String[] ZEITRAEUME = {
            "Gesamt", "Tag", "Woche", "Monat", "3 Monate", "6 Monate", "12 Monate"};

    /** Eine Tabellen-Zeile: Anzeige-Texte plus Sortier-Schlüssel je Spalte */
    protected static final class Row {
        final String[] cells;
        final Object[] keys;

        Row(String[] cells, Object[] keys) {
            this.cells = cells;
            this.keys = keys;
        }
    }

    /** Ergebnis eines Hintergrund-Ladelaufs */
    protected static final class ViewData {
        final List<Row> rows = new ArrayList<>();
        /** Rohdaten (gefiltert) fürs Chart-Neurendern beim Resize */
        Object chartData;
        String summary = "";
        String statusMessage;

        void addRow(String[] cells, Object[] keys) {
            rows.add(new Row(cells, keys));
        }
    }

    protected final MqlRealMonitorGUI gui;
    protected Shell shell;
    protected Font titleFont;

    private Combo auswahlCombo;
    private Combo zeitraumCombo;
    private Label messageLabel;
    private Canvas chartCanvas;
    private Image chartImage;
    private Table dataTable;
    private Label summaryLabel;

    private String currentAuswahl = "";
    private String aktuellerZeitraum = "Gesamt";
    private List<Row> currentRows = new ArrayList<>();
    private Object currentChartData;
    private int sortColumn;
    private boolean sortAscending;
    private int generation;
    private boolean rerenderScheduled;

    protected AbstractDataSplitWindow(MqlRealMonitorGUI gui) {
        this.gui = gui;
    }

    // ------------------------------------------------------------ Unterklassen-API

    /** Fester Teil des Fenstertitels (z. B. "Währungskurse anzeigen") */
    protected abstract String windowTitle();

    /** Befüllt die Auswahl-Combo oben und trifft die Vorwahl */
    protected abstract void fillAuswahlCombo(Combo combo);

    /** Spaltenüberschriften der Tabelle */
    protected abstract String[] tableHeaders();

    /** Spaltenbreiten in Pixeln (gleiche Reihenfolge wie tableHeaders) */
    protected abstract int[] columnWidths();

    /** Ausrichtung je Spalte: SWT.LEFT / SWT.CENTER / SWT.RIGHT */
    protected abstract int[] columnAlignments();

    /** Spalte, nach der anfangs absteigend sortiert ist (meist Zeitstempel) */
    protected abstract int getDefaultSortColumn();

    /**
     * Lädt die Daten für die Auswahl im Zeitraum (Hintergrund-Thread).
     * Zeilen mit addRow() liefern; chartData = Rohdaten fürs Chart.
     */
    protected abstract ViewData loadData(String auswahl, String zeitraum) throws Exception;

    /** Rendert das Chart für view.chartData als Bild (Hintergrund-Thread) */
    protected abstract ImageData renderChart(Object chartData, int width, int height);

    /** Dynamischer Fenstertitel nach dem Laden (z. B. inkl. Signalname) */
    protected String shellTitleFor(String auswahl) {
        return windowTitle() + " — " + auswahl;
    }

    // ------------------------------------------------------------ Fenster-Aufbau

    /** Öffnet das Fenster und startet den ersten Ladelauf */
    public void open() {
        Display display = gui.getDisplay();
        shell = new Shell(display, SWT.SHELL_TRIM | SWT.MODELESS);
        shell.setText(windowTitle());
        shell.setSize(1400, 900);
        shell.setLayout(new GridLayout(1, false));

        FontData base = display.getSystemFont().getFontData()[0];
        base.setStyle(SWT.BOLD);
        base.setHeight(base.getHeight() + 2);
        titleFont = new Font(display, base);

        // Kopfzeile: Titel + Auswahl + Zeitraum + Aktualisieren
        Composite header = new Composite(shell, SWT.NONE);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        header.setLayout(new GridLayout(6, false));

        Label titel = new Label(header, SWT.NONE);
        titel.setText(windowTitle());
        titel.setFont(titleFont);
        titel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        auswahlCombo = new Combo(header, SWT.READ_ONLY);
        auswahlCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        fillAuswahlCombo(auswahlCombo);
        currentAuswahl = auswahlCombo.getText();
        auswahlCombo.setToolTipText("Welche Daten angezeigt werden");
        auswahlCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                currentAuswahl = auswahlCombo.getText();
                startLoad();
            }
        });

        Label anzeigeLabel = new Label(header, SWT.NONE);
        anzeigeLabel.setText("Anzeige:");
        anzeigeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        zeitraumCombo = new Combo(header, SWT.READ_ONLY);
        for (String z : ZEITRAEUME) {
            zeitraumCombo.add(z);
        }
        zeitraumCombo.select(0);
        zeitraumCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        zeitraumCombo.setToolTipText("Sichtbarer Zeitraum — gilt für Chart und Tabelle");
        zeitraumCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String neu = ZEITRAEUME[zeitraumCombo.getSelectionIndex()];
                if (!neu.equals(aktuellerZeitraum)) {
                    aktuellerZeitraum = neu;
                    startLoad();
                }
            }
        });

        Button refreshButton = new Button(header, SWT.PUSH);
        refreshButton.setText("\uD83D\uDD04"); // 🔄
        refreshButton.setToolTipText("Daten neu laden (z. B. nach 'Kurse laden')");
        refreshButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                startLoad();
            }
        });

        // Status-/Hinweis-Zeile (Lade..., Fehler, Leer-Hinweise)
        messageLabel = new Label(shell, SWT.NONE);
        messageLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Geteilter Bereich: Chart oben, Tabelle unten
        SashForm sash = new SashForm(shell, SWT.VERTICAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        chartCanvas = new Canvas(sash, SWT.DOUBLE_BUFFERED);
        chartCanvas.addPaintListener(e -> {
            if (chartImage != null && !chartImage.isDisposed()) {
                e.gc.drawImage(chartImage, 0, 0);
            } else {
                e.gc.setForeground(e.display.getSystemColor(SWT.COLOR_GRAY));
                e.gc.drawText("Keine Daten", 10, 10, true);
            }
        });
        chartCanvas.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                scheduleChartRerender();
            }
        });

        Composite tableArea = new Composite(sash, SWT.NONE);
        tableArea.setLayout(new GridLayout(1, false));

        summaryLabel = new Label(tableArea, SWT.NONE);
        summaryLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        dataTable = new Table(tableArea, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        dataTable.setHeaderVisible(true);
        dataTable.setLinesVisible(true);
        dataTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        String[] headers = tableHeaders();
        int[] widths = columnWidths();
        int[] alignments = columnAlignments();
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            TableColumn column = new TableColumn(dataTable, alignments[i]);
            column.setText(headers[i]);
            column.setWidth(widths[i]);
            column.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    handleColumnClick(index);
                }
            });
        }
        sortColumn = Math.min(getDefaultSortColumn(), headers.length - 1);
        sortAscending = false; // neueste Einträge oben

        sash.setWeights(55, 45);

        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                if (chartImage != null && !chartImage.isDisposed()) {
                    chartImage.dispose();
                    chartImage = null;
                }
                if (titleFont != null && !titleFont.isDisposed()) {
                    titleFont.dispose();
                }
            }
        });

        shell.open();
        startLoad();
    }

    // ------------------------------------------------------------ Laden

    /**
     * Startet einen Ladelauf im Hintergrund. Der Generations-Zähler sorgt
     * dafür, dass Ergebnisse veralteter Läufe (z. B. nach schnellem Umschalten)
     * nicht mehr eingeblendet werden.
     */
    protected void startLoad() {
        if (shell == null || shell.isDisposed()) {
            return;
        }
        final int gen = ++generation;
        final String auswahl = currentAuswahl;
        final String zeitraum = aktuellerZeitraum;

        showMessage("Lade Daten ...");
        summaryLabel.setText("");
        clearTable();
        setChartImage(null);

        // Canvas-Größe nur im UI-Thread lesen
        org.eclipse.swt.graphics.Point canvasSize = chartCanvas.getSize();
        final int w = Math.max(canvasSize.x, 600);
        final int h = Math.max(canvasSize.y, 250);

        Thread loader = new Thread(() -> {
            try {
                ViewData view = loadData(auswahl, zeitraum);
                ImageData bild = (view.chartData != null && !view.rows.isEmpty())
                        ? renderChart(view.chartData, w, h)
                        : null;
                final ImageData finalBild = bild;
                Display display = gui.getDisplay();
                display.asyncExec(() -> {
                    if (gen != generation || shell.isDisposed()) {
                        return;
                    }
                    currentRows = view.rows;
                    currentChartData = view.chartData;
                    applySortAndFill();
                    summaryLabel.setText(view.summary != null ? view.summary : "");
                    showMessage(view.statusMessage);
                    setChartImage(finalBild);
                    shell.setText(shellTitleFor(auswahl));
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Ladelauf fehlgeschlagen (" + windowTitle() + ")", e);
                gui.getDisplay().asyncExec(() -> {
                    if (gen == generation && !shell.isDisposed()) {
                        showMessage("Fehler beim Laden: " + e.getMessage());
                    }
                });
            }
        }, "DataSplitLoad-" + getClass().getSimpleName());
        loader.setDaemon(true);
        loader.start();
    }

    // ------------------------------------------------------------ Sortierung & Tabelle

    private void handleColumnClick(int index) {
        if (sortColumn == index) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = index;
            sortAscending = true;
        }
        applySortAndFill();
    }

    /** Sortiert die aktuellen Zeilen und füllt die Tabelle neu */
    private void applySortAndFill() {
        List<Row> rows = new ArrayList<>(currentRows);
        final int col = sortColumn;
        final boolean asc = sortAscending;
        rows.sort(new Comparator<Row>() {
            @Override
            @SuppressWarnings("unchecked")
            public int compare(Row a, Row b) {
                Object ka = a.keys[col];
                Object kb = b.keys[col];
                if (ka == null && kb == null) {
                    return 0;
                }
                if (ka == null) {
                    return -1;
                }
                if (kb == null) {
                    return 1;
                }
                int r = ((Comparable<Object>) ka).compareTo(kb);
                return asc ? r : -r;
            }
        });

        TableColumn[] columns = dataTable.getColumns();
        if (sortColumn < columns.length) {
            dataTable.setSortColumn(columns[sortColumn]);
            dataTable.setSortDirection(asc ? SWT.UP : SWT.DOWN);
        }

        dataTable.setRedraw(false);
        try {
            dataTable.removeAll();
            for (Row row : rows) {
                TableItem item = new TableItem(dataTable, SWT.NONE);
                item.setText(row.cells);
            }
        } finally {
            dataTable.setRedraw(true);
        }
    }

    private void clearTable() {
        if (dataTable != null && !dataTable.isDisposed()) {
            dataTable.removeAll();
        }
        if (summaryLabel != null && !summaryLabel.isDisposed()) {
            summaryLabel.setText("");
        }
        if (dataTable != null && !dataTable.isDisposed() && dataTable.getSortColumn() != null) {
            dataTable.setSortColumn(null);
        }
    }

    // ------------------------------------------------------------ Chart-Bild

    private void setChartImage(ImageData data) {
        if (chartImage != null && !chartImage.isDisposed()) {
            chartImage.dispose();
            chartImage = null;
        }
        if (data != null && chartCanvas != null && !chartCanvas.isDisposed()) {
            chartImage = new Image(gui.getDisplay(), data);
        }
        if (chartCanvas != null && !chartCanvas.isDisposed()) {
            chartCanvas.redraw();
        }
    }

    /**
     * Chart beim Fenster-Vergrößern neu rendern — mit kurzem Timer-Abstand,
     * damit das Ziehen der Fensterkante nicht Dutzende Renderings erzeugt.
     */
    private void scheduleChartRerender() {
        if (currentChartData == null || rerenderScheduled) {
            return;
        }
        rerenderScheduled = true;
        gui.getDisplay().timerExec(250, () -> {
            rerenderScheduled = false;
            if (shell.isDisposed() || chartCanvas.isDisposed() || currentChartData == null) {
                return;
            }
            org.eclipse.swt.graphics.Point size = chartCanvas.getSize();
            if (chartImage != null && !chartImage.isDisposed()
                    && chartImage.getBounds().width == size.x
                    && chartImage.getBounds().height == size.y) {
                return;
            }
            if (size.x < 50 || size.y < 50) {
                return;
            }
            final int gen = generation;
            final Object data = currentChartData;
            final int w = size.x;
            final int h = size.y;
            Thread renderer = new Thread(() -> {
                try {
                    ImageData bild = renderChart(data, w, h);
                    gui.getDisplay().asyncExec(() -> {
                        if (gen == generation && !shell.isDisposed()) {
                            setChartImage(bild);
                        }
                    });
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Chart-Neurendering fehlgeschlagen", e);
                }
            }, "DataSplitResize-" + getClass().getSimpleName());
            renderer.setDaemon(true);
            renderer.start();
        });
    }

    private void showMessage(String text) {
        if (messageLabel != null && !messageLabel.isDisposed()) {
            messageLabel.setText(text != null ? text : "");
            messageLabel.getParent().layout();
        }
    }

    // ------------------------------------------------------------ Gemeinsame Helfer

    /**
     * Startzeitpunkt eines Anzeige-Zeitraums (gleiche Regeln wie im
     * Simulator-Fenster); null = "Gesamt"
     */
    protected static LocalDateTime zeitraumStart(String zeitraum) {
        LocalDate heute = LocalDate.now();
        switch (zeitraum) {
            case "Tag":       return heute.atStartOfDay();
            case "Woche":     return SimulatorEngine.aktuellerWochenstart().atStartOfDay();
            case "Monat":     return heute.withDayOfMonth(1).atStartOfDay();
            case "3 Monate":  return heute.minusMonths(3).atStartOfDay();
            case "6 Monate":  return heute.minusMonths(6).atStartOfDay();
            case "12 Monate": return heute.minusMonths(12).atStartOfDay();
            default:          return null;
        }
    }

    /** Dünnt eine Liste gleichmäßig ein, wenn sie mehr als max Punkte hat */
    protected static <T> List<T> sample(List<T> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        int stride = (int) Math.ceil((double) list.size() / max);
        List<T> out = new ArrayList<>(list.size() / stride + 2);
        for (int i = 0; i < list.size(); i += stride) {
            out.add(list.get(i));
        }
        // Letzten Punkt immer nehmen, damit die Kurve bis "jetzt" reicht
        T last = list.get(list.size() - 1);
        if (out.get(out.size() - 1) != last) {
            out.add(last);
        }
        return out;
    }

    protected static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    protected static Millisecond toMillisecond(LocalDateTime time) {
        return new Millisecond(toDate(time));
    }

    /** Zahl im deutschen Format mit Tausenderpunkten (z. B. 105.123,45) */
    protected static String formatZahl(double wert) {
        return String.format(Locale.GERMANY, "%,.2f", wert);
    }

    /** BufferedImage → SWT ImageData (gleiche Konvertierung wie ChartImageRenderer) */
    protected static ImageData bufferedImageToImageData(BufferedImage image) {
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
