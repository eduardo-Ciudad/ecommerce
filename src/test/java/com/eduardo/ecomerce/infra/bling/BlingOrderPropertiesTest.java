package com.eduardo.ecomerce.infra.bling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlingOrderPropertiesTest {

    private static BlingOrderProperties bind(Map<String, String> props) {
        return new Binder(new MapConfigurationPropertySource(props))
                .bindOrCreate("bling.orders", BlingOrderProperties.class);
    }

    private static BlingOrderProperties full() {
        return bind(Map.of(
                "bling.orders.enabled", "true",
                "bling.orders.store-id", "206333320",
                "bling.orders.situation-id", "15",
                "bling.orders.payment-method.pix", "9042991",
                "bling.orders.payment-method.credit-card", "11412042",
                "bling.orders.max-attempts", "7",
                "bling.orders.stale-after", "20m"
        ));
    }

    @Test
    void bind_chavesKebabCase_lidasNosCamposCertos() {
        BlingOrderProperties props = full();

        assertThat(props.enabled()).isTrue();
        assertThat(props.storeId()).isEqualTo(206333320L);
        assertThat(props.situationId()).isEqualTo(15L);
        assertThat(props.paymentMethod().pix()).isEqualTo(9042991L);
        assertThat(props.paymentMethod().creditCard()).isEqualTo(11412042L);
        assertThat(props.maxAttempts()).isEqualTo(7);
        assertThat(props.staleAfter()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void bind_semChaves_usaPadroes() {
        BlingOrderProperties props = bind(Map.of());

        assertThat(props.enabled()).isFalse();
        assertThat(props.maxAttempts()).isEqualTo(5);
        assertThat(props.staleAfter()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void paymentMethodIdFor_pixECartao_retornaIdsDoBling() {
        BlingOrderProperties props = full();

        assertThat(props.paymentMethodIdFor("pix")).isEqualTo(9042991L);
        assertThat(props.paymentMethodIdFor("credit_card")).isEqualTo(11412042L);
    }

    @Test
    void paymentMethodIdFor_meioDesconhecido_lancaExcecao() {
        assertThatThrownBy(() -> full().paymentMethodIdFor("boleto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boleto");
    }

    @Test
    void paymentMethodIdFor_nulo_lancaExcecao() {
        assertThatThrownBy(() -> full().paymentMethodIdFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
