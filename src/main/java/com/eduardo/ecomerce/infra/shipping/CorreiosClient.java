package com.eduardo.ecomerce.infra.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Component
public class CorreiosClient {

    private final RestClient restClient;

    @Value("${correios.usuario}")
    private String usuario;

    @Value("${correios.codigo-acesso}")
    private String codigoAcesso;

    @Value("${correios.cartao-postagem}")
    private String cartaoPostagem;

    @Value("${correios.numero-contrato}")
    private String numeroContrato;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public CorreiosClient(
            AppRestClientFactory restClientFactory,
            @Value("${correios.api-base-url:https://api.correios.com.br}") String apiBaseUrl
    ) {
        this.restClient = restClientFactory.create(apiBaseUrl);
    }

    private synchronized String getToken() {

        if (cachedToken != null
                && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        String credentials = Base64.getEncoder()
                .encodeToString(
                        (usuario + ":" + codigoAcesso)
                                .getBytes(StandardCharsets.UTF_8)
                );

        JsonNode response = restClient.post()
                .uri("/token/v1/autentica/cartaopostagem")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "numero", cartaoPostagem,
                        "contrato", numeroContrato
                ))
                .retrieve()
                .body(JsonNode.class);



        cachedToken = response.get("token").asText();
        tokenExpiresAt = Instant.now().plusSeconds(3300);

        log.info("Token dos Correios renovado");

        return cachedToken;
    }

    private synchronized void invalidarToken() {
        this.cachedToken = null;
        this.tokenExpiresAt = null;
    }

    private JsonNode executarComRetry(Supplier<JsonNode> chamada) {
        try {
            return chamada.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Token dos Correios rejeitado (401), renovando e tentando novamente");
            invalidarToken();
            try {
                return chamada.get();
            } catch (Exception retryEx) {
                throw new CorreiosException("Falha na integração com os Correios após renovar token", retryEx);
            }
        } catch (RestClientException e) {
            throw new CorreiosException("Falha na integração com os Correios", e);
        }
    }

    public JsonNode consultarPreco(String coProduto, String cepOrigem, String cepDestino,
                                   int pesoGramas, int comprimento, int altura, int largura) {
        return executarComRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/preco/v1/nacional/{coProduto}")
                        .queryParam("cepOrigem", cepOrigem)
                        .queryParam("cepDestino", cepDestino)
                        .queryParam("psObjeto", pesoGramas)
                        .queryParam("comprimento", comprimento)
                        .queryParam("altura", altura)
                        .queryParam("largura", largura)
                        .build(coProduto))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getToken())
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode consultarPrazo(String coProduto, String cepOrigem, String cepDestino) {
        return executarComRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/prazo/v1/nacional/{coProduto}")
                        .queryParam("cepOrigem", cepOrigem)
                        .queryParam("cepDestino", cepDestino)
                        .build(coProduto))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getToken())
                .retrieve()
                .body(JsonNode.class));
    }
}
