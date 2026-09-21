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

import com.mql.realmonitor.simulator.PortfolioDefinition;
import com.mql.realmonitor.simulator.PortfolioStore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: Rechtes Seitenpanel des Hauptfensters — Verwaltung der
 * Portfolio-Simulatoren.
 *
 * Oben: dynamische Liste — für JEDEM definierten Portfolio-Simulator ein
 * Icon (📊) mit Beschriftung (Name, änderbar über Bearbeiten). Klick auf
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

    private Composite panel;
    private Composite iconListe;
    private Font iconFont;
    private Font boldFont;
    private Color selectionColor;

    private final PortfolioStore store;
    private final List<PortfolioDefinition> portfolios = new ArrayList<>();

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
        baueIcons();

        if (p.getSignalIds().isEmpty()) {
            gui.showInfo("Portfolio-Simulator",
                    "'" + p.getName() + "' enthält keine Signale.\n\n"
                    + "Bitte über ✏️ Bearbeiten Signale zuordnen.");
            return;
        }

        LocalDate start;
        try {
            start = LocalDate.parse(p.getStartDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            start = gui.getMonitor().getConfig().getSimulatorStartDateParsed();
        }

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
        baueIcons();
        gui.updateStatus("Portfolio-Simulator '" + name + "' entfernt");
    }
}
