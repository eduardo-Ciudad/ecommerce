package com.eduardo.ecomerce.infra.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppRestClientFactory {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public AppRestClientFactory(
            @Value("${app.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.http.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
