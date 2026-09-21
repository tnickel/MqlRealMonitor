package com.mql.realmonitor.simulator;

import java.util.ArrayList;
import java.util.List;

/**
 * NEU: Definition eines Portfolio-Simulators — eine benannte Strategie-
 * Kombination ("Portfolio"), die sich im rechten Seitenpanel als Icon
 * anzeigen und simulieren lässt.
 *
 * Enthält: Name (Beschriftung des Icons), Startdatum und Startkapital
 * (je Portfolio konfigurierbar; Default beim Anlegen aus der globalen
 * Simulator-Config) sowie die Liste der enthaltenen Signal-IDs.
 */
public class PortfolioDefinition {

    private int id;
    private String name;
    private String startDate;        // yyyy-MM-dd
    private double startCapital;
    private List<String> signalIds = new ArrayList<>();

    public PortfolioDefinition() {
    }

    public PortfolioDefinition(int id, String name, String startDate, double startCapital) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.startCapital = startCapital;
    }

    /**
     * NEU: Ersetzt die Signal-Auswahl (ignorigt null/leer/duplikate)
     */
    public void setSignalIds(List<String> ids) {
        this.signalIds = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.trim().isEmpty() && !signalIds.contains(id.trim())) {
                    signalIds.add(id.trim());
                }
            }
        }
    }

    // Getter/Setter (Jackson für JSON-Persistenz)

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public double getStartCapital() {
        return startCapital;
    }

    public void setStartCapital(double startCapital) {
        this.startCapital = startCapital;
    }

    public List<String> getSignalIds() {
        return signalIds;
    }

    @Override
    public String toString() {
        return "Portfolio[" + id + ", '" + name + "', " + signalIds.size() + " Signale]";
    }
}
