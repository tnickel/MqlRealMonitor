package com.mql.realmonitor.kiscanner;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NEU: REST-Client für das schreibgeschützte MqlKiScanner-Interface.
 * 
 * Holt die Signalliste mit Gesamt-Ampel vom Scanner
 * (GET {base}/api/v1/signals?ampel=gruen,gelb) und filtert defensiv
 * clientseitig noch einmal auf grün/gelb — selbst wenn der Server-Filter
 * einmal nicht greift, landen keine roten Signale in der Überwachung.
 * 
 * Bewusst direktes HttpURLConnection statt WebDownloader: Der curl-Fallback
 * des WebDownloaders ist für MQL5-Seiten gedacht und verwirft HTTP-Status-
 * codes — hier brauchen wir präzise 401/404/500-Behandlung für REST.
 */
public class KiScannerClient {
    
    private static final Logger LOGGER = Logger.getLogger(KiScannerClient.class.getName());
    
    private final MqlRealMonitorConfig config;
    private final ObjectMapper objectMapper;
    
    public KiScannerClient(MqlRealMonitorConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * NEU: Lädt alle grünen und gelben Signale vom MqlKiScanner.
     * 
     * @return Ergebnis mit gefilterter Signalliste oder klarer Fehlermeldung
     */
    public KiScannerFetchResult fetchGreenAndYellowSignals() {
        String baseUrl = config.getKiScannerBaseUrl();
        
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return KiScannerFetchResult.failure(
                "Kein KiScanner REST-URL konfiguriert (kiscannerBaseUrl in MqlRealMonitorConfig.txt)", 0);
        }
        
        String urlString = config.buildKiScannerSignalsUrl();
        LOGGER.info("Hole KiScanner-Signalliste: " + urlString);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(config.getTimeoutSeconds() * 1000);
            connection.setReadTimeout(config.getTimeoutSeconds() * 1000);
            connection.setRequestProperty("Accept", "application/json");
            
            // NEU: Optionalen Zugriffs-Token als X-User-Key senden
            // (identische Header-Konvention wie MqlDownloader-/Tradeserver-Protokoll)
            if (config.hasKiScannerToken()) {
                connection.setRequestProperty("X-User-Key", config.getKiScannerToken());
            }
            
            int status = connection.getResponseCode();
            String body = readBody(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
            
            if (status == 200) {
                if (body.trim().isEmpty()) {
                    return KiScannerFetchResult.failure(
                        "KiScanner lieferte eine leere Antwort für " + urlString, status);
                }
                try {
                    return parseSignalsJson(body);
                } catch (Exception e) {
                    LOGGER.warning("KiScanner-Antwort konnte nicht geparst werden: " + e.getMessage());
                    return KiScannerFetchResult.failure(
                        "Ungültige KiScanner-Antwort (kein erwartetes JSON): " + e.getMessage(), status);
                }
            }
            
            if (status == 401) {
                return KiScannerFetchResult.failure(
                    "KiScanner hat den Zugriff abgelehnt (HTTP 401).\n\n"
                    + "Am Scanner ist ein Zugriffs-Token hinterlegt. Tragen Sie denselben Key als "
                    + "'kiscannerToken' in die MqlRealMonitorConfig.txt ein.", status);
            }
            
            String fehler = "KiScanner-Fehler HTTP " + status + " für " + urlString;
            if (!body.isEmpty()) {
                fehler += "\nAntwort: " + body.substring(0, Math.min(body.length(), 300));
            }
            LOGGER.warning(fehler);
            return KiScannerFetchResult.failure(fehler, status);
            
        } catch (java.net.SocketTimeoutException e) {
            return nichtErreichbar("Timeout", urlString, 0);
        } catch (java.net.ConnectException e) {
            return nichtErreichbar("Verbindung abgelehnt", urlString, 0);
        } catch (java.io.IOException e) {
            LOGGER.log(Level.WARNING, "KiScanner-Abruf fehlgeschlagen: " + e.getMessage(), e);
            return nichtErreichbar(e.getClass().getSimpleName() + ": " + e.getMessage(), urlString, 0);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unerwarteter Fehler beim KiScanner-Abruf", e);
            return KiScannerFetchResult.failure(
                "Unerwarteter Fehler: " + e.getMessage(), 0);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * NEU: Einheitliche Meldung für Verbindungsprobleme mit Bedienhinweis
     */
    private KiScannerFetchResult nichtErreichbar(String ursache, String urlString, int status) {
        String fehler = "KiScanner nicht erreichbar (" + ursache + "): " + urlString + "\n\n"
            + "Läuft der MqlKiScanner? Streamlit-App starten (start.bat) — die REST-API "
            + "läuft automatisch auf " + config.getKiScannerBaseUrl() + ".\n"
            + "URL anpassbar über kiscannerBaseUrl in der MqlRealMonitorConfig.txt.";
        LOGGER.warning("KiScanner nicht erreichbar: " + ursache);
        return KiScannerFetchResult.failure(fehler, status);
    }
    
    /**
     * NEU: Parst die /api/v1/signals-Antwort und filtert auf Ampel grün/gelb.
     * Paket-sichtbar für Unit-Tests.
     */
    KiScannerFetchResult parseSignalsJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        
        if (root == null || !root.has("signals") || !root.get("signals").isArray()) {
            throw new IllegalArgumentException("Antwort ohne 'signals'-Array");
        }
        
        int totalCount = root.has("count") ? root.get("count").asInt(-1) : -1;
        
        List<KiScannerSignal> alle = new ArrayList<>();
        List<KiScannerSignal> gruenGelb = new ArrayList<>();
        
        for (JsonNode node : root.get("signals")) {
            KiScannerSignal signal = parseSignal(node);
            if (signal != null && signal.getSignalId() > 0) {
                alle.add(signal);
                if (signal.isGruenOderGelb()) {
                    gruenGelb.add(signal);
                }
            }
        }
        
        LOGGER.info("KiScanner-Signale: " + alle.size() + " insgesamt, davon "
            + gruenGelb.size() + " grün/gelb");
        
        return KiScannerFetchResult.success(gruenGelb, totalCount >= 0 ? totalCount : alle.size());
    }
    
    /**
     * NEU: Parst ein einzelnes Signal-Objekt; null bei unvollständigen Daten.
     */
    private KiScannerSignal parseSignal(JsonNode node) {
        if (node == null || !node.has("signalId")) {
            return null;
        }
        
        KiScannerSignal signal = new KiScannerSignal();
        signal.setSignalId(node.get("signalId").asLong());
        signal.setName(textOrEmpty(node, "name"));
        signal.setPlatform(textOrEmpty(node, "platform"));
        signal.setUrl(textOrEmpty(node, "url"));
        signal.setAmpel(textOrEmpty(node, "ampel"));
        signal.setAmpelEmoji(textOrEmpty(node, "ampelEmoji"));
        signal.setUrteil(textOrEmpty(node, "urteil"));
        signal.setKurzfassung(textOrEmpty(node, "kurzfassung"));
        
        JsonNode score = node.get("score");
        signal.setScore(score != null && score.isNumber() ? score.asDouble() : null);
        
        return signal;
    }
    
    private String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : "";
    }
    
    /**
     * NEU: Liest einen Antwort-Stream als UTF-8 (leer bei null/kein Body)
     */
    private String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
