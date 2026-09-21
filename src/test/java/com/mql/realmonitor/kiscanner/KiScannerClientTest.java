package com.mql.realmonitor.kiscanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für den KiScanner REST-Client.
 *
 * Ein lokaler HttpServer liefert Fixture-Antworten des Scanner-Protokolls
 * (GET /api/v1/signals) — keine echten Netzwerkzugriffe.
 */
class KiScannerClientTest {

    private HttpServer server;
    private MqlRealMonitorConfig config;
    private KiScannerClient client;

    private String responseBody;
    private int responseStatus;
    private String serverToken; // ungleich null = Server verlangt X-User-Key

    @BeforeEach
    void setUp() throws Exception {
        config = new MqlRealMonitorConfig("target/test-mql-testdata");
        client = new KiScannerClient(config);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/signals", exchange -> {
            if (serverToken != null
                    && !serverToken.equals(exchange.getRequestHeaders().getFirst("X-User-Key"))) {
                byte[] denied = "{\"error\":\"Token fehlt oder ist falsch\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, denied.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(denied);
                }
                return;
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        config.setKiScannerBaseUrl("http://127.0.0.1:" + port);
        serverToken = null;
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testParseFiltertNurGruenUndGelb() throws Exception {
        String json = "{\"service\":\"mqlkiscanner\",\"count\":4,\"signals\":["
            + "{\"signalId\":100,\"name\":\"Gold Spike\",\"platform\":\"MT4\",\"ampel\":\"gruen\",\"ampelEmoji\":\"🟢\",\"score\":2.0},"
            + "{\"signalId\":200,\"name\":\"KiraCat\",\"platform\":\"MT4\",\"ampel\":\"gelb\",\"ampelEmoji\":\"🟡\"},"
            + "{\"signalId\":300,\"name\":\"Pure Gold\",\"platform\":\"MT5\",\"ampel\":\"rot\",\"ampelEmoji\":\"🔴\"},"
            + "{\"signalId\":400,\"name\":\"Ohne Ampel\"}"
            + "]}";

        KiScannerFetchResult result = client.parseSignalsJson(json);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getTotalCount());
        List<KiScannerSignal> signale = result.getSignals();
        assertEquals(2, signale.size(), "nur grün und gelb dürfen durchkommen");
        assertEquals(100, signale.get(0).getSignalId());
        assertEquals("gruen", signale.get(0).getAmpel());
        assertEquals("Gold Spike", signale.get(0).getName());
        assertEquals(200, signale.get(1).getSignalId());
        assertEquals("gelb", signale.get(1).getAmpel());
        assertTrue(signale.get(0).isGruenOderGelb());
        assertTrue(signale.get(1).isGruenOderGelb());
    }

    @Test
    void testParseOhneSignalsArrayWirftException() {
        assertThrows(Exception.class, () -> client.parseSignalsJson("{\"count\":0}"));
        assertThrows(Exception.class, () -> client.parseSignalsJson("kein json"));
    }

    @Test
    void testFetchGegenLokalenServer() {
        responseBody = "{\"service\":\"mqlkiscanner\",\"count\":1,\"signals\":["
            + "{\"signalId\":2349227,\"name\":\"Gold Spike\",\"platform\":\"MT4\","
            + "\"ampel\":\"gruen\",\"ampelEmoji\":\"🟢\",\"score\":2.0}]}";
        responseStatus = 200;

        KiScannerFetchResult result = client.fetchGreenAndYellowSignals();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getSignals().size());
        assertEquals(2349227, result.getSignals().get(0).getSignalId());
    }

    @Test
    void testFetchBeiHttp404LiefertFehler() {
        responseBody = "{\"error\":\"unbekannter Pfad\"}";
        responseStatus = 404;

        KiScannerFetchResult result = client.fetchGreenAndYellowSignals();

        assertFalse(result.isSuccess());
        assertEquals(404, result.getHttpStatusCode());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("HTTP 404"));
    }

    @Test
    void testFetchBeiUngueltigemJsonLiefertFehler() {
        responseBody = "<html>kein json</html>";
        responseStatus = 200;

        KiScannerFetchResult result = client.fetchGreenAndYellowSignals();

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Ungültige KiScanner-Antwort"));
    }

    @Test
    void testConfigBautSignaleUrlMitFilter() {
        config.setKiScannerBaseUrl("http://127.0.0.1:8611/");
        String url = config.buildKiScannerSignalsUrl();
        assertEquals("http://127.0.0.1:8611/api/v1/signals?ampel=gruen,gelb", url);
        assertEquals("http://127.0.0.1:8611/api/v1/health", config.buildKiScannerHealthUrl());
    }

    @Test
    void testConfigDefaultUrl() throws Exception {
        // Frische Konfiguration ohne gespeicherte Properties → Default-URL
        MqlRealMonitorConfig frisch = new MqlRealMonitorConfig("target/test-mql-testdata-default");
        assertTrue(frisch.getKiScannerBaseUrl().startsWith("http://127.0.0.1:"));
    }

    @Test
    void testTokenWirdAlsHeaderGesendet() {
        responseBody = "{\"service\":\"mqlkiscanner\",\"count\":1,\"signals\":["
            + "{\"signalId\":1,\"name\":\"Mit Token\",\"ampel\":\"gruen\"}]}";
        responseStatus = 200;
        serverToken = "geheimer-key";

        // Ohne Token: 401 vom Server, klarer Fehler
        config.setKiScannerToken("");
        KiScannerFetchResult ohne = client.fetchGreenAndYellowSignals();
        assertFalse(ohne.isSuccess());
        assertEquals(401, ohne.getHttpStatusCode());
        assertTrue(ohne.getErrorMessage().contains("kiscannerToken"));

        // Mit korrektem Token: Erfolg
        config.setKiScannerToken("geheimer-key");
        KiScannerFetchResult mit = client.fetchGreenAndYellowSignals();
        assertTrue(mit.isSuccess());
        assertEquals(1, mit.getSignals().size());
    }

    @Test
    void testHasKiScannerToken() {
        config.setKiScannerToken(null);
        assertFalse(config.hasKiScannerToken());
        config.setKiScannerToken("  ");
        assertFalse(config.hasKiScannerToken());
        config.setKiScannerToken(" key-123 ");
        assertTrue(config.hasKiScannerToken());
        assertEquals("key-123", config.getKiScannerToken()); // getrimmt
    }

    @Test
    void testTokenRoundtripUeberPropertiesDatei() throws Exception {
        // Speichern und Laden muss den Token unverändert durchreichen
        config.setKiScannerBaseUrl("http://127.0.0.1:8611");
        config.setKiScannerToken("abc123 !_special/chars?");
        config.saveConfig();

        MqlRealMonitorConfig geladen = new MqlRealMonitorConfig("target/test-mql-testdata");
        geladen.loadConfig();
        assertEquals("abc123 !_special/chars?", geladen.getKiScannerToken());
        assertEquals("http://127.0.0.1:8611", geladen.getKiScannerBaseUrl());
    }
}
