package com.mql.realmonitor.gui;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

/**
 * NEU (Fix 25.09.2026): Wählt das Datumsformat der X-Achse nach der Länge
 * des TATSÄCHLICH sichtbaren Zeitraums.
 *
 * Hintergrund: Die Charts weiten die X-Achse nach dem Kalibrieren auf das
 * Tick-Fenster zusätzlich auf die (oft monatelange) MQL5-Trade-Historie
 * aus. Das vorher dort stehende Kurzzeit-Format ("HH:mm") erzeugte dann
 * über Monate gerechte Tagesgrenzen-Ticks überall "00:00". Die Regel ist
 * jetzt: je größer der Bereich, desto mehr Datum, desto weniger Uhrzeit.
 */
public final class ChartDateAxisFormat {

    private ChartDateAxisFormat() {
    }

    /**
     * Passendes Format für einen sichtbaren Zeitraum in Millisekunden.
     * Bis 4 h nur Uhrzeit, bis 48 h Datum+Uhrzeit, darüber nur Datum
     * (mit Jahr, damit mehrmonatige Historien eindeutig bleiben).
     */
    public static SimpleDateFormat formatForBereich(long bereichMillis) {
        long stunden = bereichMillis / 3_600_000L;
        String pattern;
        if (stunden < 4) {
            pattern = "HH:mm:ss";
        } else if (stunden < 48) {
            pattern = "dd.MM HH:mm";
        } else {
            pattern = "dd.MM.yy";
        }
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setTimeZone(TimeZone.getDefault());
        return format;
    }

    /**
     * Passendes Format für die Spanne zwischen zwei Datenpunkten
     * (z. B. erster bis letzter Tick der angezeigten Daten).
     */
    public static SimpleDateFormat formatFuerSpanne(LocalDateTime von, LocalDateTime bis) {
        if (von == null || bis == null) {
            return formatForBereich(48 * 3_600_000L);
        }
        long millis = Math.max(1, von.until(bis, ChronoUnit.MILLIS));
        return formatForBereich(millis);
    }
}
