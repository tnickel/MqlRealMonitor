package com.mql.realmonitor.simulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
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
        LocalDateTime startZeit = start.atStartOfDay();
        double startkapital = config.getSimulatorStartCapital();
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
        List<EquityPoint> portfolio = new ArrayList<>();

        // Gemeinsame Zeitachse: alle Zeitstempel aller Kurven
        TreeSet<LocalDateTime> zeiten = new TreeSet<>();
        for (StrategyResult s : strategien) {
            for (EquityPoint p : s.punkte) {
                zeiten.add(p.getTime());
            }
        }
        if (zeiten.isEmpty()) {
            return portfolio;
        }

        // Cursor je Strategie: letzter Wert <= t
        int[] cursor = new int[strategien.size()];
        double summe = 0.0;
        boolean[] initialisiert = new boolean[strategien.size()];

        for (LocalDateTime t : zeiten) {
            for (int i = 0; i < strategien.size(); i++) {
                List<EquityPoint> kurve = strategien.get(i).punkte;
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
            for (int i = 0; i < strategien.size(); i++) {
                if (initialisiert[i]) {
                    summe += strategien.get(i).punkte.get(cursor[i]).getCumulatedProfit();
                }
            }
            portfolio.add(new EquityPoint(t, summe));
        }
        return portfolio;
    }

    /** Format-Helfer für Datumseingaben (yyyy-MM-dd) */
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
