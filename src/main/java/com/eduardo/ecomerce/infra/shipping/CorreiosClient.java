package com.eduardo.ecomerce.infra.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;

@Slf4j
@Component
public class CorreiosClient {

    private final RestClient restClient;

    @Value("${correios.usuario}")
    private String usuario;

    @Value("${correios.codigo-acesso}")
    private String codigoAcesso;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public CorreiosClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.correios.com.br")
                .build();
    }

    private synchronized String getToken() {
        if (cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        String credentials = Base64.getEncoder()
                .encodeToString((usuario + ":" + codigoAcesso).getBytes());

        JsonNode response = restClient.post()
                .uri("/token/v1/autentica")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .retrieve()
                .body(JsonNode.class);

        cachedToken = response.get("token").asText();
        tokenExpiresAt = Instant.now().plusSeconds(3300);
        log.info("Token dos Correios renovado");
        return cachedToken;
    }

    public JsonNode consultarPreco(String coProduto, String cepOrigem, String cepDestino,
                                   int pesoGramas, int comprimento, int altura, int largura) {
        return restClient.get()
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
                .body(JsonNode.class);
    }

    public JsonNode consultarPrazo(String coProduto, String cepOrigem, String cepDestino) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/prazo/v1/nacional/{coProduto}")
                        .queryParam("cepOrigem", cepOrigem)
                        .queryParam("cepDestino", cepDestino)
                        .build(coProduto))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getToken())
                .retrieve()
                .body(JsonNode.class);
    }
}