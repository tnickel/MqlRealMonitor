package com.mql.realmonitor.downloader;

/**
 * NEU: Strukturierte Rückgabe für Download-Operationen
 * Enthält Content, Erfolgs-Status und detaillierte Fehlerinformationen
 * 
 * ZWECK: Ermöglicht präzise Fehlerdiagnostik statt nur null/not-null
 */
public class DownloadResult {
    
    private final boolean success;
    private final String content;
    private final String errorMessage;
    private final int httpStatusCode;
    private final String errorType;
    
    /**
     * Privater Konstruktor - verwende static factory methods
     */
    private DownloadResult(boolean success, String content, String errorMessage, 
                          int httpStatusCode, String errorType) {
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
        this.httpStatusCode = httpStatusCode;
        this.errorType = errorType;
    }
    
    /**
     * Erstellt ein erfolgreiches Download-Ergebnis
     */
    public static DownloadResult success(String content) {
        return new DownloadResult(true, content, null, 200, null);
    }
    
    /**
     * Erstellt ein Fehler-Ergebnis mit HTTP-Statuscode
     */
    public static DownloadResult httpError(int statusCode, String url) {
        String errorMsg = "HTTP " + statusCode + " für URL: " + url;
        return new DownloadResult(false, null, errorMsg, statusCode, "HTTP_ERROR");
    }
    
    /**
     * Erstellt ein Fehler-Ergebnis für leeren Content
     */
    public static DownloadResult emptyContent(String url) {
        String errorMsg = "Leerer Content von URL: " + url;
        return new DownloadResult(false, null, errorMsg, 200, "EMPTY_CONTENT");
    }
    
    /**
     * Erstellt ein Fehler-Ergebnis für Exception
     */
    public static DownloadResult exception(Exception e, String url) {
        String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
        return new DownloadResult(false, null, errorMsg, -1, "EXCEPTION");
    }
    
    /**
     * Erstellt ein Fehler-Ergebnis für Timeout
     */
    public static DownloadResult timeout(String url) {
        String errorMsg = "Timeout beim Download von: " + url;
        return new DownloadResult(false, null, errorMsg, -1, "TIMEOUT");
    }
    
    /**
     * Erstellt ein Fehler-Ergebnis für ungültige Parameter
     */
    public static DownloadResult invalidParameter(String reason) {
        return new DownloadResult(false, null, reason, -1, "INVALID_PARAMETER");
    }
    
    // Getter-Methoden
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public int getHttpStatusCode() {
        return httpStatusCode;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    /**
     * Gibt eine kompakte Fehler-Beschreibung für die GUI zurück
     */
    public String getShortErrorDescription() {
        if (success) {
            return "OK";
        }
        
        switch (errorType) {
            case "HTTP_ERROR":
                return "HTTP " + httpStatusCode;
            case "EMPTY_CONTENT":
                return "Empty Response";
            case "TIMEOUT":
                return "Timeout";
            case "EXCEPTION":
                return "Connection Error";
            case "INVALID_PARAMETER":
                return "Invalid Parameter";
            default:
                return "Unknown Error";
        }
    }
    
    /**
     * Gibt eine detaillierte Fehler-Beschreibung für Logs zurück
     */
    public String getDetailedErrorDescription() {
        if (success) {
            return "Success";
        }
        
        return String.format("[%s] %s (HTTP: %d)", 
                           errorType, errorMessage, httpStatusCode);
    }
    
    @Override
    public String toString() {
        if (success) {
            return "DownloadResult{success=true, contentLength=" + 
                   (content != null ? content.length() : 0) + "}";
        } else {
            return "DownloadResult{success=false, error=" + getDetailedErrorDescription() + "}";
        }
    }
}