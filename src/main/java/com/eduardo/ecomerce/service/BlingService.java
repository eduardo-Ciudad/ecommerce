package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.blingtoken.BlingToken;
import com.eduardo.ecomerce.domain.blingtoken.BlingTokenRepository;
import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;


import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BlingService {

    private static final int CATEGORIES_PAGE_LIMIT = 100;
    private static final int MAX_PAGES_SAFETY_LIMIT = 500;

    private final BlingTokenRepository blingTokenRepository;
    private final CategoryRepository categoryRepository;
    private final BlingClient blingClient;
    private final String authorizeUrl;
    private final String clientId;

    // States pendentes de confirmação no callback OAuth. Expiram em 10 minutos
    // (tempo mais que suficiente para o usuário aprovar o app no Bling) e são
    // de uso único — removidos assim que validados.
    private final Cache<String, Boolean> pendingStates = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    public BlingService(
            BlingTokenRepository blingTokenRepository,
            CategoryRepository categoryRepository,
            BlingClient blingClient,
            @Value("${bling.authorize-url}") String authorizeUrl,
            @Value("${bling.client-id}") String clientId
    ) {
        this.blingTokenRepository = blingTokenRepository;
        this.categoryRepository = categoryRepository;
        this.blingClient = blingClient;
        this.authorizeUrl = authorizeUrl;
        this.clientId = clientId;
    }

    public String buildAuthorizationUrl() {
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, Boolean.TRUE);

        return UriComponentsBuilder.fromHttpUrl(authorizeUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public void validateState(String state) {
        Boolean pending = pendingStates.getIfPresent(state);
        if (pending == null) {
            throw new BlingIntegrationException("State inválido ou expirado no callback OAuth do Bling", null);
        }
        pendingStates.invalidate(state);
    }

    @Transactional
    public void handleAuthorizationCode(String code) {
        JsonNode response = blingClient.exchangeCodeForToken(code);
        saveToken(response);
        log.info("Token do Bling obtido via authorization_code");
    }

    @Transactional
    public synchronized String getValidAccessToken() {
        BlingToken token = blingTokenRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new BlingIntegrationException(
                        "Nenhum token do Bling encontrado — autorização ainda não foi realizada", null));

        if (token.isExpired()) {
            log.info("Access token do Bling expirado, renovando via refresh_token");
            JsonNode response = blingClient.refreshAccessToken(token.getRefreshToken());
            token = saveToken(response);
        }

        return token.getAccessToken();
    }

    @Transactional
    public void syncCategories() {
        String accessToken = getValidAccessToken();
        int page = 1;
        int syncedCount = 0;

        while (page <= MAX_PAGES_SAFETY_LIMIT) {
            JsonNode response = blingClient.getCategories(accessToken, page, CATEGORIES_PAGE_LIMIT);
            JsonNode data = response.get("data");

            if (data == null || !data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode categoryNode : data) {
                upsertCategory(categoryNode);
                syncedCount++;
            }

            page++;
        }

        log.info("Sincronização de categorias do Bling concluída: {} categorias processadas", syncedCount);
    }

    private void upsertCategory(JsonNode categoryNode) {
        Long blingCategoryId = categoryNode.get("id").asLong();
        String descricao = categoryNode.get("descricao").asText();

        Category category = categoryRepository.findByBlingCategoryId(blingCategoryId)
                .orElseGet(Category::new);

        category.setBlingCategoryId(blingCategoryId);
        category.setName(descricao);

        categoryRepository.save(category);
    }

    private BlingToken saveToken(JsonNode response) {
        BlingToken token = blingTokenRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(BlingToken::new);

        String accessToken = response.get("access_token").asText();
        String refreshToken = response.get("refresh_token").asText();
        int expiresInSeconds = response.get("expires_in").asInt();

        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));

        return blingTokenRepository.save(token);
    }
}