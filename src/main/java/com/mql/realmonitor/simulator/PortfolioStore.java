package com.mql.realmonitor.simulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * NEU: Persistenz der Portfolio-Simulator-Definitionen.
 *
 * Ablage: {CONFIG_DIR}\portfolios.json (BASE_PATH, außerhalb des
 * Git-Repositories) — enthält KEINE Geheimnisse, nur Namen, Datum,
 * Kapital und Signal-IDs.
 *
 * Fehlende/beschädigte Datei wird als leere Liste behandelt; gespeichert
 * wird pretty-printed, damit die Datei auch von Hand editierbar bleibt.
 */
public class PortfolioStore {

    private static final Logger LOGGER = Logger.getLogger(PortfolioStore.class.getName());

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** JSON-Hülle für die Liste */
    private static class PortfolioFile {
        public List<PortfolioDefinition> portfolios = new ArrayList<>();
    }

    public PortfolioStore(String configDir) {
        this.file = Paths.get(configDir, "portfolios.json");
    }

    /**
     * Lädt alle Definitionen (leere Liste, wenn keine Datei existiert)
     */
    public List<PortfolioDefinition> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            PortfolioFile data = mapper.readValue(file.toFile(), PortfolioFile.class);
            if (data == null || data.portfolios == null) {
                return new ArrayList<>();
            }
            LOGGER.info("Portfolios geladen: " + data.portfolios.size() + " aus " + file);
            return data.portfolios;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte portfolios.json nicht lesen — starte leer: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Speichert alle Definitionen
     */
    public boolean save(List<PortfolioDefinition> portfolios) {
        try {
            PortfolioFile data = new PortfolioFile();
            data.portfolios = portfolios != null ? portfolios : new ArrayList<>();
            mapper.writeValue(file.toFile(), data);
            LOGGER.info("Portfolios gespeichert: " + data.portfolios.size() + " nach " + file);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Konnte portfolios.json nicht speichern", e);
            return false;
        }
    }

    /**
     * NEU: Nächste freie ID (max + 1; 1 bei leerer Liste)
     */
    public static int nextId(List<PortfolioDefinition> portfolios) {
        int max = 0;
        if (portfolios != null) {
            for (PortfolioDefinition p : portfolios) {
                if (p.getId() > max) {
                    max = p.getId();
                }
            }
        }
        return max + 1;
    }

    /**
     * NEU: Sucht eine Definition per ID (null, wenn nicht vorhanden)
     */
    public static PortfolioDefinition findById(List<PortfolioDefinition> portfolios, int id) {
        if (portfolios != null) {
            for (PortfolioDefinition p : portfolios) {
                if (p.getId() == id) {
                    return p;
                }
            }
        }
        return null;
    }

    public String getFile() {
        return file.toString();
    }
}
