package com.eduardo.ecomerce.infra.infinitepay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfinitePayPropertiesTest {

    private static final String URL = "https://api.checkout.infinitepay.io";
    private static final String WEBHOOK = "https://api.gabikidstore.com/payments/infinitepay/webhook";

    @Test
    void desabilitado_semCredenciais_naoLanca() {
        InfinitePayProperties props = new InfinitePayProperties(false, URL, null, null, 0, null, "/pedido-confirmado.html");

        assertThat(props.enabled()).isFalse();
    }

    @Test
    void habilitadoCompleto_naoLanca() {
        InfinitePayProperties props = new InfinitePayProperties(
                true, URL, "vanda-julio-dias-898", "inchk_x", 3, WEBHOOK, "/pedido-confirmado.html");

        assertThat(props.enabled()).isTrue();
    }

    @Test
    void habilitadoSemApiKey_lancaComNomeDaPropriedade() {
        assertThatThrownBy(() -> new InfinitePayProperties(
                true, URL, "vanda-julio-dias-898", " ", 3, WEBHOOK, "/pedido-confirmado.html"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("infinitepay.api-key");
    }

    @Test
    void habilitadoSemHandle_lancaComNomeDaPropriedade() {
        assertThatThrownBy(() -> new InfinitePayProperties(
                true, URL, null, "inchk_x", 3, WEBHOOK, "/pedido-confirmado.html"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("infinitepay.handle");
    }

    @Test
    void habilitadoSemWebhook_lancaComNomeDaPropriedade() {
        assertThatThrownBy(() -> new InfinitePayProperties(
                true, URL, "vanda-julio-dias-898", "inchk_x", 3, "", "/pedido-confirmado.html"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("infinitepay.webhook-url");
    }

    @Test
    void parcelasForaDoIntervalo_lanca() {
        assertThatThrownBy(() -> new InfinitePayProperties(
                false, URL, null, null, 13, null, "/pedido-confirmado.html"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assured-installments");
    }
}
