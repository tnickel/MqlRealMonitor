package com.mql.realmonitor.simulator;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.mql5.EquityCurveBuilder;
import com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für den Simulator — Lot-Skalierung aufs Sim-Konto
 * (Compounding wie beim MT5-Signal-Kopieren) und Portfolio-Merge.
 */
class SimulatorEngineTest {

    @TempDir
    Path tempDir;

    private static final String CSV = "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment\r\n"
        + "2025.11.03 15:04:11;Balance;;;;;;;;;;1 000.00;\r\n"
        + "2025.12.01 10:00:00;buy;0.10;XAUUSD;2500.00;;;2025.12.01 12:00:00;2510.00;0;0;100.00;\r\n"
        + "2026.09.02 10:00:00;buy;0.10;XAUUSD;2500.00;;;2026.09.02 12:00:00;2540.00;0;0;400.00;\r\n"
        + "2026.09.05 10:00:00;sell;0.10;XAUUSD;2540.00;;;2026.09.05 12:00:00;2500.00;0;0;-100.00;\r\n";

    // -------------------------------------------------- Simulations-Kurve

    @Test
    void testLotSkalierungCompounding() {
        // Reales Konto zum Start (2026-09-01): 1000 (Einzahlung) + 100 (Dez-Trade) = 1100
        // Sim: 10.000 → Faktor 10.000/1100 = 9,0909...
        // Trade 2: +400 real → +3636,36 sim → 13.636,36
        // Trade 3: real vorher 1500, sim 13.636,36 → Faktor 9,0909 → -909,09 → 12.727,27
        List<EquityPoint> kurve = EquityCurveBuilder.buildSimulatedCurve(
                CSV, LocalDate.of(2026, 9, 1).atStartOfDay(), 10_000.0);

        assertEquals(3, kurve.size(), "Startpunkt + 2 Trades nach dem Startdatum");
        assertEquals(10_000.0, kurve.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(13_636.36, kurve.get(1).getCumulatedProfit(), 0.01);
        assertEquals(12_727.27, kurve.get(2).getCumulatedProfit(), 0.01);
    }

    @Test
    void testStartpunktLiegtAmStartdatum() {
        List<EquityPoint> kurve = EquityCurveBuilder.buildSimulatedCurve(
                CSV, LocalDate.of(2026, 9, 1).atStartOfDay(), 10_000.0);
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), kurve.get(0).getTime());
    }

    @Test
    void testKeineTradesNachStartdatumFlat() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        List<EquityPoint> kurve = EquityCurveBuilder.buildSimulatedCurve(CSV, start.atStartOfDay(), 10_000.0);
        assertEquals(1, kurve.size(), "Nur der Startpunkt — keine Trades im Fenster");
        assertEquals(10_000.0, kurve.get(0).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testAuszahlungNachStartVerfaelschtSimulationNicht() {
        String csvMitAuszahlung = CSV
            + "2026.09.10 08:00:00;Balance;;;;;;;;;;-5 000.00;\r\n"  // Auszahlung NACH Start
            + "2026.09.15 10:00:00;buy;0.10;XAUUSD;2500.00;;;2026.09.15 12:00:00;2520.00;0;0;60.00;\r\n";
        // Reales Konto vor Trade 4 (Auszahlung herausgerechnet): 1100+400-100 = 1400
        // Sim vorher 12.727,27 → Faktor 9,0909 → +60 × 9,0909 = +545,45 → 13.272,73
        List<EquityPoint> kurve = EquityCurveBuilder.buildSimulatedCurve(
                csvMitAuszahlung, LocalDate.of(2026, 9, 1).atStartOfDay(), 10_000.0);
        assertEquals(4, kurve.size());
        assertEquals(12_727.27, kurve.get(2).getCumulatedProfit(), 0.01); // wie vorher
        assertEquals(13_272.73, kurve.get(3).getCumulatedProfit(), 0.01); // +60 × (12727,27/1400)
    }

    // ------------------------------------------------------- Engine

    private MqlRealMonitorConfig configMit(String rohCsv) throws Exception {
        MqlRealMonitorConfig config = new MqlRealMonitorConfig(tempDir.toString());
        config.loadConfig();
        config.setSimulatorStartCapital(10_000.0);
        config.setSimulatorStartDate("2026-09-01");
        if (rohCsv != null) {
            Files.write(Path.of(config.getTradesFilePath("111")), rohCsv.getBytes("UTF-8"));
            Files.write(Path.of(config.getTradesFilePath("222")), rohCsv.getBytes("UTF-8"));
        }
        return config;
    }

    @Test
    void testEngineSimuliertUndSortiert() throws Exception {
        MqlRealMonitorConfig config = configMit(CSV);
        SimulatorEngine engine = new SimulatorEngine(config);

        SimulatorEngine.SimulationResult result = engine.simulate(
                Arrays.asList("111", "999"), Map.of("111", "Alpha", "999", "Ohne Historie"),
                LocalDate.of(2026, 9, 1));

        assertEquals(2, result.strategien.size());
        assertTrue(result.strategien.get(0).hatHistorie, "Strategie mit Kurve zuerst");
        assertEquals("Alpha", result.strategien.get(0).name);
        assertEquals(12_727.27, result.strategien.get(0).endwert, 0.01);
        assertEquals(27.27, result.strategien.get(0).prozent(), 0.01);

        assertFalse(result.strategien.get(1).hatHistorie);
        assertEquals("Ohne Historie", result.strategien.get(1).name);

        // Portfolio: nur die eine Strategie mit Kurve → Startwert 10.000, Ende 12.727,27
        assertEquals(10_000.0, result.portfolioStartwert, 1e-9);
        assertEquals(12_727.27, result.portfolioEndwert, 0.01);
        assertEquals(27.27, result.portfolioProzent(), 0.01);
    }

    @Test
    void testPortfolioSummiertZweiStrategien() {
        // Zwei einfache Strategie-Kurven mit unterschiedlichen Zeitstempeln
        List<SimulatorEngine.StrategyResult> strategien = new ArrayList<>();
        strategien.add(strategie("A", new String[][]{
                {"2026-09-01T00:00", "10000"},
                {"2026-09-02T12:00", "11000"}}));
        strategien.add(strategie("B", new String[][]{
                {"2026-09-01T00:00", "10000"},
                {"2026-09-03T12:00", "9000"}}));

        List<EquityPoint> portfolio = SimulatorEngine.mergePortfolio(strategien);

        // Zeitachse: 09-01 (20.000), 09-02 (21.000), 09-03 (20.000)
        assertEquals(3, portfolio.size());
        assertEquals(20_000.0, portfolio.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(21_000.0, portfolio.get(1).getCumulatedProfit(), 1e-9);
        assertEquals(20_000.0, portfolio.get(2).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testPortfolioMitVersetztemStart() {
        // Strategie B beginnt erst am 09-02 — vorher zählt nur A
        List<SimulatorEngine.StrategyResult> strategien = new ArrayList<>();
        strategien.add(strategie("A", new String[][]{
                {"2026-09-01T00:00", "10000"}}));
        strategien.add(strategie("B", new String[][]{
                {"2026-09-02T00:00", "10000"},
                {"2026-09-02T12:00", "10500"}}));

        List<EquityPoint> portfolio = SimulatorEngine.mergePortfolio(strategien);

        // Zeitachse: 09-01 (nur A), 09-02T00:00 (A+B), 09-02T12:00 (A+B upgedated)
        assertEquals(3, portfolio.size());
        assertEquals(10_000.0, portfolio.get(0).getCumulatedProfit(), 1e-9); // nur A
        assertEquals(20_000.0, portfolio.get(1).getCumulatedProfit(), 1e-9); // A + B startet
        assertEquals(20_500.0, portfolio.get(2).getCumulatedProfit(), 1e-9); // A + B aktualisiert
    }

    private SimulatorEngine.StrategyResult strategie(String name, String[][] punkte) {
        List<EquityPoint> kurve = new ArrayList<>();
        for (String[] p : punkte) {
            kurve.add(new EquityPoint(LocalDateTime.parse(p[0]), Double.parseDouble(p[1])));
        }
        SimulatorEngine.StrategyResult r = new SimulatorEngine.StrategyResult(
                name, name, kurve, true, 10_000.0);
        return r;
    }

    // ------------------------------------------------------- Config

    @Test
    void testSimulatorConfigRoundtrip() throws Exception {
        MqlRealMonitorConfig config = new MqlRealMonitorConfig(tempDir.toString());
        config.loadConfig();
        config.setSimulatorStartDate("2026-09-01");
        config.setSimulatorStartCapital(10_000.0);
        config.saveConfig();

        MqlRealMonitorConfig geladen = new MqlRealMonitorConfig(tempDir.toString());
        geladen.loadConfig();
        assertEquals("2026-09-01", geladen.getSimulatorStartDate());
        assertEquals(LocalDate.of(2026, 9, 1), geladen.getSimulatorStartDateParsed());
        assertEquals(10_000.0, geladen.getSimulatorStartCapital(), 1e-9);
    }

    @Test
    void testUngueltigesSimulatorDatumFaelltZurueck() {
        MqlRealMonitorConfig config = new MqlRealMonitorConfig(tempDir.toString());
        config.setSimulatorStartDate("not-a-date"); // Regex lehnt ab → Default bleibt
        LocalDate parsed = config.getSimulatorStartDateParsed(); // parsebar oder Fallback
        assertNotNull(parsed);
    }
}
