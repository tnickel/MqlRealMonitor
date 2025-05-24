package com.mql.realmonitor.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.ConsoleHandler;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility-Klasse für MqlRealMonitor
 * Sammelt allgemeine Hilfsfunktionen und Konstanten
 */
public class MqlUtils {
    
    private static final Logger LOGGER = Logger.getLogger(MqlUtils.class.getName());
    
    // Konstanten
    public static final String VERSION = "1.0.0";
    public static final String APPLICATION_NAME = "MqlRealMonitor";
    public static final String LOG_DIR = "logs";
    public static final String LOG_FILE_PREFIX = "mql_monitor";
    
    // Formatters
    public static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static final DateTimeFormatter FILE_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyyMMdd");
    
    public static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Regex Pattern für Validierungen
    public static final Pattern SIGNAL_ID_PATTERN = Pattern.compile("^[0-9]+$");
    public static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    public static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    
    // Verhindere Instanziierung
    private MqlUtils() {
        throw new AssertionError("Utility-Klasse darf nicht instanziiert werden");
    }
    
    /**
     * Initialisiert das Logging-System
     * 
     * @param logLevel Das gewünschte Log-Level
     * @param enableFileLogging Ob in Datei geloggt werden soll
     */
    public static void initializeLogging(Level logLevel, boolean enableFileLogging) {
        try {
            // Root Logger konfigurieren
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(logLevel);
            
            // Entferne Standard-Handler
            java.util.logging.Handler[] handlers = rootLogger.getHandlers();
            for (java.util.logging.Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }
            
            // Console Handler hinzufügen
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(logLevel);
            consoleHandler.setFormatter(new SimpleFormatter());
            rootLogger.addHandler(consoleHandler);
            
            // File Handler hinzufügen falls gewünscht
            if (enableFileLogging) {
                setupFileLogging(rootLogger, logLevel);
            }
            
            // Startzeit als System Property setzen
            System.setProperty("mql.start.time", String.valueOf(System.currentTimeMillis()));
            
            LOGGER.info("Logging initialisiert - Level: " + logLevel + ", File-Logging: " + enableFileLogging);
            
        } catch (Exception e) {
            System.err.println("Fehler beim Initialisieren des Loggings: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Richtet File-Logging ein
     * 
     * @param rootLogger Der Root-Logger
     * @param logLevel Das Log-Level
     */
    private static void setupFileLogging(Logger rootLogger, Level logLevel) throws IOException {
        // Log-Verzeichnis erstellen
        Path logDir = Paths.get(LOG_DIR);
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
        
        // Log-Datei-Name mit Datum
        String logFileName = String.format("%s/%s_%s.log", 
                                         LOG_DIR, 
                                         LOG_FILE_PREFIX, 
                                         LocalDateTime.now().format(FILE_DATE_FORMATTER));
        
        // File Handler erstellen
        FileHandler fileHandler = new FileHandler(logFileName, true); // append = true
        fileHandler.setLevel(logLevel);
        fileHandler.setFormatter(new MqlLogFormatter());
        rootLogger.addHandler(fileHandler);
        
        LOGGER.info("File-Logging aktiviert: " + logFileName);
    }
    
    /**
     * Validiert eine Signal-ID
     * 
     * @param signalId Die zu validierende Signal-ID
     * @return true wenn gültig, false sonst
     */
    public static boolean isValidSignalId(String signalId) {
        if (signalId == null || signalId.trim().isEmpty()) {
            return false;
        }
        
        return SIGNAL_ID_PATTERN.matcher(signalId.trim()).matches();
    }
    
    /**
     * Validiert einen Währungscode
     * 
     * @param currency Der zu validierende Währungscode
     * @return true wenn gültig, false sonst
     */
    public static boolean isValidCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            return false;
        }
        
        return CURRENCY_PATTERN.matcher(currency.trim().toUpperCase()).matches();
    }
    
    /**
     * Validiert einen Dezimalwert
     * 
     * @param value Der zu validierende Wert als String
     * @return true wenn gültig, false sonst
     */
    public static boolean isValidDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        return DECIMAL_PATTERN.matcher(value.trim()).matches();
    }
    
    /**
     * Formatiert einen Geldbetrag
     * 
     * @param amount Der Betrag
     * @param currency Die Währung
     * @param showPositiveSign Ob positive Vorzeichen angezeigt werden sollen
     * @return Formatierter Geldbetrag
     */
    public static String formatCurrency(double amount, String currency, boolean showPositiveSign) {
        if (currency == null || currency.trim().isEmpty()) {
            currency = "USD";
        }
        
        String sign = "";
        if (showPositiveSign && amount > 0) {
            sign = "+";
        }
        
        return String.format("%s%.2f %s", sign, amount, currency.toUpperCase());
    }
    
    /**
     * Formatiert einen Geldbetrag (ohne positives Vorzeichen)
     * 
     * @param amount Der Betrag
     * @param currency Die Währung
     * @return Formatierter Geldbetrag
     */
    public static String formatCurrency(double amount, String currency) {
        return formatCurrency(amount, currency, false);
    }
    
    /**
     * Parst einen Geldbetrag aus einem String
     * 
     * @param currencyString Der String mit Geldbetrag
     * @return Der geparste Betrag oder null bei Fehlern
     */
    public static Double parseCurrencyAmount(String currencyString) {
        if (currencyString == null || currencyString.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Entferne alles außer Zahlen, Dezimalpunkt und Minus
            String cleanValue = currencyString
                .replaceAll("[^0-9.+-]", "")
                .trim();
            
            if (cleanValue.isEmpty()) {
                return null;
            }
            
            return Double.parseDouble(cleanValue);
            
        } catch (NumberFormatException e) {
            LOGGER.warning("Konnte Geldbetrag nicht parsen: " + currencyString);
            return null;
        }
    }
    
    /**
     * Extrahiert Währung aus einem Geldbetrag-String
     * 
     * @param currencyString Der String mit Geldbetrag
     * @return Die Währung oder null bei Fehlern
     */
    public static String extractCurrency(String currencyString) {
        if (currencyString == null || currencyString.trim().isEmpty()) {
            return null;
        }
        
        // Suche nach 3-Buchstaben Währungscode
        Matcher matcher = CURRENCY_PATTERN.matcher(currencyString.toUpperCase());
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }
    
    /**
     * Erstellt einen Backup-Dateinamen mit Zeitstempel
     * 
     * @param originalFileName Der ursprüngliche Dateiname
     * @return Backup-Dateiname mit Zeitstempel
     */
    public static String createBackupFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            originalFileName = "backup";
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Dateiendung separieren
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot > 0) {
            String name = originalFileName.substring(0, lastDot);
            String extension = originalFileName.substring(lastDot);
            return name + "_backup_" + timestamp + extension;
        } else {
            return originalFileName + "_backup_" + timestamp;
        }
    }
    
    /**
     * Berechnet die Dateigröße in lesbarem Format
     * 
     * @param bytes Anzahl Bytes
     * @return Formatierte Dateigröße
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    /**
     * Validiert einen Dateipfad
     * 
     * @param filePath Der zu validierende Pfad
     * @return true wenn gültig, false sonst
     */
    public static boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        try {
            Path path = Paths.get(filePath);
            return path.isAbsolute() || !path.toString().contains("..");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Erstellt ein Verzeichnis falls es nicht existiert
     * 
     * @param dirPath Der Verzeichnispfad
     * @return true wenn erfolgreich erstellt oder bereits vorhanden
     */
    public static boolean ensureDirectoryExists(String dirPath) {
        if (dirPath == null || dirPath.trim().isEmpty()) {
            return false;
        }
        
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                LOGGER.info("Verzeichnis erstellt: " + dirPath);
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Verzeichnis nicht erstellen: " + dirPath, e);
            return false;
        }
    }
    
    /**
     * Liest eine Textdatei vollständig
     * 
     * @param filePath Der Dateipfad
     * @return Der Dateiinhalt oder null bei Fehlern
     */
    public static String readTextFile(String filePath) {
        if (!isValidFilePath(filePath)) {
            return null;
        }
        
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            }
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Datei nicht lesen: " + filePath, e);
            return null;
        }
    }
    
    /**
     * Schreibt Text in eine Datei
     * 
     * @param filePath Der Dateipfad
     * @param content Der zu schreibende Inhalt
     * @param append Ob an die Datei angehängt werden soll
     * @return true wenn erfolgreich geschrieben
     */
    public static boolean writeTextFile(String filePath, String content, boolean append) {
        if (!isValidFilePath(filePath) || content == null) {
            return false;
        }
        
        try {
            Path path = Paths.get(filePath);
            Path parentDir = path.getParent();
            
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            if (append) {
                Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8);
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Konnte Datei nicht schreiben: " + filePath, e);
            return false;
        }
    }
    
    /**
     * Berechnet einen einfachen Hash-Wert für einen String
     * 
     * @param input Der Input-String
     * @return Hash-Wert
     */
    public static int calculateSimpleHash(String input) {
        if (input == null) {
            return 0;
        }
        
        int hash = 0;
        for (char c : input.toCharArray()) {
            hash = 31 * hash + c;
        }
        
        return Math.abs(hash);
    }
    
    /**
     * Gibt die Anwendungsversion zurück
     * 
     * @return Die Version
     */
    public static String getVersion() {
        return VERSION;
    }
    
    /**
     * Gibt den Anwendungsnamen zurück
     * 
     * @return Der Anwendungsname
     */
    public static String getApplicationName() {
        return APPLICATION_NAME;
    }
    
    /**
     * Gibt eine Anwendungsinfo zurück
     * 
     * @return Anwendungsinfo als String
     */
    public static String getApplicationInfo() {
        return String.format("%s Version %s", APPLICATION_NAME, VERSION);
    }
    
    /**
     * Benutzerdefinierter Log-Formatter
     */
    public static class MqlLogFormatter extends java.util.logging.Formatter {
        
        @Override
        public String format(java.util.logging.LogRecord record) {
            StringBuilder sb = new StringBuilder();
            
            // Zeitstempel
            sb.append(LocalDateTime.now().format(LOG_TIMESTAMP_FORMATTER));
            sb.append(" ");
            
            // Log-Level
            sb.append("[").append(record.getLevel()).append("]");
            sb.append(" ");
            
            // Logger-Name (verkürzt)
            String loggerName = record.getLoggerName();
            if (loggerName != null && loggerName.length() > 30) {
                loggerName = "..." + loggerName.substring(loggerName.length() - 27);
            }
            sb.append("[").append(loggerName).append("]");
            sb.append(" ");
            
            // Nachricht
            sb.append(formatMessage(record));
            sb.append(System.lineSeparator());
            
            // Exception falls vorhanden
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                sb.append(sw.toString());
            }
            
            return sb.toString();
        }
    }
}