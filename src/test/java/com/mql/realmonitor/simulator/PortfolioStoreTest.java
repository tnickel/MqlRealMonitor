package com.mql.realmonitor.simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für die Persistenz der Portfolio-Simulator-Definitionen
 * (portfolios.json im Config-Verzeichnis, außerhalb des Repos).
 */
class PortfolioStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void testOhneDateiLiefertLeereListe() {
        PortfolioStore store = new PortfolioStore(tempDir.toString());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void testRoundtripMehrerePortfolios() {
        PortfolioStore store = new PortfolioStore(tempDir.toString());

        PortfolioDefinition a = new PortfolioDefinition(1, "Gold-Strategien",
                "2026-09-01", 10_000.0);
        a.setSignalIds(Arrays.asList("2349227", "2332166", " 2364703 ", "", null));

        PortfolioDefinition b = new PortfolioDefinition(2, "Konservativ",
                "2026-06-01", 5_000.0);
        b.setSignalIds(List.of("2342895"));

        assertTrue(store.save(new ArrayList<>(Arrays.asList(a, b))));

        List<PortfolioDefinition> geladen = new PortfolioStore(tempDir.toString()).load();
        assertEquals(2, geladen.size());

        PortfolioDefinition ea = geladen.get(0);
        assertEquals(1, ea.getId());
        assertEquals("Gold-Strategien", ea.getName());
        assertEquals("2026-09-01", ea.getStartDate());
        assertEquals(10_000.0, ea.getStartCapital(), 1e-9);
        // Leere/null/duplizierte IDs werden bereinigt, Whitespace getrimmt
        assertEquals(Arrays.asList("2349227", "2332166", "2364703"), ea.getSignalIds());

        assertEquals(List.of("2342895"), geladen.get(1).getSignalIds());
    }

    @Test
    void testBeschaedigteDateiLiefertLeereListe() throws Exception {
        java.nio.file.Files.writeString(
                tempDir.resolve("portfolios.json"), "kein gueltiges json {{{");
        PortfolioStore store = new PortfolioStore(tempDir.toString());
        assertTrue(store.load().isEmpty());
    }

    @Test
    void testNextIdUndFindById() {
        List<PortfolioDefinition> liste = new ArrayList<>();
        assertEquals(1, PortfolioStore.nextId(liste));

        liste.add(new PortfolioDefinition(1, "A", "2026-09-01", 1000));
        liste.add(new PortfolioDefinition(7, "B", "2026-09-01", 1000));
        assertEquals(8, PortfolioStore.nextId(liste));

        assertEquals("B", PortfolioStore.findById(liste, 7).getName());
        assertNull(PortfolioStore.findById(liste, 99));
        assertNull(PortfolioStore.findById(null, 1));
    }

    @Test
    void testLeereSignalListeIstErlaubt() {
        // Ein Portfolio ohne Signale muss speicherbar sein (UI mahnt beim Öffnen)
        PortfolioStore store = new PortfolioStore(tempDir.toString());
        PortfolioDefinition p = new PortfolioDefinition(1, "Leer", "2026-09-01", 1000);
        p.setSignalIds(null);
        assertTrue(p.getSignalIds().isEmpty());
        assertTrue(store.save(List.of(p)));
        assertTrue(new PortfolioStore(tempDir.toString()).load().get(0).getSignalIds().isEmpty());
    }
}
