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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.mql5.Mql5Credentials;

/**
 * NEU: Einstellungs-Dialog (Menü → Einstellungen → Konfiguration).
 *
 * HINWEIS (Sicherheit): MQL5-Login und Passwort werden in einer separaten
 * Datei im CONFIG_DIR des BASE_PATH gespeichert (z. B.
 * C:\Forex\MqlAnalyzer\config\mql5_credentials.properties) — AUSSERHALB
 * des Git-Repositories. Das Passwort wird nie geloggt.
 */
public class MqlSettingsDialog extends Dialog {

    private static final String VERSION_TAG = " (v1.4.5)";

    /**
     * NEU: Erklärtext für den Info-Button neben der KiScanner-Base-URL.
     */
    private static final String INFO_KISCANNER_URL =
        "Diese Adresse ist die REST-Schnittstelle des Programms MqlKiScanner — "
        + "sie zeigt auf den eigenen Rechner, nicht ins Internet.\n\n"
        + "Wofür der RealMonitor sie benutzt:\n"
        + "Beim Klick auf den Button „🤖 KiScanner\" ruft er genau diese Adresse auf "
        + "und holt die gescannte Signalliste:\n"
        + "   GET {Base-URL}/api/v1/signals?ampel=gruen,gelb\n"
        + "Ist ein Zugriffs-Token eingetragen, wird er als Header „X-User-Key\" mitgesendet.\n\n"
        + "Welche Daten übertragen werden:\n"
        + "• Zum Scanner: nur dieser eine Lese-Aufruf — keine Logins, keine Kontodaten, keine Trades.\n"
        + "• Vom Scanner: eine JSON-Liste der gescannten Signale (Signal-ID, Name, Plattform, URL, "
        + "Ampel-Farbe, Urteil, Kurzfassung, Score).\n"
        + "Der RealMonitor übernimmt daraus ausschließlich grün/gelb bewertete Signale in die "
        + "Überwachung und gleicht die Favoritenklasse an die Scanner-Ampel an. Alles Weitere "
        + "(Kurshistorie, Trade-Download) holt er sich selbst direkt von mql5.com.\n\n"
        + "Was ist wo einzutragen?\n"
        + "Die Scanner-API lauscht bewusst nur lokal (127.0.0.1) — Fernzugriff über das Netzwerk "
        + "(z. B. ein Server wie Contabo) ist nicht vorgesehen.\n"
        + "• Läuft der MqlKiScanner auf demselben Rechner wie dieser RealMonitor: "
        + "http://127.0.0.1:8611\n"
        + "• Läuft dort kein Scanner, schlägt der KiScanner-Abruf mit einer Fehlermeldung fehl — "
        + "am Rest des Programms ändert das nichts.";

    private final MqlRealMonitorGUI gui;
    private final MqlRealMonitorConfig config;
    private final Mql5Credentials credentials;

    private Text mql5UserText;
    private Text mql5PassText;
    private Text kiScannerUrlText;
    private Text kiScannerTokenText;
    private Text simStartText;
    private Text simCapitalText;
    private Text intervalText;
    private Text timeoutText;
    private Text userAgentText;
    private Text urlTemplateText;

    public MqlSettingsDialog(MqlRealMonitorGUI gui) {
        super(gui.getShell());
        this.gui = gui;
        this.config = gui.getMonitor().getConfig();
        this.credentials = new Mql5Credentials(config.getConfigDir());
    }

    /**
     * Öffnet den Dialog (modal); true wenn gespeichert wurde
     */
    public boolean openDialog() {
        Shell shell = new Shell(getParent(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("MqlRealMonitor Einstellungen" + VERSION_TAG);
        shell.setLayout(new GridLayout(1, false));

        // ---- MQL5-Zugangsdaten (sicher gelagert, außerhalb des Repos)
        Group mql5Group = new Group(shell, SWT.NONE);
        mql5Group.setText("MQL5-Zugang (für Trade-Historie-Download)");
        mql5Group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        mql5Group.setLayout(new GridLayout(2, false));

        addLabel(mql5Group, "MQL5-Login:");
        mql5UserText = new Text(mql5Group, SWT.BORDER);
        mql5UserText.setLayoutData(fill());
        mql5UserText.setText(credentials.getUser());
        mql5UserText.setToolTipText("MQL5-Benutzername oder E-Mail — wird in mql5_credentials.properties "
                + "im Config-Verzeichnis gespeichert (NICHT im Git-Repository)");

        addLabel(mql5Group, "MQL5-Passwort:");
        mql5PassText = new Text(mql5Group, SWT.BORDER | SWT.PASSWORD);
        mql5PassText.setLayoutData(fill());
        mql5PassText.setText(credentials.getPassword());
        mql5PassText.setToolTipText("Wird nie geloggt und nie committet");

        addLabel(mql5Group, "");
        Label credInfo = new Label(mql5Group, SWT.WRAP);
        credInfo.setLayoutData(fill());
        credInfo.setText("Ablage: " + credentials.getCredentialsFile());

        // ---- KiScanner-Verbindung
        Group kiGroup = new Group(shell, SWT.NONE);
        kiGroup.setText("MqlKiScanner REST-Verbindung");
        kiGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        kiGroup.setLayout(new GridLayout(2, false));

        addLabel(kiGroup, "Base-URL:");
        Composite urlCell = new Composite(kiGroup, SWT.NONE);
        urlCell.setLayoutData(fill());
        GridLayout urlLayout = new GridLayout(2, false);
        urlLayout.marginWidth = 0;
        urlLayout.marginHeight = 0;
        urlCell.setLayout(urlLayout);
        kiScannerUrlText = new Text(urlCell, SWT.BORDER);
        kiScannerUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        kiScannerUrlText.setText(config.getKiScannerBaseUrl());
        kiScannerUrlText.setToolTipText("Adresse der lokalen REST-Schnittstelle des MqlKiScanner "
                + "(Standard: http://127.0.0.1:8611)");
        addInfoButton(urlCell, "KiScanner Base-URL — was passiert hier?", INFO_KISCANNER_URL);

        addLabel(kiGroup, "Zugriffs-Token:");
        kiScannerTokenText = new Text(kiGroup, SWT.BORDER | SWT.PASSWORD);
        kiScannerTokenText.setLayoutData(fill());
        kiScannerTokenText.setText(config.getKiScannerToken());
        kiScannerTokenText.setToolTipText("Muss mit rest_api_token im MqlKiScanner "
                + "(config/secrets.local.json) übereinstimmen; leer = kein Token");

        // ---- Simulator
        Group simGroup = new Group(shell, SWT.NONE);
        simGroup.setText("Simulator");
        simGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        simGroup.setLayout(new GridLayout(2, false));

        addLabel(simGroup, "Startdatum (JJJJ-MM-TT):");
        simStartText = new Text(simGroup, SWT.BORDER);
        simStartText.setLayoutData(fill());
        simStartText.setText(config.getSimulatorStartDate());
        simStartText.setToolTipText("Ab diesem Datum simuliert der Simulator alle Strategien "
                + "(Button 📊 Simulator)");

        addLabel(simGroup, "Startkapital je Strategie:");
        simCapitalText = new Text(simGroup, SWT.BORDER);
        simCapitalText.setLayoutData(fill());
        simCapitalText.setText(String.valueOf(config.getSimulatorStartCapital()));
        simCapitalText.setToolTipText("Jede Strategie startet die Simulation mit diesem Betrag");

        // ---- Monitoring-Parameter
        Group monitorGroup = new Group(shell, SWT.NONE);
        monitorGroup.setText("Monitoring");
        monitorGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        monitorGroup.setLayout(new GridLayout(2, false));

        addLabel(monitorGroup, "Intervall (Minuten):");
        intervalText = new Text(monitorGroup, SWT.BORDER);
        intervalText.setLayoutData(fill());
        intervalText.setText(String.valueOf(config.getIntervalMinutes()));

        addLabel(monitorGroup, "Timeout (Sekunden):");
        timeoutText = new Text(monitorGroup, SWT.BORDER);
        timeoutText.setLayoutData(fill());
        timeoutText.setText(String.valueOf(config.getTimeoutSeconds()));

        addLabel(monitorGroup, "User-Agent:");
        userAgentText = new Text(monitorGroup, SWT.BORDER);
        userAgentText.setLayoutData(fill());
        userAgentText.setText(config.getUserAgent());

        addLabel(monitorGroup, "URL-Template:");
        urlTemplateText = new Text(monitorGroup, SWT.BORDER);
        urlTemplateText.setLayoutData(fill());
        urlTemplateText.setText(config.getUrlTemplate());

        // ---- Buttons
        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        buttons.setLayout(new GridLayout(2, true));

        Button saveButton = new Button(buttons, SWT.PUSH);
        saveButton.setText("Speichern");
        saveButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button cancelButton = new Button(buttons, SWT.PUSH);
        cancelButton.setText("Abbrechen");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        final boolean[] saved = {false};

        saveButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (validateAndSave()) {
                    saved[0] = true;
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

        shell.setSize(620, 640);
        centerOnParent(shell);
        shell.open();

        while (!shell.isDisposed()) {
            if (!gui.getDisplay().readAndDispatch()) {
                gui.getDisplay().sleep();
            }
        }
        return saved[0];
    }

    private boolean validateAndSave() {
        // Intervall/Timeout numerisch prüfen
        try {
            int interval = Integer.parseInt(intervalText.getText().trim());
            if (interval <= 0) {
                showError("Intervall muss positiv sein.");
                return false;
            }
            config.setIntervalMinutes(interval);
        } catch (NumberFormatException e) {
            showError("Intervall muss eine Zahl sein.");
            return false;
        }
        try {
            int timeout = Integer.parseInt(timeoutText.getText().trim());
            if (timeout <= 0) {
                showError("Timeout muss positiv sein.");
                return false;
            }
            config.setTimeoutSeconds(timeout);
        } catch (NumberFormatException e) {
            showError("Timeout muss eine Zahl sein.");
            return false;
        }

        // Simulator-Parameter validieren und übernehmen
        String simDate = simStartText.getText().trim();
        try {
            java.time.LocalDate.parse(simDate,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            config.setSimulatorStartDate(simDate);
        } catch (Exception e) {
            showError("Simulator-Startdatum muss im Format JJJJ-MM-TT sein (z. B. 2026-09-01).");
            return false;
        }
        try {
            double capital = Double.parseDouble(simCapitalText.getText().trim().replace(",", "."));
            if (capital <= 0) {
                showError("Simulator-Startkapital muss positiv sein.");
                return false;
            }
            config.setSimulatorStartCapital(capital);
        } catch (NumberFormatException e) {
            showError("Simulator-Startkapital muss eine Zahl sein.");
            return false;
        }

        config.setUserAgent(userAgentText.getText().trim());
        String urlTemplate = urlTemplateText.getText().trim();
        if (!urlTemplate.contains("%s")) {
            showError("URL-Template muss '%s' für die Signal-ID enthalten.");
            return false;
        }
        // setUrlTemplate fehlt als Setter? Dann direkt über Property — hier Setter ergänzt in Config
        config.setUrlTemplate(urlTemplate);
        config.setKiScannerBaseUrl(kiScannerUrlText.getText().trim());
        config.setKiScannerToken(kiScannerTokenText.getText());
        config.saveConfig();

        // Zugangsdaten in separate Datei (außerhalb des Repos)
        credentials.save(mql5UserText.getText().trim(), mql5PassText.getText());

        gui.updateStatus("Einstellungen gespeichert");
        return true;
    }

    private void showError(String message) {
        MessageBox box = new MessageBox(getParent(), SWT.ICON_ERROR | SWT.OK);
        box.setText("Ungültige Eingabe");
        box.setMessage(message);
        box.open();
    }

    private void addLabel(Composite parent, String text) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(text);
        label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    }

    /**
     * NEU: Kleiner Info-Button („i") neben einem Feld — öffnet einen Erklär-Dialog.
     */
    private void addInfoButton(Composite parent, String titel, String text) {
        Button info = new Button(parent, SWT.FLAT);
        info.setText("i");
        info.setToolTipText("Klicken für eine Erklärung zu diesem Feld");
        info.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        info.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                MessageBox box = new MessageBox(getParent(), SWT.ICON_INFORMATION | SWT.OK);
                box.setText(titel);
                box.setMessage(text);
                box.open();
            }
        });
    }

    private GridData fill() {
        return new GridData(SWT.FILL, SWT.CENTER, true, false);
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
