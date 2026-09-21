package com.mql.realmonitor.mql5;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: Verwaltung der MQL5-Zugangsdaten für den Trade-Export.
 *
 * ACHTUNG (Sicherheit): Die Datei liegt im CONFIG_DIR des BASE_PATH
 * (z. B. C:\Forex\MqlAnalyzer\config\mql5_credentials.properties) und
 * damit AUSSERHALB des Git-Repositories — die Zugangsdaten werden nie
 * committet. Das Passwort wird bewusst NIE geloggt.
 */
public class Mql5Credentials {

    private static final Logger LOGGER = Logger.getLogger(Mql5Credentials.class.getName());

    private final String credentialsFile;
    private String user = "";
    private String password = "";

    public Mql5Credentials(String configDir) {
        this.credentialsFile = configDir + File.separator + "mql5_credentials.properties";
        load();
    }

    private void load() {
        File file = new File(credentialsFile);
        if (!file.exists()) {
            LOGGER.info("Keine MQL5-Credentials Datei vorhanden: " + credentialsFile);
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            this.user = props.getProperty("user", "").trim();
            this.password = props.getProperty("password", "");
            LOGGER.info("MQL5-Credentials geladen (Benutzer: " + user
                    + ", Passwort: " + (password.isEmpty() ? "nicht gesetzt" : "gesetzt, nicht geloggt") + ")");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte MQL5-Credentials nicht laden: " + e.getMessage(), e);
        }
    }

    /**
     * Speichert die Zugangsdaten (Passwort landet nur in der lokalen Datei,
     * nie ins Log und nie ins Repository)
     */
    public boolean save(String user, String password) {
        this.user = user != null ? user.trim() : "";
        this.password = password != null ? password : "";

        try {
            File file = new File(credentialsFile);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                Properties props = new Properties();
                props.setProperty("user", this.user);
                props.setProperty("password", this.password);
                props.store(fos, "MQL5-Zugangsdaten - NIE in ein Git-Repository committen!");
            }
            LOGGER.info("MQL5-Credentials gespeichert: " + credentialsFile);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Konnte MQL5-Credentials nicht speichern", e);
            return false;
        }
    }

    public boolean isConfigured() {
        return !user.isEmpty() && !password.isEmpty();
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }
}
