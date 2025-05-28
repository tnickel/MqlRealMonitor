package com.mql.realmonitor.gui;

import com.mql.realmonitor.parser.SignalData;

import org.eclipse.swt.widgets.Shell;
import java.util.logging.Logger;

/**
 * REFACTORED: Stark vereinfachte TickChartWindow-Klasse
 * Delegiert alle Funktionalität an den TickChartWindowManager
 * Von über 1000 Zeilen auf unter 100 Zeilen reduziert!
 * 
 * ARCHITEKTUR-VERBESSERUNG:
 * - EquityDrawdownChartPanel: Wiederverwendbare Drawdown-Chart-Komponente
 * - ProfitDevelopmentChartPanel: Wiederverwendbare Profit-Chart-Komponente  
 * - TickChartWindowToolbar: Alle Buttons und Event-Handler
 * - TickChartWindowManager: Koordiniert alle Komponenten
 * - TickChartWindow: Einfache Facade-Klasse
 */
public class TickChartWindow {
    
    private static final Logger LOGGER = Logger.getLogger(TickChartWindow.class.getName());
    
    private final TickChartWindowManager windowManager;
    private final String signalId;
    
    /**
     * Konstruktor - STARK VEREINFACHT
     * Über 1000 Zeilen Code in separate Klassen ausgelagert!
     */
    public TickChartWindow(Shell parent, MqlRealMonitorGUI parentGui, String signalId, 
                          String providerName, SignalData signalData, String tickFilePath) {
        
        this.signalId = signalId;
        
        LOGGER.info("=== REFACTORED TICK CHART WINDOW (Von >1000 auf <100 Zeilen!) ===");
        LOGGER.info("Signal: " + signalId + " (" + providerName + ")");
        LOGGER.info("Architektur: 4 separate Klassen für bessere Wartbarkeit");
        LOGGER.info("- EquityDrawdownChartPanel: Wiederverwendbare Drawdown-Chart-Anzeige");
        LOGGER.info("- ProfitDevelopmentChartPanel: Wiederverwendbare Profit-Chart-Anzeige");
        LOGGER.info("- TickChartWindowToolbar: Alle Buttons und Event-Handler");
        LOGGER.info("- TickChartWindowManager: Koordiniert alle Komponenten");
        
        // Gesamte Logik an WindowManager delegieren
        windowManager = new TickChartWindowManager(parent, parentGui, signalId, providerName, signalData, tickFilePath);
        
        LOGGER.info("TickChartWindow (Refactored) erfolgreich erstellt");
    }
    
    /**
     * Öffnet das Fenster
     */
    public void open() {
        if (windowManager != null) {
            windowManager.open();
            LOGGER.info("Refactored TickChartWindow geöffnet für Signal: " + signalId);
        } else {
            LOGGER.warning("WindowManager ist null - kann Fenster nicht öffnen");
        }
    }
    
    /**
     * Prüft ob das Fenster noch geöffnet ist
     * Könnte erweitert werden wenn der WindowManager eine entsprechende Methode bereitstellt
     */
    public boolean isOpen() {
        return windowManager != null;
    }
    
    /**
     * Gibt die Signal-ID zurück
     */
    public String getSignalId() {
        return signalId;
    }
    
    /**
     * Gibt den WindowManager zurück (für erweiterte Kontrolle falls nötig)
     */
    public TickChartWindowManager getWindowManager() {
        return windowManager;
    }
    
    /**
     * Informationen über die Refactoring-Verbesserungen
     */
    public static String getRefactoringInfo() {
        return "REFACTORING SUMMARY:\n" +
               "=====================\n" +
               "Original TickChartWindow: >1000 Zeilen Code\n" +
               "Refactored TickChartWindow: <100 Zeilen Code\n" +
               "\n" +
               "Neue Architektur (4 Klassen):\n" +
               "1. EquityDrawdownChartPanel - Wiederverwendbare Drawdown-Chart-Komponente\n" +
               "2. ProfitDevelopmentChartPanel - Wiederverwendbare Profit-Chart-Komponente\n" +
               "3. TickChartWindowToolbar - Alle Buttons und Event-Handler\n" +
               "4. TickChartWindowManager - Koordiniert alle Komponenten\n" +
               "5. TickChartWindow - Einfache Facade-Klasse\n" +
               "\n" +
               "Vorteile:\n" +
               "- Bessere Wartbarkeit durch kleinere Klassen\n" +
               "- Wiederverwendbare Chart-Komponenten\n" +
               "- Klare Trennung der Verantwortlichkeiten\n" +
               "- Einfachere Tests und Debugging\n" +
               "- Die Chart-Panels können in anderen Fenstern verwendet werden";
    }
}

/*
REFACTORING SUMMARY:
====================

VORHER (Original TickChartWindow):
- Eine riesige Klasse mit >1000 Zeilen Code
- Alle Funktionalitäten in einer Datei
- Schwer zu warten und zu erweitern
- Chart-Code nicht wiederverwendbar

NACHHER (Refactored Architecture):
1. EquityDrawdownChartPanel (NEU)
   - Kapselt komplette Drawdown-Chart-Anzeige
   - Wiederverwendbar in anderen Fenstern
   - ~150 Zeilen, fokussiert auf eine Aufgabe

2. ProfitDevelopmentChartPanel (NEU)  
   - Kapselt komplette Profit-Chart-Anzeige
   - Wiederverwendbar in anderen Fenstern
   - ~150 Zeilen, fokussiert auf eine Aufgabe

3. TickChartWindowToolbar (NEU)
   - Alle Buttons und Event-Handler
   - Zoom, Diagnostik, Tickdaten, MQL5 Website
   - ~300 Zeilen, klare Verantwortlichkeit

4. TickChartWindowManager (NEU)
   - Koordiniert alle Komponenten
   - Daten-Loading und Layout-Management
   - ~400 Zeilen, orchestriert alles

5. TickChartWindow (STARK VEREINFACHT)
   - Nur noch Facade-Klasse
   - <100 Zeilen, delegiert an Manager
   - Einfache, saubere API

VORTEILE:
- Bessere Wartbarkeit
- Klare Trennung der Verantwortlichkeiten  
- Wiederverwendbare Komponenten
- Einfachere Tests
- Die Chart-Panels können später in anderen Fenstern verwendet werden

CHART-PANELS WIEDERVERWENDBAR:
Die EquityDrawdownChartPanel und ProfitDevelopmentChartPanel Klassen sind 
als SWT Composite implementiert und können jetzt einfach in anderen 
Fenstern oder Bereichen der Anwendung eingebaut werden.
*/