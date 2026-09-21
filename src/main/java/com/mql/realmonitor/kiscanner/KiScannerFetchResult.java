package com.mql.realmonitor.kiscanner;

import java.util.Collections;
import java.util.List;

/**
 * NEU: Ergebnis eines KiScanner-Abrufs (Struktur angelehnt an DownloadResult).
 * 
 * Bei Erfolg enthält es die gefilterte Signalliste (nur Ampel grün/gelb),
 * bei Misserfolg eine klare, GUI-taugliche Fehlerbeschreibung.
 */
public class KiScannerFetchResult {
    
    private final boolean success;
    private final List<KiScannerSignal> signals;
    private final String errorMessage;
    private final int httpStatusCode;
    private final int totalCount;
    
    private KiScannerFetchResult(boolean success, List<KiScannerSignal> signals,
                                 String errorMessage, int httpStatusCode, int totalCount) {
        this.success = success;
        this.signals = signals != null ? signals : Collections.emptyList();
        this.errorMessage = errorMessage;
        this.httpStatusCode = httpStatusCode;
        this.totalCount = totalCount;
    }
    
    public static KiScannerFetchResult success(List<KiScannerSignal> signals, int totalCount) {
        return new KiScannerFetchResult(true, signals, null, 200, totalCount);
    }
    
    public static KiScannerFetchResult failure(String errorMessage, int httpStatusCode) {
        return new KiScannerFetchResult(false, null, errorMessage, httpStatusCode, 0);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public List<KiScannerSignal> getSignals() {
        return signals;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public int getHttpStatusCode() {
        return httpStatusCode;
    }
    
    /**
     * Gesamtzahl der Signale in der Scanner-Datenbank (vor dem Ampel-Filter)
     */
    public int getTotalCount() {
        return totalCount;
    }
}
