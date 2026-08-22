package com.eduardo.ecomerce.domain.blingtoken;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestPropertySource(properties = "app.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class BlingTokenEncryptionPersistenceTest {

    @Autowired
    private BlingTokenRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deve criptografar tokens no banco e descriptografá-los ao carregar a entidade")
    void saveAndLoad_encryptsTokensAtRestAndPreservesRoundTrip() {
        String accessToken = "access-token-secreto";
        String refreshToken = "refresh-token-secreto";

        BlingToken token = new BlingToken();
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));

        UUID id = repository.saveAndFlush(token).getId();

        String persistedAccessToken = jdbcTemplate.queryForObject(
                "SELECT access_token FROM bling_tokens WHERE id = ?",
                String.class,
                id
        );
        String persistedRefreshToken = jdbcTemplate.queryForObject(
                "SELECT refresh_token FROM bling_tokens WHERE id = ?",
                String.class,
                id
        );

        assertThat(persistedAccessToken)
                .startsWith("v1:")
                .doesNotContain(accessToken)
                .isNotEqualTo(accessToken);
        assertThat(persistedRefreshToken)
                .startsWith("v1:")
                .doesNotContain(refreshToken)
                .isNotEqualTo(refreshToken);
        assertThat(Base64.getDecoder().decode(persistedAccessToken.substring(3))).isNotEmpty();
        assertThat(Base64.getDecoder().decode(persistedRefreshToken.substring(3))).isNotEmpty();

        entityManager.clear();

        BlingToken loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getAccessToken()).isEqualTo(accessToken);
        assertThat(loaded.getRefreshToken()).isEqualTo(refreshToken);
    }

    @Test
    @DisplayName("Deve gerar ciphertexts diferentes ao criptografar o mesmo token")
    void saveSameTokenTwice_usesRandomNonceForEachValue() {
        BlingToken first = newToken("token-repetido", "refresh-repetido");
        BlingToken second = newToken("token-repetido", "refresh-repetido");

        UUID firstId = repository.saveAndFlush(first).getId();
        UUID secondId = repository.saveAndFlush(second).getId();

        String firstCiphertext = persistedAccessToken(firstId);
        String secondCiphertext = persistedAccessToken(secondId);

        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext);
    }

    private BlingToken newToken(String accessToken, String refreshToken) {
        BlingToken token = new BlingToken();
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        return token;
    }

    private String persistedAccessToken(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT access_token FROM bling_tokens WHERE id = ?",
                String.class,
                id
        );
    }
}
