package com.mql.realmonitor.tickdata;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.parser.SignalData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * VERBESSERT: Writer für Tick-Daten mit robustem Lesen für Chart-Diagnostik
 * Verwaltet das Schreiben von Signaldaten in Tick-Dateien
 * ALLE LESE-PROBLEME BEHOBEN: Umfassende Diagnostik und Fehlerbehandlung
 */
public class TickDataWriter {
    
    private static final Logger LOGGER = Logger.getLogger(TickDataWriter.class.getName());
    
    private final MqlRealMonitorConfig config;
    
    public TickDataWriter(MqlRealMonitorConfig config) {
        this.config = config;
    }
    
    /**
     * Schreibt Signaldaten in die entsprechende Tick-Datei
     * 
     * @param signalData Die zu schreibenden Signaldaten
     * @return true wenn erfolgreich geschrieben, false bei Fehlern
     */
    public boolean writeTickData(SignalData signalData) {
        if (signalData == null || !signalData.isValid()) {
            LOGGER.warning("Ungültige Signaldaten - kann nicht schreiben");
            return false;
        }
        
        String tickFilePath = config.getTickFilePath(signalData.getSignalId());
        
        try {
            LOGGER.fine("Schreibe Tick-Daten für Signal " + signalData.getSignalId() + " nach " + tickFilePath);
            
            // Verzeichnis erstellen falls nicht vorhanden
            ensureTickDirectoryExists();
            
            // Prüfen ob Daten bereits existieren (Duplikat-Vermeidung)
            if (shouldSkipDuplicateData(signalData, tickFilePath)) {
                LOGGER.fine("Überspringe Duplikat-Daten für Signal " + signalData.getSignalId());
                return true;
            }
            
            // Tick-Datei-Eintrag erstellen
            String tickEntry = signalData.toFullTickFileEntry();
            
            // An Datei anhängen (niemals überschreiben)
            appendToTickFile(tickFilePath, tickEntry);
            
            LOGGER.info("Tick-Daten erfolgreich geschrieben: " + signalData.getSummary());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Schreiben der Tick-Daten für Signal " + 
                      signalData.getSignalId(), e);
            return false;
        }
    }
    
    /**
     * Schreibt mehrere Signaldaten in einem Batch
     * 
     * @param signalDataList Liste der zu schreibenden Signaldaten
     * @return Anzahl erfolgreich geschriebener Einträge
     */
    public int writeTickDataBatch(List<SignalData> signalDataList) {
        if (signalDataList == null || signalDataList.isEmpty()) {
            LOGGER.warning("Leere Signal-Daten Liste");
            return 0;
        }
        
        int successCount = 0;
        
        for (SignalData signalData : signalDataList) {
            if (writeTickData(signalData)) {
                successCount++;
            }
        }
        
        LOGGER.info("Batch-Schreibvorgang abgeschlossen: " + successCount + "/" + 
                   signalDataList.size() + " erfolgreich");
        
        return successCount;
    }
    
    /**
     * Stellt sicher, dass das Tick-Verzeichnis existiert
     */
    private void ensureTickDirectoryExists() throws IOException {
        Path tickDir = Paths.get(config.getTickDir());
        
        if (!Files.exists(tickDir)) {
            Files.createDirectories(tickDir);
            LOGGER.info("Tick-Verzeichnis erstellt: " + tickDir);
        }
    }
    
    /**
     * Hängt einen Eintrag an eine Tick-Datei an
     * 
     * @param tickFilePath Der Pfad zur Tick-Datei
     * @param tickEntry Der anzuhängende Eintrag
     */
    private void appendToTickFile(String tickFilePath, String tickEntry) throws IOException {
        Path filePath = Paths.get(tickFilePath);
        
        // Header schreiben falls Datei neu ist
        if (!Files.exists(filePath)) {
            writeTickFileHeader(filePath);
        }
        
        // Eintrag anhängen
        try (FileWriter writer = new FileWriter(tickFilePath, StandardCharsets.UTF_8, true)) {
            writer.write(tickEntry);
            writer.write(System.lineSeparator());
        }
        
        LOGGER.fine("Tick-Eintrag angehängt: " + tickFilePath);
    }
    
    /**
     * Schreibt den Header für eine neue Tick-Datei
     * 
     * @param filePath Der Pfad zur Tick-Datei
     */
    private void writeTickFileHeader(Path filePath) throws IOException {
        String header = "# MQL5 Signal Tick Data - Format: Datum,Uhrzeit,Equity,FloatingProfit,Profit" + 
                       System.lineSeparator() +
                       "# Signal ID: " + extractSignalIdFromFilePath(filePath.toString()) + 
                       System.lineSeparator() +
                       "# Created: " + java.time.LocalDateTime.now().format(SignalData.TIMESTAMP_FORMATTER) + 
                       System.lineSeparator();
        
        Files.writeString(filePath, header, StandardCharsets.UTF_8, 
                         StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        
        LOGGER.info("Tick-Datei Header geschrieben (mit Profit-Feld): " + filePath);
    }
    
    /**
     * Extrahiert die Signal-ID aus dem Dateipfad
     * 
     * @param filePath Der Dateipfad
     * @return Die Signal-ID
     */
    private String extractSignalIdFromFilePath(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();
            
            // Entferne .txt Endung
            if (fileName.endsWith(".txt")) {
                return fileName.substring(0, fileName.length() - 4);
            }
            
            return fileName;
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Prüft ob Daten bereits existieren und übersprungen werden sollten
     * 
     * @param signalData Die neuen Signaldaten
     * @param tickFilePath Der Pfad zur Tick-Datei
     * @return true wenn Daten übersprungen werden sollten
     */
    private boolean shouldSkipDuplicateData(SignalData signalData, String tickFilePath) {
        try {
            Path filePath = Paths.get(tickFilePath);
            
            if (!Files.exists(filePath)) {
                return false; // Neue Datei - nicht überspringen
            }
            
            // Letzte Zeile der Datei lesen
            SignalData lastEntry = readLastTickEntry(tickFilePath, signalData.getSignalId());
            
            if (lastEntry == null) {
                return false; // Kann letzte Zeile nicht lesen - sicherheitshalber schreiben
            }
            
            // Prüfe ob Werte sich geändert haben
            boolean valuesChanged = signalData.hasValuesChanged(lastEntry);
            
            if (!valuesChanged) {
                // Zusätzlich Zeit-Check: Mindestens 1 Minute zwischen identischen Einträgen
                long timeDiffMinutes = java.time.Duration.between(
                    lastEntry.getTimestamp(), signalData.getTimestamp()).toMinutes();
                
                if (timeDiffMinutes < 1) {
                    return true; // Überspringen - zu kurzer Abstand
                }
            }
            
            return false; // Schreiben - Werte haben sich geändert oder genug Zeit vergangen
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler bei Duplikat-Prüfung für " + tickFilePath, e);
            return false; // Bei Fehlern sicherheitshalber schreiben
        }
    }
    
    /**
     * KOMPLETT NEU GESCHRIEBEN: Liest den letzten Tick-Eintrag aus einer Datei
     * ALLE PROBLEME BEHOBEN: Robuste Fehlerbehandlung und umfassende Diagnostik
     * 
     * @param tickFilePath Der Pfad zur Tick-Datei
     * @param signalId Die Signal-ID
     * @return Der letzte SignalData-Eintrag oder null
     */
    public SignalData readLastTickEntry(String tickFilePath, String signalId) {
        LOGGER.info("=== LESE LETZTEN TICK-EINTRAG für Signal: " + signalId + " ===");
        LOGGER.info("Datei-Pfad: " + tickFilePath);
        
        try {
            Path filePath = Paths.get(tickFilePath);
            
            if (!Files.exists(filePath)) {
                LOGGER.warning("Tick-Datei existiert nicht: " + tickFilePath);
                return null;
            }
            
            long fileSize = Files.size(filePath);
            LOGGER.info("Datei-Größe: " + fileSize + " Bytes");
            
            if (fileSize == 0) {
                LOGGER.warning("Tick-Datei ist leer: " + tickFilePath);
                return null;
            }
            
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            LOGGER.info("Gesamte Zeilen in Datei: " + lines.size());
            
            if (lines.isEmpty()) {
                LOGGER.warning("Keine Zeilen in Tick-Datei gefunden: " + tickFilePath);
                return null;
            }
            
            // ERWEITERTE DIAGNOSTIK: Zeige erste und letzte paar Zeilen
            LOGGER.info("=== DATEI-INHALT ANALYSE ===");
            LOGGER.info("Erste 3 Zeilen:");
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                LOGGER.info("  Zeile " + (i+1) + ": " + lines.get(i));
            }
            
            if (lines.size() > 6) {
                LOGGER.info("...");
            }
            
            LOGGER.info("Letzte 3 Zeilen:");
            for (int i = Math.max(0, lines.size() - 3); i < lines.size(); i++) {
                LOGGER.info("  Zeile " + (i+1) + ": " + lines.get(i));
            }
            
            // Von hinten nach vorne durch die Zeilen gehen
            int dataLineCount = 0;
            SignalData lastValidEntry = null;
            
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                
                LOGGER.fine("Prüfe Zeile " + (i+1) + ": '" + line + "'");
                
                // Kommentare und leere Zeilen überspringen
                if (line.isEmpty() || line.startsWith("#")) {
                    LOGGER.fine("Überspringe Kommentar/Leer-Zeile " + (i+1));
                    continue;
                }
                
                dataLineCount++;
                LOGGER.info("Verarbeite Daten-Zeile #" + dataLineCount + " (Zeile " + (i+1) + "): " + line);
                
                // Versuche Zeile zu parsen
                SignalData signalData = parseTickLineRobust(signalId, line, i+1);
                
                if (signalData != null) {
                    LOGGER.info("ERFOLG: Letzter Tick-Eintrag gefunden und geparst!");
                    LOGGER.info("Ergebnis: " + signalData.getSummary());
                    LOGGER.info("Diagnostik: " + signalData.getDiagnosticSummary());
                    lastValidEntry = signalData;
                    break;
                } else {
                    LOGGER.warning("FEHLER: Konnte Zeile " + (i+1) + " nicht parsen: " + line);
                    // Weiter versuchen mit vorherigen Zeilen
                }
            }
            
            if (lastValidEntry == null) {
                LOGGER.warning("KEIN GÜLTIGER EINTRAG GEFUNDEN in " + dataLineCount + " Daten-Zeilen");
            }
            
            LOGGER.info("=== LESE LETZTEN TICK-EINTRAG ENDE ===");
            return lastValidEntry;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATALER FEHLER beim Lesen des letzten Tick-Eintrags: " + tickFilePath, e);
            return null;
        }
    }
    
    /**
     * NEU: Robuste Parsing-Methode für Tick-Zeilen mit umfassender Diagnostik
     * 
     * @param signalId Die Signal-ID
     * @param line Die zu parsende Zeile
     * @param lineNumber Die Zeilennummer für Logging
     * @return SignalData oder null bei Fehlern
     */
    private SignalData parseTickLineRobust(String signalId, String line, int lineNumber) {
        LOGGER.info("=== PARSE TICK-ZEILE #" + lineNumber + " ===");
        LOGGER.info("Input: '" + line + "'");
        
        if (line == null || line.trim().isEmpty()) {
            LOGGER.warning("Zeile ist leer oder null");
            return null;
        }
        
        try {
            String[] parts = line.split(",");
            LOGGER.info("Aufgeteilte Teile: " + parts.length + " -> " + java.util.Arrays.toString(parts));
            
            // Verschiedene Formate unterstützen
            if (parts.length == 4) {
                // Altes Format ohne Profit: Datum,Zeit,Equity,FloatingProfit
                return parseStandardFormat(signalId, parts, lineNumber);
                
            } else if (parts.length == 5) {
                // NEUES Format mit Profit: Datum,Zeit,Equity,FloatingProfit,Profit
                return parseExtendedFormat(signalId, parts, lineNumber);
                
            } else if (parts.length == 6) {
                // Fehlerhaftes Format mit Tausendertrennzeichen: 
                // 24.05.2025,15:22:13,53745,30,0,00
                return parseBrokenFormat(signalId, parts, lineNumber);
                
            } else if (parts.length == 7) {
                // NEUES fehlerhaftes Format mit Profit und Tausendertrennzeichen:
                // 24.05.2025,15:22:13,53745,30,0,00,179,29
                return parseBrokenExtendedFormat(signalId, parts, lineNumber);
                
            } else {
                LOGGER.warning("Unbekanntes Format: " + parts.length + " Teile - " + java.util.Arrays.toString(parts));
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception beim Parsen von Zeile " + lineNumber + ": " + line, e);
            return null;
        }
    }
    
    /**
    
    /**
     * NEU: Parst Standard-Format (4 Teile)
     */
    private SignalData parseStandardFormat(String signalId, String[] parts, int lineNumber) {
        LOGGER.info("Parse Standard-Format ohne Profit (4 Teile)");
        
        try {
            String dateStr = parts[0].trim();
            String timeStr = parts[1].trim();
            String equityStr = parts[2].trim();
            String floatingStr = parts[3].trim();
            
            LOGGER.info("Datum: '" + dateStr + "', Zeit: '" + timeStr + "', Equity: '" + equityStr + "', Floating: '" + floatingStr + "'");
            
            // Timestamp parsen
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.parse(
                dateStr + " " + timeStr, SignalData.FILE_TIMESTAMP_FORMATTER);
            LOGGER.info("Geparster Timestamp: " + timestamp);
            
            // Werte parsen
            double equity = Double.parseDouble(equityStr);
            double floatingProfit = Double.parseDouble(floatingStr);
            LOGGER.info("Geparste Werte: Equity=" + equity + ", Floating=" + floatingProfit + " (Profit=0.0)");
            
            // Profit auf 0.0 setzen für alte Daten
            SignalData result = new SignalData(signalId, null, equity, floatingProfit, 0.0, "USD", timestamp);
            LOGGER.info("STANDARD-FORMAT ERFOLGREICH (ohne Profit): " + result.getSummary());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Parsen Standard-Format in Zeile " + lineNumber, e);
            return null;
        }
    }
    
    
    /**
     * NEU: Parst fehlerhaftes Format (6 Teile)
     */
    private SignalData parseBrokenFormat(String signalId, String[] parts, int lineNumber) {
        LOGGER.info("Parse Fehlerhaftes Format ohne Profit (6 Teile)");
        
        try {
            String dateStr = parts[0].trim();
            String timeStr = parts[1].trim();
            String equityWhole = parts[2].trim();
            String equityDecimal = parts[3].trim();
            String floatingWhole = parts[4].trim();
            String floatingDecimal = parts[5].trim();
            
            LOGGER.info("Teile: Datum='" + dateStr + "', Zeit='" + timeStr + "', " +
                       "EquityWhole='" + equityWhole + "', EquityDecimal='" + equityDecimal + "', " +
                       "FloatingWhole='" + floatingWhole + "', FloatingDecimal='" + floatingDecimal + "'");
            
            // Timestamp parsen
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.parse(
                dateStr + " " + timeStr, SignalData.FILE_TIMESTAMP_FORMATTER);
            LOGGER.info("Geparster Timestamp: " + timestamp);
            
            // Werte zusammensetzen und parsen
            String equityStr = equityWhole + "." + equityDecimal;
            String floatingStr = floatingWhole + "." + floatingDecimal;
            
            double equity = Double.parseDouble(equityStr);
            double floatingProfit = Double.parseDouble(floatingStr);
            
            LOGGER.info("Zusammengesetzte Werte: Equity='" + equityStr + "'=" + equity + 
                       ", Floating='" + floatingStr + "'=" + floatingProfit + " (Profit=0.0)");
            
            // Profit auf 0.0 setzen für alte Daten
            SignalData result = new SignalData(signalId, null, equity, floatingProfit, 0.0, "USD", timestamp);
            LOGGER.info("FEHLERHAFTES FORMAT REPARIERT (ohne Profit): " + result.getSummary());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Parsen fehlerhaftes Format in Zeile " + lineNumber, e);
            return null;
        }
    }
    
    /**
     * Liest alle Tick-Einträge einer Signal-ID
     * 
     * @param signalId Die Signal-ID
     * @return Liste aller Tick-Einträge
     */
    public List<SignalData> readAllTickEntries(String signalId) {
        List<SignalData> entries = new ArrayList<>();
        String tickFilePath = config.getTickFilePath(signalId);
        
        LOGGER.info("=== LESE ALLE TICK-EINTRÄGE für Signal: " + signalId + " ===");
        LOGGER.info("Datei: " + tickFilePath);
        
        try {
            Path filePath = Paths.get(tickFilePath);
            
            if (!Files.exists(filePath)) {
                LOGGER.fine("Tick-Datei nicht gefunden: " + tickFilePath);
                return entries;
            }
            
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            LOGGER.info("Zeilen in Datei: " + lines.size());
            
            int parsedCount = 0;
            int skippedCount = 0;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                
                // Kommentare und leere Zeilen überspringen
                if (line.isEmpty() || line.startsWith("#")) {
                    skippedCount++;
                    continue;
                }
                
                // Versuche Zeile zu parsen
                SignalData signalData = parseTickLineRobust(signalId, line, i+1);
                if (signalData != null) {
                    entries.add(signalData);
                    parsedCount++;
                    
                    // Logge ersten und letzten paar Einträge
                    if (parsedCount <= 3 || i >= lines.size() - 3) {
                        LOGGER.info("Eintrag #" + parsedCount + ": " + signalData.getSummary());
                    }
                } else {
                    LOGGER.warning("Konnte Zeile " + (i+1) + " nicht parsen: " + line);
                }
            }
            
            LOGGER.info("ALLE TICK-EINTRÄGE GELESEN: " + parsedCount + " geparst, " + 
                       skippedCount + " übersprungen von " + lines.size() + " Zeilen");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Lesen aller Tick-Einträge für Signal " + signalId, e);
        }
        
        return entries;
    }
    
    /**
     * Gibt Statistiken über eine Tick-Datei zurück
     * 
     * @param signalId Die Signal-ID
     * @return TickFileStatistics-Objekt
     */
    public TickFileStatistics getTickFileStatistics(String signalId) {
        String tickFilePath = config.getTickFilePath(signalId);
        Path filePath = Paths.get(tickFilePath);
        
        TickFileStatistics stats = new TickFileStatistics(signalId);
        stats.filePath = tickFilePath;
        stats.fileExists = Files.exists(filePath);
        
        if (stats.fileExists) {
            try {
                stats.fileSize = Files.size(filePath);
                stats.lastModified = Files.getLastModifiedTime(filePath).toMillis();
                
                List<SignalData> entries = readAllTickEntries(signalId);
                stats.entryCount = entries.size();
                
                if (!entries.isEmpty()) {
                    stats.firstEntry = entries.get(0);
                    stats.lastEntry = entries.get(entries.size() - 1);
                }
                
            } catch (Exception e) {
                stats.error = e.getMessage();
            }
        }
        
        return stats;
    }
    
    /**
     * Bereinigt alte Tick-Einträge (löscht Einträge älter als X Tage)
     * 
     * @param signalId Die Signal-ID
     * @param maxAgeDays Maximales Alter in Tagen
     * @return Anzahl gelöschter Einträge
     */
    public int cleanupOldTickEntries(String signalId, int maxAgeDays) {
        List<SignalData> allEntries = readAllTickEntries(signalId);
        
        if (allEntries.isEmpty()) {
            return 0;
        }
        
        java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(maxAgeDays);
        List<SignalData> validEntries = new ArrayList<>();
        int deletedCount = 0;
        
        for (SignalData entry : allEntries) {
            if (entry.getTimestamp().isAfter(cutoffDate)) {
                validEntries.add(entry);
            } else {
                deletedCount++;
            }
        }
        
        if (deletedCount > 0) {
            // Datei neu schreiben mit nur gültigen Einträgen
            rewriteTickFile(signalId, validEntries);
            LOGGER.info("Tick-Einträge bereinigt für Signal " + signalId + ": " + 
                       deletedCount + " alte Einträge entfernt");
        }
        
        return deletedCount;
    }
    
    /**
     * Schreibt eine Tick-Datei komplett neu
     * 
     * @param signalId Die Signal-ID
     * @param entries Die zu schreibenden Einträge
     */
    private void rewriteTickFile(String signalId, List<SignalData> entries) {
        String tickFilePath = config.getTickFilePath(signalId);
        
        try {
            Path filePath = Paths.get(tickFilePath);
            
            // Backup der alten Datei erstellen
            if (Files.exists(filePath)) {
                Path backupPath = Paths.get(tickFilePath + ".backup");
                Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Neue Datei schreiben
            writeTickFileHeader(filePath);
            
            for (SignalData entry : entries) {
                appendToTickFile(tickFilePath, entry.toFullTickFileEntry());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Neuschreiben der Tick-Datei für Signal " + signalId, e);
        }
    }
    
    /**
     * Repariert eine Tick-Datei mit fehlerhaftem Format
     * Konvertiert Format von "53745,30,0,00" zu "53745.30,0.00"
     * 
     * @param signalId Die Signal-ID
     * @return true wenn erfolgreich repariert, false bei Fehlern
     */
    public boolean repairTickFile(String signalId) {
        String tickFilePath = config.getTickFilePath(signalId);
        Path filePath = Paths.get(tickFilePath);
        
        if (!Files.exists(filePath)) {
            LOGGER.warning("Tick-Datei existiert nicht: " + tickFilePath);
            return false;
        }
        
        try {
            LOGGER.info("Repariere Tick-Datei für Signal: " + signalId);
            
            // Backup erstellen
            Path backupPath = Paths.get(tickFilePath + ".backup_" + System.currentTimeMillis());
            Files.copy(filePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backup erstellt: " + backupPath);
            
            // Datei lesen
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            List<String> repairedLines = new ArrayList<>();
            
            int repairedCount = 0;
            
            for (String line : lines) {
                // Kommentare und leere Zeilen unverändert übernehmen
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    repairedLines.add(line);
                    continue;
                }
                
                String[] parts = line.split(",");
                
                if (parts.length == 6) {
                    // Fehlerhaftes Format: 24.05.2025,15:22:13,53745,30,0,00
                    // Repariere zu: 24.05.2025,15:22:13,53745.30,0.00
                    String repaired = String.format("%s,%s,%s.%s,%s.%s",
                                                  parts[0].trim(),  // Datum
                                                  parts[1].trim(),  // Zeit
                                                  parts[2].trim(),  // Equity Ganzzahl
                                                  parts[3].trim(),  // Equity Nachkomma
                                                  parts[4].trim(),  // Floating Ganzzahl
                                                  parts[5].trim()); // Floating Nachkomma
                    repairedLines.add(repaired);
                    repairedCount++;
                    LOGGER.fine("Repariert: " + line + " -> " + repaired);
                } else if (parts.length == 4) {
                    // Format bereits korrekt
                    repairedLines.add(line);
                } else {
                    LOGGER.warning("Unbekanntes Format, überspringe Zeile: " + line);
                    repairedLines.add(line);
                }
            }
            
            if (repairedCount > 0) {
                // Reparierte Datei schreiben
                Files.write(filePath, repairedLines, StandardCharsets.UTF_8);
                LOGGER.info("Tick-Datei erfolgreich repariert: " + repairedCount + " Zeilen korrigiert");
                return true;
            } else {
                LOGGER.info("Keine Reparatur notwendig für: " + tickFilePath);
                // Backup löschen wenn keine Änderungen
                Files.delete(backupPath);
                return true;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Reparieren der Tick-Datei: " + tickFilePath, e);
            return false;
        }
    }
    private SignalData parseExtendedFormat(String signalId, String[] parts, int lineNumber) {
        LOGGER.info("Parse Erweitertes Format mit Profit (5 Teile)");
        
        try {
            String dateStr = parts[0].trim();
            String timeStr = parts[1].trim();
            String equityStr = parts[2].trim();
            String floatingStr = parts[3].trim();
            String profitStr = parts[4].trim();
            
            LOGGER.info("Datum: '" + dateStr + "', Zeit: '" + timeStr + 
                       "', Equity: '" + equityStr + "', Floating: '" + floatingStr + 
                       "', Profit: '" + profitStr + "'");
            
            // Timestamp parsen
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.parse(
                dateStr + " " + timeStr, SignalData.FILE_TIMESTAMP_FORMATTER);
            LOGGER.info("Geparster Timestamp: " + timestamp);
            
            // Werte parsen
            double equity = Double.parseDouble(equityStr);
            double floatingProfit = Double.parseDouble(floatingStr);
            double profit = Double.parseDouble(profitStr);
            LOGGER.info("Geparste Werte: Equity=" + equity + ", Floating=" + floatingProfit + ", Profit=" + profit);
            
            SignalData result = new SignalData(signalId, null, equity, floatingProfit, profit, "USD", timestamp);
            LOGGER.info("ERWEITERTES FORMAT ERFOLGREICH: " + result.getSummary());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Parsen erweitertes Format in Zeile " + lineNumber, e);
            return null;
        }
    }
    private SignalData parseBrokenExtendedFormat(String signalId, String[] parts, int lineNumber) {
        LOGGER.info("Parse Fehlerhaftes Erweitertes Format mit Profit (7 Teile)");
        
        try {
            String dateStr = parts[0].trim();
            String timeStr = parts[1].trim();
            String equityWhole = parts[2].trim();
            String equityDecimal = parts[3].trim();
            String floatingWhole = parts[4].trim();
            String floatingDecimal = parts[5].trim();
            String profitStr = parts[6].trim();
            
            // Prüfe ob Profit auch aufgeteilt ist (8 Teile)
            if (parts.length >= 8) {
                String profitDecimal = parts[7].trim();
                profitStr = profitStr + "." + profitDecimal;
                LOGGER.info("Profit zusammengesetzt aus 2 Teilen: " + profitStr);
            }
            
            LOGGER.info("Teile: Datum='" + dateStr + "', Zeit='" + timeStr + "', " +
                       "EquityWhole='" + equityWhole + "', EquityDecimal='" + equityDecimal + "', " +
                       "FloatingWhole='" + floatingWhole + "', FloatingDecimal='" + floatingDecimal + "', " +
                       "Profit='" + profitStr + "'");
            
            // Timestamp parsen
            java.time.LocalDateTime timestamp = java.time.LocalDateTime.parse(
                dateStr + " " + timeStr, SignalData.FILE_TIMESTAMP_FORMATTER);
            LOGGER.info("Geparster Timestamp: " + timestamp);
            
            // Werte zusammensetzen und parsen
            String equityStr = equityWhole + "." + equityDecimal;
            String floatingStr = floatingWhole + "." + floatingDecimal;
            
            double equity = Double.parseDouble(equityStr);
            double floatingProfit = Double.parseDouble(floatingStr);
            double profit = Double.parseDouble(profitStr);
            
            LOGGER.info("Zusammengesetzte Werte: Equity='" + equityStr + "'=" + equity + 
                       ", Floating='" + floatingStr + "'=" + floatingProfit +
                       ", Profit='" + profitStr + "'=" + profit);
            
            SignalData result = new SignalData(signalId, null, equity, floatingProfit, profit, "USD", timestamp);
            LOGGER.info("FEHLERHAFTES ERWEITERTES FORMAT REPARIERT: " + result.getSummary());
            
            return result;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Parsen fehlerhaftes erweitertes Format in Zeile " + lineNumber, e);
            return null;
        }
    }
    /**
     * Repariert alle Tick-Dateien im Tick-Verzeichnis
     * 
     * @return Map mit Signal-ID und Reparatur-Status
     */
    public Map<String, Boolean> repairAllTickFiles() {
        Map<String, Boolean> results = new HashMap<>();
        
        try {
            Path tickDir = Paths.get(config.getTickDir());
            
            if (!Files.exists(tickDir)) {
                LOGGER.warning("Tick-Verzeichnis existiert nicht: " + tickDir);
                return results;
            }
            
            LOGGER.info("Starte Reparatur aller Tick-Dateien in: " + tickDir);
            
            try (var stream = Files.list(tickDir)) {
                List<Path> tickFiles = stream
                    .filter(path -> path.toString().toLowerCase().endsWith(".txt"))
                    .collect(java.util.stream.Collectors.toList());
                
                for (Path tickFile : tickFiles) {
                    String fileName = tickFile.getFileName().toString();
                    String signalId = fileName.substring(0, fileName.lastIndexOf('.'));
                    
                    boolean success = repairTickFile(signalId);
                    results.put(signalId, success);
                }
            }
            
            long successCount = results.values().stream().filter(v -> v).count();
            LOGGER.info("Reparatur abgeschlossen: " + successCount + "/" + results.size() + " erfolgreich");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Reparieren aller Tick-Dateien", e);
        }
        
        return results;
    }
    
    /**
     * Statistik-Klasse für Tick-Dateien
     */
    public static class TickFileStatistics {
        public String signalId;
        public String filePath;
        public boolean fileExists;
        public long fileSize;
        public long lastModified;
        public int entryCount;
        public SignalData firstEntry;
        public SignalData lastEntry;
        public String error;
        
        public TickFileStatistics(String signalId) {
            this.signalId = signalId;
        }
        
        @Override
        public String toString() {
            if (!fileExists) {
                return "Tick-Datei existiert nicht für Signal " + signalId;
            }
            
            return String.format("Signal %s: %d Einträge, %d Bytes, letzte Änderung: %s", 
                               signalId, entryCount, fileSize, 
                               new java.util.Date(lastModified));
        }
    }
}