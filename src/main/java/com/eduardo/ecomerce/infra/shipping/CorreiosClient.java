package com.eduardo.ecomerce.infra.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

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

    public CorreiosClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.correios.com.br")
                .build();
    }

    private synchronized String getToken() {
        log.info("Gerando token com cartão de postagem: {}", cartaoPostagem);

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

        log.info("Resposta bruta do token: {}", response);

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