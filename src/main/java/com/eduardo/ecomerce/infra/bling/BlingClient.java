package com.eduardo.ecomerce.infra.bling;


import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
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
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;


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

    public BlingClient(
            AppRestClientFactory restClientFactory,
            @Value("${bling.api-base-url}") String apiBaseUrl
    ) {
        this.restClient = restClientFactory.create(apiBaseUrl);
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

    public JsonNode listProducts(String accessToken, int page) {
        return authenticatedGet(
                accessToken,
                uriBuilder -> uriBuilder
                        .path("/produtos/list")
                        .queryParam("pagina", page)
                        .build()
        );
    }

    public JsonNode getProductById(String accessToken, Long id) {
        return authenticatedGet(
                accessToken,
                uriBuilder -> uriBuilder
                        .path("/produtos/{id}")
                        .build(id)
        );
    }

    public JsonNode getCategories(String accessToken, int page, int limit) {
        return authenticatedGet(
                accessToken,
                uriBuilder -> uriBuilder
                        .path("/categorias/produtos")
                        .queryParam("pagina", page)
                        .queryParam("limite", limit)
                        .build()
        );
    }

    private JsonNode authenticatedGet(String accessToken, Function<UriBuilder, URI> uriFunction) {
        try {
            return restClient.get()
                    .uri(uriFunction::apply)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("enable-jwt", "1")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("Falha em chamada autenticada à API do Bling", e);
            throw new BlingIntegrationException("Falha ao consultar a API do Bling", e);
        }
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
