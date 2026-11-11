package com.eduardo.ecomerce.infra.shipping;

import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FreeShippingPolicyTest {

    private final FreeShippingPolicy policy = new FreeShippingPolicy(new BigDecimal("199.00"), Set.of("PAC", "SEDEX"));

    @Test
    void exatamente199_pac_elegivel() {
        assertThat(policy.isEligible("PAC", new BigDecimal("199.00"))).isTrue();
    }

    @Test
    void acimaDe199_sedex_elegivel() {
        assertThat(policy.isEligible("SEDEX", new BigDecimal("250.00"))).isTrue();
    }

    @Test
    void abaixoDe199_naoElegivel() {
        assertThat(policy.isEligible("PAC", new BigDecimal("198.99"))).isFalse();
    }

    @Test
    void metodoForaDaLista_naoElegivel() {
        FreeShippingPolicy soPac = new FreeShippingPolicy(new BigDecimal("199.00"), Set.of("PAC"));

        assertThat(soPac.isEligible("SEDEX", new BigDecimal("300.00"))).isFalse();
    }

    @Test
    void metodoEmMinusculo_elegivel() {
        assertThat(policy.isEligible("pac", new BigDecimal("199.00"))).isTrue();
    }

    @Test
    void totalOuMetodoNulo_naoElegivel() {
        assertThat(policy.isEligible("PAC", null)).isFalse();
        assertThat(policy.isEligible(null, new BigDecimal("300.00"))).isFalse();
    }

    @Test
    void apply_elegivel_zeraPrecoMantemModalidadeEPrazo() {
        ShippingOutput shipping = new ShippingOutput("PAC", "PAC", new BigDecimal("25.50"), 7);

        ShippingOutput result = policy.apply(shipping, new BigDecimal("199.00"));

        assertThat(result.price()).isEqualByComparingTo("0");
        assertThat(result.freeShipping()).isTrue();
        assertThat(result.method()).isEqualTo("PAC");
        assertThat(result.methodLabel()).isEqualTo("PAC");
        assertThat(result.deadlineDays()).isEqualTo(7);
    }

    @Test
    void apply_naoElegivel_devolveFreteComoVeio() {
        ShippingOutput shipping = new ShippingOutput("PAC", "PAC", new BigDecimal("25.50"), 7);

        ShippingOutput result = policy.apply(shipping, new BigDecimal("150.00"));

        assertThat(result).isSameAs(shipping);
        assertThat(result.freeShipping()).isFalse();
    }

    @Test
    void apply_semTotal_devolveFreteComoVeio() {
        ShippingOutput shipping = new ShippingOutput("SEDEX", "SEDEX", new BigDecimal("45.90"), 2);

        assertThat(policy.apply(shipping, null)).isSameAs(shipping);
    }
}
