package com.mql.realmonitor.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Verwaltet die Zuordnung von Signal-IDs zu Provider-Namen
 * 
 * Diese Klasse lädt und speichert eine idtranslation.txt Datei im Config-Verzeichnis,
 * die eine Zuordnung von Signal-ID zu Provider-Name enthält. 
 * 
 * Format der idtranslation.txt:
 * signalId:ProviderName
 * 285398:Unlimited
 * 2296908:TradingBot Pro
 * 
 * Zweck: Beim Programmstart und bei Fehlerfällen soll ein sinnvoller Provider-Name
 * angezeigt werden statt "Unbekannt", auch wenn die HTML-Seite noch nicht geladen wurde.
 */
public class IdTranslationManager {
    
    private static final Logger LOGGER = Logger.getLogger(IdTranslationManager.class.getName());
    
    private static final String TRANSLATION_FILE_NAME = "idtranslation.txt";
    private static final String DELIMITER = ":";
    
    private final MqlRealMonitorConfig config;
    private final File translationFile;
    private final Map<String, String> idToNameMap;
    private boolean cacheLoaded;
    
    /**
     * Konstruktor
     * 
     * @param config Die MqlRealMonitorConfig Instanz
     */
    public IdTranslationManager(MqlRealMonitorConfig config) {
        this.config = config;
        this.translationFile = new File(config.getConfigDir(), TRANSLATION_FILE_NAME);
        this.idToNameMap = new HashMap<>();
        this.cacheLoaded = false;
        
        LOGGER.info("IdTranslationManager initialisiert - Translation-Datei: " + translationFile.getAbsolutePath());
        
        // Initial laden
        loadTranslations();
    }
    
    /**
     * Lädt die ID-zu-Name Zuordnungen aus der Datei
     */
    public void loadTranslations() {
        idToNameMap.clear();
        cacheLoaded = false;
        
        if (!translationFile.exists()) {
            LOGGER.warning("DEBUG: Translation-Datei existiert noch nicht: " + translationFile.getAbsolutePath() + " - wird bei Bedarf erstellt");
            createEmptyTranslationFile();
            cacheLoaded = true;
            return;
        } else {
            LOGGER.info("DEBUG: Translation-Datei gefunden: " + translationFile.getAbsolutePath());
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(translationFile))) {
            String line;
            int lineNumber = 0;
            int loadedMappings = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Leere Zeilen und Kommentare überspringen
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Zeile parsen: signalId:providerName
                String[] parts = line.split(DELIMITER, 2);
                if (parts.length == 2) {
                    String signalId = parts[0].trim();
                    String providerName = parts[1].trim();
                    
                    if (!signalId.isEmpty() && !providerName.isEmpty()) {
                        idToNameMap.put(signalId, providerName);
                        loadedMappings++;
                        LOGGER.info("Translation geladen: '" + signalId + "' -> '" + providerName + "'");
                    } else {
                        LOGGER.warning("Ungültige Translation in Zeile " + lineNumber + ": '" + line + "' - Signal-ID oder Provider-Name leer");
                    }
                } else {
                    LOGGER.warning("Ungültiges Format in Zeile " + lineNumber + ": '" + line + "' - Erwartet: 'signalId:providerName'");
                }
            }
            
            cacheLoaded = true;
            LOGGER.info("ID-Translation geladen: " + loadedMappings + " Zuordnungen aus " + translationFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Laden der ID-Translation-Datei: " + e.getMessage());
            cacheLoaded = true; // Cache als geladen markieren, auch wenn leer
        }
    }
    
    /**
     * Speichert die aktuellen ID-zu-Name Zuordnungen in die Datei
     */
    public void saveTranslations() {
        try {
            // Config-Verzeichnis erstellen falls es nicht existiert
            File configDir = translationFile.getParentFile();
            if (!configDir.exists()) {
                if (configDir.mkdirs()) {
                    LOGGER.info("Config-Verzeichnis erstellt: " + configDir.getAbsolutePath());
                } else {
                    LOGGER.severe("Konnte Config-Verzeichnis nicht erstellen: " + configDir.getAbsolutePath());
                    return;
                }
            }
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(translationFile))) {
                // Header schreiben
                writer.write("# ID-zu-Provider-Name Translation\n");
                writer.write("# Format: signalId:providerName\n");
                writer.write("# Generiert von MqlRealMonitor\n");
                writer.write("#\n");
                
                // Zuordnungen schreiben (sortiert nach Signal-ID)
                idToNameMap.entrySet().stream()
                    .sorted(Map.Entry.<String, String>comparingByKey())
                    .forEach(entry -> {
                        try {
                            writer.write(entry.getKey() + DELIMITER + entry.getValue() + "\n");
                        } catch (IOException e) {
                            LOGGER.warning("Fehler beim Schreiben der Translation: " + entry.getKey() + " -> " + entry.getValue());
                        }
                    });
                
                writer.flush();
                LOGGER.info("ID-Translation gespeichert: " + idToNameMap.size() + " Zuordnungen in " + translationFile.getAbsolutePath());
                
            }
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Speichern der ID-Translation-Datei: " + e.getMessage());
        }
    }
    
    /**
     * Erstellt eine leere Translation-Datei mit Beispielen
     */
    private void createEmptyTranslationFile() {
        try {
            // Config-Verzeichnis erstellen falls es nicht existiert
            File configDir = translationFile.getParentFile();
            if (!configDir.exists()) {
                if (configDir.mkdirs()) {
                    LOGGER.info("Config-Verzeichnis erstellt: " + configDir.getAbsolutePath());
                } else {
                    LOGGER.severe("Konnte Config-Verzeichnis nicht erstellen: " + configDir.getAbsolutePath());
                    return;
                }
            }
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(translationFile))) {
                writer.write("# ID-zu-Provider-Name Translation\n");
                writer.write("# Format: signalId:providerName\n");
                writer.write("# Diese Datei wird automatisch von MqlRealMonitor verwaltet\n");
                writer.write("#\n");
                writer.write("# Beispiele:\n");
                writer.write("# 285398:Unlimited Trading\n");
                writer.write("# 2296908:TradingBot Pro\n");
                writer.write("#\n");
                writer.write("# Neue Einträge werden automatisch hinzugefügt wenn Provider-Namen\n");
                writer.write("# aus den HTML-Seiten geladen werden.\n");
                writer.write("#\n");
                
                writer.flush();
                LOGGER.info("Leere ID-Translation-Datei erstellt: " + translationFile.getAbsolutePath());
            }
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Erstellen der leeren ID-Translation-Datei: " + e.getMessage());
        }
    }
    
    /**
     * Gibt den Provider-Namen für eine Signal-ID zurück
     * 
     * @param signalId Die Signal-ID
     * @return Der Provider-Name oder "Unbekannt" wenn nicht vorhanden
     */
    public String getProviderName(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            LOGGER.warning("DEBUG: Leere oder null Signal-ID übergeben");
            return "Unbekannt";
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            LOGGER.info("DEBUG: Cache noch nicht geladen, lade jetzt...");
            loadTranslations();
        }
        
        String cleanSignalId = signalId.trim();
        String providerName = idToNameMap.get(cleanSignalId);
        
        LOGGER.info("DEBUG: Signal-ID '" + cleanSignalId + "' -> Provider-Name: '" + providerName + "' (Map-Größe: " + idToNameMap.size() + ")");
        
        if (providerName != null && !providerName.trim().isEmpty()) {
            LOGGER.info("Provider-Name für Signal " + cleanSignalId + " gefunden: " + providerName);
            return providerName;
        } else {
            LOGGER.info("Kein Provider-Name für Signal " + cleanSignalId + " gefunden - verwende 'Unbekannt'");
            return "Unbekannt";
        }
    }
    
    /**
     * Fügt eine neue ID-zu-Name Zuordnung hinzu oder aktualisiert eine bestehende
     * 
     * @param signalId Die Signal-ID
     * @param providerName Der Provider-Name
     * @return true wenn eine neue Zuordnung hinzugefügt wurde, false wenn aktualisiert
     */
    public boolean addOrUpdateMapping(String signalId, String providerName) {
        if (signalId == null || signalId.trim().isEmpty() || 
            providerName == null || providerName.trim().isEmpty()) {
            LOGGER.warning("Ungültige Parameter für ID-Translation: signalId='" + signalId + "', providerName='" + providerName + "'");
            return false;
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        String cleanSignalId = signalId.trim();
        String cleanProviderName = providerName.trim();
        
        // Prüfen ob bereits vorhanden
        String existingName = idToNameMap.get(cleanSignalId);
        boolean isNewMapping = (existingName == null);
        
        // Mapping hinzufügen/aktualisieren
        idToNameMap.put(cleanSignalId, cleanProviderName);
        
        if (isNewMapping) {
            LOGGER.info("Neue ID-Translation hinzugefügt: " + cleanSignalId + " -> " + cleanProviderName);
        } else if (!existingName.equals(cleanProviderName)) {
            LOGGER.info("ID-Translation aktualisiert: " + cleanSignalId + " -> " + cleanProviderName + " (war: " + existingName + ")");
        } else {
            LOGGER.fine("ID-Translation unverändert: " + cleanSignalId + " -> " + cleanProviderName);
            return false; // Keine Änderung
        }
        
        // Automatisch speichern bei Änderungen
        saveTranslations();
        
        return isNewMapping;
    }
    
    /**
     * Entfernt eine ID-zu-Name Zuordnung
     * 
     * @param signalId Die Signal-ID
     * @return true wenn entfernt wurde, false wenn nicht vorhanden war
     */
    public boolean removeMapping(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return false;
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        String removedName = idToNameMap.remove(signalId.trim());
        
        if (removedName != null) {
            LOGGER.info("ID-Translation entfernt: " + signalId + " -> " + removedName);
            saveTranslations();
            return true;
        } else {
            LOGGER.fine("ID-Translation zum Entfernen nicht gefunden: " + signalId);
            return false;
        }
    }
    
    /**
     * Prüft ob eine Zuordnung für die Signal-ID existiert
     * 
     * @param signalId Die Signal-ID
     * @return true wenn Zuordnung vorhanden
     */
    public boolean hasMapping(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return false;
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return idToNameMap.containsKey(signalId.trim());
    }
    
    /**
     * Gibt die Anzahl der geladenen Zuordnungen zurück
     * 
     * @return Anzahl der ID-zu-Name Zuordnungen
     */
    public int getMappingCount() {
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return idToNameMap.size();
    }
    
    /**
     * Gibt alle Signal-IDs zurück für die Zuordnungen existieren
     * 
     * @return Set mit allen Signal-IDs
     */
    public java.util.Set<String> getAllSignalIds() {
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return new java.util.HashSet<>(idToNameMap.keySet());
    }
    
    /**
     * Gibt alle Zuordnungen als Map zurück (Kopie)
     * 
     * @return Map mit allen ID-zu-Name Zuordnungen
     */
    public Map<String, String> getAllMappings() {
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return new HashMap<>(idToNameMap);
    }
    
    /**
     * Leert den Cache und lädt die Zuordnungen neu
     */
    public void refreshCache() {
        LOGGER.info("ID-Translation Cache wird neu geladen...");
        loadTranslations();
    }
    
    /**
     * Gibt den Pfad zur Translation-Datei zurück
     * 
     * @return Pfad zur idtranslation.txt Datei
     */
    public String getTranslationFilePath() {
        return translationFile.getAbsolutePath();
    }
    
    /**
     * Gibt Diagnose-Informationen zurück
     * 
     * @return Diagnose-String mit Details über geladene Zuordnungen
     */
    public String getDiagnosticInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== ID TRANSLATION DIAGNOSTIC ===\n");
        info.append("Translation File: ").append(translationFile.getAbsolutePath()).append("\n");
        info.append("File Exists: ").append(translationFile.exists()).append("\n");
        info.append("Cache Loaded: ").append(cacheLoaded).append("\n");
        info.append("Mapping Count: ").append(getMappingCount()).append("\n");
        
        if (getMappingCount() > 0) {
            info.append("\nMappings:\n");
            getAllMappings().entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey())
                .forEach(entry -> 
                    info.append("  ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n")
                );
        }
        
        return info.toString();
    }
    
    /**
     * Erstellt eine Backup-Kopie der Translation-Datei
     * 
     * @return true wenn Backup erfolgreich erstellt wurde
     */
    public boolean createBackup() {
        if (!translationFile.exists()) {
            LOGGER.warning("Kann kein Backup erstellen - Translation-Datei existiert nicht: " + translationFile.getAbsolutePath());
            return false;
        }
        
        try {
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File backupFile = new File(translationFile.getParent(), "idtranslation_backup_" + timestamp + ".txt");
            
            java.nio.file.Files.copy(translationFile.toPath(), backupFile.toPath());
            
            LOGGER.info("Backup der ID-Translation erstellt: " + backupFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Erstellen des ID-Translation Backups: " + e.getMessage());
            return false;
        }
    }
}