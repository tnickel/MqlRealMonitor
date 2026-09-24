package com.mql.realmonitor.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;

import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.simulator.PortfolioDefinition;
import com.mql.realmonitor.simulator.PortfolioStore;
import com.mql.realmonitor.simulator.SimulatorEngine;
import com.mql.realmonitor.simulator.SimulatorEngine.SimulationResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: Rechtes Seitenpanel des Hauptfensters — Verwaltung der
 * Portfolio-Simulatoren.
 *
 * Oben: dynamische Liste — für JEDEM definierten Portfolio-Simulator ein
 * Icon (📊) mit Beschriftung (Name, änderbar über Bearbeiten) und einer
 * Wochen-Gewinn-Zeile (€ + % der laufenden Woche, aus der Portfolio-
 * Simulation der Trade-Historien, im Hintergrund berechnet). Klick auf
 * ein Icon öffnet die Simulator-Ansicht (scrollbare Equity-Kurven der
 * enthaltenen Signale + Portfolio-Kurve) und wählt das Icon aus.
 *
 * Unten: ➕ Hinzufügen / ✏️ Bearbeiten / 🗑️ Entfernen für die Auswahl.
 * Beliebig viele Portfolios; Persistenz in {CONFIG_DIR}\portfolios.json.
 */
public class MqlSidePanel {

    private static final Logger LOGGER = Logger.getLogger(MqlSidePanel.class.getName());

    private final Composite parent;
    private final MqlRealMonitorGUI gui;

    /**
     * NEU: Zeitraum-Auswahl für die Gewinn-Zeilen unter den Portfolio-Icons
     */
    private enum Zeitraum {
        TAG("Tag", "Tag"),
        WOCHE("Woche", "Woche"),
        MONAT("Monat", "Monat"),
        M3("3 Monate", "3M"),
        M6("6 Monate", "6M"),
        M12("12 Monate", "12M");

        final String label;    // Text in der Combo
        final String praefix;  // Präfix der Gewinn-Zeile

        Zeitraum(String label, String praefix) {
            this.label = label;
            this.praefix = praefix;
        }

        /** Start des Zeitraums (jeweiliger Tag 00:00) */
        java.time.LocalDateTime start() {
            LocalDate heute = LocalDate.now();
            switch (this) {
                case TAG:   return heute.atStartOfDay();
                case WOCHE: return SimulatorEngine.aktuellerWochenstart().atStartOfDay();
                case MONAT: return heute.withDayOfMonth(1).atStartOfDay();
                case M3:    return heute.minusMonths(3).atStartOfDay();
                case M6:    return heute.minusMonths(6).atStartOfDay();
                case M12:   return heute.minusMonths(12).atStartOfDay();
                default:    return heute.atStartOfDay();
            }
        }
    }

    private Composite panel;
    private Composite iconListe;
    private Font iconFont;
    private Font boldFont;
    private Color selectionColor;

    /** NEU: Zeitraum-Auswahl der Gewinn-Zeilen (Combo + aktueller Wert) */
    private org.eclipse.swt.widgets.Combo zeitraumCombo;
    private Zeitraum zeitraum = Zeitraum.WOCHE;

    private final PortfolioStore store;
    private final List<PortfolioDefinition> portfolios = new ArrayList<>();

    /**
     * NEU: Gewinn-Zeilen je Portfolio ("Woche: +123 € (+2,34 %)") für den
     * ausgewählten Zeitraum. Cache-Key: "<portfolioId>@<ZEITRAUM>@<Start>",
     * Wert null = Berechnung läuft. Wird bei Zeitraumwechsel und Add/Edit/
     * Remove geleert, beim Anklicken nur der Eintrag des Portfolios.
     */
    private final Map<String, String> gewinnCache =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** NEU: Hintergrund-Thread für die Gewinn-Berechnung (Datei-IO) */
    private final java.util.concurrent.ExecutorService gewinnPool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Portfolio-Gewinn");
                t.setDaemon(true);
                return t;
            });

    /** Aktuell ausgewähltes Portfolio (Klick zuletzt geklicktes Icon) */
    private PortfolioDefinition auswahl;

    public MqlSidePanel(Composite parent, MqlRealMonitorGUI gui) {
        this.parent = parent;
        this.gui = gui;
        this.store = new PortfolioStore(gui.getMonitor().getConfig().getConfigDir());
    }

    /**
     * Erstellt den Panel-Inhalt und lädt die gespeicherten Portfolios
     */
    public void createContent() {
        portfolios.clear();
        portfolios.addAll(store.load());

        panel = new Composite(parent, SWT.NONE);
        panel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        panel.setLayout(new GridLayout(1, false));

        FontData[] fd = gui.getDisplay().getSystemFont().getFontData();
        FontData iconData = fd[0];
        iconData.setHeight(iconData.getHeight() + 6);
        iconFont = new Font(gui.getDisplay(), iconData);
        FontData boldData = fd[0];
        boldData.setStyle(SWT.BOLD);
        boldFont = new Font(gui.getDisplay(), boldData);
        selectionColor = new Color(gui.getDisplay(), 220, 235, 250);

        // Titel
        Label titel = new Label(panel, SWT.NONE);
        titel.setText("Portfolios");
        titel.setFont(boldFont);
        titel.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));

        Label trenner = new Label(panel, SWT.SEPARATOR | SWT.HORIZONTAL);
        trenner.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // NEU: Zeitraum-Auswahl für die Gewinn-Zeilen
        Composite zeitraumZeile = new Composite(panel, SWT.NONE);
        zeitraumZeile.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        zeitraumZeile.setLayout(new GridLayout(2, false));

        Label gewinnTitel = new Label(zeitraumZeile, SWT.NONE);
        gewinnTitel.setText("Gewinn:");
        gewinnTitel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        zeitraumCombo = new org.eclipse.swt.widgets.Combo(zeitraumZeile, SWT.READ_ONLY);
        for (Zeitraum z : Zeitraum.values()) {
            zeitraumCombo.add(z.label);
        }
        zeitraumCombo.select(Zeitraum.WOCHE.ordinal());
        zeitraumCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        zeitraumCombo.setToolTipText("Zeitraum der Gewinn-Zeilen unter den Portfolios.\n"
                + "Basis: Live-Tick-Daten (Δ Profit+Floating) je Signal,\n"
                + "angewendet auf das Sim-Kapital am Periodenstart.");
        zeitraumCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Zeitraum neu = Zeitraum.values()[zeitraumCombo.getSelectionIndex()];
                if (neu != zeitraum) {
                    zeitraum = neu;
                    gewinnCache.clear();
                    baueIcons();
                }
            }
        });

        // Dynamische Icon-Liste
        iconListe = new Composite(panel, SWT.NONE);
        iconListe.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        iconListe.setLayout(new GridLayout(1, false));

        baueIcons();

        // Verwaltungs-Buttons
        Label trenner2 = new Label(panel, SWT.SEPARATOR | SWT.HORIZONTAL);
        trenner2.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite buttons = new Composite(panel, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        buttons.setLayout(new GridLayout(1, true));

        Button addButton = new Button(buttons, SWT.PUSH);
        addButton.setText("➕ Hinzufügen");
        addButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        addButton.setToolTipText("Neuen Portfolio-Simulator anlegen (Name, Startdatum, Startkapital, Signale)");

        Button editButton = new Button(buttons, SWT.PUSH);
        editButton.setText("✏️ Bearbeiten");
        editButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        editButton.setToolTipText("Ausgewählten Portfolio-Simulator bearbeiten (Beschriftung ändern, Signale anpassen)");

        Button removeButton = new Button(buttons, SWT.PUSH);
        removeButton.setText("🗑️ Entfernen");
        removeButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        removeButton.setToolTipText("Ausgewählten Portfolio-Simulator löschen");

        addButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                portfolioHinzufuegen();
            }
        });
        editButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                portfolioBearbeiten();
            }
        });
        removeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                portfolioEntfernen();
            }
        });

        LOGGER.info("Seitenpanel erstellt: " + portfolios.size() + " Portfolio-Simulatoren");
    }

    // ------------------------------------------------------------- Icons

    /**
     * Baut die Icon-Liste neu auf (nach Add/Edit/Remove)
     */
    private void baueIcons() {
        for (var kind : iconListe.getChildren()) {
            kind.dispose();
        }

        if (portfolios.isEmpty()) {
            Label leer = new Label(iconListe, SWT.WRAP);
            leer.setText("Noch keine Portfolio-Simulatoren.\n➕ Hinzufügen klicken.");
            leer.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        }

        for (PortfolioDefinition p : portfolios) {
            Composite eintrag = new Composite(iconListe, SWT.NONE);
            eintrag.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            eintrag.setLayout(new GridLayout(1, false));

            Button icon = new Button(eintrag, SWT.PUSH);
            icon.setText("\uD83D\uDCCA"); // 📊
            icon.setFont(iconFont);
            GridData gd = new GridData(SWT.CENTER, SWT.TOP, true, false);
            gd.widthHint = 56;
            gd.heightHint = 48;
            icon.setLayoutData(gd);
            icon.setToolTipText(portfolioTooltip(p));

            Label beschriftung = new Label(eintrag, SWT.WRAP | SWT.CENTER);
            beschriftung.setText(p.getName() != null ? p.getName() : ("#" + p.getId()));
            beschriftung.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));

            // NEU: Gewinn-Zeile unter der Beschriftung (€ und %, Zeitraum siehe Combo)
            Label gewinn = new Label(eintrag, SWT.WRAP | SWT.CENTER);
            String gewinnZeile = gewinnCache.get(gewinnKey(p));
            gewinn.setText(gewinnZeile != null ? gewinnZeile : "berechne …");
            gewinn.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
            gewinn.setToolTipText("Gewinn im Zeitraum ab " + zeitraum.start().toLocalDate() + ":\n"
                    + "Δ Profit+Floating je Signal aus den Tick-Daten (15-Minuten-\n"
                    + "Snapshots von Kontostand/Equity), angewendet auf das Sim-\n"
                    + "Kapital am Periodenstart. Funktioniert auch ohne Trade-\n"
                    + "Historie (nicht abonnierte Signale). Signale ohne Tick-Daten\n"
                    + "fehlt die Basis. Fallback: Simulationskurve ohne Ticks.\n"
                    + "Klick auf das Portfolio rechnet mit aktuellen Daten neu.");
            if (gewinnZeile != null) {
                faerbeGewinnZeile(gewinn, gewinnZeile);
            }

            markiereAuswahl(eintrag, p);

            SelectionAdapter klick = new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    portfolioAnklicken(p);
                }
            };
            icon.addSelectionListener(klick);
            // Beschriftung klickt wie das Icon
            beschriftung.addMouseListener(new org.eclipse.swt.events.MouseAdapter() {
                @Override
                public void mouseDown(org.eclipse.swt.events.MouseEvent e) {
                    portfolioAnklicken(p);
                }
            });

            eintrag.setData("portfolioId", p.getId());
        }

        iconListe.layout();
        panel.layout();
        if (parent instanceof org.eclipse.swt.custom.SashForm) {
            ((org.eclipse.swt.custom.SashForm) parent).layout();
        }

        // NEU: Fehlende Gewinn-Werte im Hintergrund nachrechnen
        berechneGewinneAsynchron();
    }

    // --------------------------------------------------- Gewinn-Zeilen

    /** Cache-Key eines Portfolios für den aktuellen Zeitraum */
    private String gewinnKey(PortfolioDefinition p) {
        return gewinnKey(p.getId());
    }

    /** Cache-Key für eine Portfolio-ID im aktuellen Zeitraum */
    private String gewinnKey(int portfolioId) {
        return portfolioId + "@" + zeitraum.name() + "@" + zeitraum.start().toLocalDate();
    }

    /**
     * NEU: Berechnet fehlende Gewinn-Werte je Portfolio im Hintergrund
     * (Portfolio-Simulation + Periodenprozente aus den Tick-Daten) und
     * aktualisiert die Icon-Zeilen, sobald Ergebnisse vorliegen.
     */
    private void berechneGewinneAsynchron() {
        if (portfolios.isEmpty()) {
            return;
        }

        final Zeitraum periode = zeitraum;
        SimulatorEngine engine = new SimulatorEngine(gui.getMonitor().getConfig());
        Map<String, String> namen = new LinkedHashMap<>();
        IdTranslationManager translation = gui.getProviderTable() != null
                ? gui.getProviderTable().getIdTranslationManager() : null;

        for (PortfolioDefinition p : portfolios) {
            for (String id : p.getSignalIds()) {
                namen.put(id, translation != null ? translation.getProviderName(id) : id);
            }

            final PortfolioDefinition portfolio = p;
            final String key = p.getId() + "@" + periode.name() + "@" + periode.start().toLocalDate();
            final Map<String, String> namenSnapshot = namen;
            synchronized (gewinnCache) {
                if (gewinnCache.containsKey(key)) {
                    continue; // schon berechnet oder Berechnung läuft
                }
                gewinnCache.put(key, null); // Platzhalter "in Arbeit"
            }

            gewinnPool.submit(() -> {
                String zeile;
                try {
                    SimulationResult result = engine.simulate(
                            new ArrayList<>(portfolio.getSignalIds()), namenSnapshot,
                            parseStartdatum(portfolio.getStartDate()), portfolio.getStartCapital());
                    zeile = formatGewinnZeile(periode, result, ladeProzente(portfolio, periode));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "Gewinn für '" + portfolio.getName() + "' fehlgeschlagen", e);
                    zeile = periode.praefix + ": —";
                }
                gewinnCache.put(key, zeile);

                if (gui.getDisplay() != null && !gui.getDisplay().isDisposed()) {
                    gui.getDisplay().asyncExec(() -> {
                        if (iconListe != null && !iconListe.isDisposed()) {
                            aktualisiereGewinnLabels();
                        }
                    });
                }
            });
        }
    }

    /**
     * NEU: Schreibt die fertigen Gewinn-Zeilen in die vorhandenen Labels
     * (ohne die Icons komplett neu zu bauen)
     */
    private void aktualisiereGewinnLabels() {
        for (var kind : iconListe.getChildren()) {
            Object pid = kind.getData("portfolioId");
            if (pid == null || !(kind instanceof Composite)) {
                continue;
            }
            var kinder = ((Composite) kind).getChildren();
            if (kinder.length < 3 || !(kinder[2] instanceof Label) || kinder[2].isDisposed()) {
                continue;
            }
            String zeile = gewinnCache.get(pid + "@" + zeitraum.name() + "@"
                    + zeitraum.start().toLocalDate());
            if (zeile == null) {
                continue; // noch in Arbeit
            }
            Label gewinn = (Label) kinder[2];
            if (!gewinn.getText().equals(zeile)) {
                gewinn.setText(zeile);
                faerbeGewinnZeile(gewinn, zeile);
            }
        }
        iconListe.layout();
        panel.layout();
    }

    /**
     * NEU: Lädt je Signal den Gewinn in % seit dem Periodenstart aus den
     * Tick-Daten (dieselbe Quelle wie die Gewinn-Spalten der Tabelle).
     * Signale ohne Daten fehlen in der Map — ihr Kapital zählt mit 0 %.
     */
    private Map<String, Double> ladeProzente(PortfolioDefinition portfolio, Zeitraum periode) {
        Map<String, Double> prozente = new LinkedHashMap<>();
        var config = gui.getMonitor().getConfig();
        for (String id : portfolio.getSignalIds()) {
            try {
                com.mql.realmonitor.utils.PeriodProfitCalculator.PeriodResult pr =
                        com.mql.realmonitor.utils.PeriodProfitCalculator.calculatePeriodProfit(
                                config.getTickFilePath(id), id, periode.start());
                if (pr.hasData) {
                    prozente.put(id, pr.percent);
                }
            } catch (Exception e) {
                LOGGER.fine("Keine Periodendaten für Signal " + id + ": " + e.getMessage());
            }
        }
        return prozente;
    }

    /** NEU: Formatiert die Gewinn-Zeile aus dem Simulationsergebnis */
    private String formatGewinnZeile(Zeitraum periode, SimulationResult result,
                                     Map<String, Double> prozente) {
        if (result.portfolio.isEmpty() && prozente.isEmpty()) {
            return periode.praefix + ": —"; // weder Trade-Historie noch Tick-Daten
        }
        // Bevorzugt LIVE-Periodenprozente aus den Tick-Daten — die hängen nicht
        // an der Trade-Historie und funktionieren damit auch auf Systemen, deren
        // Signale nicht abonniert sind (MQL5 liefert dort die Trade-Liste nur
        // verzögert). Fallback auf die Simulationskurve, wenn keine Ticks.
        double[] w = !prozente.isEmpty()
                ? SimulatorEngine.gewinnSeitAusTicks(result, prozente, periode.start())
                : SimulatorEngine.gewinnSeitAusKurve(result, periode.start());
        return String.format(Locale.GERMANY, "%s: %+.0f € (%+.2f %%)",
                periode.praefix, w[0], w[1]);
    }

    /** NEU: Grün bei Gewinn, Rot bei Verlust (anhand des Vorzeichens) */
    private void faerbeGewinnZeile(Label label, String zeile) {
        int pos = zeile.indexOf(':') + 2;
        if (pos >= 2 && pos < zeile.length()) {
            char vorzeichen = zeile.charAt(pos);
            if (vorzeichen == '-') {
                label.setForeground(gui.getDisplay().getSystemColor(SWT.COLOR_DARK_RED));
            } else if (vorzeichen == '+') {
                label.setForeground(gui.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
            }
        }
    }

    /** NEU: Startdatum parsen (Fallback: globale Simulator-Config) */
    private LocalDate parseStartdatum(String startDate) {
        try {
            return LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return gui.getMonitor().getConfig().getSimulatorStartDateParsed();
        }
    }

    private void markiereAuswahl(Composite eintrag, PortfolioDefinition p) {
        boolean istAuswahl = auswahl != null && auswahl.getId() == p.getId();
        eintrag.setBackground(istAuswahl ? selectionColor : null);
        for (var kind : eintrag.getChildren()) {
            kind.setBackground(istAuswahl ? selectionColor : null);
        }
    }

    private String portfolioTooltip(PortfolioDefinition p) {
        return p.getName() + " — " + p.getSignalIds().size() + " Signale, Start "
                + p.getStartDate() + ", " + String.format("%.0f", p.getStartCapital())
                + " je Strategie (Klick: Simulation öffnen)";
    }

    /**
     * Klick auf ein Portfolio-Icon: auswählen und Simulator öffnen
     */
    private void portfolioAnklicken(PortfolioDefinition p) {
        auswahl = p;
        // Gewinn-Zeilen des angeklickten Portfolios mit aktuellen Tick-Daten neu rechnen
        gewinnCache.keySet().removeIf(k -> k.startsWith(p.getId() + "@"));
        baueIcons();

        if (p.getSignalIds().isEmpty()) {
            gui.showInfo("Portfolio-Simulator",
                    "'" + p.getName() + "' enthält keine Signale.\n\n"
                    + "Bitte über ✏️ Bearbeiten Signale zuordnen.");
            return;
        }

        LocalDate start = parseStartdatum(p.getStartDate());

        new SimulatorWindow(gui).open(
                p.getName(),
                new ArrayList<>(p.getSignalIds()),
                start,
                p.getStartCapital());
    }

    // --------------------------------------------------------- CRUD

    /**
     * ➕ Hinzufügen: neuer Portfolio-Simulator mit Defaults aus der Config
     */
    private void portfolioHinzufuegen() {
        try {
            PortfolioDefinition neu = new PortfolioDefinition(
                    PortfolioStore.nextId(portfolios),
                    "Portfolio " + (portfolios.size() + 1),
                    gui.getMonitor().getConfig().getSimulatorStartDate(),
                    gui.getMonitor().getConfig().getSimulatorStartCapital());

            PortfolioEditDialog dialog = new PortfolioEditDialog(gui, neu, true);
            if (dialog.openDialog()) {
                portfolios.add(neu);
                auswahl = neu;
                store.save(portfolios);
                gewinnCache.clear(); // NEU: Werte neu berechnen
                baueIcons();
                gui.updateStatus("Portfolio-Simulator '" + neu.getName() + "' angelegt ("
                        + neu.getSignalIds().size() + " Signale)");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Anlegen eines Portfolio-Simulators", e);
            gui.showError("Portfolio-Simulator", "Anlegen fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * ✏️ Bearbeiten: Beschriftung/Datum/Kapital/Signale der Auswahl ändern
     */
    private void portfolioBearbeiten() {
        if (auswahl == null) {
            gui.showInfo("Portfolio-Simulator", "Bitte zuerst ein Portfolio-Icon anklicken.");
            return;
        }
        try {
            // Arbeitskopie — Abbrechen verwirft die Änderungen
            PortfolioDefinition kopie = new PortfolioDefinition(
                    auswahl.getId(), auswahl.getName(),
                    auswahl.getStartDate(), auswahl.getStartCapital());
            kopie.setSignalIds(new ArrayList<>(auswahl.getSignalIds()));

            PortfolioEditDialog dialog = new PortfolioEditDialog(gui, kopie, false);
            if (dialog.openDialog()) {
                PortfolioDefinition original = PortfolioStore.findById(portfolios, auswahl.getId());
                if (original != null) {
                    original.setName(kopie.getName());
                    original.setStartDate(kopie.getStartDate());
                    original.setStartCapital(kopie.getStartCapital());
                    original.setSignalIds(kopie.getSignalIds());
                }
                store.save(portfolios);
                gewinnCache.clear(); // NEU: Werte neu berechnen
                baueIcons();
                gui.updateStatus("Portfolio-Simulator '" + original.getName() + "' gespeichert");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Bearbeiten eines Portfolio-Simulators", e);
            gui.showError("Portfolio-Simulator", "Bearbeiten fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * 🗑️ Entfernen: Auswahl löschen (mit Rückfrage)
     */
    private void portfolioEntfernen() {
        if (auswahl == null) {
            gui.showInfo("Portfolio-Simulator", "Bitte zuerst ein Portfolio-Icon anklicken.");
            return;
        }
        MessageBox box = new MessageBox(gui.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        box.setText("Portfolio-Simulator entfernen");
        box.setMessage("Portfolio-Simulator '" + auswahl.getName() + "' wirklich löschen?\n\n"
                + "(" + auswahl.getSignalIds().size() + " Signale — die Signale selbst und alle\n"
                + "gespeicherten Daten bleiben erhalten.)");
        if (box.open() != SWT.YES) {
            return;
        }

        portfolios.removeIf(p -> p.getId() == auswahl.getId());
        store.save(portfolios);
        String name = auswahl.getName();
        auswahl = null;
        gewinnCache.clear(); // NEU: Werte neu berechnen
        baueIcons();
        gui.updateStatus("Portfolio-Simulator '" + name + "' entfernt");
    }
}
