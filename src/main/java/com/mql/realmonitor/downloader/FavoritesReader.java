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
 * ERWEITERT: Unterstützt Favoritenklassen (ID:Klasse Format mit Zahlen 1-10)
 */
public class FavoritesReader {
    
    private static final Logger LOGGER = Logger.getLogger(FavoritesReader.class.getName());
    
    private final MqlRealMonitorConfig config;
    
    // Cache für Favoriten
    private List<String> cachedFavorites;
    private Map<String, String> cachedFavoriteClasses; // Signal-ID -> Favoritenklasse (1-10)
    private long lastModified = 0;
    
    public FavoritesReader(MqlRealMonitorConfig config) {
        this.config = config;
        this.cachedFavorites = new ArrayList<>();
        this.cachedFavoriteClasses = new HashMap<>();
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
            Map<String, String> favoriteClasses = new HashMap<>();
            
            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    FavoriteEntry entry = processLineWithClass(line, lineNumber);
                    
                    if (entry != null && entry.signalId != null && !entry.signalId.isEmpty()) {
                        favorites.add(entry.signalId);
                        if (entry.favoriteClass != null && !entry.favoriteClass.isEmpty()) {
                            favoriteClasses.put(entry.signalId, entry.favoriteClass);
                        }
                    }
                }
            }
            
            // Cache aktualisieren
            cachedFavorites = new ArrayList<>(favorites);
            cachedFavoriteClasses = new HashMap<>(favoriteClasses);
            lastModified = currentModified;
            
            LOGGER.info("Favoriten erfolgreich geladen: " + favorites.size() + " Einträge, " + 
                       favoriteClasses.size() + " mit Klassen");
            logFavoritesSummary(favorites, favoriteClasses);
            
            return favorites;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Lesen der Favoriten-Datei: " + favoritesFile, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * NEU: Liest alle Favoriten mit ihren Klassen
     * 
     * @return Map mit Signal-ID -> Favoritenklasse
     */
    public Map<String, String> readFavoritesWithClasses() {
        // Erst die normalen Favoriten laden (um Cache zu aktualisieren)
        readFavorites();
        
        // Dann die gecachten Klassen zurückgeben
        return new HashMap<>(cachedFavoriteClasses);
    }
    
    /**
     * NEU: Gibt die Favoritenklasse für eine Signal-ID zurück
     * 
     * @param signalId Die Signal-ID
     * @return Die Favoritenklasse (1-10) oder null wenn nicht vorhanden
     */
    public String getFavoriteClass(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return null;
        }
        
        // Sicherstellen dass Favoriten geladen sind
        if (cachedFavoriteClasses.isEmpty() && isFavoritesFileAvailable()) {
            readFavorites();
        }
        
        return cachedFavoriteClasses.get(signalId.trim());
    }
    
    /**
     * Verarbeitet eine Zeile aus der Favoriten-Datei (Legacy-Methode)
     * 
     * @param line Die Zeile aus der Datei
     * @param lineNumber Die Zeilennummer für Logging
     * @return Die extrahierte Signal-ID oder null bei Fehlern
     */
    private String processLine(String line, int lineNumber) {
        FavoriteEntry entry = processLineWithClass(line, lineNumber);
        return entry != null ? entry.signalId : null;
    }
    
    /**
     * NEU: Verarbeitet eine Zeile und extrahiert sowohl Signal-ID als auch Favoritenklasse
     * 
     * @param line Die Zeile aus der Datei
     * @param lineNumber Die Zeilennummer für Logging
     * @return FavoriteEntry mit Signal-ID und Klasse oder null bei Fehlern
     */
    private FavoriteEntry processLineWithClass(String line, int lineNumber) {
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
            String signalId;
            String favoriteClass = null;
            
            // Format: ID:Klasse (1-10) oder nur ID
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                signalId = parts[0].trim();
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    String classCandidate = parts[1].trim();
                    // Validiere dass Klasse eine Nummer zwischen 1-10 ist
                    if (isValidFavoriteClass(classCandidate)) {
                        favoriteClass = classCandidate;
                    } else {
                        LOGGER.warning("Ungültige Favoritenklasse in Zeile " + lineNumber + ": " + classCandidate + " (muss 1-10 sein)");
                    }
                }
            } else {
                signalId = line;
            }
            
            // Signal-ID validieren
            if (isValidSignalId(signalId)) {
                LOGGER.fine("Gefundene Signal-ID in Zeile " + lineNumber + ": " + signalId + 
                           (favoriteClass != null ? " (Klasse: " + favoriteClass + ")" : ""));
                return new FavoriteEntry(signalId, favoriteClass);
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
     * NEU: Validiert eine Favoritenklasse (muss 1-10 sein)
     * 
     * @param favoriteClass Die zu validierende Favoritenklasse
     * @return true wenn gültig (1-10), false sonst
     */
    private boolean isValidFavoriteClass(String favoriteClass) {
        if (favoriteClass == null || favoriteClass.trim().isEmpty()) {
            return false;
        }
        
        try {
            int classNumber = Integer.parseInt(favoriteClass.trim());
            return classNumber >= 1 && classNumber <= 10;
        } catch (NumberFormatException e) {
            return false;
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
        content.append("# Format: SignalID:Favoritenklasse oder nur SignalID\n");
        content.append("# Favoritenklasse muss eine Nummer zwischen 1-10 sein\n");
        content.append("# Zeilen die mit # oder // beginnen werden ignoriert\n");
        content.append("#\n");
        content.append("# Beispiele mit Favoritenklassen (1-10):\n");
        content.append("# 123456:1\n");
        content.append("# 789012:2\n");
        content.append("# 345678:3\n");
        content.append("# 111222:4\n");
        content.append("# 333444:5\n");
        content.append("# 555666:6\n");
        content.append("# 777888:7\n");
        content.append("# 999000:8\n");
        content.append("# 111333:9\n");
        content.append("# 444666:10\n");
        content.append("#\n");
        content.append("# Beispiele ohne Klasse:\n");
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
     * @param favoriteClasses Map der Favoritenklassen
     */
    private void logFavoritesSummary(List<String> favorites, Map<String, String> favoriteClasses) {
        if (favorites.isEmpty()) {
            LOGGER.info("Keine Favoriten gefunden");
            return;
        }
        
        LOGGER.info("Favoriten-Übersicht:");
        for (int i = 0; i < Math.min(favorites.size(), 10); i++) {
            String signalId = favorites.get(i);
            String favoriteClass = favoriteClasses.get(signalId);
            String displayText = signalId;
            if (favoriteClass != null) {
                displayText += " (Klasse " + favoriteClass + ")";
            }
            LOGGER.info("  " + (i + 1) + ". " + displayText);
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
        cachedFavoriteClasses.clear();
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
        stats.put("favorites_with_classes_count", cachedFavoriteClasses.size());
        stats.put("cache_active", !cachedFavorites.isEmpty());
        
        return stats;
    }
    
    /**
     * NEU: Datenklasse für Favoriten-Einträge
     */
    private static class FavoriteEntry {
        public final String signalId;
        public final String favoriteClass;
        
        public FavoriteEntry(String signalId, String favoriteClass) {
            this.signalId = signalId;
            this.favoriteClass = favoriteClass;
        }
    }
}