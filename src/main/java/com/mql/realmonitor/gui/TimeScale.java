package com.mql.realmonitor.gui;

/**
 * ERWEITERT: Zeitintervalle für die Chart-Skalierung mit ALL-Option
 * NEU: ALL zeigt den kompletten verfügbaren Zeitraum ohne Filterung
 */
public enum TimeScale {
    M1("M1", 1, 120),           // 1 Minute, letzte 120 Minuten
    M5("M5", 5, 600),           // 5 Minuten, letzte 600 Minuten
    M15("M15", 15, 1800),       // 15 Minuten, letzte 1800 Minuten
    H1("H1", 60, 7200),         // 1 Stunde, letzte 7200 Minuten
    H4("H4", 240, 28800),       // 4 Stunden, letzte 28800 Minuten
    D1("D", 1440, 172800),      // 1 Tag, letzte 172800 Minuten (120 Tage)
    ALL("ALL", 0, 0);           // NEU: Kompletter verfügbarer Zeitraum (keine Filterung)
    
    private final String label;
    private final int intervalMinutes;
    private final int displayMinutes;
    
    TimeScale(String label, int intervalMinutes, int displayMinutes) {
        this.label = label;
        this.intervalMinutes = intervalMinutes;
        this.displayMinutes = displayMinutes;
    }
    
    public String getLabel() { 
        return label; 
    }
    
    public int getIntervalMinutes() { 
        return intervalMinutes; 
    }
    
    public int getDisplayMinutes() { 
        return displayMinutes; 
    }
    
    public String getToolTipText() {
        if (this == ALL) {
            return "Zeige kompletten verfügbaren Zeitraum";
        }
        return "Zeige letzte " + displayMinutes + " Minuten";
    }
    
    /**
     * NEU: Prüft ob dies der ALL-Modus ist
     */
    public boolean isAll() {
        return this == ALL;
    }
    
    /**
     * NEU: Gibt eine passende X-Achsen-Formatierung zurück
     */
    public String getDateTimeFormat() {
        switch (this) {
            case M1:
            case M5:
            case M15:
                return "HH:mm";           // Nur Zeit für kurze Intervalle
            case H1:
                return "dd.MM HH:mm";     // Datum + Zeit für Stunden
            case H4:
            case D1:
                return "dd.MM.yy HH:mm";  // Vollständiges Datum für lange Intervalle
            case ALL:
                return "dd.MM.yy HH:mm";  // Vollständiges Datum für kompletten Zeitraum
            default:
                return "HH:mm";
        }
    }
    
    /**
     * NEU: Gibt eine passende Titel-Formatierung zurück
     */
    public String getTitleDateFormat() {
        switch (this) {
            case M1:
            case M5:
            case M15:
                return "dd.MM.yyyy HH:mm"; // Für Titel immer vollständig
            case H1:
            case H4:
            case D1:
                return "dd.MM.yyyy HH:mm";
            case ALL:
                return "dd.MM.yyyy";       // Für ALL nur Datum im Titel
            default:
                return "dd.MM.yyyy HH:mm";
        }
    }
}