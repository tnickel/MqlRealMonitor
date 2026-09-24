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

    // ------------------------------------------------- Perioden-Gewinn

    @Test
    void testGewinnSeitAusKurveMisstAbReferenz() {
        LocalDate wochenStart = SimulatorEngine.aktuellerWochenstart();

        SimulatorEngine.SimulationResult result = new SimulatorEngine.SimulationResult();
        result.portfolioStartwert = 20_000.0;
        result.portfolio.add(new EquityPoint(wochenStart.minusDays(3).atTime(10, 0), 20_000.0));
        result.portfolio.add(new EquityPoint(wochenStart.plusDays(1).atTime(10, 0), 22_000.0));
        result.portfolio.add(new EquityPoint(wochenStart.plusDays(2).atTime(10, 0), 21_500.0));

        // Basis = letzter Wert vor/am Referenzzeitpunkt (20.000) → +1.500 = +7,5 %
        double[] w = SimulatorEngine.gewinnSeitAusKurve(result, wochenStart.atStartOfDay());
        assertEquals(1_500.0, w[0], 1e-9);
        assertEquals(7.5, w[1], 1e-9);
    }

    @Test
    void testGewinnSeitAusKurveDreimonatsReferenz() {
        LocalDate heute = LocalDate.now();

        SimulatorEngine.SimulationResult result = new SimulatorEngine.SimulationResult();
        result.portfolioStartwert = 30_000.0;
        result.portfolio.add(new EquityPoint(heute.minusMonths(4).atStartOfDay(), 25_000.0));
        result.portfolio.add(new EquityPoint(heute.minusMonths(4).plusDays(1).atStartOfDay(), 26_000.0));
        result.portfolio.add(new EquityPoint(heute.minusDays(1).atStartOfDay(), 29_000.0));

        // 3M-Start liegt NACH allen Kurvenpunkten? Nein — vor minus 1 Tag:
        // Basis = letzter Wert <= (heute − 3 Monate) = 26.000 → +3.000 = +11,54 %
        double[] w = SimulatorEngine.gewinnSeitAusKurve(
                result, heute.minusMonths(3).atStartOfDay());
        assertEquals(3_000.0, w[0], 1e-9);
        assertEquals(3_000.0 / 26_000.0 * 100.0, w[1], 1e-9);
    }

    @Test
    void testGewinnSeitAusKurveKurveKomplettNachReferenz() {
        LocalDate wochenStart = SimulatorEngine.aktuellerWochenstart();

        SimulatorEngine.SimulationResult result = new SimulatorEngine.SimulationResult();
        result.portfolioStartwert = 10_000.0;
        result.portfolio.add(new EquityPoint(wochenStart.plusDays(1).atTime(10, 0), 11_000.0));

        // Kein Wert vor dem Referenzzeitpunkt → Basis ist der Portfolio-Startwert
        double[] w = SimulatorEngine.gewinnSeitAusKurve(result, wochenStart.atStartOfDay());
        assertEquals(1_000.0, w[0], 1e-9);
        assertEquals(10.0, w[1], 1e-9);
    }

    @Test
    void testGewinnSeitOhneKurveIstNull() {
        double[] w = SimulatorEngine.gewinnSeitAusKurve(
                new SimulatorEngine.SimulationResult(), LocalDate.now().atStartOfDay());
        assertEquals(0.0, w[0], 1e-9);
        assertEquals(0.0, w[1], 1e-9);
    }

    @Test
    void testGewinnSeitAusTicksGewichtetMitStartkapital() {
        LocalDate wochenStart = SimulatorEngine.aktuellerWochenstart();

        // A: 20.000 am Wochenstart → +5 % = +1.000 | B: 10.000 → −10 % = −1.000
        List<SimulatorEngine.StrategyResult> strategien = new ArrayList<>();
        strategien.add(strategie("A", new String[][]{
                {"2026-09-01T00:00", "20000"},
                {wochenStart.minusDays(2).atTime(10, 0).toString(), "20000"},
                {wochenStart.plusDays(1).atTime(10, 0).toString(), "21000"}}));
        strategien.add(strategie("B", new String[][]{
                {"2026-09-01T00:00", "10000"},
                {wochenStart.plusDays(1).atTime(10, 0).toString(), "9000"}}));

        SimulatorEngine.SimulationResult result = new SimulatorEngine.SimulationResult();
        result.strategien.addAll(strategien);
        result.portfolio.add(new EquityPoint(wochenStart.minusDays(2).atTime(10, 0), 30_000.0));

        // Wochenstart-Kapitale: A = 20.000 (Punkt vor Sonntag), B = 10.000 (Startkapital)
        java.util.Map<String, Double> prozente = java.util.Map.of("A", 5.0, "B", -10.0);
        double[] w = SimulatorEngine.gewinnSeitAusTicks(
                result, prozente, wochenStart.atStartOfDay());
        assertEquals(0.0, w[0], 1e-9); // +1.000 − 1.000
        assertEquals(0.0, w[1], 1e-9); // gegenüber 30.000 Basis

        java.util.Map<String, Double> nurA = java.util.Map.of("A", 5.0);
        double[] w2 = SimulatorEngine.gewinnSeitAusTicks(
                result, nurA, wochenStart.atStartOfDay());
        assertEquals(1_000.0, w2[0], 1e-9); // B ohne Wochendaten zählt mit 0 %
        assertEquals(10.0 / 3.0, w2[1], 1e-9);
    }

    @Test
    void testGewinnSeitAusTicksOhneTradeHistorie() {
        // Nicht abonnierte Signale: keine Trade-Exporte → keine Kurven.
        // Das konfigurierte Startkapital zählt als Basis, der Gewinn kommt
        // allein aus den Tick-Prozenten (Zeile darf nicht "—" zeigen).
        SimulatorEngine.SimulationResult result = new SimulatorEngine.SimulationResult();
        result.strategien.add(new SimulatorEngine.StrategyResult(
                "A", "A", new ArrayList<>(), false, 10_000.0));
        result.strategien.add(new SimulatorEngine.StrategyResult(
                "B", "B", new ArrayList<>(), false, 20_000.0));

        LocalDate wochenStart = SimulatorEngine.aktuellerWochenstart();
        java.util.Map<String, Double> prozente = java.util.Map.of("A", 5.0, "B", -2.0);
        double[] w = SimulatorEngine.gewinnSeitAusTicks(
                result, prozente, wochenStart.atStartOfDay());

        // Basis 30.000; Gewinn +500 − 400 = +100 → +0,3333… %
        assertEquals(100.0, w[0], 1e-9);
        assertEquals(100.0 / 30_000.0 * 100.0, w[1], 1e-9);

        // Ohne Tick-Prozente gibt es keinen Gewinn, aber eine Basis
        double[] wOhne = SimulatorEngine.gewinnSeitAusTicks(
                result, java.util.Map.of(), wochenStart.atStartOfDay());
        assertEquals(0.0, wOhne[0], 1e-9);
        assertEquals(0.0, wOhne[1], 1e-9);
    }

    // ---------------------------------------------------- Open Equity

    @Test
    void testOpenEquityKurveVerankertAmErstenTick() {
        List<EquityPoint> simKurve = new ArrayList<>();
        simKurve.add(new EquityPoint(LocalDateTime.parse("2026-09-01T00:00"), 10_000.0));
        simKurve.add(new EquityPoint(LocalDateTime.parse("2026-09-10T12:00"), 11_000.0));

        List<com.mql.realmonitor.data.TickDataLoader.TickData> ticks = new ArrayList<>();
        ticks.add(new com.mql.realmonitor.data.TickDataLoader.TickData(
                LocalDateTime.parse("2026-09-20T12:00"), 12_000.0, -50.0, 500.0));
        ticks.add(new com.mql.realmonitor.data.TickDataLoader.TickData(
                LocalDateTime.parse("2026-09-21T12:00"), 12_100.0, -200.0, 600.0));
        ticks.add(new com.mql.realmonitor.data.TickDataLoader.TickData(
                LocalDateTime.parse("2026-09-22T12:00"), 12_300.0, +150.0, 800.0));

        List<EquityPoint> overlay = SimulatorEngine.openEquityKurve(
                ticks, simKurve, LocalDateTime.parse("2026-09-01T00:00"));

        // OPTIK-FIX: Trägerpunkt am Sim-Kurven-Ende (10.09, 11.000) verbindet
        // die Lücke zwischen letztem CLOSED Trade und dem ersten Tick
        assertEquals(4, overlay.size());
        assertEquals(LocalDateTime.parse("2026-09-10T12:00"), overlay.get(0).getTime());
        assertEquals(11_000.0, overlay.get(0).getCumulatedProfit(), 1e-9);
        // Anker = erster Tick: Overlay startet mit dem Sim-Wert (11.000)
        // Performance = Profit + Floating: 450 → 400 → 950
        assertEquals(11_000.0, overlay.get(1).getCumulatedProfit(), 1e-9);
        // Performance-Delta −50 (Floating-Dip −150) → 10.950
        assertEquals(10_950.0, overlay.get(2).getCumulatedProfit(), 1e-9);
        // Performance-Delta +500 (Floating +200 über Anker) → 11.500
        assertEquals(11_500.0, overlay.get(3).getCumulatedProfit(), 1e-9);
        assertEquals(LocalDateTime.parse("2026-09-22T12:00"), overlay.get(3).getTime());
    }

    @Test
    void testOpenEquityKurveLeerOhneTicksImZeitraum() {
        List<EquityPoint> simKurve = new ArrayList<>();
        simKurve.add(new EquityPoint(LocalDateTime.parse("2026-09-01T00:00"), 10_000.0));

        List<com.mql.realmonitor.data.TickDataLoader.TickData> ticks = new ArrayList<>();
        ticks.add(new com.mql.realmonitor.data.TickDataLoader.TickData(
                LocalDateTime.parse("2026-08-15T12:00"), 9_000.0, 0.0, 0.0));

        // Alle Ticks VOR dem Simulationsstart → kein Overlay
        assertTrue(SimulatorEngine.openEquityKurve(
                ticks, simKurve, LocalDateTime.parse("2026-09-01T00:00")).isEmpty());
        assertTrue(SimulatorEngine.openEquityKurve(
                null, simKurve, LocalDateTime.parse("2026-09-01T00:00")).isEmpty());
    }

    @Test
    void testMergeKurvenSummiertStepweise() {
        List<EquityPoint> a = new ArrayList<>();
        a.add(new EquityPoint(LocalDateTime.parse("2026-09-01T00:00"), 100.0));
        List<EquityPoint> b = new ArrayList<>();
        b.add(new EquityPoint(LocalDateTime.parse("2026-09-02T00:00"), 200.0));

        List<EquityPoint> summe = SimulatorEngine.mergeKurven(java.util.List.of(a, b));
        assertEquals(2, summe.size());
        assertEquals(100.0, summe.get(0).getCumulatedProfit(), 1e-9); // nur A bekannt
        assertEquals(300.0, summe.get(1).getCumulatedProfit(), 1e-9); // A + B
    }

    /**
     * REGRESSION (User-Report 23.09.2026): Das Portfolio-Open-Equity wurde
     * vorher nur aus den Overlay-SEGMENTEN gemerged — Strategien trugen vor
     * ihrem Overlay-Start 0 bei, das Portfolio begann deshalb fälschlich bei
     * EINEM Sim-Konto (10K) statt bei der Summe aller (30K).
     */
    @Test
    void testPortfolioOpenEquityZaehltAlleStrategienAbSimStart() {
        LocalDateTime simStart = LocalDateTime.parse("2026-09-01T00:00");

        // A: Sim 10.000, Overlay sagt 11.000 (Floating +1.000) ab 21.09.
        List<EquityPoint> simA = new ArrayList<>();
        simA.add(new EquityPoint(simStart, 10_000.0));
        List<EquityPoint> overlayA = new ArrayList<>();
        overlayA.add(new EquityPoint(LocalDateTime.parse("2026-09-03T12:00"), 10_000.0)); // Brücke
        overlayA.add(new EquityPoint(LocalDateTime.parse("2026-09-21T18:00"), 11_000.0));
        // B und C: Sim 10.000, KEINE Ticks (kein Overlay)
        List<EquityPoint> simB = new ArrayList<>();
        simB.add(new EquityPoint(simStart, 10_000.0));
        List<EquityPoint> simC = new ArrayList<>();
        simC.add(new EquityPoint(simStart, 10_000.0));

        List<List<EquityPoint>> kurven = new ArrayList<>();
        kurven.add(SimulatorEngine.vereineKurven(simA, overlayA));
        kurven.add(SimulatorEngine.vereineKurven(simB, new ArrayList<>()));
        kurven.add(SimulatorEngine.vereineKurven(simC, null));

        List<EquityPoint> portfolio = SimulatorEngine.mergeKurven(kurven);

        // Ab Sim-Start zählen ALLE drei Konten: 30.000, nicht 10.000
        assertEquals(30_000.0, portfolio.get(0).getCumulatedProfit(), 1e-9);
        // Brückenpunkt von A ändert nichts: 3 × 10.000
        assertEquals(30_000.0, portfolio.get(1).getCumulatedProfit(), 1e-9);
        // Ab 21.09. trägt A sein Floating: 30.000 + 1.000 = 31.000
        assertEquals(31_000.0, portfolio.get(portfolio.size() - 1).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testVereineKurvenUebergibtAmOverlayStart() {
        List<EquityPoint> sim = new ArrayList<>();
        sim.add(new EquityPoint(LocalDateTime.parse("2026-09-01T00:00"), 10_000.0));
        sim.add(new EquityPoint(LocalDateTime.parse("2026-09-10T12:00"), 11_000.0));
        List<EquityPoint> overlay = new ArrayList<>();
        overlay.add(new EquityPoint(LocalDateTime.parse("2026-09-10T12:00"), 11_000.0)); // Brücke
        overlay.add(new EquityPoint(LocalDateTime.parse("2026-09-21T18:00"), 10_800.0));

        List<EquityPoint> kombiniert = SimulatorEngine.vereineKurven(sim, overlay);

        assertEquals(3, kombiniert.size()); // kein Duplikat am Brückenpunkt
        assertEquals(10_000.0, kombiniert.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(11_000.0, kombiniert.get(1).getCumulatedProfit(), 1e-9);
        assertEquals(10_800.0, kombiniert.get(2).getCumulatedProfit(), 1e-9);

        // Ohne Overlay: reine Sim-Kurve
        assertEquals(2, SimulatorEngine.vereineKurven(sim, new ArrayList<>()).size());
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
