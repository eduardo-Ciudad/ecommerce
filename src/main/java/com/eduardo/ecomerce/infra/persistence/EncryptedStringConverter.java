package com.eduardo.ecomerce.infra.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VALUE_PREFIX = "v1:";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    @Autowired
    public EncryptedStringConverter(@Value("${app.encryption-key}") String encodedKey) {
        this(encodedKey, new SecureRandom());
    }

    EncryptedStringConverter(String encodedKey, SecureRandom secureRandom) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY é obrigatória");
        }

        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY deve estar em Base64 válido", exception);
        }

        if (keyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY deve representar exatamente 32 bytes (AES-256)");
        }

        this.key = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = secureRandom;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce)
                    .put(ciphertext)
                    .array();
            return VALUE_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível criptografar o valor", exception);
        }
    }

    @Override
    public String convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null) {
            return null;
        }
        if (!databaseValue.startsWith(VALUE_PREFIX)) {
            throw new IllegalStateException("Valor criptografado possui formato desconhecido");
        }

        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(databaseValue.substring(VALUE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Valor criptografado possui Base64 inválido", exception);
        }

        if (payload.length <= NONCE_BYTES) {
            throw new IllegalStateException("Valor criptografado está truncado");
        }

        byte[] nonce = new byte[NONCE_BYTES];
        byte[] ciphertext = new byte[payload.length - NONCE_BYTES];
        ByteBuffer.wrap(payload).get(nonce).get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException("Valor criptografado não pôde ser autenticado", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível descriptografar o valor", exception);
        }
    }
}
