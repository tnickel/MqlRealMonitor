package com.mql.realmonitor.downloader;

import com.mql.realmonitor.config.MqlRealMonitorConfig;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NEU: Tests für den Umgang des WebDownloaders mit verschwundenen Signalen.
 *
 * Ein lokaler HttpServer antwortet mit HTTP 404 — genau wie MQL5 bei
 * gelöschten Signalen. Kernforderung: der 404 muss als klarer HTTP-Fehler
 * durchgereicht werden (kein curl-Fallback, der die Fehlerseite als
 * Erfolg deklarieren würde).
 */
class WebDownloader404Test {

    private HttpServer server;
    private WebDownloader downloader;

    @BeforeEach
    void setUp() throws Exception {
        MqlRealMonitorConfig config = new MqlRealMonitorConfig("target/test-webdownloader-404");
        downloader = new WebDownloader(config);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/signals/geloescht", exchange -> {
            byte[] body = "<html>Signal nicht gefunden</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testHttp404WirdAlsKlarerFehlerDurchgereicht() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/signals/geloescht";

        DownloadResult result = downloader.downloadSignalPage("123456", url);

        // Der 404 darf nicht durch den curl-Fallback in einen "Erfolg"
        // (Fehlerseite als Content) verwandelt werden
        assertFalse(result.isSuccess());
        assertEquals("HTTP_ERROR", result.getErrorType());
        assertEquals(404, result.getHttpStatusCode());
        assertEquals("HTTP 404", result.getShortErrorDescription());
    }
}
