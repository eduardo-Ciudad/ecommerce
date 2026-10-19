package com.eduardo.ecomerce.infra.bling;


import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;


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

    private final BlingRequestThrottler throttler;// um pouco acima de 333ms (3 req/s) por margem de segurança


    private final ObjectMapper objectMapper;

    public BlingClient(
            AppRestClientFactory restClientFactory,
            BlingRequestThrottler throttler,
            @Value("${bling.api-base-url}") String apiBaseUrl, ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.restClient = restClientFactory.create(apiBaseUrl);
        this.throttler = throttler;
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
                        .path("/produtos")
                        .queryParam("pagina", page)
                        .queryParam("limite", 100)
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

    // ---------- Pedidos de venda / contatos (sync site -> Bling) ----------

    public JsonNode findContactsByDocument(String accessToken, String documentDigits) {
        return authenticatedGet(
                accessToken,
                uriBuilder -> uriBuilder
                        .path("/contatos")
                        .queryParam("numeroDocumento", documentDigits)
                        .build()
        );
    }

    public JsonNode createContact(String accessToken, JsonNode body) {
        return authenticatedPost(accessToken, uriBuilder -> uriBuilder.path("/contatos").build(), body);
    }

    public JsonNode findSaleOrdersByStoreNumber(String accessToken, Long storeId, String storeNumber) {
        return authenticatedGet(
                accessToken,
                uriBuilder -> uriBuilder
                        .path("/pedidos/vendas")
                        .queryParam("idLoja", storeId)
                        .queryParam("numerosLojas[]", storeNumber)
                        .build()
        );
    }

    public JsonNode createSaleOrder(String accessToken, JsonNode body) {
        return authenticatedPost(accessToken, uriBuilder -> uriBuilder.path("/pedidos/vendas").build(), body);
    }

    /** Lança o estoque do pedido no depósito padrão. Resposta pode vir vazia (retorna null). */
    public JsonNode launchSaleOrderStock(String accessToken, Long saleOrderId) {
        return authenticatedPost(
                accessToken,
                uriBuilder -> uriBuilder.path("/pedidos/vendas/{id}/lancar-estoque").build(saleOrderId),
                null
        );
    }

    // ---------- HTTP autenticado ----------

    private JsonNode authenticatedGet(String accessToken, Function<UriBuilder, URI> uriFunction) {
        return executeAuthenticated(() -> restClient.get()
                .uri(uriFunction::apply)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("enable-jwt", "1")
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode authenticatedPost(String accessToken, Function<UriBuilder, URI> uriFunction, Object body) {
        return executeAuthenticated(() -> {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(uriFunction::apply)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("enable-jwt", "1")
                    .contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.body(body);
            }
            return request.retrieve().body(JsonNode.class);
        });
    }

    /** Throttle + tradução de erros comum a todas as chamadas autenticadas. */
    private JsonNode executeAuthenticated(Supplier<JsonNode> call) {
        throttler.throttle();
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            JsonNode errorBody = parseErrorBody(e);
            String type = extractBlingErrorField(errorBody, "type");
            String description = extractBlingErrorField(errorBody, "description");
            log.warn("Token do Bling rejeitado (401): type={}, description={}", type, description);
            throw new BlingUnauthorizedException(
                    "Token do Bling expirado ou inválido: " + description, e, 401, type, description
            );
        } catch (HttpStatusCodeException e) {
            JsonNode errorBody = parseErrorBody(e);
            String type = extractBlingErrorField(errorBody, "type");
            String description = extractBlingErrorField(errorBody, "description");
            String fields = extractBlingErrorFields(errorBody);
            int status = e.getStatusCode().value();
            log.error("Erro HTTP {} em chamada autenticada à API do Bling: type={}, description={}, fields={}",
                    status, type, description, fields);
            String message = "Falha ao consultar a API do Bling (HTTP " + status + "): " + description
                    + (fields != null ? " | " + fields : "");
            throw new BlingIntegrationException(message, e, status, type, description);
        } catch (RestClientException e) {
            log.error("Falha em chamada autenticada à API do Bling", e);
            throw new BlingIntegrationException("Falha ao consultar a API do Bling", e);
        }
    }

    private JsonNode postToken(MultiValueMap<String, String> body) {
        throttler.throttle();
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
        } catch (HttpStatusCodeException e) {
            JsonNode errorBody = parseErrorBody(e);
            String type = extractBlingErrorField(errorBody, "type");
            String description = extractBlingErrorField(errorBody, "description");
            int status = e.getStatusCode().value();
            log.error("Erro HTTP {} na chamada de token do Bling: type={}, description={}", status, type, description);
            throw new BlingIntegrationException(
                    "Falha ao obter/renovar token do Bling (HTTP " + status + "): " + description, e, status, type, description
            );
        } catch (RestClientException e) {
            log.error("Falha na chamada de token do Bling", e);
            throw new BlingIntegrationException("Falha ao obter/renovar token do Bling", e);
        }
    }

    private JsonNode parseErrorBody(HttpStatusCodeException e) {
        try {
            return objectMapper.readTree(e.getResponseBodyAsString());
        } catch (Exception parseError) {
            return null;
        }
    }

    /** Junta error.fields[] do Bling como "element: msg; element: msg". Null se não houver. */
    private String extractBlingErrorFields(JsonNode errorBody) {
        if (errorBody == null) {
            return null;
        }
        JsonNode fields = errorBody.path("error").path("fields");
        if (!fields.isArray() || fields.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode field : fields) {
            String element = field.path("element").asText("");
            String msg = field.path("msg").asText("");
            parts.add(element.isBlank() ? msg : element + ": " + msg);
        }
        return String.join("; ", parts);
    }

    private String extractBlingErrorField(JsonNode errorBody, String field) {
        if (errorBody == null) {
            return null;
        }
        JsonNode value = errorBody.path("error").path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
