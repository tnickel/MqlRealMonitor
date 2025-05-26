package com.mql.realmonitor.gui;

/**
 * Zeitintervalle für die Chart-Skalierung
 */
public enum TimeScale {
    M1("M1", 1, 120),           // 1 Minute, letzte 120 Minuten
    M5("M5", 5, 600),           // 5 Minuten, letzte 600 Minuten
    M15("M15", 15, 1800),       // 15 Minuten, letzte 1800 Minuten
    H1("H1", 60, 7200),         // 1 Stunde, letzte 7200 Minuten
    H4("H4", 240, 28800),       // 4 Stunden, letzte 28800 Minuten
    D1("D", 1440, 172800);      // 1 Tag, letzte 172800 Minuten (120 Tage)
    
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
        return "Zeige letzte " + displayMinutes + " Minuten";
    }
}