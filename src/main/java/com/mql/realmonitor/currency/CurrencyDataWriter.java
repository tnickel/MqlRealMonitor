package com.mql.realmonitor.currency;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.mql.realmonitor.config.MqlRealMonitorConfig;
import com.mql.realmonitor.exception.MqlMonitorException;
import com.mql.realmonitor.exception.MqlMonitorException.ErrorType;
import com.mql.realmonitor.utils.MqlUtils;

/**
 * Writer für Währungskursdaten.
 * Schreibt CurrencyData in entsprechende Dateien im tick_kurse Verzeichnis.
 */
public class CurrencyDataWriter {
    
    private static final Logger logger = Logger.getLogger(CurrencyDataWriter.class.getName());
    
    private final MqlRealMonitorConfig config;
    private final String tickKurseDir;
    
    /**
     * Konstruktor für CurrencyDataWriter.
     * 
     * @param config Konfiguration für base_path
     */
    public CurrencyDataWriter(MqlRealMonitorConfig config) {
        this.config = config;
        this.tickKurseDir = config.getBasePath() + "/realtick/tick_kurse";
        
        // Verzeichnis erstellen falls nicht vorhanden
        MqlUtils.ensureDirectoryExists(tickKurseDir);
    }
    
    /**
     * Schreibt eine Liste von Kursdaten in die entsprechenden Dateien.
     * 
     * @param currencyDataList Liste der zu schreibenden Kursdaten
     * @throws MqlMonitorException bei Schreibfehlern
     */
    public void writeCurrencyData(List<CurrencyData> currencyDataList) throws MqlMonitorException {
        if (currencyDataList == null || currencyDataList.isEmpty()) {
            logger.warning("Keine Kursdaten zum Schreiben vorhanden");
            return;
        }
        
        logger.info("Schreibe " + currencyDataList.size() + " Kursdaten...");
        
        for (CurrencyData data : currencyDataList) {
            writeSingleCurrencyData(data);
        }
        
        logger.info("Alle Kursdaten erfolgreich geschrieben");
    }
    
    /**
     * Schreibt einen einzelnen Kursdatensatz in die entsprechende Datei.
     * 
     * @param data Die zu schreibenden Kursdaten
     * @throws MqlMonitorException bei Schreibfehlern
     */
    public void writeSingleCurrencyData(CurrencyData data) throws MqlMonitorException {
        if (data == null) {
            throw new MqlMonitorException(ErrorType.VALIDATION_ERROR, "CurrencyData ist null");
        }
        
        String filename = data.getSymbol().toLowerCase() + ".txt";
        String filepath = tickKurseDir + "/" + filename;
        
        try {
            // Prüfe ob Datei existiert und Header schreiben
            File file = new File(filepath);
            boolean writeHeader = !file.exists() || file.length() == 0;
            
            StringBuilder content = new StringBuilder();
            
            if (writeHeader) {
                content.append("timestamp,symbol,price\n");
                logger.info("Header für neue Datei hinzugefügt: " + filename);
            }
            
            content.append(data.toCsvLine()).append("\n");
            
            // Schreibe mit MqlUtils
            boolean success = MqlUtils.writeTextFile(filepath, content.toString(), true); // append = true
            
            if (!success) {
                String errorMsg = "Fehler beim Schreiben der Kursdaten für " + data.getSymbol();
                logger.severe(errorMsg);
                throw new MqlMonitorException(ErrorType.FILE_IO_ERROR, errorMsg);
            }
            
            logger.fine("Kursdaten geschrieben: " + data.toString() + " -> " + filename);
            
        } catch (Exception e) {
            String errorMsg = "Fehler beim Schreiben der Kursdaten für " + data.getSymbol() + ": " + e.getMessage();
            logger.severe(errorMsg);
            throw new MqlMonitorException(ErrorType.FILE_IO_ERROR, errorMsg, e);
        }
    }
    
    /**
     * Liest alle Kursdaten für ein bestimmtes Symbol.
     * 
     * @param symbol Das Währungssymbol (z.B. "XAUUSD", "BTCUSD")
     * @return Liste aller Kursdaten für das Symbol
     * @throws MqlMonitorException bei Lesefehlern
     */
    public List<CurrencyData> readCurrencyData(String symbol) throws MqlMonitorException {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new MqlMonitorException(ErrorType.VALIDATION_ERROR, "Symbol ist leer oder null");
        }
        
        String filename = symbol.toLowerCase() + ".txt";
        String filepath = tickKurseDir + "/" + filename;
        
        List<CurrencyData> dataList = new ArrayList<>();
        File file = new File(filepath);
        
        if (!file.exists()) {
            logger.info("Kursdatei existiert nicht: " + filename);
            return dataList; // Leere Liste zurückgeben
        }
        
        try {
            // Lese Datei mit MqlUtils
            String fileContent = MqlUtils.readTextFile(filepath);
            
            if (fileContent == null || fileContent.trim().isEmpty()) {
                logger.warning("Kursdatei ist leer: " + filename);
                return dataList;
            }
            
            // Zeile für Zeile verarbeiten
            String[] lines = fileContent.split("\n");
            boolean firstLine = true;
            
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                
                // Header-Zeile überspringen
                if (firstLine && line.startsWith("timestamp")) {
                    firstLine = false;
                    continue;
                }
                
                CurrencyData data = CurrencyData.fromCsvLine(line.trim());
                if (data != null) {
                    dataList.add(data);
                }
                firstLine = false;
            }
            
            logger.info("Gelesen: " + dataList.size() + " Kursdaten aus " + filename);
            
        } catch (Exception e) {
            String errorMsg = "Fehler beim Lesen der Kursdaten für " + symbol + ": " + e.getMessage();
            logger.severe(errorMsg);
            throw new MqlMonitorException(ErrorType.FILE_IO_ERROR, errorMsg, e);
        }
        
        return dataList;
    }
    
    /**
     * Gibt die Anzahl der Einträge für ein Symbol zurück.
     * 
     * @param symbol Das Währungssymbol
     * @return Anzahl der Einträge oder 0 wenn Datei nicht existiert
     */
    public int getEntryCount(String symbol) {
        try {
            List<CurrencyData> data = readCurrencyData(symbol);
            return data.size();
        } catch (MqlMonitorException e) {
            logger.warning("Fehler beim Ermitteln der Einträge für " + symbol + ": " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Löscht alle Kursdaten für ein bestimmtes Symbol.
     * 
     * @param symbol Das Währungssymbol
     * @return true wenn erfolgreich gelöscht
     */
    public boolean deleteCurrencyData(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        
        String filename = symbol.toLowerCase() + ".txt";
        String filepath = tickKurseDir + "/" + filename;
        
        try {
            File file = new File(filepath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    logger.info("Kursdatei gelöscht: " + filename);
                } else {
                    logger.warning("Kursdatei konnte nicht gelöscht werden: " + filename);
                }
                return deleted;
            }
        } catch (Exception e) {
            logger.severe("Fehler beim Löschen der Kursdatei " + filename + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Gibt Informationen über alle verfügbaren Kursdateien zurück.
     * 
     * @return Liste mit Datei-Informationen
     */
    public List<String> getCurrencyFileInfo() {
        List<String> info = new ArrayList<>();
        
        File directory = new File(tickKurseDir);
        if (!directory.exists() || !directory.isDirectory()) {
            info.add("Verzeichnis existiert nicht: " + tickKurseDir);
            return info;
        }
        
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null || files.length == 0) {
            info.add("Keine Kursdateien gefunden in: " + tickKurseDir);
            return info;
        }
        
        for (File file : files) {
            String symbol = file.getName().replace(".txt", "").toUpperCase();
            int entryCount = getEntryCount(symbol);
            long fileSize = file.length();
            
            info.add(String.format("%s: %d Einträge, %s", 
                symbol, entryCount, MqlUtils.formatFileSize(fileSize)));
        }
        
        return info;
    }
    
    /**
     * Gibt den Pfad zum tick_kurse Verzeichnis zurück.
     * 
     * @return Verzeichnispfad
     */
    public String getTickKurseDirectory() {
        return tickKurseDir;
    }
}