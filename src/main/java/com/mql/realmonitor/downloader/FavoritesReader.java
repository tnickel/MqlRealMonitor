package com.mql.realmonitor.downloader;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Reader für die Favoriten-Datei
 * Verwaltet das Lesen und Parsen der favorites.txt Datei
 */
public class FavoritesReader {
    
    private static final Logger LOGGER = Logger.getLogger(FavoritesReader.class.getName());
    
    private final MqlRealMonitorConfig config;
    
    // Cache für Favoriten
    private List<String> cachedFavorites;
    private long lastModified = 0;
    
    public FavoritesReader(MqlRealMonitorConfig config) {
        this.config = config;
        this.cachedFavorites = new ArrayList<>();
    }
    
    /**
     * Liest alle Favoriten-IDs aus der favorites.txt Datei
     * 
     * @return Liste der Signal-IDs oder leere Liste bei Fehlern
     */
    public List<String> readFavorites() {
        String favoritesFile = config.getFavoritesFile();
        
        try {
            Path filePath = Paths.get(favoritesFile);
            
            if (!Files.exists(filePath)) {
                LOGGER.warning("Favoriten-Datei nicht gefunden: " + favoritesFile);
                createSampleFavoritesFile(favoritesFile);
                return new ArrayList<>();
            }
            
            // Prüfen ob Datei geändert wurde (für Caching)
            long currentModified = Files.getLastModifiedTime(filePath).toMillis();
            if (currentModified == lastModified && !cachedFavorites.isEmpty()) {
                LOGGER.fine("Verwende gecachte Favoriten (" + cachedFavorites.size() + " Einträge)");
                return new ArrayList<>(cachedFavorites);
            }
            
            LOGGER.info("Lade Favoriten aus: " + favoritesFile);
            
            List<String> favorites = new ArrayList<>();
            
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String processedLine = processLine(line, lineNumber);
                    
                    if (processedLine != null && !processedLine.isEmpty()) {
                        favorites.add(processedLine);
                    }
                }
            }
            
            // Cache aktualisieren
            cachedFavorites = new ArrayList<>(favorites);
            lastModified = currentModified;
            
            LOGGER.info("Favoriten erfolgreich geladen: " + favorites.size() + " Einträge");
            logFavoritesSummary(favorites);
            
            return favorites;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Lesen der Favoriten-Datei: " + favoritesFile, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Verarbeitet eine Zeile aus der Favoriten-Datei
     * 
     * @param line Die Zeile aus der Datei
     * @param lineNumber Die Zeilennummer für Logging
     * @return Die extrahierte Signal-ID oder null bei Fehlern
     */
    private String processLine(String line, int lineNumber) {
        if (line == null) {
            return null;
        }
        
        // Whitespace entfernen
        line = line.trim();
        
        // Leere Zeilen und Kommentare ignorieren
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
            return null;
        }
        
        try {
            // Format: ID:Kategorie oder nur ID
            String signalId;
            
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                signalId = parts[0].trim();
            } else {
                signalId = line;
            }
            
            // Signal-ID validieren
            if (isValidSignalId(signalId)) {
                LOGGER.fine("Gefundene Signal-ID in Zeile " + lineNumber + ": " + signalId);
                return signalId;
            } else {
                LOGGER.warning("Ungültige Signal-ID in Zeile " + lineNumber + ": " + signalId);
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Parsen von Zeile " + lineNumber + ": " + line + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validiert eine Signal-ID
     * 
     * @param signalId Die zu validierende Signal-ID
     * @return true wenn gültig, false sonst
     */
    private boolean isValidSignalId(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return false;
        }
        
        // Signal-IDs sollten numerisch sein
        try {
            Long.parseLong(signalId);
            return true;
        } catch (NumberFormatException e) {
            // Möglicherweise alphanumerische IDs - prüfe Länge und Zeichen
            return signalId.matches("[a-zA-Z0-9]+") && signalId.length() >= 1 && signalId.length() <= 20;
        }
    }
    
    /**
     * Erstellt eine Beispiel-Favoriten-Datei falls keine existiert
     * 
     * @param favoritesFile Der Pfad zur Favoriten-Datei
     */
    private void createSampleFavoritesFile(String favoritesFile) {
        try {
            Path filePath = Paths.get(favoritesFile);
            Path parentDir = filePath.getParent();
            
            // Verzeichnis erstellen falls nicht vorhanden
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            String sampleContent = createSampleFavoritesContent();
            
            try (FileWriter writer = new FileWriter(favoritesFile, StandardCharsets.UTF_8)) {
                writer.write(sampleContent);
            }
            
            LOGGER.info("Beispiel-Favoriten-Datei erstellt: " + favoritesFile);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Beispiel-Favoriten-Datei nicht erstellen: " + favoritesFile, e);
        }
    }
    
    /**
     * Erstellt den Inhalt für eine Beispiel-Favoriten-Datei
     * 
     * @return Der Beispiel-Inhalt
     */
    private String createSampleFavoritesContent() {
        StringBuilder content = new StringBuilder();
        content.append("# MQL5 Signalprovider Favoriten\n");
        content.append("# Format: SignalID:Kategorie oder nur SignalID\n");
        content.append("# Zeilen die mit # oder // beginnen werden ignoriert\n");
        content.append("#\n");
        content.append("# Beispiele:\n");
        content.append("# 123456:Trading Robot\n");
        content.append("# 789012:Manual Trading\n");
        content.append("# 345678\n");
        content.append("#\n");
        content.append("# Fügen Sie hier Ihre Signal-IDs hinzu:\n");
        content.append("\n");
        
        return content.toString();
    }
    
    /**
     * Loggt eine Zusammenfassung der geladenen Favoriten
     * 
     * @param favorites Die Liste der Favoriten
     */
    private void logFavoritesSummary(List<String> favorites) {
        if (favorites.isEmpty()) {
            LOGGER.info("Keine Favoriten gefunden");
            return;
        }
        
        LOGGER.info("Favoriten-Übersicht:");
        for (int i = 0; i < Math.min(favorites.size(), 10); i++) {
            LOGGER.info("  " + (i + 1) + ". " + favorites.get(i));
        }
        
        if (favorites.size() > 10) {
            LOGGER.info("  ... und " + (favorites.size() - 10) + " weitere");
        }
    }
    
    /**
     * Prüft ob die Favoriten-Datei existiert und lesbar ist
     * 
     * @return true wenn Datei verfügbar, false sonst
     */
    public boolean isFavoritesFileAvailable() {
        String favoritesFile = config.getFavoritesFile();
        Path filePath = Paths.get(favoritesFile);
        
        return Files.exists(filePath) && Files.isReadable(filePath);
    }
    
    /**
     * Gibt die Anzahl der Favoriten zurück
     * 
     * @return Anzahl der Favoriten
     */
    public int getFavoritesCount() {
        return readFavorites().size();
    }
    
    /**
     * Prüft ob eine bestimmte Signal-ID in den Favoriten enthalten ist
     * 
     * @param signalId Die zu prüfende Signal-ID
     * @return true wenn enthalten, false sonst
     */
    public boolean containsSignalId(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return false;
        }
        
        List<String> favorites = readFavorites();
        return favorites.contains(signalId.trim());
    }
    
    /**
     * Erneuert den Cache (erzwingt Neueinlesen der Datei)
     */
    public void refreshCache() {
        cachedFavorites.clear();
        lastModified = 0;
        LOGGER.info("Favoriten-Cache geleert - Datei wird beim nächsten Zugriff neu geladen");
    }
    
    /**
     * Gibt Statistiken über die Favoriten-Datei zurück
     * 
     * @return Map mit Statistiken
     */
    public Map<String, Object> getFavoritesStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        String favoritesFile = config.getFavoritesFile();
        Path filePath = Paths.get(favoritesFile);
        
        stats.put("file_path", favoritesFile);
        stats.put("file_exists", Files.exists(filePath));
        
        if (Files.exists(filePath)) {
            try {
                stats.put("file_size", Files.size(filePath));
                stats.put("last_modified", Files.getLastModifiedTime(filePath).toMillis());
                stats.put("is_readable", Files.isReadable(filePath));
            } catch (Exception e) {
                stats.put("error", e.getMessage());
            }
        }
        
        List<String> favorites = readFavorites();
        stats.put("favorites_count", favorites.size());
        stats.put("cache_active", !cachedFavorites.isEmpty());
        
        return stats;
    }
}