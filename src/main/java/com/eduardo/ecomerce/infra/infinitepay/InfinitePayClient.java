package com.eduardo.ecomerce.infra.infinitepay;

import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class InfinitePayClient {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final RestClient restClient;
    private final InfinitePayProperties properties;

    public InfinitePayClient(AppRestClientFactory restClientFactory, InfinitePayProperties properties) {
        this.restClient = restClientFactory.create(properties.baseUrl());
        this.properties = properties;
    }

    public CreateLinkResponse createLink(CreateLinkRequest request) {
        CreateLinkResponse response = post("/links", request, CreateLinkResponse.class);
        if (response == null || response.url() == null || response.url().isBlank()) {
            log.error("InfinitePay respondeu ao POST /links sem URL do checkout — pedido {}", request.orderNsu());
            throw new InfinitePayException("InfinitePay respondeu sem URL do checkout");
        }
        return response;
    }

    public PaymentCheckResponse checkPayment(PaymentCheckRequest request) {
        PaymentCheckResponse response = post("/payment_check", request, PaymentCheckResponse.class);
        if (response == null) {
            throw new InfinitePayException("InfinitePay respondeu ao payment_check sem corpo");
        }
        return response;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.error("Erro HTTP {} na InfinitePay em {}: {}", status, path, e.getResponseBodyAsString());
            throw new InfinitePayException("Falha na API da InfinitePay (HTTP " + status + ") em " + path, e, status);
        } catch (RestClientException e) {
            log.error("Falha de comunicação com a InfinitePay em {}", path, e);
            throw new InfinitePayException("Falha de comunicação com a InfinitePay em " + path, e);
        }
    }
}
