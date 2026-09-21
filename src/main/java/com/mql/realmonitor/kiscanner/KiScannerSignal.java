package com.mql.realmonitor.kiscanner;

/**
 * NEU: Datenmodell für ein Signal des MqlKiScanner (REST /api/v1/signals).
 * 
 * Enthält die Gesamt-Ampel des Scanners als ASCII-Kürzel ("gruen", "gelb",
 * "orange", "rot", "keine_daten", "ausgeschlossen") und als Original-Emoji
 * sowie die Kernkennzahlen für Anzeige und Diagnose.
 */
public class KiScannerSignal {
    
    public static final String AMPEL_GRUEN = "gruen";
    public static final String AMPEL_GELB = "gelb";
    
    private long signalId;
    private String name;
    private String platform;
    private String url;
    private String ampel;        // ASCII-Kürzel, z. B. "gruen"
    private String ampelEmoji;   // Original, z. B. "🟢"
    private Double score;        // Risiko-Score 1-10 (kleiner = besser), kann null sein
    private String urteil;
    private String kurzfassung;
    
    public KiScannerSignal() {
    }
    
    public KiScannerSignal(long signalId, String name, String ampel) {
        this.signalId = signalId;
        this.name = name;
        this.ampel = ampel;
    }
    
    /**
     * NEU: Ist dieses Signal laut Scanner grün oder gelb (überwachungswürdig)?
     */
    public boolean isGruenOderGelb() {
        return AMPEL_GRUEN.equals(ampel) || AMPEL_GELB.equals(ampel);
    }
    
    // Getter/Setter (von Jackson für das JSON-Parsing benötigt)
    
    public long getSignalId() {
        return signalId;
    }
    
    public void setSignalId(long signalId) {
        this.signalId = signalId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPlatform() {
        return platform;
    }
    
    public void setPlatform(String platform) {
        this.platform = platform;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getAmpel() {
        return ampel;
    }
    
    public void setAmpel(String ampel) {
        this.ampel = ampel;
    }
    
    public String getAmpelEmoji() {
        return ampelEmoji;
    }
    
    public void setAmpelEmoji(String ampelEmoji) {
        this.ampelEmoji = ampelEmoji;
    }
    
    public Double getScore() {
        return score;
    }
    
    public void setScore(Double score) {
        this.score = score;
    }
    
    public String getUrteil() {
        return urteil;
    }
    
    public void setUrteil(String urteil) {
        this.urteil = urteil;
    }
    
    public String getKurzfassung() {
        return kurzfassung;
    }
    
    public void setKurzfassung(String kurzfassung) {
        this.kurzfassung = kurzfassung;
    }
    
    @Override
    public String toString() {
        return "KiScannerSignal[" + signalId + ", " + name + ", " + ampel + "]";
    }
}
