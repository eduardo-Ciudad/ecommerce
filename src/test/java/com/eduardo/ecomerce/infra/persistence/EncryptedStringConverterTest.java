package com.eduardo.ecomerce.infra.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    @Test
    @DisplayName("Deve falhar quando a chave de criptografia está ausente")
    void constructor_missingKey_fails() {
        assertThatThrownBy(() -> new EncryptedStringConverter(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY é obrigatória");
    }

    @Test
    @DisplayName("Deve falhar quando a chave não está em Base64 válido")
    void constructor_invalidBase64Key_fails() {
        assertThatThrownBy(() -> new EncryptedStringConverter("não-é-base64"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64 válido");
    }

    @Test
    @DisplayName("Deve falhar quando a chave Base64 não representa 32 bytes")
    void constructor_wrongKeyLength_fails() {
        assertThatThrownBy(() -> new EncryptedStringConverter("Y2hhdmUtY3VydGE="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exatamente 32 bytes");
    }
}
