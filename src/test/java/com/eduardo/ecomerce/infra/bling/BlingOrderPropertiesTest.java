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

    private static BlingOrderProperties withLocal() {
        return bind(Map.ofEntries(
                Map.entry("bling.orders.payment-method.pix", "9042991"),
                Map.entry("bling.orders.payment-method.credit-card", "11412042"),
                Map.entry("bling.orders.local.contact-id", "777"),
                Map.entry("bling.orders.local.situation-id", "9"),
                Map.entry("bling.orders.local.payment-method.cash", "1001"),
                Map.entry("bling.orders.local.payment-method.local-pix", "1002"),
                Map.entry("bling.orders.local.payment-method.card-machine", "1003"),
                Map.entry("bling.orders.local.payment-method.payment-link", "1004"),
                Map.entry("bling.orders.local.payment-method.store-credit", "1005")
        ));
    }

    @Test
    void bind_vendaLocal_contatoSituacaoEFormasDoBalcao() {
        BlingOrderProperties props = withLocal();

        assertThat(props.localContactId()).isEqualTo(777L);
        assertThat(props.localSituationId()).isEqualTo(9L);
        assertThat(props.paymentMethodIdFor("cash")).isEqualTo(1001L);
        assertThat(props.paymentMethodIdFor("local_pix")).isEqualTo(1002L);
        assertThat(props.paymentMethodIdFor("card_machine")).isEqualTo(1003L);
        assertThat(props.paymentMethodIdFor("payment_link")).isEqualTo(1004L);
        assertThat(props.paymentMethodIdFor("store_credit")).isEqualTo(1005L);
        // o site continua igual
        assertThat(props.paymentMethodIdFor("pix")).isEqualTo(9042991L);
    }

    @Test
    void vendaLocal_naoConfigurada_falhaComMensagemClara() {
        BlingOrderProperties props = full();

        assertThatThrownBy(props::localContactId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contact-id");
        assertThatThrownBy(() -> props.paymentMethodIdFor("store_credit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store_credit");
    }

    @Test
    void vendaLocal_formaDoBalcaoSemId_falhaComMensagemClara() {
        BlingOrderProperties props = bind(Map.of(
                "bling.orders.local.contact-id", "777",
                "bling.orders.local.payment-method.cash", "1001"));

        assertThatThrownBy(() -> props.paymentMethodIdFor("store_credit"))
                .isInstanceOf(IllegalArgumentException.class);
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
