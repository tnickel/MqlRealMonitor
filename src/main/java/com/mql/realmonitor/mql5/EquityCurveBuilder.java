package com.mql.realmonitor.mql5;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: Rekonstruiert Gewinn- und Trading-Kurven aus dem MQL5-Trade-Export.
 *
 * Zwei bekannte Export-Formate (Semikolon-getrennt, siehe MqlKiScanner/
 * parser.py und AGENTS.md):
 *  - MT5-Positions-Export (11 Spalten): Profit = Index 10, Close-Zeit = Index 6
 *  - MT4-Orderbuch (13 Spalten):        Profit = Index 11, Close-Zeit = Index 7
 *  Kommission (Index 8) und Swap (Index 9) liegen in BEIDEN Formaten gleich.
 *
 * Behandlung bekannter Export-Artefakte:
 *  - UTF-8-BOM am Dateianfang wird toleriert
 *  - MT4-Summenzeile am Ende (Typ Buy/Sell, Symbol wörtlich "profit") wird
 *    übersprungen; stornierte Pending-Orders (kein Profit) ebenso
 *  - Zahlen mit Tausendertrennzeichen Leerzeichen ("1 403.03") werden normalisiert
 *
 * Kontobewegungen (Balance-Zeilen = Ein-/Auszahlungen) werden gemäß der
 * Forensik-Methode des MqlKiScanner (forensics/drawdown.py) HERAUSGERECHNET:
 * Nur Kontobewegungen vor dem ersten Trade bilden das Startkapital der
 * Trading-Kurve; spätere Auszahlungen erzeugen sonst Schein-Drawdowns
 * (Diagnose 21.09.2026: Provider entnehmen laufend Gewinne).
 */
public final class EquityCurveBuilder {

    private static final Logger LOGGER = Logger.getLogger(EquityCurveBuilder.class.getName());

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    /** Ein Punkt der Kurve (Zeitpunkt, kumulierter Wert). */
    public static class EquityPoint {
        private final LocalDateTime time;
        private final double value;

        public EquityPoint(LocalDateTime time, double value) {
            this.time = time;
            this.value = value;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public double getCumulatedProfit() {
            return value;
        }
    }

    private EquityCurveBuilder() {
    }

    /**
     * GEWINNKURVE: Kumulierte NETTO-Trade-Profits (Profit + Kommission + Swap),
     * startend bei 0 — ohne jegliche Kontobewegungen.
     */
    public static List<EquityPoint> buildProfitCurve(String csvContent) {
        return cumulate(scanRows(csvContent), null);
    }

    /**
     * TRADING-KURVE nach der Forensik-Methode des MqlKiScanner:
     * Startkapital = Summe aller Balance-Zeilen VOR dem ersten Trade-Öffnen
     * (dieses Kapital war für den Handel da), danach kumulierte NETTO-Profits.
     * Spätere Ein-/Auszahlungen fließen bewusst NICHT ein — sie erzeugen
     * Schein-Drawdowns ohne Handelsbezug.
     */
    public static List<EquityPoint> buildTradingCurve(String csvContent) {
        RowScan scan = scanRows(csvContent);
        if (scan == null) {
            return new ArrayList<>();
        }
        return cumulate(scan, scan.startkapital);
    }

    /**
     * NEU: SIMULATIONS-KURVE — "Was wäre gewesen, wenn ich das Signal ab
     * {@code start} mit {@code simStartKapital} kopiert hätte?"
     *
     * Lot-Skalierung wie beim echten MT5-Signal-Kopieren: Jeder Trade wird
     * proportional auf das simulierte Konto umgerechnet — Faktor je Trade
     * simKonto / realKonto (Compounding: wächst das reale Konto, wächst die
     * übernommene Lotsize mit). Gewinn/Verlust je Trade = NETTO
     * (Profit + Kommission + Swap) × Faktor.
     *
     * Reales Konto zum Startdatum = Startkapital (vor erstem Trade) +
     * alle Nettos davor. Ein-/Auszahlungen danach sind herausgerechnet.
     *
     * @return Punkte ab Startdatum (erster Punkt = Startdatum mit Startkapital);
     *         nur ein Punkt, wenn seit dem Startdatum keine Trades geschlossen wurden
     */
    public static List<EquityPoint> buildSimulatedCurve(String csvContent,
                                                        LocalDateTime start,
                                                        double simStartKapital) {
        List<EquityPoint> result = new ArrayList<>();
        RowScan scan = scanRows(csvContent);
        if (scan == null || start == null) {
            return result;
        }

        // Reales Konto unmittelbar vor dem Startdatum
        double realEquity = scan.startkapital;
        for (int i = 0; i < scan.tradeTimes.size(); i++) {
            if (scan.tradeTimes.get(i).isBefore(start)) {
                realEquity += scan.tradeNets.get(i);
            }
        }

        // Startpunkt der Simulation
        double sim = simStartKapital;
        double lastScale = realEquity > 0 ? simStartKapital / realEquity : 1.0;
        result.add(new EquityPoint(start, sim));

        // Trades ab Startdatum mit Compounding-Skalierung übernehmen
        for (int i = 0; i < scan.tradeTimes.size(); i++) {
            LocalDateTime t = scan.tradeTimes.get(i);
            if (t.isBefore(start)) {
                continue;
            }
            double netto = scan.tradeNets.get(i);
            double scale = (realEquity > 0 && sim > 0) ? sim / realEquity : lastScale;
            lastScale = scale;
            sim += netto * scale;
            realEquity += netto;
            result.add(new EquityPoint(t, sim));
        }

        return result;
    }

    // ------------------------------------------------------------------

    private static List<EquityPoint> cumulate(RowScan scan, Double startwert) {
        List<EquityPoint> curve = new ArrayList<>();
        if (scan == null || scan.tradeNets.isEmpty()) {
            return curve;
        }
        double bal = startwert != null ? startwert : 0.0;
        for (int i = 0; i < scan.tradeNets.size(); i++) {
            bal += scan.tradeNets.get(i);
            curve.add(new EquityPoint(scan.tradeTimes.get(i), bal));
        }
        return curve;
    }

    /** Zeilen-Sammlung: chronologische Trade-Nettos und Balance-Bewegungen. */
    private static class RowScan {
        final List<LocalDateTime> tradeTimes = new ArrayList<>();
        final List<Double> tradeNets = new ArrayList<>();
        final List<LocalDateTime> balanceTimes = new ArrayList<>();
        final List<Double> balanceAmounts = new ArrayList<>();
        LocalDateTime firstTradeOpenTime;
        double startkapital = 0.0; // Kontobewegungen vor dem ersten Trade
    }

    /**
     * Parst den Export einmal und liefert chronologische Trade-Nettos
     * (Profit + Kommission + Swap) sowie die Balance-Bewegungen.
     */
    private static RowScan scanRows(String csvContent) {
        RowScan scan = new RowScan();
        if (csvContent == null || csvContent.isEmpty()) {
            return null;
        }

        // BOM tolerieren
        String content = csvContent.startsWith("\ufeff") ? csvContent.substring(1) : csvContent;

        String[] lines = content.split("\r?\n");
        if (lines.length == 0 || !lines[0].trim().startsWith("Time")) {
            LOGGER.warning("Equity-Kurve: kein gültiger Export (Header beginnt nicht mit 'Time')");
            return null;
        }

        // Format am Header erkennen: 13 Felder = MT4-Orderbuch, sonst MT5-Positionen
        boolean mt4Orderbook = lines[0].split(";", -1).length >= 13;
        int closeTimeIdx = mt4Orderbook ? 7 : 6;
        int profitIdx = mt4Orderbook ? 11 : 10;
        // Kommission/Swap: MT5 = Idx 8/9; MT4-Orderbuch = Idx 9/10
        // (Idx 8 ist beim MT4-Orderbuch der CLOSE-PREIS!)
        int commissionIdx = mt4Orderbook ? 9 : 8;
        int swapIdx = mt4Orderbook ? 10 : 9;

        // Ereignisse sammeln: (Zeit, Netto) je Trade; (Zeit, Betrag) je Balance
        List<double[]> events = new ArrayList<>();      // [netto]
        List<LocalDateTime> eventTimes = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(";", -1);
            if (cols.length <= Math.max(closeTimeIdx, profitIdx)) {
                continue;
            }

            // Summenzeile überspringen (Typ Buy/Sell mit Symbol "profit", keine Preise)
            String type = cols[1].trim();
            String symbol = cols[3].trim();
            if ((type.equalsIgnoreCase("Buy") || type.equalsIgnoreCase("Sell"))
                    && symbol.equalsIgnoreCase("profit")) {
                continue;
            }

            // Balance-Zeile (Einzahlung/Auszahlung): Zeit steht in Spalte 0
            if (type.equalsIgnoreCase("Balance")) {
                LocalDateTime t = parseTime(cols[0]);
                Double amount = parseNumber(cols[profitIdx]);
                if (t != null && amount != null) {
                    scan.balanceTimes.add(t);
                    scan.balanceAmounts.add(amount);
                }
                continue;
            }

            // MT5-Zeilen mit nur Zeitstempel (Artefakt) überspringen
            if (cols[0].trim().isEmpty()) {
                continue;
            }

            // Trade-Zeile: Close-Zeit (Fallback Open-Zeit) und NETTO
            LocalDateTime closeTime = parseTime(cols[closeTimeIdx]);
            if (closeTime == null) {
                closeTime = parseTime(cols[0]);
            }
            Double profit = parseNumber(cols[profitIdx]);
            if (closeTime == null || profit == null) {
                LOGGER.fine("Equity-Kurve: Zeile übersprungen (Zeile " + (i + 1) + "): "
                        + line.substring(0, Math.min(line.length(), 60)));
                continue;
            }

            Double commission = parseNumber(cols[commissionIdx]);
            Double swap = parseNumber(cols[swapIdx]);
            double netto = profit
                    + (commission != null ? commission : 0.0)
                    + (swap != null ? swap : 0.0);

            events.add(new double[]{netto});
            eventTimes.add(closeTime);

            LocalDateTime openTime = parseTime(cols[0]);
            if (openTime != null
                    && (scan.firstTradeOpenTime == null || openTime.isBefore(scan.firstTradeOpenTime))) {
                scan.firstTradeOpenTime = openTime;
            }
        }

        // Chronologisch sortieren (Exporte sind i. d. R. sortiert, aber sicher ist sicher)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < eventTimes.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> eventTimes.get(a).compareTo(eventTimes.get(b)));
        for (int idx : order) {
            scan.tradeTimes.add(eventTimes.get(idx));
            scan.tradeNets.add(events.get(idx)[0]);
        }

        // Startkapital: Kontobewegungen vor dem ersten Trade-Öffnen
        for (int i = 0; i < scan.balanceTimes.size(); i++) {
            LocalDateTime t = scan.balanceTimes.get(i);
            if (scan.firstTradeOpenTime == null || !t.isAfter(scan.firstTradeOpenTime)) {
                scan.startkapital += scan.balanceAmounts.get(i);
            }
        }

        LOGGER.info("Equity-Kurven gescannt: " + scan.tradeNets.size() + " Trades, "
                + scan.balanceTimes.size() + " Kontobewegungen, Startkapital "
                + String.format("%.2f", scan.startkapital) + " ("
                + (mt4Orderbook ? "MT4-Orderbuch" : "MT5-Positionen") + ")");
        return scan;
    }

    private static LocalDateTime parseTime(String text) {
        try {
            return LocalDateTime.parse(text.trim(), TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Zahlen mit Tausendertrennzeichen Leerzeichen ("1 403.03"); null bei leeren/ungültigen
     */
    private static Double parseNumber(String text) {
        String normalized = text.trim().replace(" ", "").replace("\u00a0", "");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Baut die Kurve direkt aus einem InputStream (BOM-tolerant)
     */
    public static List<EquityPoint> buildCurve(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return buildProfitCurve(sb.toString());
    }
}
