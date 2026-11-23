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

    /** Falha na subida se o checkout estiver ligado sem credenciais (melhor do que falhar no primeiro pagamento). */
    public InfinitePayProperties {
        if (assuredInstallments < 0 || assuredInstallments > 12) {
            throw new IllegalStateException("infinitepay.assured-installments deve estar entre 0 e 12");
        }
        if (enabled) {
            requireText(handle, "infinitepay.handle");
            requireText(apiKey, "infinitepay.api-key");
            requireText(webhookUrl, "infinitepay.webhook-url");
        }
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " é obrigatório com infinitepay.enabled=true");
        }
    }
}
