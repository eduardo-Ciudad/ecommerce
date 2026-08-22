package com.eduardo.ecomerce.infra.bling;

import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class BlingClientTest {

    private MockWebServer server;
    private BlingClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        AppRestClientFactory factory = new AppRestClientFactory(500, 100);
        client = new BlingClient(factory, server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Deve interromper chamada ao Bling quando o servidor excede o read timeout")
    void listProducts_slowServer_timesOut() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[]}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        BlingIntegrationException exception = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThrows(
                        BlingIntegrationException.class,
                        () -> client.listProducts("access-token", 1)
                )
        );

        assertThat(exception).hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    }
}
