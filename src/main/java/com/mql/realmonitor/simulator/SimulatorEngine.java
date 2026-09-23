package com.mql.realmonitor.simulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.data.TickDataLoader;
import com.mql.realmonitor.data.TickDataLoader.TickData;
import com.mql.realmonitor.data.TickDataLoader.TickDataSet;
import com.mql.realmonitor.mql5.EquityCurveBuilder;
import com.mql.realmonitor.mql5.EquityCurveBuilder.EquityPoint;

/**
 * NEU: Simulator — "Was wäre gewesen, wenn ich jedes Signal ab einem
 * Startdatum mit festem Startkapital kopiert hätte?"
 *
 * Je Signal wird die Equity aus dem MQL5-Trade-Export simuliert:
 * Lot-Skalierung proportional zum Sim-Konto (Compounding, wie beim
 * echten MT5-Signal-Kopieren), Ein-/Auszahlungen des Providers sind
 * herausgerechnet (Forensik-Methode, siehe EquityCurveBuilder).
 *
 * Das PORTFOLIO am Ende summiert alle Sim-Konten über die gemeinsame
 * Zeitachse (jede Strategie startet mit dem konfigurierten Kapital).
 */
public class SimulatorEngine {

    private static final Logger LOGGER = Logger.getLogger(SimulatorEngine.class.getName());

    /** Ergebnis einer einzelnen Strategie in der Simulation */
    public static class StrategyResult {
        public final String signalId;
        public final String name;
        public final List<EquityPoint> punkte;   // leer = keine Historie geladen
        public final boolean hatHistorie;
        public final double startkapital;
        public double endwert;

        StrategyResult(String signalId, String name, List<EquityPoint> punkte,
                       boolean hatHistorie, double startkapital) {
            this.signalId = signalId;
            this.name = name;
            this.punkte = punkte;
            this.hatHistorie = hatHistorie;
            this.startkapital = startkapital;
            this.endwert = punkte.isEmpty() ? startkapital
                    : punkte.get(punkte.size() - 1).getCumulatedProfit();
        }

        public double prozent() {
            return startkapital > 0 ? (endwert - startkapital) / startkapital * 100.0 : 0.0;
        }
    }

    /** Gesamt-Ergebnis des Simulator-Laufs */
    public static class SimulationResult {
        public final List<StrategyResult> strategien = new ArrayList<>();
        public final List<EquityPoint> portfolio = new ArrayList<>();
        public double portfolioStartwert;
        public double portfolioEndwert;

        public double portfolioProzent() {
            return portfolioStartwert > 0
                    ? (portfolioEndwert - portfolioStartwert) / portfolioStartwert * 100.0 : 0.0;
        }
    }

    private final MqlRealMonitorConfig config;

    public SimulatorEngine(MqlRealMonitorConfig config) {
        this.config = config;
    }

    /**
     * Führt die Simulation für alle Signale mit geladener Trade-Historie durch.
     *
     * @param signalIds Die Signal-IDs (Reihenfolge der Favoriten)
     * @param namen     Optional: Signal-ID → Name (IdTranslation); null = ID als Name
     * @param start     Startdatum der Simulation
     * @return sortierte Strategien (beste zuerst) + Portfolio-Kurve
     */
    public SimulationResult simulate(List<String> signalIds, java.util.Map<String, String> namen,
                                     LocalDate start) {
        return simulate(signalIds, namen, start, config.getSimulatorStartCapital());
    }

    /**
     * Führt die Simulation mit explizitem Startkapital je Strategie durch —
     * Portfolio-Simulatoren haben ein je Portfolio konfiguriertes Kapital,
     * das nicht durch die globale Config überschrieben werden darf.
     *
     * @param signalIds    Die Signal-IDs (Reihenfolge der Favoriten)
     * @param namen        Optional: Signal-ID → Name (IdTranslation); null = ID als Name
     * @param start        Startdatum der Simulation
     * @param startkapital Startkapital JE Strategie
     * @return sortierte Strategien (beste zuerst) + Portfolio-Kurve
     */
    public SimulationResult simulate(List<String> signalIds, java.util.Map<String, String> namen,
                                     LocalDate start, double startkapital) {
        LocalDateTime startZeit = start.atStartOfDay();
        SimulationResult result = new SimulationResult();

        for (String signalId : signalIds) {
            String name = namen != null && namen.containsKey(signalId)
                    ? namen.get(signalId) : signalId;

            String csv = ladeRohExport(signalId);
            if (csv == null) {
                result.strategien.add(new StrategyResult(signalId, name,
                        new ArrayList<>(), false, startkapital));
                continue;
            }

            List<EquityPoint> kurve = EquityCurveBuilder.buildSimulatedCurve(csv, startZeit, startkapital);
            result.strategien.add(new StrategyResult(signalId, name, kurve, true, startkapital));
        }

        // Beste Strategie zuerst (nach Endwert absteigend; ohne Historie nach hinten)
        result.strategien.sort((a, b) -> {
            if (a.hatHistorie != b.hatHistorie) {
                return a.hatHistorie ? -1 : 1;
            }
            return Double.compare(b.endwert, a.endwert);
        });

        // Portfolio: Summe aller Sim-Konten über die gemeinsame Zeitachse
        List<StrategyResult> mitKurve = new ArrayList<>();
        for (StrategyResult s : result.strategien) {
            if (s.hatHistorie && !s.punkte.isEmpty()) {
                mitKurve.add(s);
            }
        }
        if (!mitKurve.isEmpty()) {
            result.portfolioStartwert = mitKurve.size() * startkapital;
            result.portfolio.addAll(mergePortfolio(mitKurve));
            result.portfolioEndwert = result.portfolio.isEmpty() ? result.portfolioStartwert
                    : result.portfolio.get(result.portfolio.size() - 1).getCumulatedProfit();
        }

        LOGGER.info("Simulation ab " + start + " mit " + startkapital + " je Strategie: "
                + mitKurve.size() + " Kurven, Portfolio-Endwert " + result.portfolioEndwert);
        return result;
    }

    /**
     * Lädt den gespeicherten Roh-Export eines Signals (oder null)
     */
    public String ladeRohExport(String signalId) {
        try {
            Path path = Paths.get(config.getTradesFilePath(signalId));
            if (!Files.exists(path)) {
                return null;
            }
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Konnte Trade-Export für " + signalId + " nicht lesen", e);
            return null;
        }
    }

    /**
     * PORTFOLIO: Summiert alle Strategie-Kurven auf einer gemeinsamen Zeitachse.
     * Zu jedem Zeitpunkt zählt der jeweils letzte bekannte Wert je Strategie
     * (Step-Funktion) — unabhängig davon, wann die einzelnen Signale traden.
     */
    static List<EquityPoint> mergePortfolio(List<StrategyResult> strategien) {
        List<List<EquityPoint>> kurven = new ArrayList<>();
        for (StrategyResult s : strategien) {
            kurven.add(s.punkte);
        }
        return mergeKurven(kurven);
    }

    /**
     * NEU: Summiert beliebige Kurven auf einer gemeinsamen Zeitachse
     * (Step-Funktion, letzter bekannter Wert je Kurve) — benutzt von der
     * Portfolio-Kurve und dem Open-Equity-Overlay des Portfolios.
     */
    public static List<EquityPoint> mergeKurven(List<List<EquityPoint>> kurven) {
        List<EquityPoint> portfolio = new ArrayList<>();

        // Gemeinsame Zeitachse: alle Zeitstempel aller Kurven
        TreeSet<LocalDateTime> zeiten = new TreeSet<>();
        for (List<EquityPoint> kurve : kurven) {
            for (EquityPoint p : kurve) {
                zeiten.add(p.getTime());
            }
        }
        if (zeiten.isEmpty()) {
            return portfolio;
        }

        // Cursor je Kurve: letzter Wert <= t
        int[] cursor = new int[kurven.size()];
        double summe = 0.0;
        boolean[] initialisiert = new boolean[kurven.size()];

        for (LocalDateTime t : zeiten) {
            for (int i = 0; i < kurven.size(); i++) {
                List<EquityPoint> kurve = kurven.get(i);
                // Cursor vorziehen, solange der nächste Punkt <= t ist
                while (cursor[i] + 1 < kurve.size() && !kurve.get(cursor[i] + 1).getTime().isAfter(t)) {
                    cursor[i]++;
                    initialisiert[i] = true;
                }
                // Ersten Punkt der Kurve einbeziehen, sobald t ihn erreicht hat
                if (!initialisiert[i] && !kurve.isEmpty() && !kurve.get(0).getTime().isAfter(t)) {
                    initialisiert[i] = true;
                }
            }
            summe = 0.0;
            for (int i = 0; i < kurven.size(); i++) {
                if (initialisiert[i]) {
                    summe += kurven.get(i).get(cursor[i]).getCumulatedProfit();
                }
            }
            portfolio.add(new EquityPoint(t, summe));
        }
        return portfolio;
    }

    // -------------------------------------------------- Open Equity

    /**
     * NEU: Open-Equity-Kurve eines Sim-Kontos aus den Tick-Daten.
     *
     * Die Trade-Exporte enthalten nur GESCHLOSSENE Trades — Schwankungen
     * durch offene Positionen (Floating) sind darin nicht historisch
     * vorhanden. Wo Tick-Daten existieren (ab Monitoring-Start), wird die
     * echte Performance-Änderung (Profit + FloatingProfit, ein-/auszahlungs-
     * frei) auf das Sim-Konto übertragen: Anker ist der erste Tick innerhalb
     * der Simulation, dort gilt der Sim-Kurvenwert; danach verschiebt sich
     * die Sim-Kurve 1:1 mit der Performance (gleiche Skalen-Logik wie das
     * Compounding, für kurze Zeiträume hinreichend genau).
     *
     * @return Overlay-Punkte (leer, wenn keine Ticks im Simulationszeitraum)
     */
    public static List<EquityPoint> openEquityKurve(List<TickData> ticks,
                                                    List<EquityPoint> simKurve,
                                                    LocalDateTime simStart) {
        List<EquityPoint> overlay = new ArrayList<>();
        if (ticks == null || ticks.isEmpty() || simKurve == null || simKurve.isEmpty() || simStart == null) {
            return overlay;
        }

        // Anker: erster Tick innerhalb des Simulationszeitraums
        TickData anker = null;
        for (TickData t : ticks) {
            if (!t.getTimestamp().isBefore(simStart)) {
                anker = t;
                break;
            }
        }
        if (anker == null) {
            return overlay;
        }

        // Sim-Konto-Wert zum Ankerzeitpunkt (letzter Kurvenpunkt <= Anker)
        double simAmAnker = simKurve.get(0).getCumulatedProfit();
        for (EquityPoint p : simKurve) {
            if (p.getTime().isAfter(anker.getTimestamp())) {
                break;
            }
            simAmAnker = p.getCumulatedProfit();
        }

        double performanceAmAnker = anker.getProfit() + anker.getFloatingProfit();
        for (TickData t : ticks) {
            if (t.getTimestamp().isBefore(anker.getTimestamp())) {
                continue;
            }
            double performance = t.getProfit() + t.getFloatingProfit();
            overlay.add(new EquityPoint(t.getTimestamp(), simAmAnker + performance - performanceAmAnker));
        }

        // OPTIK-FIX: Overlay an das Ende der Sim-Kurve anhängen — ohne diesen
        // Trägerpunkt schwebt die gelbe Linie als losgelöstes Segment rechts
        // (grüne Kurve endet am letzten CLOSED Trade, Ticks starten erst beim
        // Monitoring-Start). Der flache Verlauf entspricht exakt der Sim-Kurve.
        EquityPoint simEnde = simKurve.get(simKurve.size() - 1);
        if (simEnde.getTime().isBefore(anker.getTimestamp())) {
            overlay.add(0, new EquityPoint(simEnde.getTime(), simAmAnker));
        }
        return overlay;
    }

    /**
     * NEU: Lädt die Open-Equity-Kurve eines Signals aus seiner Tick-Datei
     * (Hintergrund-IO; leer bei fehlenden Ticks/Datei).
     */
    public static List<EquityPoint> ladeOpenEquityKurve(MqlRealMonitorConfig config,
                                                        StrategyResult strategie,
                                                        LocalDate simStart) {
        try {
            TickDataSet dataSet = TickDataLoader.loadTickData(
                    config.getTickFilePath(strategie.signalId), strategie.signalId);
            if (dataSet == null || dataSet.getTickCount() == 0) {
                return new ArrayList<>();
            }
            return openEquityKurve(dataSet.getTicks(), strategie.punkte, simStart.atStartOfDay());
        } catch (Exception e) {
            LOGGER.warning("Open-Equity-Kurve für " + strategie.signalId + " nicht ladbar: "
                    + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * NEU: Start der laufenden Woche — letzter Sonntag 00:00 (heute bei
     * Sonntag), dieselbe Regel wie PeriodProfitCalculator für die
     * Wochengewinn-Spalte der Tabelle.
     */
    public static LocalDate aktuellerWochenstart() {
        LocalDate heute = LocalDate.now();
        if (heute.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return heute;
        }
        return heute.minusDays(heute.getDayOfWeek().getValue());
    }

    /**
     * NEU: Gewinn des Portfolios seit einem Referenzzeitpunkt, aus der
     * Portfolio-Kurve. Basis ist der letzte Portfolio-Wert vor bzw. am
     * Referenzzeitpunkt; liegt die komplette Kurve danach (frisch
     * angelegtes Portfolio), zählt der Zeitraum seit Sim-Start.
     * Fallback für {@link #gewinnSeitAusTicks}, wenn keine Tick-Daten.
     *
     * Rückgabe: [0] = Gewinn in Euro, [1] = Gewinn in Prozent relativ zum
     * Portfolio-Wert am Referenzzeitpunkt. Beide 0.0, wenn keine Kurve.
     */
    public static double[] gewinnSeitAusKurve(SimulationResult result, LocalDateTime seit) {
        double[] out = new double[2];
        if (result == null || result.portfolio.isEmpty()) {
            return out;
        }

        double basis = result.portfolioStartwert;
        for (EquityPoint p : result.portfolio) {
            if (p.getTime().isAfter(seit)) {
                break;
            }
            basis = p.getCumulatedProfit();
        }

        double endwert = result.portfolio.get(result.portfolio.size() - 1).getCumulatedProfit();
        out[0] = endwert - basis;
        out[1] = basis > 0 ? out[0] / basis * 100.0 : 0.0;
        return out;
    }

    /**
     * NEU: Gewinn des Portfolios seit einem Referenzzeitpunkt aus den
     * LIVE-Periodenprozenten der Tick-Daten (Tag/Woche/Monat/3M/6M/12M).
     *
     * Jede Strategie bringt ihr Sim-Kapital am Referenzzeitpunkt (letzter
     * Kurvenwert vor/am {@code seit}, sonst Startkapital) ein und wird mit
     * ihrem Perioden-Gewinnprozentsatz verzinst — dieselbe Quelle wie die
     * Gewinn-Spalten der Tabelle (Δ Profit+Floating aus den Tick-Daten).
     * Damit die Zeile Werte zeigt, obwohl Trade-Exporte nur per
     * "Trades laden" aktualisiert werden.
     *
     * @param result             Simulationsergebnis des Portfolios
     * @param prozenteJeSignal   Signal-ID → Gewinn in % seit dem Referenzzeitpunkt (nur Signale MIT Daten)
     * @param seit               Start des Zeitraums
     * @return [0] = Gewinn in Euro, [1] = Gewinn in Prozent
     */
    public static double[] gewinnSeitAusTicks(SimulationResult result,
                                              java.util.Map<String, Double> prozenteJeSignal,
                                              LocalDateTime seit) {
        double[] out = new double[2];
        if (result == null || result.portfolio.isEmpty() || seit == null) {
            return out;
        }

        double basis = 0.0;
        double gewinn = 0.0;
        int strategienMitKapital = 0;

        for (StrategyResult s : result.strategien) {
            if (!s.hatHistorie || s.punkte.isEmpty()) {
                continue;
            }
            double kapitalAmStart = s.startkapital;
            for (EquityPoint p : s.punkte) {
                if (p.getTime().isAfter(seit)) {
                    break;
                }
                kapitalAmStart = p.getCumulatedProfit();
            }
            basis += kapitalAmStart;
            strategienMitKapital++;

            Double prozent = prozenteJeSignal != null
                    ? prozenteJeSignal.get(s.signalId) : null;
            if (prozent != null) {
                gewinn += kapitalAmStart * prozent / 100.0;
            }
        }

        if (strategienMitKapital == 0 || basis <= 0) {
            return out;
        }
        out[0] = gewinn;
        out[1] = gewinn / basis * 100.0;
        return out;
    }

    /**
     * NEU: Vereinigt die Sim-Kurve einer Strategie mit ihrem Open-Equity-
     * Overlay zu EINER Kurve: bis zum Overlay-Start der Sim-Verlauf (das
     * Konto existiert ja ab Sim-Start — ohne bekannte Floating-Daten ist
     * der Gesamtwert dort gleich dem Kontostand), ab dem Overlay-Start die
     * Open-Equity-Werte (Kontostand + Floating).
     *
     * Wichtig für das PORTFOLIO: Summiert man stattdessen nur die Overlay-
     * Segmente, tragen Strategien vor ihrem Overlay-Start 0 bei — das
     * Portfolio würde fälschlich bei EINEM Sim-Konto (~10K) statt bei der
     * Summe aller (~30K) beginnen.
     *
     * @param simKurve Simulations-Kurve (closed trades, ab Sim-Start)
     * @param overlay  Open-Equity-Punkte (beginnt am Brücken-/Ankerpunkt); darf leer sein
     * @return kombinierte Kurve; Sim-Kurve, wenn kein Overlay vorhanden
     */
    public static List<EquityPoint> vereineKurven(List<EquityPoint> simKurve,
                                                  List<EquityPoint> overlay) {
        List<EquityPoint> kombiniert = new ArrayList<>();
        if (overlay == null || overlay.isEmpty()) {
            if (simKurve != null) {
                kombiniert.addAll(simKurve);
            }
            return kombiniert;
        }

        LocalDateTime overlayStart = overlay.get(0).getTime();
        if (simKurve != null) {
            for (EquityPoint p : simKurve) {
                if (p.getTime().isBefore(overlayStart)) {
                    kombiniert.add(p);
                }
            }
        }
        kombiniert.addAll(overlay);
        return kombiniert;
    }

    /** Format-Helfer für Datumseingaben (yyyy-MM-dd) */
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
