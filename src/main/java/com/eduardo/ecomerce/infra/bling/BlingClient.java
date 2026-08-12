package com.eduardo.ecomerce.infra.bling;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Camada de chamadas HTTP para a API v3 do Bling.
 * Não gerencia estado de token (cache/persistência) — isso é responsabilidade
 * do BlingService, conforme a arquitetura em camadas definida para a integração.
 *
 * TODO: o path exato do endpoint de token ("/Api/v3/oauth/token") é uma suposição
 * baseada no padrão OAuth2 do Bling e ainda não foi confirmado contra a
 * documentação atual da API v3. Validar antes do primeiro teste real.
 */


@Slf4j
@Component
public class BlingClient {

    private final RestClient restClient;

    @Value("${bling.client-id}")
    private String clientId;

    @Value("${bling.client-secret}")
    private String clientSecret;

    @Value("${bling.redirect-uri}")
    private String redirectUri;

    public BlingClient(@Value("${bling.api-base-url}") String apiBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    public JsonNode exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        return postToken(body);
    }

    public JsonNode refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        return postToken(body);
    }


    private JsonNode postToken(MultiValueMap<String, String> body) {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        try {
            return restClient.post()
                    .uri("/oauth/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .header("enable-jwt", "1")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Falha na chamada de token do Bling", e);
            throw new BlingIntegrationException("Falha ao obter/renovar token do Bling", e);
        }
    }
}