package com.eduardo.ecomerce.infra.shipping;

import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CorreiosClientTest {

    private MockWebServer server;
    private CorreiosClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        AppRestClientFactory factory = new AppRestClientFactory(500, 100);
        client = new CorreiosClient(factory, server.url("/").toString());
        ReflectionTestUtils.setField(client, "usuario", "usuario");
        ReflectionTestUtils.setField(client, "codigoAcesso", "codigo");
        ReflectionTestUtils.setField(client, "cartaoPostagem", "cartao");
        ReflectionTestUtils.setField(client, "numeroContrato", "contrato");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Deve interromper chamada aos Correios quando o servidor excede o read timeout")
    void consultarPreco_slowServer_timesOut() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"token\":\"token\"}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        CorreiosException exception = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> assertThrows(
                        CorreiosException.class,
                        () -> client.consultarPreco(
                                "03298", "15046806", "01310100", 300, 20, 10, 15
                        )
                )
        );

        assertThat(exception).hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    }
}
