package com.eduardo.ecomerce.infra.shipping;

import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Regra de frete grátis (shipping.free.*): total dos produtos, já com desconto de cupom,
 * a partir do threshold e em uma das modalidades configuradas.
 */
@ConfigurationProperties(prefix = "shipping.free")
public record FreeShippingPolicy(
        @DefaultValue("199.00") BigDecimal threshold,
        @DefaultValue({"PAC", "SEDEX"}) Set<String> methods
) {

    public boolean isEligible(String method, BigDecimal productsTotal) {
        if (method == null || productsTotal == null || threshold == null || methods == null) {
            return false;
        }
        boolean methodAllowed = methods.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(method));
        return methodAllowed && productsTotal.compareTo(threshold) >= 0;
    }

    /** Mesma modalidade e prazo, com preço zerado quando elegível; caso contrário devolve o frete como veio. */
    public ShippingOutput apply(ShippingOutput shipping, BigDecimal productsTotal) {
        if (shipping == null || !isEligible(shipping.method(), productsTotal)) {
            return shipping;
        }
        return new ShippingOutput(shipping.method(), shipping.methodLabel(), BigDecimal.ZERO,
                shipping.deadlineDays(), true);
    }
}
