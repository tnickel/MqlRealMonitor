package com.mql.realmonitor.mql5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für die Kurven-Rekonstruktion aus MQL5-Trade-Exporten und die
 * sichere Ablage der MQL5-Zugangsdaten.
 *
 * Kernforderung (Nutzer, 21.09.2026): Ein- und Auszahlungen müssen
 * HERAUSGERECHNET werden — die Gewinnkurve zeigt nur Trade-Profits, die
 * Trading-Kurve startet mit dem Kapital vor dem ersten Trade und lässt
 * spätere Kontobewegungen außerhalb (Forensik-Methode des MqlKiScanner).
 */
class Mql5TradeHistoryTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------ Gewinnkurve

    @Test
    void testMt5PositionsExportWirdKorrektKumuliert() {
        String csv = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\r\n"
            + "2026.09.01 10:00:00;buy;0.10;XAUUSD;2500.00;0.00;2026.09.01 11:00:00;2505.00;0;0;50.00\r\n"
            + "2026.09.02 10:00:00;sell;0.10;XAUUSD;2505.00;0.00;2026.09.02 12:00:00;2500.00;0;0;49.60\r\n"
            + "2026.09.03 10:00:00;buy;0.10;XAUUSD;2500.00;0.00;2026.09.03 12:00:00;2498.00;0;0;-20.50\r\n";

        List<EquityCurveBuilder.EquityPoint> curve = EquityCurveBuilder.buildProfitCurve(csv);

        assertEquals(3, curve.size());
        assertEquals(LocalDateTime.of(2026, 9, 1, 11, 0, 0), curve.get(0).getTime());
        assertEquals(50.00, curve.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(99.60, curve.get(1).getCumulatedProfit(), 1e-9);
        assertEquals(79.10, curve.get(2).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testNettoEnthaeltKommissionUndSwap() {
        String csv = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\r\n"
            + "2026.09.03 10:00:00;buy;0.10;XAUUSD;2500.00;0.00;2026.09.03 12:00:00;2498.00;-2.50;-1.00;-20.50\r\n";

        List<EquityCurveBuilder.EquityPoint> curve = EquityCurveBuilder.buildProfitCurve(csv);

        assertEquals(1, curve.size());
        // NETTO = Profit + Kommission + Swap = -20.50 - 2.50 - 1.00 = -24.00
        assertEquals(-24.00, curve.get(0).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testMt4OrderbuchMitSummenzeileUndTausenderpunkten() {
        String csv = "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment\r\n"
            + "2026.09.01 10:00:00;buy;0.10;XAUUSD;2500.00;; ;2026.09.01 11:00:00;2505.00;0;0;1 403.03;[sl]\r\n"
            + "2026.09.02 10:00:00;Buy;0.10;profit;;;;;;0;-1 500.00;\r\n"  // Summenzeile: muss übersprungen werden
            + "2026.09.03 10:00:00;sell;0.10;XAUUSD;2505.00;;;2026.09.03 12:00:00;2500.00;0;0;-3.30;\r\n";

        List<EquityCurveBuilder.EquityPoint> curve = EquityCurveBuilder.buildProfitCurve(csv);

        assertEquals(2, curve.size(), "Summenzeile muss übersprungen werden");
        assertEquals(1403.03, curve.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(1399.73, curve.get(1).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testBomAmAnfangWirdToleriert() {
        String csv = "\ufeffTime;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n"
            + "2026.09.01 10:00:00;buy;0.10;XAUUSD;2500.00;0.00;2026.09.01 11:00:00;2505.00;0;0;10.00\n";

        List<EquityCurveBuilder.EquityPoint> curve = EquityCurveBuilder.buildProfitCurve(csv);
        assertEquals(1, curve.size());
        assertEquals(10.0, curve.get(0).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testStorniertePendingOrdersUndBalanceZeilenBleibenDraussen() {
        // Reale Struktur aus Gold Spike #2349227 (Diagnose 21.09.2026):
        // 716 stornierte Buy/Sell-Stop-Zeilen ohne Profit, 9 Balance-Zeilen
        String csv = "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment\r\n"
            + "2026.09.18 03:00:00;Buy Stop;0.01;XAUUSD;4 400.54;4 221.13;4 414.75;2026.09.18 23:00:00;4 376.60;;;;cancelled\r\n"
            + "2025.11.03 15:04:11;Balance;;;;;;;;;;1 444.00;\r\n"
            + "2026.09.13 21:21:54;Balance;;;;;;;;;;-240.00;\r\n"
            + "2025.11.10 10:00:00;buy;0.10;XAUUSD;2500.00;;;2025.11.10 11:00:00;2510.00;0;0;100.00;\r\n";

        List<EquityCurveBuilder.EquityPoint> gewinn = EquityCurveBuilder.buildProfitCurve(csv);
        assertEquals(1, gewinn.size(), "Stornos und Balance-Zeilen gehören nicht in die Gewinnkurve");
        assertEquals(100.0, gewinn.get(0).getCumulatedProfit(), 1e-9);
    }

    @Test
    void testUngueltigerInhaltLiefertLeereKurve() {
        assertTrue(EquityCurveBuilder.buildProfitCurve((String) null).isEmpty());
        assertTrue(EquityCurveBuilder.buildProfitCurve("").isEmpty());
        assertTrue(EquityCurveBuilder.buildProfitCurve("<html>login</html>").isEmpty());
        assertTrue(EquityCurveBuilder.buildTradingCurve("irgendein text").isEmpty());
    }

    @Test
    void testUngueltigeZeilenWerdenUebersprungen() {
        String csv = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n"
            + "2026.09.01\n" // Artefakt: nur Zeitstempel
            + "2026.09.01 10:00:00;buy;0.10;XAUUSD;2500.00;0.00;2026.09.01 11:00:00;2505.00;0;0;25.00\n";

        List<EquityCurveBuilder.EquityPoint> curve = EquityCurveBuilder.buildProfitCurve(csv);
        assertEquals(1, curve.size());
    }

    // ------------------------------------------------------- Trading-Kurve

    @Test
    void testTradingKurveStartetMitStartkapitalUndRechnetAuszahlungenHeraus() {
        // Szenario Gold Spike: Einzahlungen 1444 + 1039 vor dem ersten Trade,
        // Auszahlung -1403.03 NACH Handelsbeginn — die darf den Trading-Drawdown
        // nicht verfälschen (Nutzerforderung: "Ein-/Auszahlungen rausrechnen")
        String csv = "Time;Type;Volume;Symbol;Price;S/L;T/P;Time;Price;Commission;Swap;Profit;Comment\r\n"
            + "2025.11.03 15:04:11;Balance;;;;;;;;;;1 444.00;\r\n"
            + "2025.11.03 15:07:16;Balance;;;;;;;;;;1 039.00;\r\n"
            + "2025.11.10 10:00:00;buy;0.10;XAUUSD;2500.00;;;2025.11.10 11:00:00;2510.00;0;0;100.00;\r\n"
            + "2025.12.01 10:00:00;sell;0.10;XAUUSD;2510.00;;;2025.12.01 12:00:00;2500.00;0;0;50.00;\r\n"
            + "2026.03.19 07:07:48;Balance;;;;;;;;;;-1 403.03;\r\n";

        List<EquityCurveBuilder.EquityPoint> trading = EquityCurveBuilder.buildTradingCurve(csv);

        assertEquals(2, trading.size(), "Nur die 2 Trades — Auszahlung rausgerechnet");
        // Startkapital = Kontobewegungen vor dem ersten Trade-Öffnen (2025.11.10 10:00);
        // der erste Kurvenpunkt liegt am ersten Trade-Close: 2483 + 100
        assertEquals(2583.0, trading.get(0).getCumulatedProfit(), 1e-9);
        assertEquals(2633.0, trading.get(1).getCumulatedProfit(), 1e-9);
    }

    // ------------------------------------------------------------ Credentials

    @Test
    void testCredentialsRoundtripAusserhalbDesRepos() {
        Path configDir = tempDir.resolve("config");
        Mql5Credentials credentials = new Mql5Credentials(configDir.toString());

        assertFalse(credentials.isConfigured());

        assertTrue(credentials.save("tnickel", "geheim-123"));

        // Neu laden aus der Datei (simuliert Programmstart)
        Mql5Credentials geladen = new Mql5Credentials(configDir.toString());
        assertTrue(geladen.isConfigured());
        assertEquals("tnickel", geladen.getUser());
        assertEquals("geheim-123", geladen.getPassword());

        // Datei liegt im übergebenen configDir (BASE_PATH/config), NICHT im Repo
        assertTrue(Files.exists(Path.of(geladen.getCredentialsFile())));
    }

    @Test
    void testCredentialsLeerBleibtNichtKonfiguriert() {
        Mql5Credentials credentials = new Mql5Credentials(tempDir.toString());
        credentials.save("user", "");
        assertFalse(credentials.isConfigured());
    }
}
