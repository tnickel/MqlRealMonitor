package com.mql.realmonitor.exception;

/**
 * Spezifische Exception-Klasse für MqlRealMonitor
 * Behandelt alle MQL-spezifischen Fehler und Ausnahmen
 */
public class MqlMonitorException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Fehlertypen für bessere Kategorisierung
     */
    public enum ErrorType {
        CONFIGURATION_ERROR("Konfigurationsfehler"),
        DOWNLOAD_ERROR("Download-Fehler"), 
        PARSING_ERROR("HTML-Parse-Fehler"),
        FILE_IO_ERROR("Datei-I/O-Fehler"),
        VALIDATION_ERROR("Validierungsfehler"),
        NETWORK_ERROR("Netzwerk-Fehler"),
        GUI_ERROR("GUI-Fehler"),
        UNKNOWN_ERROR("Unbekannter Fehler");
        
        private final String description;
        
        ErrorType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final ErrorType errorType;
    private final String signalId;
    private final String additionalInfo;
    
    /**
     * Konstruktor mit ErrorType
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     */
    public MqlMonitorException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.signalId = null;
        this.additionalInfo = null;
    }
    
    /**
     * Konstruktor mit ErrorType und Signal-ID
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     * @param signalId Die betroffene Signal-ID
     */
    public MqlMonitorException(ErrorType errorType, String message, String signalId) {
        super(message);
        this.errorType = errorType;
        this.signalId = signalId;
        this.additionalInfo = null;
    }
    
    /**
     * Konstruktor mit ErrorType, Signal-ID und zusätzlichen Informationen
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     * @param signalId Die betroffene Signal-ID
     * @param additionalInfo Zusätzliche Informationen
     */
    public MqlMonitorException(ErrorType errorType, String message, String signalId, String additionalInfo) {
        super(message);
        this.errorType = errorType;
        this.signalId = signalId;
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Konstruktor mit ErrorType und Ursache
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     * @param cause Die Ursache
     */
    public MqlMonitorException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.signalId = null;
        this.additionalInfo = null;
    }
    
    /**
     * Konstruktor mit ErrorType, Signal-ID und Ursache
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     * @param signalId Die betroffene Signal-ID
     * @param cause Die Ursache
     */
    public MqlMonitorException(ErrorType errorType, String message, String signalId, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.signalId = signalId;
        this.additionalInfo = null;
    }
    
    /**
     * Vollständiger Konstruktor
     * 
     * @param errorType Der Fehlertyp
     * @param message Die Fehlernachricht
     * @param signalId Die betroffene Signal-ID
     * @param additionalInfo Zusätzliche Informationen
     * @param cause Die Ursache
     */
    public MqlMonitorException(ErrorType errorType, String message, String signalId, 
                              String additionalInfo, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.signalId = signalId;
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Gibt den Fehlertyp zurück
     * 
     * @return Der Fehlertyp
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Gibt die betroffene Signal-ID zurück
     * 
     * @return Die Signal-ID oder null
     */
    public String getSignalId() {
        return signalId;
    }
    
    /**
     * Gibt zusätzliche Informationen zurück
     * 
     * @return Zusätzliche Informationen oder null
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    /**
     * Erstellt eine detaillierte Fehlermeldung
     * 
     * @return Detaillierte Fehlermeldung
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[").append(errorType.getDescription()).append("] ");
        sb.append(getMessage());
        
        if (signalId != null && !signalId.trim().isEmpty()) {
            sb.append(" (Signal-ID: ").append(signalId).append(")");
        }
        
        if (additionalInfo != null && !additionalInfo.trim().isEmpty()) {
            sb.append(" - ").append(additionalInfo);
        }
        
        return sb.toString();
    }
    
    /**
     * Gibt eine benutzerfreundliche Fehlermeldung zurück
     * 
     * @return Benutzerfreundliche Fehlermeldung
     */
    public String getUserFriendlyMessage() {
        switch (errorType) {
            case CONFIGURATION_ERROR:
                return "Konfigurationsproblem: " + getMessage() + 
                       "\nBitte prüfen Sie die Konfigurationsdateien.";
            
            case DOWNLOAD_ERROR:
                return "Download-Problem: " + getMessage() + 
                       (signalId != null ? " (Signal: " + signalId + ")" : "") +
                       "\nBitte prüfen Sie Ihre Internetverbindung.";
            
            case PARSING_ERROR:
                return "Datenanalyse-Problem: " + getMessage() + 
                       (signalId != null ? " (Signal: " + signalId + ")" : "") +
                       "\nDie Webseite hat möglicherweise ihr Format geändert.";
            
            case FILE_IO_ERROR:
                return "Dateizugriffs-Problem: " + getMessage() + 
                       "\nBitte prüfen Sie die Dateiberechtigungen.";
            
            case NETWORK_ERROR:
                return "Netzwerk-Problem: " + getMessage() + 
                       "\nBitte prüfen Sie Ihre Internetverbindung und Firewall-Einstellungen.";
            
            case VALIDATION_ERROR:
                return "Eingabefehler: " + getMessage() + 
                       "\nBitte überprüfen Sie Ihre Eingaben.";
            
            case GUI_ERROR:
                return "Oberflächen-Problem: " + getMessage() + 
                       "\nBitte starten Sie die Anwendung neu.";
            
            default:
                return "Ein unerwarteter Fehler ist aufgetreten: " + getMessage();
        }
    }
    
    /**
     * Gibt Lösungsvorschläge für den Fehler zurück
     * 
     * @return Lösungsvorschläge
     */
    public String getSuggestions() {
        switch (errorType) {
            case CONFIGURATION_ERROR:
                return "• Überprüfen Sie die Konfigurationsdatei\n" +
                       "• Stellen Sie sicher, dass alle Pfade korrekt sind\n" +
                       "• Löschen Sie die Konfigurationsdatei für Standardwerte";
            
            case DOWNLOAD_ERROR:
                return "• Prüfen Sie Ihre Internetverbindung\n" +
                       "• Überprüfen Sie Proxy-Einstellungen\n" +
                       "• Versuchen Sie es später erneut\n" +
                       "• Prüfen Sie ob die MQL5-Website erreichbar ist";
            
            case PARSING_ERROR:
                return "• Die MQL5-Website könnte ihr Format geändert haben\n" +
                       "• Überprüfen Sie die Signal-ID\n" +
                       "• Versuchen Sie eine manuelle Aktualisierung\n" +
                       "• Kontaktieren Sie den Support für Updates";
            
            case FILE_IO_ERROR:
                return "• Überprüfen Sie Dateiberechtigungen\n" +
                       "• Stellen Sie sicher, dass genug Speicherplatz verfügbar ist\n" +
                       "• Prüfen Sie ob Dateien von anderen Programmen verwendet werden\n" +
                       "• Führen Sie das Programm als Administrator aus";
            
            case NETWORK_ERROR:
                return "• Prüfen Sie Ihre Internetverbindung\n" +
                       "• Überprüfen Sie Firewall-Einstellungen\n" +
                       "• Prüfen Sie Proxy-Konfiguration\n" +
                       "• Erhöhen Sie das Timeout in den Einstellungen";
            
            case VALIDATION_ERROR:
                return "• Überprüfen Sie das Format Ihrer Eingaben\n" +
                       "• Stellen Sie sicher, dass alle Pflichtfelder ausgefüllt sind\n" +
                       "• Prüfen Sie die Favorites-Datei auf korrekte Einträge";
            
            case GUI_ERROR:
                return "• Starten Sie die Anwendung neu\n" +
                       "• Überprüfen Sie die Java-Installation\n" +
                       "• Prüfen Sie ob SWT-Bibliotheken verfügbar sind\n" +
                       "• Versuchen Sie es mit Administratorrechten";
            
            default:
                return "• Starten Sie die Anwendung neu\n" +
                       "• Überprüfen Sie die Log-Dateien\n" +
                       "• Kontaktieren Sie den Support";
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append(": ");
        sb.append(getDetailedMessage());
        
        if (getCause() != null) {
            sb.append(" (Ursache: ").append(getCause().getClass().getSimpleName())
              .append(": ").append(getCause().getMessage()).append(")");
        }
        
        return sb.toString();
    }
    
    // Static Factory Methods für häufige Fehlertypen
    
    /**
     * Erstellt eine Konfigurationsfehler-Exception
     */
    public static MqlMonitorException configurationError(String message) {
        return new MqlMonitorException(ErrorType.CONFIGURATION_ERROR, message);
    }
    
    /**
     * Erstellt eine Konfigurationsfehler-Exception mit Ursache
     */
    public static MqlMonitorException configurationError(String message, Throwable cause) {
        return new MqlMonitorException(ErrorType.CONFIGURATION_ERROR, message, cause);
    }
    
    /**
     * Erstellt eine Download-Fehler-Exception
     */
    public static MqlMonitorException downloadError(String message, String signalId) {
        return new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, message, signalId);
    }
    
    /**
     * Erstellt eine Download-Fehler-Exception mit Ursache
     */
    public static MqlMonitorException downloadError(String message, String signalId, Throwable cause) {
        return new MqlMonitorException(ErrorType.DOWNLOAD_ERROR, message, signalId, cause);
    }
    
    /**
     * Erstellt eine Parse-Fehler-Exception
     */
    public static MqlMonitorException parseError(String message, String signalId) {
        return new MqlMonitorException(ErrorType.PARSING_ERROR, message, signalId);
    }
    
    /**
     * Erstellt eine Parse-Fehler-Exception mit zusätzlichen Informationen
     */
    public static MqlMonitorException parseError(String message, String signalId, String htmlSample) {
        return new MqlMonitorException(ErrorType.PARSING_ERROR, message, signalId, htmlSample);
    }
    
    /**
     * Erstellt eine Datei-I/O-Fehler-Exception
     */
    public static MqlMonitorException fileError(String message, String filePath) {
        return new MqlMonitorException(ErrorType.FILE_IO_ERROR, message, null, filePath);
    }
    
    /**
     * Erstellt eine Datei-I/O-Fehler-Exception mit Ursache
     */
    public static MqlMonitorException fileError(String message, String filePath, Throwable cause) {
        return new MqlMonitorException(ErrorType.FILE_IO_ERROR, message, null, filePath, cause);
    }
    
    /**
     * Erstellt eine Netzwerk-Fehler-Exception
     */
    public static MqlMonitorException networkError(String message) {
        return new MqlMonitorException(ErrorType.NETWORK_ERROR, message);
    }
    
    /**
     * Erstellt eine Netzwerk-Fehler-Exception mit Ursache
     */
    public static MqlMonitorException networkError(String message, Throwable cause) {
        return new MqlMonitorException(ErrorType.NETWORK_ERROR, message, cause);
    }
    
    /**
     * Erstellt eine Validierungs-Fehler-Exception
     */
    public static MqlMonitorException validationError(String message) {
        return new MqlMonitorException(ErrorType.VALIDATION_ERROR, message);
    }
    
    /**
     * Erstellt eine GUI-Fehler-Exception
     */
    public static MqlMonitorException guiError(String message) {
        return new MqlMonitorException(ErrorType.GUI_ERROR, message);
    }
    
    /**
     * Erstellt eine GUI-Fehler-Exception mit Ursache
     */
    public static MqlMonitorException guiError(String message, Throwable cause) {
        return new MqlMonitorException(ErrorType.GUI_ERROR, message, cause);
    }
}