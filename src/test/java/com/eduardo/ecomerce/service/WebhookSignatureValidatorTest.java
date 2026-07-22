package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.infra.payment.WebhookSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;
    private final String secret = "minha-chave-secreta-de-teste";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
        ReflectionTestUtils.setField(validator, "webhookSecret", secret);
    }

    @Test
    void isValid_assinaturaCorreta_retornaTrue() throws Exception {
        String dataId = "123456789";
        String requestId = "abc-123";
        String ts = "1704908010";

        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String expectedHash = calcularHmacEsperado(manifest, secret);

        String xSignature = "ts=" + ts + ",v1=" + expectedHash;

        boolean result = validator.isValid(xSignature, requestId, dataId);

        assertThat(result).isTrue();
    }

    @Test
    void isValid_assinaturaIncorreta_retornaFalse() {
        String xSignature = "ts=1704908010,v1=hashinventadoerrado";

        boolean result = validator.isValid(xSignature, "abc-123", "123456789");

        assertThat(result).isFalse();
    }

    @Test
    void isValid_headerNulo_retornaFalse() {
        boolean result = validator.isValid(null, "abc-123", "123456789");

        assertThat(result).isFalse();
    }

    @Test
    void isValid_dataIdEmMaiusculo_normalizaERetornaTrue() throws Exception {
        // Testa que "ID123" e "id123" geram o mesmo hash (regra do lowercase)
        String dataId = "ID123";
        String requestId = "abc-123";
        String ts = "1704908010";

        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        String expectedHash = calcularHmacEsperado(manifest, secret);

        String xSignature = "ts=" + ts + ",v1=" + expectedHash;

        boolean result = validator.isValid(xSignature, requestId, dataId);

        assertThat(result).isTrue();
    }

    private String calcularHmacEsperado(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }
}
