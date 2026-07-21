package com.eduardo.ecomerce.infra.config;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoConfiguration {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        System.out.println("==================================");
        System.out.println(accessToken);
        System.out.println("==================================");
        MercadoPagoConfig.setAccessToken(accessToken);
    }
}
