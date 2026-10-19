package com.eduardo.ecomerce.infra.bling;

import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        client = new BlingClient(factory, new BlingRequestThrottler(), server.url("/").toString(), new ObjectMapper());
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

    // ---------- Pedidos de venda / contatos ----------

    private final ObjectMapper mapper = new ObjectMapper();

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private static void assertAuthHeaders(RecordedRequest request) {
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("enable-jwt")).isEqualTo("1");
    }

    @Test
    void findContactsByDocument_fazGetComDocumentoNaQuery() throws Exception {
        enqueueJson(200, "{\"data\":[{\"id\":555}]}");

        JsonNode result = client.findContactsByDocument("access-token", "52998224725");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/contatos");
        assertThat(request.getRequestUrl().queryParameter("numeroDocumento")).isEqualTo("52998224725");
        assertAuthHeaders(request);
        assertThat(result.path("data").get(0).path("id").asLong()).isEqualTo(555L);
    }

    @Test
    void createContact_fazPostComCorpoJson() throws Exception {
        enqueueJson(201, "{\"data\":{\"id\":777}}");
        JsonNode body = mapper.readTree("{\"nome\":\"Maria\",\"tipo\":\"F\",\"situacao\":\"A\"}");

        JsonNode result = client.createContact("access-token", body);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/contatos");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertAuthHeaders(request);
        assertThat(mapper.readTree(request.getBody().readUtf8())).isEqualTo(body);
        assertThat(result.path("data").path("id").asLong()).isEqualTo(777L);
    }

    @Test
    void findSaleOrdersByStoreNumber_enviaIdLojaENumerosLojas() throws Exception {
        enqueueJson(200, "{\"data\":[]}");

        client.findSaleOrdersByStoreNumber("access-token", 206333320L, "c697c4c5-9e7b-49f8-a9b7-8a3f244d6726");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/pedidos/vendas");
        assertThat(request.getRequestUrl().queryParameter("idLoja")).isEqualTo("206333320");
        assertThat(request.getRequestUrl().queryParameter("numerosLojas[]"))
                .isEqualTo("c697c4c5-9e7b-49f8-a9b7-8a3f244d6726");
        assertAuthHeaders(request);
    }

    @Test
    void createSaleOrder_fazPostEmPedidosVendasELeResposta201() throws Exception {
        enqueueJson(201, "{\"data\":{\"id\":12345678,\"alertas\":[]}}");
        JsonNode body = mapper.readTree("{\"numeroLoja\":\"abc\",\"itens\":[]}");

        JsonNode result = client.createSaleOrder("access-token", body);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/pedidos/vendas");
        assertThat(mapper.readTree(request.getBody().readUtf8())).isEqualTo(body);
        assertThat(result.path("data").path("id").asLong()).isEqualTo(12345678L);
    }

    @Test
    void createSaleOrder_400ComFields_mensagemTrazCamposDoBling() {
        enqueueJson(400, """
                {"error":{"type":"VALIDATION_ERROR","message":"Não foi possível salvar",
                  "description":"Não foi possível salvar o pedido de venda",
                  "fields":[{"code":1,"msg":"Produto não encontrado","element":"itens[0].produto.id"},
                            {"code":2,"msg":"Contato inválido","element":"contato.id"}]}}
                """);

        assertThatThrownBy(() -> client.createSaleOrder("access-token", mapper.createObjectNode()))
                .isInstanceOfSatisfying(BlingIntegrationException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getBlingErrorType()).isEqualTo("VALIDATION_ERROR");
                    assertThat(e.getMessage())
                            .contains("HTTP 400")
                            .contains("Não foi possível salvar o pedido de venda")
                            .contains("itens[0].produto.id: Produto não encontrado")
                            .contains("contato.id: Contato inválido");
                });
    }

    @Test
    void createSaleOrder_400SemFields_mensagemSoComDescription() {
        enqueueJson(400, "{\"error\":{\"type\":\"VALIDATION_ERROR\",\"description\":\"Dados inválidos\"}}");

        assertThatThrownBy(() -> client.createSaleOrder("access-token", mapper.createObjectNode()))
                .isInstanceOfSatisfying(BlingIntegrationException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).endsWith("Dados inválidos");
                });
    }

    @Test
    void post401_lancaBlingUnauthorizedException() {
        enqueueJson(401, "{\"error\":{\"type\":\"invalid_token\",\"description\":\"expirado\"}}");

        assertThatThrownBy(() -> client.createSaleOrder("access-token", mapper.createObjectNode()))
                .isInstanceOf(BlingUnauthorizedException.class);
    }

    @Test
    void post500_lancaBlingIntegrationExceptionComStatus() {
        enqueueJson(500, "{\"error\":{\"type\":\"SERVER_ERROR\",\"description\":\"erro interno\"}}");

        assertThatThrownBy(() -> client.createContact("access-token", mapper.createObjectNode()))
                .isInstanceOfSatisfying(BlingIntegrationException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(500));
    }

    @Test
    void launchSaleOrderStock_postSemCorpo_204VazioRetornaNull() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        JsonNode result = client.launchSaleOrderStock("access-token", 123L);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/pedidos/vendas/123/lancar-estoque");
        assertThat(request.getBodySize()).isZero();
        assertAuthHeaders(request);
        assertThat(result).isNull();
    }

    @Test
    void listProducts401_continuaLancandoBlingUnauthorizedException() {
        // regressão da refatoração do authenticatedGet
        enqueueJson(401, "{\"error\":{\"type\":\"invalid_token\",\"description\":\"expirado\"}}");

        assertThatThrownBy(() -> client.listProducts("access-token", 1))
                .isInstanceOf(BlingUnauthorizedException.class);
    }
}