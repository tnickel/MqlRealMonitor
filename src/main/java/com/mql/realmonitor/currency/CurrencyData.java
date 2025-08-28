package com.mql.realmonitor.currency;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Model-Klasse für Währungskursdaten.
 * Speichert Symbol, Kurs und Zeitstempel für XAUUSD/BTCUSD Daten.
 * KORRIGIERT: Locale-unabhängige CSV-Formatierung mit Punkt als Dezimaltrennzeichen.
 */
public class CurrencyData {
    
    private String symbol;
    private double price;
    private LocalDateTime timestamp;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Konstruktor für CurrencyData.
     * 
     * @param symbol Das Währungssymbol (z.B. "XAUUSD", "BTCUSD")
     * @param price Der aktuelle Kurs
     * @param timestamp Zeitstempel der Kursdaten
     */
    public CurrencyData(String symbol, double price, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.timestamp = timestamp;
    }
    
    /**
     * Standard-Konstruktor mit aktuellem Zeitstempel.
     * 
     * @param symbol Das Währungssymbol
     * @param price Der aktuelle Kurs
     */
    public CurrencyData(String symbol, double price) {
        this(symbol, price, LocalDateTime.now());
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Gibt den Zeitstempel als formatierten String zurück.
     * 
     * @return Formatierter Zeitstempel (yyyy-MM-dd HH:mm:ss)
     */
    public String getFormattedTimestamp() {
        return timestamp.format(FORMATTER);
    }
    
    /**
     * Konvertiert die Kursdaten zu einer CSV-Zeile.
     * Format: timestamp,symbol,price
     * KORRIGIERT: Verwendet IMMER Punkt als Dezimaltrennzeichen (Locale.US)
     * 
     * @return CSV-formatierte Zeile
     */
    public String toCsvLine() {
        // WICHTIG: Verwende Locale.US um IMMER Punkt als Dezimaltrennzeichen zu garantieren
        return String.format(Locale.US, "%s,%s,%.5f", 
            getFormattedTimestamp(), 
            symbol, 
            price);
    }
    
    /**
     * Erstellt CurrencyData aus einer CSV-Zeile.
     * ERWEITERT: Unterstützt sowohl Punkt als auch Komma als Dezimaltrennzeichen beim Lesen.
     * 
     * @param csvLine CSV-Zeile im Format "timestamp,symbol,price"
     * @return CurrencyData Objekt oder null bei Fehlern
     */
    public static CurrencyData fromCsvLine(String csvLine) {
        if (csvLine == null || csvLine.trim().isEmpty()) {
            return null;
        }
        
        try {
            String[] parts = csvLine.split(",");
            
            // ERWEITERT: Handle auch fehlerhafte CSV-Zeilen mit zu vielen Kommas
            if (parts.length < 3) {
                System.err.println("CSV-Zeile hat zu wenige Spalten: " + csvLine);
                return null;
            }
            
            LocalDateTime timestamp = LocalDateTime.parse(parts[0].trim(), FORMATTER);
            String symbol = parts[1].trim();
            
            // KORRIGIERT: Handle fehlerhafte CSV mit separatem Dezimalteil
            String priceString;
            if (parts.length == 4) {
                // Fehlerhafte CSV: "timestamp,symbol,ganzer_teil,dezimal_teil"
                // Beispiel: "2025-08-28 10:48:18,XAUUSD,3397,38000"
                String wholePart = parts[2].trim();
                String decimalPart = parts[3].trim();
                
                // Kombiniere zu korrekter Dezimalzahl
                priceString = wholePart + "." + decimalPart;
                System.out.println("Korrigiere fehlerhafte CSV-Zeile: " + csvLine + " → " + priceString);
                
            } else if (parts.length == 3) {
                // Normale CSV: "timestamp,symbol,price"
                priceString = parts[2].trim();
                
                // Normalisiere Dezimaltrennzeichen (Komma zu Punkt)
                if (priceString.contains(",")) {
                    priceString = priceString.replace(",", ".");
                }
            } else {
                System.err.println("CSV-Zeile hat unerwartete Anzahl Spalten (" + parts.length + "): " + csvLine);
                return null;
            }
            
            double price = Double.parseDouble(priceString);
            
            return new CurrencyData(symbol, price, timestamp);
            
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der CSV-Zeile: " + csvLine + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * NEU: Korrigiert eine fehlerhafte CSV-Zeile zu korrektem Format.
     * Beispiel: "2025-08-28 10:48:18,XAUUSD,3397,38000" → "2025-08-28 10:48:18,XAUUSD,3397.38000"
     * 
     * @param faultyCsvLine Fehlerhafte CSV-Zeile
     * @return Korrigierte CSV-Zeile oder null bei Fehlern
     */
    public static String correctFaultyCsvLine(String faultyCsvLine) {
        try {
            CurrencyData data = fromCsvLine(faultyCsvLine);
            if (data != null) {
                return data.toCsvLine();
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Korrigieren der CSV-Zeile: " + faultyCsvLine);
        }
        return null;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.US, "CurrencyData{symbol='%s', price=%.5f, timestamp='%s'}", 
            symbol, price, getFormattedTimestamp());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CurrencyData that = (CurrencyData) obj;
        return Double.compare(that.price, price) == 0 &&
               symbol.equals(that.symbol) &&
               timestamp.equals(that.timestamp);
    }
    
    @Override
    public int hashCode() {
        int result = symbol.hashCode();
        result = 31 * result + Double.hashCode(price);
        result = 31 * result + timestamp.hashCode();
        return result;
    }
}