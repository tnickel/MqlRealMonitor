package com.mql.realmonitor.downloader;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für updateSignalClass (KiScanner-Import gleicht die
 * Favoritenklasse an die Scanner-Ampel an: grün = 1, gelb = 2).
 */
class FavoritesReaderUpdateClassTest {

    private MqlRealMonitorConfig config;
    private FavoritesReader reader;
    private Path favoritesPath;

    @BeforeEach
    void setUp() throws Exception {
        String basePath = "target/test-favorites-update-" + System.nanoTime();
        config = new MqlRealMonitorConfig(basePath);
        config.loadConfig();
        reader = new FavoritesReader(config);

        favoritesPath = Paths.get(config.getFavoritesFile());
        Files.createDirectories(favoritesPath.getParent());

        // Bestandsdatei: ein Klasse-1-Signal (historisch), Kommentare bleiben erhalten
        Files.write(favoritesPath, List.of(
                "# Kommentar bleibt stehen",
                "111:1",
                "222:2"));
        reader.refreshCache();
    }

    @Test
    void testKlasseWirdUmgeschriebenUndKommentareBleiben() throws Exception {
        assertTrue(reader.updateSignalClass("111", "2"));

        List<String> zeilen = Files.readAllLines(favoritesPath);
        assertEquals("# Kommentar bleibt stehen", zeilen.get(0));
        assertEquals("111:2", zeilen.get(1));
        assertEquals("222:2", zeilen.get(2));

        // Cache ist aktualisiert
        assertEquals("2", reader.getFavoriteClass("111"));
    }

    @Test
    void testGleicheKlasseIstKeineAenderung() {
        assertFalse(reader.updateSignalClass("222", "2"));
    }

    @Test
    void testUnbekanntesSignalSchlaegtFehl() {
        assertFalse(reader.updateSignalClass("999", "2"));
    }

    @Test
    void testSignalOhneKlasseKannNichtAktualisiertWerden() throws Exception {
        Files.write(favoritesPath.toAbsolutePath(), List.of("333"));
        reader.refreshCache();
        assertFalse(reader.updateSignalClass("333", "1"));
    }

    @Test
    void testUngueltigeKlasseWirdAbgelehnt() {
        assertFalse(reader.updateSignalClass("111", "99"));
        assertFalse(reader.updateSignalClass("111", null));
        assertFalse(reader.updateSignalClass(null, "2"));
    }

    @Test
    void testAmpelAnpassungSzenarioAusDemImport() throws Exception {
        // Szenario aus der Nutzerbeobachtung: altes Klasse-1-Signal wird
        // beim Scanner nur noch gelb -> Import muss auf Klasse 2 umschreiben
        assertEquals("1", reader.getFavoriteClass("111"));
        assertTrue(reader.updateSignalClass("111", "2"));

        Map<String, String> klassen = reader.readFavoritesWithClasses();
        assertEquals("2", klassen.get("111"));
        assertEquals("2", klassen.get("222"));
    }
}
