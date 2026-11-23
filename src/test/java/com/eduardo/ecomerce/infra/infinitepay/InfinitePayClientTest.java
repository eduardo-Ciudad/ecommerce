package com.eduardo.ecomerce.infra.infinitepay;

import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InfinitePayClientTest {

    private static final String API_KEY = "inchk_chave_secreta_teste";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;
    private InfinitePayClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        InfinitePayProperties properties = new InfinitePayProperties(
                true, server.url("/").toString(), "gabikids", API_KEY, 3,
                "https://api.gabikidstore.com/payments/infinitepay/webhook", "/pedido-confirmado.html");
        client = new InfinitePayClient(new AppRestClientFactory(500, 300), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ---------- createLink ----------

    @Test
    void createLink_enviaApiKeyNoHeaderEPayloadEmSnakeCase() throws Exception {
        server.enqueue(jsonResponse(200, "{\"url\":\"https://checkout.infinitepay.io/gabikids/abc\"}"));

        client.createLink(sampleLinkRequest());

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/links");
        assertThat(recorded.getHeader("X-Api-Key")).isEqualTo(API_KEY);

        JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("handle").asText()).isEqualTo("gabikids");
        assertThat(body.get("order_nsu").asText()).isEqualTo("pedido-123");
        assertThat(body.get("redirect_url").asText()).isEqualTo("https://www.gabikidstore.com/pedido-confirmado.html");
        assertThat(body.get("webhook_url").asText()).isEqualTo("https://api.gabikidstore.com/payments/infinitepay/webhook");
        assertThat(body.get("items").get(0).get("price").asInt()).isEqualTo(4990);
        assertThat(body.get("items").get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(body.get("customer").get("phone_number").asText()).isEqualTo("+5517999990000");
        assertThat(body.get("options").get("assured_installments").asInt()).isEqualTo(3);
        assertThat(body.get("options").get("single_payment").asBoolean()).isTrue();
        assertThat(body.get("options").get("valid_until").asText()).isEqualTo("2026-11-23T18:00:00-03:00");
        assertThat(body.get("options").get("payment_methods_enabled").get(0).asText()).isEqualTo("credit_card");
        assertThat(body.get("options").get("payment_methods_enabled")).hasSize(1);
    }

    @Test
    void createLink_camposNulosNaoSaoEnviados() throws Exception {
        server.enqueue(jsonResponse(200, "{\"url\":\"https://checkout.infinitepay.io/gabikids/abc\"}"));
        CreateLinkRequest request = new CreateLinkRequest(
                "gabikids", List.of(new CreateLinkRequest.Item("Produto", 1000, 1)),
                "pedido-123", null, null, null, null, null);

        client.createLink(request);

        JsonNode body = objectMapper.readTree(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
        assertThat(body.has("customer")).isFalse();
        assertThat(body.has("options")).isFalse();
        assertThat(body.has("redirect_url")).isFalse();
    }

    @Test
    void createLink_retornaUrlDaResposta() {
        server.enqueue(jsonResponse(200, "{\"url\":\"https://checkout.infinitepay.io/gabikids/abc\",\"slug\":\"abc\"}"));

        CreateLinkResponse response = client.createLink(sampleLinkRequest());

        assertThat(response.url()).isEqualTo("https://checkout.infinitepay.io/gabikids/abc");
        assertThat(response.slug()).isEqualTo("abc");
    }

    @Test
    void createLink_respostaComCamposExtras_naoQuebra() {
        server.enqueue(jsonResponse(200,
                "{\"url\":\"https://checkout.infinitepay.io/gabikids/abc\",\"campo_novo\":{\"x\":1},\"outro\":true}"));

        CreateLinkResponse response = client.createLink(sampleLinkRequest());

        assertThat(response.url()).isEqualTo("https://checkout.infinitepay.io/gabikids/abc");
        assertThat(response.slug()).isNull();
    }

    @Test
    void createLink_respostaSemUrl_lancaInfinitePayException() {
        server.enqueue(jsonResponse(200, "{\"success\":true}"));

        assertThatThrownBy(() -> client.createLink(sampleLinkRequest()))
                .isInstanceOf(InfinitePayException.class)
                .hasMessageContaining("sem URL");
    }

    @Test
    void createLink_erroHttp_lancaExcecaoComStatusSemExporApiKey() {
        server.enqueue(jsonResponse(400, "{\"success\":false,\"message\":\"param is missing\"}"));

        assertThatThrownBy(() -> client.createLink(sampleLinkRequest()))
                .isInstanceOfSatisfying(InfinitePayException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).contains("HTTP 400").doesNotContain(API_KEY);
                });
    }

    // ---------- checkPayment ----------

    @Test
    void checkPayment_enviaIdentificadoresERetornaPaid() throws Exception {
        server.enqueue(jsonResponse(200,
                "{\"success\":true,\"paid\":true,\"amount\":4990,\"paid_amount\":5120,\"installments\":3,\"capture_method\":\"credit_card\"}"));

        PaymentCheckResponse response = client.checkPayment(
                new PaymentCheckRequest("gabikids", "pedido-123", "trx-uuid", "abc"));

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getPath()).isEqualTo("/payment_check");
        assertThat(recorded.getHeader("X-Api-Key")).isEqualTo(API_KEY);
        JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("handle").asText()).isEqualTo("gabikids");
        assertThat(body.get("order_nsu").asText()).isEqualTo("pedido-123");
        assertThat(body.get("transaction_nsu").asText()).isEqualTo("trx-uuid");
        assertThat(body.get("slug").asText()).isEqualTo("abc");

        assertThat(response.success()).isTrue();
        assertThat(response.paid()).isTrue();
        assertThat(response.amount()).isEqualTo(4990L);
        assertThat(response.paidAmount()).isEqualTo(5120L);
        assertThat(response.installments()).isEqualTo(3);
        assertThat(response.captureMethod()).isEqualTo("credit_card");
    }

    @Test
    void checkPayment_successFalse_retornaRespostaSemExcecao() {
        server.enqueue(jsonResponse(200, "{\"success\":false,\"paid\":false}"));

        PaymentCheckResponse response = client.checkPayment(
                new PaymentCheckRequest("gabikids", "pedido-123", "trx-uuid", "abc"));

        assertThat(response.success()).isFalse();
        assertThat(response.paid()).isFalse();
    }

    @Test
    void checkPayment_servidorLento_lancaExcecaoPorTimeout() {
        server.enqueue(jsonResponse(200, "{\"success\":true,\"paid\":true}").setBodyDelay(2, TimeUnit.SECONDS));

        InfinitePayException exception = assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                org.junit.jupiter.api.Assertions.assertThrows(InfinitePayException.class, () ->
                        client.checkPayment(new PaymentCheckRequest("gabikids", "pedido-123", "trx-uuid", "abc"))));

        assertThat(exception).hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
    }

    // ---------- helpers ----------

    private CreateLinkRequest sampleLinkRequest() {
        return new CreateLinkRequest(
                "gabikids",
                List.of(new CreateLinkRequest.Item("Short Moletinho", 4990, 1)),
                "pedido-123",
                "https://www.gabikidstore.com/pedido-confirmado.html",
                "https://api.gabikidstore.com/payments/infinitepay/webhook",
                new CreateLinkRequest.Customer("Maria Silva", "maria@teste.com", "+5517999990000"),
                new CreateLinkRequest.Address("15000000", "Rua A", "10", "Centro", "São José do Rio Preto", "SP", null),
                new CreateLinkRequest.Options(3, false, true, "2026-11-23T18:00:00-03:00", List.of("credit_card")));
    }

    private static MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
