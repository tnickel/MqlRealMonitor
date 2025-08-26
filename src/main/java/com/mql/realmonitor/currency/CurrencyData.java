package com.mql.realmonitor.currency;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model-Klasse für Währungskursdaten.
 * Speichert Symbol, Kurs und Zeitstempel für XAUUSD/BTCUSD Daten.
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
     * 
     * @return CSV-formatierte Zeile
     */
    public String toCsvLine() {
        return String.format("%s,%s,%.5f", 
            getFormattedTimestamp(), 
            symbol, 
            price);
    }
    
    /**
     * Erstellt CurrencyData aus einer CSV-Zeile.
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
            if (parts.length != 3) {
                return null;
            }
            
            LocalDateTime timestamp = LocalDateTime.parse(parts[0].trim(), FORMATTER);
            String symbol = parts[1].trim();
            double price = Double.parseDouble(parts[2].trim());
            
            return new CurrencyData(symbol, price, timestamp);
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen der CSV-Zeile: " + csvLine + " - " + e.getMessage());
            return null;
        }
    }
    
    @Override
    public String toString() {
        return String.format("CurrencyData{symbol='%s', price=%.5f, timestamp='%s'}", 
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