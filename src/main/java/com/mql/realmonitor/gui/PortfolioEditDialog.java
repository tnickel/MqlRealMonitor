package com.mql.realmonitor.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.config.IdTranslationManager;
import com.mql.realmonitor.downloader.FavoritesReader;
import com.mql.realmonitor.simulator.PortfolioDefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * NEU: Dialog zum Anlegen (Add) und Bearbeiten (Edit) eines Portfolio-
 * Simulators.
 *
 * Felder: Name (Beschriftung des Icons im Seitenpanel), Startdatum
 * (JJJJ-MM-TT), Startkapital je Strategie und die Signal-Auswahl als
 * Checkbox-Liste der aktuellen Favoriten (mit Namen aus der ID-Translation).
 *
 * Bei Add wird die Definition vorbelegt mit den Werten aus der globalen
 * Simulator-Config.
 */
public class PortfolioEditDialog extends Dialog {

    private final MqlRealMonitorGUI gui;
    private final PortfolioDefinition definition;   // Arbeitskopie
    private final boolean isNeu;

    private Text nameText;
    private Text dateText;
    private Text capitalText;
    private Table signalTable;

    private boolean saved = false;

    /**
     * @param gui        Haupt-GUI
     * @param definition Zu bearbeitende Definition (Arbeitskopie) — bei Add
     *                   bereits mit Defaults vorbelegt, id = 0
     * @param isNeu      true = Anlegen (Titel "hinzufügen"), false = Bearbeiten
     */
    public PortfolioEditDialog(MqlRealMonitorGUI gui, PortfolioDefinition definition, boolean isNeu) {
        super(gui.getShell());
        this.gui = gui;
        this.definition = definition;
        this.isNeu = isNeu;
    }

    /**
     * Öffnet den Dialog (modal).
     *
     * @return true wenn gespeichert wurde (die übergebene Definition ist dann gefüllt)
     */
    public boolean openDialog() {
        Shell shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText(isNeu ? "Portfolio-Simulator hinzufügen" : "Portfolio-Simulator bearbeiten");
        shell.setLayout(new GridLayout(1, false));

        // ---- Stammdaten
        Composite top = new Composite(shell, SWT.NONE);
        top.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        top.setLayout(new GridLayout(2, false));

        Label nameLabel = new Label(top, SWT.NONE);
        nameLabel.setText("Name (Beschriftung):");
        nameText = new Text(top, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        nameText.setText(definition.getName() != null ? definition.getName() : "");
        nameText.setToolTipText("Erscheint als Beschriftung des Icons im Seitenpanel");

        Label dateLabel = new Label(top, SWT.NONE);
        dateLabel.setText("Startdatum (JJJJ-MM-TT):");
        dateText = new Text(top, SWT.BORDER);
        dateText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        dateText.setText(definition.getStartDate() != null ? definition.getStartDate() : "");

        Label capitalLabel = new Label(top, SWT.NONE);
        capitalLabel.setText("Startkapital je Strategie:");
        capitalText = new Text(top, SWT.BORDER);
        capitalText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        capitalText.setText(String.valueOf(definition.getStartCapital()));

        // ---- Signal-Auswahl
        Group signalGroup = new Group(shell, SWT.NONE);
        signalGroup.setText("Signale in diesem Portfolio");
        signalGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        signalGroup.setLayout(new GridLayout(1, false));

        signalTable = new Table(signalGroup, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL);
        signalTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        signalTable.setToolTipText("Häkchen setzen = Signal gehört zum Portfolio");

        // Favoriten mit Namen auflisten, Häkchen für bereits enthaltene
        List<String> favoriten = new FavoritesReader(gui.getMonitor().getConfig()).readFavorites();
        IdTranslationManager translation = gui.getProviderTable() != null
                ? gui.getProviderTable().getIdTranslationManager() : null;
        for (String id : favoriten) {
            TableItem item = new TableItem(signalTable, SWT.NONE);
            String name = translation != null ? translation.getProviderName(id) : id;
            item.setText(id + "  ·  " + (name == null ? "?" : name));
            item.setData("signalId", id);
            item.setChecked(definition.getSignalIds().contains(id));
        }

        Label auswahlInfo = new Label(signalGroup, SWT.WRAP);
        auswahlInfo.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        // ---- Buttons
        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        buttons.setLayout(new GridLayout(3, true));

        Button selectAll = new Button(buttons, SWT.PUSH);
        selectAll.setText("Alle auswählen");
        selectAll.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button okButton = new Button(buttons, SWT.PUSH);
        okButton.setText("Speichern");
        okButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button cancelButton = new Button(buttons, SWT.PUSH);
        cancelButton.setText("Abbrechen");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Auswahl-Zähler aktualisieren
        Runnable updateCount = () -> {
            int n = 0;
            for (TableItem item : signalTable.getItems()) {
                if (item.getChecked()) {
                    n++;
                }
            }
            auswahlInfo.setText(n + " von " + signalTable.getItemCount() + " Signalen ausgewählt");
        };
        signalTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateCount.run();
            }
        });
        selectAll.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (TableItem item : signalTable.getItems()) {
                    item.setChecked(true);
                }
                updateCount.run();
            }
        });
        updateCount.run();

        okButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (validateAndStore()) {
                    saved = true;
                    shell.close();
                }
            }
        });
        cancelButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                shell.close();
            }
        });

        shell.setSize(560, 620);
        centerOnParent(shell);
        shell.open();

        while (!shell.isDisposed()) {
            if (!gui.getDisplay().readAndDispatch()) {
                gui.getDisplay().sleep();
            }
        }
        return saved;
    }

    private boolean validateAndStore() {
        String name = nameText.getText().trim();
        if (name.isEmpty()) {
            showError("Bitte einen Namen eingeben.");
            return false;
        }
        String date = dateText.getText().trim();
        try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            showError("Startdatum muss im Format JJJJ-MM-TT sein (z. B. 2026-09-01).");
            return false;
        }
        double capital;
        try {
            capital = Double.parseDouble(capitalText.getText().trim().replace(",", "."));
            if (capital <= 0) {
                showError("Startkapital muss positiv sein.");
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Startkapital muss eine Zahl sein.");
            return false;
        }
        List<String> ids = new ArrayList<>();
        for (TableItem item : signalTable.getItems()) {
            if (item.getChecked()) {
                ids.add((String) item.getData("signalId"));
            }
        }
        if (ids.isEmpty()) {
            showError("Bitte mindestens ein Signal auswählen.");
            return false;
        }

        definition.setName(name);
        definition.setStartDate(date);
        definition.setStartCapital(capital);
        definition.setSignalIds(ids);
        return true;
    }

    private void showError(String message) {
        MessageBox box = new MessageBox(getParent(), SWT.ICON_ERROR | SWT.OK);
        box.setText("Ungültige Eingabe");
        box.setMessage(message);
        box.open();
    }

    private void centerOnParent(Shell shell) {
        Shell parent = getParent();
        Point parentLocation = parent.getLocation();
        Point parentSize = parent.getSize();
        Point dialogSize = shell.getSize();
        shell.setLocation(
                parentLocation.x + Math.max(0, (parentSize.x - dialogSize.x) / 2),
                parentLocation.y + Math.max(0, (parentSize.y - dialogSize.y) / 2));
    }
}
