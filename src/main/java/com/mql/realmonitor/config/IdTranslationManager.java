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
 * ERWEITERT: Vollständiges Debug-Logging für Problem-Diagnose
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
     */
    public IdTranslationManager(MqlRealMonitorConfig config) {
        LOGGER.info("=== DEBUG: IdTranslationManager Konstruktor START ===");
        
        this.config = config;
        this.translationFile = new File(config.getConfigDir(), TRANSLATION_FILE_NAME);
        this.idToNameMap = new HashMap<>();
        this.cacheLoaded = false;
        
        LOGGER.info("DEBUG: Config-Dir: " + config.getConfigDir());
        LOGGER.info("DEBUG: Translation-Datei-Pfad: " + translationFile.getAbsolutePath());
        LOGGER.info("DEBUG: Translation-Datei existiert: " + translationFile.exists());
        if (translationFile.exists()) {
            LOGGER.info("DEBUG: Translation-Datei-Größe: " + translationFile.length() + " Bytes");
        }
        
        LOGGER.info("IdTranslationManager initialisiert - Translation-Datei: " + translationFile.getAbsolutePath());
        
        // Initial laden
        LOGGER.info("DEBUG: Rufe loadTranslations() vom Konstruktor auf...");
        loadTranslations();
        
        LOGGER.info("=== DEBUG: IdTranslationManager Konstruktor ENDE ===");
    }
    
    /**
     * Lädt die ID-zu-Name Zuordnungen aus der Datei
     */
    public void loadTranslations() {
        LOGGER.info("=== DEBUG: loadTranslations() START ===");
        
        idToNameMap.clear();
        cacheLoaded = false;
        
        LOGGER.info("DEBUG: Prüfe ob Translation-Datei existiert: " + translationFile.getAbsolutePath());
        
        if (!translationFile.exists()) {
            LOGGER.warning("DEBUG: Translation-Datei existiert noch nicht: " + translationFile.getAbsolutePath() + " - wird bei Bedarf erstellt");
            createEmptyTranslationFile();
            cacheLoaded = true;
            LOGGER.info("DEBUG: loadTranslations() beendet - Datei nicht gefunden, leere Datei erstellt");
            return;
        } else {
            LOGGER.info("DEBUG: Translation-Datei gefunden: " + translationFile.getAbsolutePath());
            LOGGER.info("DEBUG: Dateigröße: " + translationFile.length() + " Bytes");
            LOGGER.info("DEBUG: Datei lesbar: " + translationFile.canRead());
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(translationFile))) {
            LOGGER.info("DEBUG: BufferedReader erfolgreich erstellt");
            
            String line;
            int lineNumber = 0;
            int loadedMappings = 0;
            int skippedLines = 0;
            int errorLines = 0;
            
            LOGGER.info("DEBUG: Beginne Zeile-für-Zeile-Parsing...");
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                LOGGER.info("DEBUG: Zeile " + lineNumber + " gelesen: '" + line + "'");
                
                // Leere Zeilen und Kommentare überspringen
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    LOGGER.info("DEBUG: Zeile " + lineNumber + " übersprungen (leer oder Kommentar)");
                    skippedLines++;
                    continue;
                }
                
                LOGGER.info("DEBUG: Zeile " + lineNumber + " wird geparst: '" + line + "'");
                
                // Zeile parsen: signalId:providerName
                String[] parts = line.split(DELIMITER, 2);
                LOGGER.info("DEBUG: Nach split() - Anzahl Teile: " + parts.length);
                
                if (parts.length >= 1) {
                    LOGGER.info("DEBUG: Teil[0] (Signal-ID): '" + parts[0] + "'");
                }
                if (parts.length >= 2) {
                    LOGGER.info("DEBUG: Teil[1] (Provider-Name): '" + parts[1] + "'");
                }
                
                if (parts.length == 2) {
                    String signalId = parts[0].trim();
                    String providerName = parts[1].trim();
                    
                    LOGGER.info("DEBUG: Getrimmt - Signal-ID: '" + signalId + "', Provider-Name: '" + providerName + "'");
                    
                    if (!signalId.isEmpty() && !providerName.isEmpty()) {
                        // HIER IST DIE WICHTIGE STELLE - MAP.PUT()
                        LOGGER.info("DEBUG: Füge Mapping hinzu: '" + signalId + "' -> '" + providerName + "'");
                        idToNameMap.put(signalId, providerName);
                        loadedMappings++;
                        
                        // VERIFIKATION: Sofort wieder lesen
                        String verification = idToNameMap.get(signalId);
                        LOGGER.info("DEBUG: Verifikation - Mapping gespeichert: '" + signalId + "' -> '" + verification + "'");
                        
                        LOGGER.info("Translation geladen: '" + signalId + "' -> '" + providerName + "'");
                    } else {
                        LOGGER.warning("Ungültige Translation in Zeile " + lineNumber + ": '" + line + "' - Signal-ID oder Provider-Name leer");
                        LOGGER.warning("DEBUG: Signal-ID leer: " + signalId.isEmpty() + ", Provider-Name leer: " + providerName.isEmpty());
                        errorLines++;
                    }
                } else {
                    LOGGER.warning("Ungültiges Format in Zeile " + lineNumber + ": '" + line + "' - Erwartet: 'signalId:providerName'");
                    LOGGER.warning("DEBUG: Anzahl Teile nach split('" + DELIMITER + "'): " + parts.length);
                    errorLines++;
                }
            }
            
            cacheLoaded = true;
            
            LOGGER.info("=== DEBUG: PARSING-ZUSAMMENFASSUNG ===");
            LOGGER.info("DEBUG: Gesamte Zeilen: " + lineNumber);
            LOGGER.info("DEBUG: Übersprungene Zeilen (Kommentare/leer): " + skippedLines);
            LOGGER.info("DEBUG: Fehlerhafte Zeilen: " + errorLines);
            LOGGER.info("DEBUG: Erfolgreich geladene Mappings: " + loadedMappings);
            LOGGER.info("DEBUG: Map-Größe nach dem Laden: " + idToNameMap.size());
            
            // ZEIGE ALLE GELADENEN MAPPINGS
            LOGGER.info("=== DEBUG: ALLE GELADENEN MAPPINGS ===");
            for (Map.Entry<String, String> entry : idToNameMap.entrySet()) {
                LOGGER.info("DEBUG: Map-Entry: '" + entry.getKey() + "' -> '" + entry.getValue() + "'");
            }
            
            LOGGER.info("ID-Translation geladen: " + loadedMappings + " Zuordnungen aus " + translationFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Laden der ID-Translation-Datei: " + e.getMessage());
            LOGGER.severe("DEBUG: IOException Details: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            cacheLoaded = true; // Cache als geladen markieren, auch wenn leer
        }
        
        LOGGER.info("=== DEBUG: loadTranslations() ENDE ===");
    }
    
    /**
     * Gibt den Provider-Namen für eine Signal-ID zurück
     */
    /**
     * Gibt den Provider-Namen für eine Signal-ID zurück
     * VERBESSERTE DEBUG-VERSION - zeigt auch die Values
     */
    public String getProviderName(String signalId) {
        LOGGER.info("=== DEBUG: getProviderName('" + signalId + "') START ===");
        
        if (signalId == null || signalId.trim().isEmpty()) {
            LOGGER.warning("DEBUG: Leere oder null Signal-ID übergeben");
            return "Unbekannt";
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            LOGGER.info("DEBUG: Cache noch nicht geladen, lade jetzt...");
            loadTranslations();
        } else {
            LOGGER.info("DEBUG: Cache bereits geladen");
        }
        
        String cleanSignalId = signalId.trim();
        LOGGER.info("DEBUG: Bereinigte Signal-ID: '" + cleanSignalId + "'");
        LOGGER.info("DEBUG: Map-Größe beim Lookup: " + idToNameMap.size());
        
        // VERBESSERT: ZEIGE ALLE KEYS UND VALUES IN DER MAP
        LOGGER.info("DEBUG: Alle Key-Value-Paare in der Map:");
        for (Map.Entry<String, String> entry : idToNameMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            boolean matches = key.equals(cleanSignalId);
            LOGGER.info("DEBUG:   '" + key + "' -> '" + value + "' (equals test: " + matches + ")");
        }
        
        // ZUSÄTZLICH: DIREKTER GET-TEST
        String providerName = idToNameMap.get(cleanSignalId);
        LOGGER.info("DEBUG: DIREKTER MAP.GET TEST:");
        LOGGER.info("DEBUG: Map.get('" + cleanSignalId + "') Ergebnis: '" + providerName + "'");
        
        // ZUSÄTZLICH: CONTAINSKEY TEST
        boolean containsKey = idToNameMap.containsKey(cleanSignalId);
        LOGGER.info("DEBUG: Map.containsKey('" + cleanSignalId + "'): " + containsKey);
        
        // ZUSÄTZLICH: ZEIGE ALLE KEYS ALS STRING-ARRAY
        LOGGER.info("DEBUG: Alle Keys als String-Array:");
        String[] keyArray = idToNameMap.keySet().toArray(new String[0]);
        for (int i = 0; i < keyArray.length; i++) {
            LOGGER.info("DEBUG:   [" + i + "] '" + keyArray[i] + "' (length: " + keyArray[i].length() + ")");
        }
        
        // ZUSÄTZLICH: ZEIGE STRING-DETAILS DER GESUCHTEN SIGNAL-ID
        LOGGER.info("DEBUG: Signal-ID String-Details:");
        LOGGER.info("DEBUG:   cleanSignalId: '" + cleanSignalId + "'");
        LOGGER.info("DEBUG:   cleanSignalId.length(): " + cleanSignalId.length());
        LOGGER.info("DEBUG:   cleanSignalId bytes: " + java.util.Arrays.toString(cleanSignalId.getBytes()));
        
        LOGGER.info("DEBUG: Signal-ID '" + cleanSignalId + "' -> Provider-Name: '" + providerName + "' (Map-Größe: " + idToNameMap.size() + ")");
        
        if (providerName != null && !providerName.trim().isEmpty()) {
            LOGGER.info("Provider-Name für Signal " + cleanSignalId + " gefunden: " + providerName);
            LOGGER.info("=== DEBUG: getProviderName() ENDE - ERFOLG ===");
            return providerName;
        } else {
            LOGGER.info("Kein Provider-Name für Signal " + cleanSignalId + " gefunden - verwende 'Unbekannt'");
            LOGGER.info("DEBUG: Grund: providerName ist " + (providerName == null ? "null" : "empty/whitespace"));
            LOGGER.info("=== DEBUG: getProviderName() ENDE - NICHT GEFUNDEN ===");
            return "Unbekannt";
        }
    
    }
    
    /**
     * Speichert die aktuellen ID-zu-Name Zuordnungen in die Datei
     */
    public void saveTranslations() {
        LOGGER.info("=== DEBUG: saveTranslations() START ===");
        
        try {
            // Config-Verzeichnis erstellen falls es nicht existiert
            File configDir = translationFile.getParentFile();
            LOGGER.info("DEBUG: Config-Verzeichnis: " + configDir.getAbsolutePath());
            LOGGER.info("DEBUG: Config-Verzeichnis existiert: " + configDir.exists());
            
            if (!configDir.exists()) {
                LOGGER.info("DEBUG: Erstelle Config-Verzeichnis...");
                if (configDir.mkdirs()) {
                    LOGGER.info("Config-Verzeichnis erstellt: " + configDir.getAbsolutePath());
                } else {
                    LOGGER.severe("Konnte Config-Verzeichnis nicht erstellen: " + configDir.getAbsolutePath());
                    return;
                }
            }
            
            LOGGER.info("DEBUG: Beginne Schreibvorgang in: " + translationFile.getAbsolutePath());
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(translationFile))) {
                // Header schreiben
                writer.write("# ID-zu-Provider-Name Translation\n");
                writer.write("# Format: signalId:providerName\n");
                writer.write("# Generiert von MqlRealMonitor\n");
                writer.write("#\n");
                
                LOGGER.info("DEBUG: Header geschrieben, schreibe " + idToNameMap.size() + " Mappings...");
                
                // Zuordnungen schreiben (sortiert nach Signal-ID)
                idToNameMap.entrySet().stream()
                    .sorted(Map.Entry.<String, String>comparingByKey())
                    .forEach(entry -> {
                        try {
                            String line = entry.getKey() + DELIMITER + entry.getValue();
                            LOGGER.info("DEBUG: Schreibe Zeile: '" + line + "'");
                            writer.write(line + "\n");
                        } catch (IOException e) {
                            LOGGER.warning("Fehler beim Schreiben der Translation: " + entry.getKey() + " -> " + entry.getValue());
                        }
                    });
                
                writer.flush();
                LOGGER.info("ID-Translation gespeichert: " + idToNameMap.size() + " Zuordnungen in " + translationFile.getAbsolutePath());
                
            }
            
        } catch (IOException e) {
            LOGGER.severe("Fehler beim Speichern der ID-Translation-Datei: " + e.getMessage());
            LOGGER.severe("DEBUG: IOException Details beim Speichern: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        
        LOGGER.info("=== DEBUG: saveTranslations() ENDE ===");
    }
    
    /**
     * Erstellt eine leere Translation-Datei mit Beispielen
     */
    private void createEmptyTranslationFile() {
        LOGGER.info("=== DEBUG: createEmptyTranslationFile() START ===");
        
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
            e.printStackTrace();
        }
        
        LOGGER.info("=== DEBUG: createEmptyTranslationFile() ENDE ===");
    }
    
    /**
     * Fügt eine neue ID-zu-Name Zuordnung hinzu oder aktualisiert eine bestehende
     */
    public boolean addOrUpdateMapping(String signalId, String providerName) {
        LOGGER.info("=== DEBUG: addOrUpdateMapping('" + signalId + "', '" + providerName + "') START ===");
        
        if (signalId == null || signalId.trim().isEmpty() || 
            providerName == null || providerName.trim().isEmpty()) {
            LOGGER.warning("Ungültige Parameter für ID-Translation: signalId='" + signalId + "', providerName='" + providerName + "'");
            return false;
        }
        
        // Cache laden falls noch nicht geschehen
        if (!cacheLoaded) {
            LOGGER.info("DEBUG: Cache nicht geladen beim addOrUpdateMapping, lade jetzt...");
            loadTranslations();
        }
        
        String cleanSignalId = signalId.trim();
        String cleanProviderName = providerName.trim();
        
        // Prüfen ob bereits vorhanden
        String existingName = idToNameMap.get(cleanSignalId);
        boolean isNewMapping = (existingName == null);
        
        LOGGER.info("DEBUG: Existierender Name für '" + cleanSignalId + "': '" + existingName + "'");
        LOGGER.info("DEBUG: Ist neues Mapping: " + isNewMapping);
        
        // Mapping hinzufügen/aktualisieren
        idToNameMap.put(cleanSignalId, cleanProviderName);
        LOGGER.info("DEBUG: Mapping in Map gespeichert: '" + cleanSignalId + "' -> '" + cleanProviderName + "'");
        
        if (isNewMapping) {
            LOGGER.info("Neue ID-Translation hinzugefügt: " + cleanSignalId + " -> " + cleanProviderName);
        } else if (!existingName.equals(cleanProviderName)) {
            LOGGER.info("ID-Translation aktualisiert: " + cleanSignalId + " -> " + cleanProviderName + " (war: " + existingName + ")");
        } else {
            LOGGER.fine("ID-Translation unverändert: " + cleanSignalId + " -> " + cleanProviderName);
            LOGGER.info("=== DEBUG: addOrUpdateMapping() ENDE - UNVERÄNDERT ===");
            return false; // Keine Änderung
        }
        
        // Automatisch speichern bei Änderungen
        LOGGER.info("DEBUG: Rufe saveTranslations() auf...");
        saveTranslations();
        
        LOGGER.info("=== DEBUG: addOrUpdateMapping() ENDE - ERFOLG ===");
        return isNewMapping;
    }
    
    /**
     * Entfernt eine ID-zu-Name Zuordnung
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
     */
    public int getMappingCount() {
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return idToNameMap.size();
    }
    
    /**
     * Gibt alle Signal-IDs zurück für die Zuordnungen existieren
     */
    public java.util.Set<String> getAllSignalIds() {
        if (!cacheLoaded) {
            loadTranslations();
        }
        
        return new java.util.HashSet<>(idToNameMap.keySet());
    }
    
    /**
     * Gibt alle Zuordnungen als Map zurück (Kopie)
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
     */
    public String getTranslationFilePath() {
        return translationFile.getAbsolutePath();
    }
    
    /**
     * Gibt Diagnose-Informationen zurück
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