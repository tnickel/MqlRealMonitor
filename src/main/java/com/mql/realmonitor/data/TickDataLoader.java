package com.mql.realmonitor.data;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Lädt und parst Tick-Daten aus den CSV-Dateien
 * ERWEITERT: Format: Datum,Uhrzeit,Equity,FloatingProfit[,Profit]
 */
public class TickDataLoader {
    
    private static final Logger LOGGER = Logger.getLogger(TickDataLoader.class.getName());
    
    // Datum/Zeit-Formatter für das MQL5-Format
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    
    /**
     * ERWEITERT: Datenklasse für einen einzelnen Tick mit Profit
     */
    public static class TickData {
        private final LocalDateTime timestamp;
        private final double equity;
        private final double floatingProfit;
        private final double profit; // NEU
        private final double totalValue;
        
        public TickData(LocalDateTime timestamp, double equity, double floatingProfit, double profit) {
            this.timestamp = timestamp;
            this.equity = equity;
            this.floatingProfit = floatingProfit;
            this.profit = profit;
            this.totalValue = equity + floatingProfit;
        }
        
        // Konstruktor für Abwärtskompatibilität (ohne Profit)
        public TickData(LocalDateTime timestamp, double equity, double floatingProfit) {
            this(timestamp, equity, floatingProfit, 0.0);
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getEquity() { return equity; }
        public double getFloatingProfit() { return floatingProfit; }
        public double getProfit() { return profit; } // NEU
        public double getTotalValue() { return totalValue; }
        
        @Override
        public String toString() {
            return String.format("TickData{%s, Equity=%.2f, Floating=%.2f, Profit=%.2f, Total=%.2f}", 
                               timestamp, equity, floatingProfit, profit, totalValue);
        }
    }
    
    /**
     * Container für alle Tick-Daten eines Signals
     */
    public static class TickDataSet {
        private final String signalId;
        private final List<TickData> ticks;
        private final LocalDateTime createdDate;
        private final String filePath;
        
        public TickDataSet(String signalId, String filePath, LocalDateTime createdDate) {
            this.signalId = signalId;
            this.filePath = filePath;
            this.createdDate = createdDate;
            this.ticks = new ArrayList<>();
        }
        
        public void addTick(TickData tick) {
            ticks.add(tick);
        }
        
        public String getSignalId() { return signalId; }
        public List<TickData> getTicks() { return Collections.unmodifiableList(ticks); }
        public LocalDateTime getCreatedDate() { return createdDate; }
        public String getFilePath() { return filePath; }
        public int getTickCount() { return ticks.size(); }
        
        public TickData getLatestTick() {
            return ticks.isEmpty() ? null : ticks.get(ticks.size() - 1);
        }
        
        public TickData getFirstTick() {
            return ticks.isEmpty() ? null : ticks.get(0);
        }
        
        public double getMaxEquity() {
            return ticks.stream().mapToDouble(TickData::getEquity).max().orElse(0.0);
        }
        
        public double getMinEquity() {
            return ticks.stream().mapToDouble(TickData::getEquity).min().orElse(0.0);
        }
        
        public double getMaxFloatingProfit() {
            return ticks.stream().mapToDouble(TickData::getFloatingProfit).max().orElse(0.0);
        }
        
        public double getMinFloatingProfit() {
            return ticks.stream().mapToDouble(TickData::getFloatingProfit).min().orElse(0.0);
        }
        
        // NEU: Profit Min/Max Methoden
        public double getMaxProfit() {
            return ticks.stream().mapToDouble(TickData::getProfit).max().orElse(0.0);
        }
        
        public double getMinProfit() {
            return ticks.stream().mapToDouble(TickData::getProfit).min().orElse(0.0);
        }
        
        public double getMaxTotalValue() {
            return ticks.stream().mapToDouble(TickData::getTotalValue).max().orElse(0.0);
        }
        
        public double getMinTotalValue() {
            return ticks.stream().mapToDouble(TickData::getTotalValue).min().orElse(0.0);
        }
        
        @Override
        public String toString() {
            return String.format("TickDataSet{Signal=%s, Ticks=%d, Created=%s}", 
                               signalId, ticks.size(), createdDate);
        }
    }
    
    /**
     * Lädt Tick-Daten aus einer Datei
     * 
     * @param filePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return TickDataSet mit allen geladenen Daten oder null bei Fehlern
     */
    public static TickDataSet loadTickData(String filePath, String signalId) {
        File file = new File(filePath);
        
        if (!file.exists()) {
            LOGGER.warning("Tick-Datei nicht gefunden: " + filePath);
            return null;
        }
        
        if (!file.canRead()) {
            LOGGER.warning("Tick-Datei nicht lesbar: " + filePath);
            return null;
        }
        
        LOGGER.info("Lade Tick-Daten für Signal " + signalId + " aus: " + filePath);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseTickFile(reader, signalId, filePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Lesen der Tick-Datei: " + filePath, e);
            return null;
        }
    }
    
    /**
     * Parst eine Tick-Datei
     * 
     * @param reader Der BufferedReader für die Datei
     * @param signalId Die Signal-ID
     * @param filePath Der Dateipfad für Logging
     * @return TickDataSet mit geparsten Daten
     */
    private static TickDataSet parseTickFile(BufferedReader reader, String signalId, String filePath) throws IOException {
        String line;
        int lineNumber = 0;
        LocalDateTime createdDate = null;
        TickDataSet dataSet = null;
        
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            
            // Leerzeilen überspringen
            if (line.isEmpty()) {
                continue;
            }
            
            // Kommentarzeilen verarbeiten
            if (line.startsWith("#")) {
                if (line.contains("Created:")) {
                    createdDate = parseCreatedDate(line);
                }
                continue;
            }
            
            // Beim ersten Daten-Eintrag das DataSet initialisieren
            if (dataSet == null) {
                dataSet = new TickDataSet(signalId, filePath, createdDate);
            }
            
            // Tick-Daten parsen
            TickData tick = parseTickLine(line, lineNumber);
            if (tick != null) {
                dataSet.addTick(tick);
            }
        }
        
        if (dataSet == null) {
            LOGGER.warning("Keine gültigen Tick-Daten in Datei gefunden: " + filePath);
            return null;
        }
        
        LOGGER.info("Tick-Daten geladen: " + dataSet.getTickCount() + " Einträge für Signal " + signalId);
        return dataSet;
    }
    
    /**
     * Parst das Created-Datum aus einer Kommentarzeile
     * 
     * @param commentLine Die Kommentarzeile
     * @return Das geparste Datum oder null
     */
    private static LocalDateTime parseCreatedDate(String commentLine) {
        try {
            // Format: "# Created: 2025-05-24 15:13:36"
            String dateTimeStr = commentLine.substring(commentLine.indexOf("Created:") + 8).trim();
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            LOGGER.warning("Konnte Created-Datum nicht parsen: " + commentLine);
            return null;
        }
    }
    
    /**
     * ERWEITERT: Parst eine einzelne Tick-Zeile mit Profit-Unterstützung
     * Format: 24.05.2025,15:13:36,2000.00,-479.54[,179.29]
     * Oder fehlerhaftes Format: 24.05.2025,15:13:36,53745,30,0,00 (6 Teile)
     * 
     * @param line Die zu parsende Zeile
     * @param lineNumber Die Zeilennummer für Fehlerbehandlung
     * @return TickData-Objekt oder null bei Fehlern
     */
    private static TickData parseTickLine(String line, int lineNumber) {
        try {
            String[] parts = line.split(",");
            
            // Flexibles Parsing für verschiedene Formate
            if (parts.length == 4) {
                // Altes Format ohne Profit: Datum,Zeit,Equity,FloatingProfit
                String dateStr = parts[0].trim();
                String timeStr = parts[1].trim();
                LocalDateTime timestamp = LocalDateTime.parse(dateStr + " " + timeStr, DATETIME_FORMATTER);
                
                // Equity und Floating Profit parsen
                double equity = parseNumber(parts[2].trim());
                double floatingProfit = parseNumber(parts[3].trim());
                
                return new TickData(timestamp, equity, floatingProfit, 0.0); // Profit = 0.0
                
            } else if (parts.length == 5) {
                // NEUES Format mit Profit: Datum,Zeit,Equity,FloatingProfit,Profit
                String dateStr = parts[0].trim();
                String timeStr = parts[1].trim();
                LocalDateTime timestamp = LocalDateTime.parse(dateStr + " " + timeStr, DATETIME_FORMATTER);
                
                // Equity, Floating Profit und Profit parsen
                double equity = parseNumber(parts[2].trim());
                double floatingProfit = parseNumber(parts[3].trim());
                double profit = parseNumber(parts[4].trim());
                
                return new TickData(timestamp, equity, floatingProfit, profit);
                
            } else if (parts.length == 6) {
                // Fehlerhaftes Format mit Tausendertrennzeichen: 
                // 24.05.2025,15:22:13,53745,30,0,00
                String dateStr = parts[0].trim();
                String timeStr = parts[1].trim();
                LocalDateTime timestamp = LocalDateTime.parse(dateStr + " " + timeStr, DATETIME_FORMATTER);
                
                // Equity zusammensetzen (parts[2] + "." + parts[3])
                String equityStr = parts[2].trim() + "." + parts[3].trim();
                double equity = parseNumber(equityStr);
                
                // Floating Profit zusammensetzen (parts[4] + "." + parts[5])
                String floatingStr = parts[4].trim() + "." + parts[5].trim();
                double floatingProfit = parseNumber(floatingStr);
                
                LOGGER.fine("Konvertiere fehlerhaftes Format in Zeile " + lineNumber + 
                           ": Equity=" + equity + ", Floating=" + floatingProfit);
                
                return new TickData(timestamp, equity, floatingProfit, 0.0); // Profit = 0.0
                
            } else if (parts.length >= 7) {
                // NEUES fehlerhaftes Format mit Profit und Tausendertrennzeichen:
                // 24.05.2025,15:22:13,53745,30,0,00,179,29
                String dateStr = parts[0].trim();
                String timeStr = parts[1].trim();
                LocalDateTime timestamp = LocalDateTime.parse(dateStr + " " + timeStr, DATETIME_FORMATTER);
                
                // Equity zusammensetzen (parts[2] + "." + parts[3])
                String equityStr = parts[2].trim() + "." + parts[3].trim();
                double equity = parseNumber(equityStr);
                
                // Floating Profit zusammensetzen (parts[4] + "." + parts[5])
                String floatingStr = parts[4].trim() + "." + parts[5].trim();
                double floatingProfit = parseNumber(floatingStr);
                
                // Profit parsen
                double profit = 0.0;
                if (parts.length == 7) {
                    // Profit ist eine ganze Zahl
                    profit = parseNumber(parts[6].trim());
                } else if (parts.length >= 8) {
                    // Profit ist aufgeteilt (parts[6] + "." + parts[7])
                    String profitStr = parts[6].trim() + "." + parts[7].trim();
                    profit = parseNumber(profitStr);
                }
                
                LOGGER.fine("Konvertiere fehlerhaftes erweitertes Format in Zeile " + lineNumber + 
                           ": Equity=" + equity + ", Floating=" + floatingProfit + ", Profit=" + profit);
                
                return new TickData(timestamp, equity, floatingProfit, profit);
                
            } else {
                LOGGER.warning("Ungültiges Tick-Format in Zeile " + lineNumber + 
                              ": " + line + " (erwartet 4, 5, 6 oder 7+ Teile, gefunden: " + parts.length + ")");
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Parsen von Zeile " + lineNumber + ": " + line + " -> " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Hilfsmethode zum robusten Parsen von Zahlen
     * 
     * @param numberStr Der zu parsende String
     * @return Die geparste Zahl
     */
    private static double parseNumber(String numberStr) throws NumberFormatException {
        if (numberStr == null || numberStr.trim().isEmpty()) {
            throw new NumberFormatException("Leerer String");
        }
        
        // Entferne alle Leerzeichen und ersetze Komma durch Punkt
        String cleaned = numberStr.trim()
                                 .replaceAll("\\s+", "")
                                 .replace(",", ".");
        
        return Double.parseDouble(cleaned);
    }
    
    /**
     * Lädt die neuesten N Ticks aus einer Datei
     * 
     * @param filePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @param maxTicks Maximale Anzahl der zu ladenden Ticks (von hinten)
     * @return TickDataSet mit den neuesten Ticks
     */
    public static TickDataSet loadLatestTicks(String filePath, String signalId, int maxTicks) {
        TickDataSet fullDataSet = loadTickData(filePath, signalId);
        
        if (fullDataSet == null || fullDataSet.getTickCount() <= maxTicks) {
            return fullDataSet;
        }
        
        // Neue TickDataSet mit nur den neuesten Ticks erstellen
        TickDataSet limitedDataSet = new TickDataSet(signalId, filePath, fullDataSet.getCreatedDate());
        List<TickData> allTicks = fullDataSet.getTicks();
        
        int startIndex = Math.max(0, allTicks.size() - maxTicks);
        for (int i = startIndex; i < allTicks.size(); i++) {
            limitedDataSet.addTick(allTicks.get(i));
        }
        
        LOGGER.info("Neueste " + limitedDataSet.getTickCount() + " von " + fullDataSet.getTickCount() + " Ticks geladen");
        return limitedDataSet;
    }
    
    /**
     * Lädt Tick-Daten für einen bestimmten Zeitraum
     * 
     * @param filePath Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @param fromDate Startdatum (inklusive)
     * @param toDate Enddatum (inklusive)
     * @return TickDataSet mit gefilterten Daten
     */
    public static TickDataSet loadTickDataForPeriod(String filePath, String signalId, 
                                                   LocalDateTime fromDate, LocalDateTime toDate) {
        TickDataSet fullDataSet = loadTickData(filePath, signalId);
        
        if (fullDataSet == null) {
            return null;
        }
        
        TickDataSet filteredDataSet = new TickDataSet(signalId, filePath, fullDataSet.getCreatedDate());
        
        for (TickData tick : fullDataSet.getTicks()) {
            LocalDateTime tickTime = tick.getTimestamp();
            if (!tickTime.isBefore(fromDate) && !tickTime.isAfter(toDate)) {
                filteredDataSet.addTick(tick);
            }
        }
        
        LOGGER.info("Tick-Daten für Zeitraum " + fromDate + " bis " + toDate + ": " + 
                   filteredDataSet.getTickCount() + " von " + fullDataSet.getTickCount() + " Ticks");
        
        return filteredDataSet;
    }
    
    /**
     * ERWEITERT: Erstellt eine Zusammenfassung der Tick-Daten mit Profit
     * 
     * @param dataSet Das TickDataSet
     * @return Zusammenfassung als String
     */
    public static String createSummary(TickDataSet dataSet) {
        if (dataSet == null || dataSet.getTickCount() == 0) {
            return "Keine Tick-Daten verfügbar";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("=== Tick-Daten Zusammenfassung ===\n");
        summary.append("Signal ID: ").append(dataSet.getSignalId()).append("\n");
        summary.append("Datei: ").append(dataSet.getFilePath()).append("\n");
        summary.append("Erstellt: ").append(dataSet.getCreatedDate()).append("\n");
        summary.append("Anzahl Ticks: ").append(dataSet.getTickCount()).append("\n");
        
        if (dataSet.getTickCount() > 0) {
            summary.append("Zeitraum: ").append(dataSet.getFirstTick().getTimestamp())
                   .append(" bis ").append(dataSet.getLatestTick().getTimestamp()).append("\n");
            summary.append("Equity: ").append(String.format("%.2f - %.2f", 
                         dataSet.getMinEquity(), dataSet.getMaxEquity())).append("\n");
            summary.append("Floating Profit: ").append(String.format("%.2f - %.2f", 
                         dataSet.getMinFloatingProfit(), dataSet.getMaxFloatingProfit())).append("\n");
            
            // NEU: Profit-Zusammenfassung
            double minProfit = dataSet.getMinProfit();
            double maxProfit = dataSet.getMaxProfit();
            if (minProfit != 0.0 || maxProfit != 0.0) {
                summary.append("Profit: ").append(String.format("%.2f - %.2f", 
                             minProfit, maxProfit)).append("\n");
            }
            
            summary.append("Gesamtwert: ").append(String.format("%.2f - %.2f", 
                         dataSet.getMinTotalValue(), dataSet.getMaxTotalValue())).append("\n");
        }
        
        return summary.toString();
    }
}