package com.eduardo.ecomerce.infra.infinitepay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do Checkout Integrado da InfinitePay (infinitepay.*).
 * enabled=false mantém o cartão no Mercado Pago; apiKey é usada só no servidor (header X-Api-Key).
 */
@ConfigurationProperties(prefix = "infinitepay")
public record InfinitePayProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://api.checkout.infinitepay.io") String baseUrl,
        String handle,
        String apiKey,
        @DefaultValue("0") int assuredInstallments,
        String webhookUrl,
        @DefaultValue("/pedido-confirmado.html") String returnPath
) {
}
