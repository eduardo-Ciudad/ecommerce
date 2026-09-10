package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.blingtoken.BlingToken;
import com.eduardo.ecomerce.domain.blingtoken.BlingTokenRepository;
import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productimage.ImageSource;
import com.eduardo.ecomerce.domain.productimage.ProductImage;
import com.eduardo.ecomerce.domain.productimage.ProductImageRepository;
import com.eduardo.ecomerce.domain.productspecification.ProductSpecification;
import com.eduardo.ecomerce.domain.productspecification.ProductSpecificationRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.output.bling.SyncProductsResult;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.eduardo.ecomerce.infra.bling.BlingUnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.Locale;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BlingService {

    private static final int CATEGORIES_PAGE_LIMIT = 100;
    private static final int MAX_PAGES_SAFETY_LIMIT = 500;
    private static final int MAX_SIZE_LENGTH = 100;
    private static final int MAX_SPECIFICATION_NAME_LENGTH = 150;
    private static final int MAX_SPECIFICATION_VALUE_LENGTH = 500;

    private final ProductImageRepository productImageRepository;
    private final ProductSpecificationRepository productSpecificationRepository;
    private final BlingTokenRepository blingTokenRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final BlingClient blingClient;
    private final String authorizeUrl;
    private final String clientId;

    private final TransactionTemplate transactionTemplate;

    private final Cache<String, Boolean> pendingStates = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();



    public BlingService(
            BlingTokenRepository blingTokenRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            ProductSpecificationRepository productSpecificationRepository,
            BlingClient blingClient,
            PlatformTransactionManager transactionManager,
            @Value("${bling.authorize-url}") String authorizeUrl,
            @Value("${bling.client-id}") String clientId
    ) {
        this.blingTokenRepository = blingTokenRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.productSpecificationRepository = productSpecificationRepository;
        this.blingClient = blingClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

    private synchronized String forceRefreshAccessToken() {
        BlingToken token = blingTokenRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new BlingIntegrationException(
                        "Nenhum token do Bling encontrado — autorização ainda não foi realizada", null));

        log.info("Renovando access token do Bling após 401 recebido da API");
        JsonNode response = blingClient.refreshAccessToken(token.getRefreshToken());
        return saveToken(response).getAccessToken();
    }

    private JsonNode callWithRetry(AtomicReference<String> tokenRef, Function<String, JsonNode> apiCall) {
        try {
            return apiCall.apply(tokenRef.get());
        } catch (BlingUnauthorizedException e) {
            String newToken = forceRefreshAccessToken();
            tokenRef.set(newToken);
            return apiCall.apply(newToken);
        }
    }


    public void syncCategories() {
        AtomicReference<String> tokenRef = new AtomicReference<>(getValidAccessToken());
        int page = 1;
        List<JsonNode> categoryNodes = new ArrayList<>();

        while (page <= MAX_PAGES_SAFETY_LIMIT) {
            int currentPage = page;
            JsonNode response = callWithRetry(tokenRef, token -> blingClient.getCategories(token, currentPage, CATEGORIES_PAGE_LIMIT));
            JsonNode data = response.get("data");

            if (data == null || !data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode categoryNode : data) {
                categoryNodes.add(categoryNode);
            }

            page++;
        }

        Map<Long, Category> categoriesByBlingId = new HashMap<>();
        transactionTemplate.execute(status -> {
            for (JsonNode categoryNode : categoryNodes) {
                Category category = upsertCategory(categoryNode);
                if (category != null) categoriesByBlingId.put(category.getBlingCategoryId(), category);
            }
            for (JsonNode categoryNode : categoryNodes) {
                resolveCategoryParent(categoryNode, categoriesByBlingId);
            }
            return null;
        });



        log.info("Sincronização de categorias do Bling concluída: {} categorias processadas", categoryNodes.size());
    }

    private Category upsertCategory(JsonNode categoryNode) {
        Long blingCategoryId = categoryNode.path("id").asLong();
        String descricao = categoryNode.path("descricao").asText();

        Optional<Category> byBlingId = categoryRepository.findByBlingCategoryId(blingCategoryId);

        if (byBlingId.isPresent()) {
            Category category = byBlingId.get();
            category.setName(descricao);
            return categoryRepository.save(category);
        }

        Optional<Category> byName = categoryRepository.findByName(descricao);

        if (byName.isPresent()) {
            Category category = byName.get();
            if (category.getBlingCategoryId() != null && !category.getBlingCategoryId().equals(blingCategoryId)) {
                log.error(
                        "Conflito de categoria: nome '{}' já está vinculado a blingCategoryId={}, "
                                + "mas o Bling está enviando blingCategoryId={} para o mesmo nome — pulando",
                        descricao, category.getBlingCategoryId(), blingCategoryId
                );
                return null;
            }

            category.setBlingCategoryId(blingCategoryId);
            return categoryRepository.save(category);
        }

        Category category = new Category();
        category.setBlingCategoryId(blingCategoryId);
        category.setName(descricao);
        return categoryRepository.save(category);
    }

    private void resolveCategoryParent(JsonNode categoryNode, Map<Long, Category> categoriesByBlingId) {
        long categoryBlingId = categoryNode.path("id").asLong();
        Category category = categoriesByBlingId.get(categoryBlingId);

        if (category == null) {
            return;
        }

        JsonNode parentIdNode = categoryNode.path("categoriaPai").path("id");

        if (parentIdNode.isMissingNode() || parentIdNode.isNull()) {
            category.setParent(null);
            categoryRepository.save(category);
            return;
        }

        long parentBlingId = parentIdNode.asLong();
        Category parent = categoriesByBlingId.get(parentBlingId);

        if (parent == null) {
            log.warn(
                    "Categoria-pai do Bling não encontrada: categoria={}, pai={}",
                    categoryBlingId, parentBlingId
            );
            category.setParent(null);
        } else {
            category.setParent(parent);
        }

        categoryRepository.save(category);
    }


    public SyncProductsResult syncProducts() {
        return syncProducts(MAX_PAGES_SAFETY_LIMIT);
    }


    public SyncProductsResult syncProducts(int maxPages) {
        AtomicReference<String> tokenRef = new AtomicReference<>(getValidAccessToken());

        List<ProductListItem> allItems = fetchAllProductListItems(tokenRef, maxPages);
        ClassifiedListItems classified = classifyListItems(allItems);

        Set<Long> parentIdsWithMediaSynced = new HashSet<>();

        int variantsSynced = 0;
        int variantsSkipped = 0;

        for (ProductListItem item : classified.variantItems()) {
            try {
                boolean synced = upsertProductFromVariation(tokenRef, item, classified.parentNames(), parentIdsWithMediaSynced);
                if (synced) {
                    variantsSynced++;
                } else {
                    variantsSkipped++;
                }
            } catch (Exception e) {
                log.error("Falha ao sincronizar variação do Bling (id={})", item.id(), e);
                variantsSkipped++;
            }
        }

        int standaloneSynced = 0;
        int standaloneSkipped = 0;

        for (ProductListItem item : classified.standaloneItems()) {
            try {
                boolean synced = upsertProductSimples(tokenRef, item);
                if (synced) {
                    standaloneSynced++;
                } else {
                    standaloneSkipped++;
                }
            } catch (Exception e) {
                standaloneSkipped++;
            }
        }

        int deactivated = reconcileRemovedProducts(classified);

        SyncProductsResult result = new SyncProductsResult(
                classified.parentNames().size(),
                variantsSynced,
                variantsSkipped,
                standaloneSynced,
                standaloneSkipped
        );



                log.info(
                "Sincronização de produtos do Bling concluída: {} produtos pai identificados, "
                        + "{} variações sincronizadas, {} variações puladas (conflito/erro), "
                        + "{} produtos simples sincronizados, {} produtos simples pulados (erro)",
                result.parentProductsFound(), result.variantsSynced(), result.variantsSkipped(),
                result.standaloneSynced(), result.standaloneSkipped()
        );

        return result;
    }

    private int reconcileRemovedProducts(ClassifiedListItems classified) {
        Set<Long> seenBlingProductIds = new HashSet<>(classified.parentNames().keySet());
        classified.standaloneItems().forEach(item -> seenBlingProductIds.add(item.id()));

        if (seenBlingProductIds.isEmpty()) {
            log.warn("Reconciliação de produtos removidos abortada: sync não retornou nenhum produto (possível falha na listagem do Bling) — evitando desativar o catálogo inteiro por segurança");
            return 0;
        }

        int deactivated = transactionTemplate.execute(status -> productRepository.deactivateMissingFromBling(seenBlingProductIds));
        if (deactivated > 0) {
            log.info("{} produto(s) desativado(s) por não aparecerem mais na listagem do Bling", deactivated);
        }
        return deactivated;
    }

    private List<ProductListItem> fetchAllProductListItems(AtomicReference<String> tokenRef, int maxPages) {
        List<ProductListItem> items = new ArrayList<>();
        int page = 1;
        int lastPage = Math.min(maxPages, MAX_PAGES_SAFETY_LIMIT);

        while (page <= lastPage) {
            int currentPage = page;
            JsonNode response = callWithRetry(tokenRef, token -> blingClient.listProducts(token, currentPage));
            JsonNode data = response.get("data");

            if (data == null || !data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode itemNode : data) {
                items.add(parseListItem(itemNode));
            }

            page++;
        }

        return items;
    }

    private ProductListItem parseListItem(JsonNode node) {
        JsonNode idProdutoPaiNode = node.path("idProdutoPai");
        Long idProdutoPai = idProdutoPaiNode.isMissingNode() || idProdutoPaiNode.isNull()
                ? null
                : idProdutoPaiNode.asLong();

        return new ProductListItem(
                node.get("id").asLong(),
                node.get("nome").asText(),
                node.get("codigo").asText(),
                new BigDecimal(node.path("preco").asText("0")),
                idProdutoPai,
                node.path("formato").asText("")
        );
    }

    private ClassifiedListItems classifyListItems(List<ProductListItem> items) {
        Map<Long, String> parentNames = new HashMap<>();
        List<ProductListItem> variantItems = new ArrayList<>();
        List<ProductListItem> standaloneItems = new ArrayList<>();

        for (ProductListItem item : items) {
            if (item.idProdutoPai() != null) {
                variantItems.add(item);
            } else if ("V".equals(item.formato())) {
                parentNames.put(item.id(), item.nome());
            } else {
                standaloneItems.add(item);
            }
        }

        return new ClassifiedListItems(parentNames, variantItems, standaloneItems);
    }

    /**
     * @return true se a variação foi sincronizada; false se foi pulada
     * (categoria ausente, produto pai não encontrado na listagem, ou
     * conflito de categoria com o produto pai já existente).
     */

    private boolean upsertProductFromVariation(
            AtomicReference<String> tokenRef,
            ProductListItem item,
            Map<Long, String> parentNames,
            Set<Long> parentIdsWithMediaSynced
    ) {
        JsonNode detail = callWithRetry(tokenRef, token -> blingClient.getProductById(token, item.id()));

        Long blingCategoryId = extractCategoryId(detail);
        Integer stock = extractStock(detail);
        String size = extractSize(detail);
        String description = extractDescription(detail);

        if (blingCategoryId == null) {
            log.error("Variação do Bling sem categoria.id (blingVariationId={}), pulando", item.id());
            return false;
        }

        String parentNome = parentNames.get(item.idProdutoPai());
        if (parentNome == null) {
            log.warn(
                    "Variação do Bling (id={}) referencia idProdutoPai={} não encontrado entre os produtos pai da listagem, pulando",
                    item.id(), item.idProdutoPai()
            );
            return false;
        }

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Category category = categoryRepository.findByBlingCategoryId(blingCategoryId).orElse(null);
            if (category == null) {
                log.error(
                        "Categoria do Bling não encontrada localmente (blingCategoryId={}) para variação id={} "
                                + "— rode syncCategories antes de syncProducts",
                        blingCategoryId, item.id()
                );
                return false;
            }

            Product product = resolveParentProduct(item.idProdutoPai(), item.id(), parentNome, category, description);
            if (product == null) {
                return false;
            }

            if (parentIdsWithMediaSynced.add(item.idProdutoPai())) {
                syncProductMedia(product, detail);
            }

            upsertVariant(product, item.id(), item.sku(), item.price(), stock, size);
            return true;
        }));
    }

    private boolean upsertProductSimples(AtomicReference<String> tokenRef, ProductListItem item) {
        JsonNode detail = callWithRetry(tokenRef, token -> blingClient.getProductById(token, item.id()));

        Long blingCategoryId = extractCategoryId(detail);
        Integer stock = extractStock(detail);
        String description = extractDescription(detail);

        if (blingCategoryId == null) {
            log.error("Produto simples do Bling sem categoria.id (blingProductId={}), pulando", item.id());
            return false;
        }

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Category category = categoryRepository.findByBlingCategoryId(blingCategoryId).orElse(null);
            if (category == null) {
                log.error(
                        "Categoria do Bling não encontrada localmente (blingCategoryId={}) para produto simples id={} "
                                + "— rode syncCategories antes de syncProducts",
                        blingCategoryId, item.id()
                );
                return false;
            }

            Product product = productRepository.findByBlingProductId(item.id()).orElseGet(Product::new);
            product.setBlingProductId(item.id());
            product.setName(item.nome());
            product.setCategory(category);
            if (description != null) {
                product.setDescription(description);
            }
            product = productRepository.save(product);

            syncProductMedia(product, detail);

            upsertVariant(product, null, item.sku(), item.price(), stock, null);
            return true;
        }));
    }

    private void syncProductMedia(Product product, JsonNode detail) {
        List<ExtractedImage> images = extractImages(detail);
        productImageRepository.deleteByProductIdAndSource(product.getId(), ImageSource.BLING);

        for (ExtractedImage image : images) {
            ProductImage productImage = new ProductImage();
            productImage.setProduct(product);
            productImage.setUrl(image.url());
            productImage.setThumbnailUrl(image.thumbnailUrl());
            productImage.setSource(ImageSource.BLING);
            productImage.setDisplayOrder(image.displayOrder());
            productImageRepository.save(productImage);
        }

        List<ExtractedSpecification> specifications = extractSpecifications(detail);
        productSpecificationRepository.deleteByProductId(product.getId());

        for (ExtractedSpecification specification : specifications) {
            ProductSpecification productSpecification = new ProductSpecification();
            productSpecification.setProduct(product);
            productSpecification.setName(specification.name());
            productSpecification.setValue(specification.value());
            productSpecification.setDisplayOrder(specification.displayOrder());
            productSpecificationRepository.save(productSpecification);
        }

        String coverImageUrl = images.isEmpty() ? null : images.get(0).url();
        product.setImageUrl(coverImageUrl);
    }


    private Product resolveParentProduct(Long blingProductId, Long blingVariationId, String nome, Category category, String description) {
        Optional<Product> existing = productRepository.findByBlingProductId(blingProductId);

        if (existing.isEmpty()) {
            Product product = new Product();
            product.setBlingProductId(blingProductId);
            product.setName(nome);
            product.setCategory(category);
            if (description != null) {
                product.setDescription(description);
            }
            return productRepository.save(product);
        }

        Product product = existing.get();
        Long currentCategoryId = product.getCategory() != null
                ? product.getCategory().getBlingCategoryId()
                : null;

        if (currentCategoryId != null && !currentCategoryId.equals(category.getBlingCategoryId())) {
            log.warn(
                    "Categoria alterada no Bling: blingProductId={}, blingVariationId={}, categoria atual={}, "
                            + "nova categoria={} — atualizando (Bling é a fonte da verdade)",
                    blingProductId, blingVariationId, currentCategoryId, category.getBlingCategoryId()
            );
            product.setCategory(category);
        }

        if (description != null) {
            product.setDescription(description);
        }

        return product;
    }


    private void upsertVariant(Product product, Long blingVariationId, String sku, BigDecimal price, Integer stock, String size) {
        ProductVariant variant = findExistingVariant(blingVariationId, sku)
                .orElseGet(ProductVariant::new);

        variant.setProduct(product);
        variant.setBlingVariationId(blingVariationId);
        variant.setSku(sku);
        variant.setPrice(price);
        variant.setStock(stock);
        variant.setSize(size);

        productVariantRepository.save(variant);
    }

    private Optional<ProductVariant> findExistingVariant(Long blingVariationId, String sku) {
        if (blingVariationId != null) {
            Optional<ProductVariant> byVariationId = productVariantRepository.findByBlingVariationId(blingVariationId);
            if (byVariationId.isPresent()) {
                return byVariationId;
            }
        }
        return productVariantRepository.findBySku(sku);
    }

    private Long extractCategoryId(JsonNode detail) {
        JsonNode categoria = detail.path("data").path("categoria");
        JsonNode id = categoria.path("id");
        return id.isMissingNode() || id.isNull() ? null : id.asLong();
    }

    private Integer extractStock(JsonNode detail) {
        JsonNode saldo = detail.path("data").path("estoque").path("saldoVirtualTotal");
        return saldo.isMissingNode() || saldo.isNull() ? 0 : saldo.asInt(0);
    }


    private String extractSize(JsonNode detail) {
        JsonNode nome = detail.path("data").path("variacao").path("nome");
        if (nome.isMissingNode() || nome.isNull()) {
            return null;
        }

        String raw = nome.asText(); // ex: "tamanho:10"
        int colonIndex = raw.indexOf(':');
        String size = colonIndex >= 0 ? raw.substring(colonIndex + 1).trim() : raw;

        if (size.length() > MAX_SIZE_LENGTH) {
            log.warn("Valor de tamanho excede {} caracteres, truncando: \"{}\"", MAX_SIZE_LENGTH, size);
            return size.substring(0, MAX_SIZE_LENGTH);
        }

        return size;
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

    private record ProductListItem(
            Long id,
            String nome,
            String sku,
            BigDecimal price,
            Long idProdutoPai,
            String formato
    ) {
    }

    private record ClassifiedListItems(
            Map<Long, String> parentNames,
            List<ProductListItem> variantItems,
            List<ProductListItem> standaloneItems
    ) {
    }

    private record ExtractedImage(String url, String thumbnailUrl, int displayOrder) {
    }

    private record ExtractedSpecification(String name, String value, int displayOrder) {
    }


    public void debugInspectBlingContract(Long sampleProductId) {
        String accessToken = getValidAccessToken();

        JsonNode listResponse = blingClient.listProducts(accessToken, 1);
        log.info("[DEBUG BLING] Listagem página 1 (bruto): {}", listResponse.toPrettyString());

        if (sampleProductId != null) {
            JsonNode detailResponse = blingClient.getProductById(accessToken, sampleProductId);
            log.info("[DEBUG BLING] Detalhe do produto {} (bruto): {}", sampleProductId, detailResponse.toPrettyString());
        }
    }


    private String extractDescription(JsonNode detail) {
        JsonNode data = detail.path("data");
        String curta = data.path("descricaoCurta").asText("");
        String fonte = !curta.isBlank() ? curta : data.path("descricaoComplementar").asText("");

        if (fonte.isBlank()) {
            return null;
        }

        String texto = Jsoup.parse(fonte).text().replace('\u00a0', ' ').trim();
        return texto.isBlank() ? null : texto;
    }

    private List<ExtractedImage> extractImages(JsonNode detail) {
        JsonNode internas = detail.path("data").path("midia").path("imagens").path("internas");
        if (!internas.isArray() || internas.isEmpty()) {
            return List.of();
        }

        List<ExtractedImage> images = new ArrayList<>();
        int order = 0;

        for (JsonNode imageNode : internas) {
            JsonNode linkNode = imageNode.path("link");
            if (linkNode.isMissingNode() || linkNode.isNull()) {
                continue;
            }

            String url = linkNode.asText();
            if (url.isBlank()) {
                continue;
            }

            JsonNode thumbnailNode = imageNode.path("linkMiniatura");
            String thumbnailUrl = thumbnailNode.isMissingNode()
                    || thumbnailNode.isNull()
                    || thumbnailNode.asText().isBlank()
                    ? null
                    : thumbnailNode.asText();

            images.add(new ExtractedImage(url, thumbnailUrl, order++));
        }

        return images;
    }

    private List<ExtractedSpecification> extractSpecifications(JsonNode detail) {
        String html = detail.path("data").path("descricaoComplementar").asText("");
        if (html.isBlank()) {
            return List.of();
        }

        Document doc = Jsoup.parse(html);
        Elements allElements = doc.getAllElements();

        Element marker = null;
        for (Element element : allElements) {
            String ownText = element.ownText();
            if (!ownText.isBlank() && ownText.toLowerCase(Locale.ROOT).contains("especifica")) {
                marker = element;
                break;
            }
        }

        if (marker == null) {
            return List.of();
        }

        Element specList = null;
        boolean pastMarker = false;
        for (Element element : allElements) {
            if (element == marker) {
                pastMarker = true;
                continue;
            }
            if (pastMarker && "ul".equalsIgnoreCase(element.tagName())) {
                specList = element;
                break;
            }
        }

        if (specList == null) {
            return List.of();
        }

        List<ExtractedSpecification> specifications = new ArrayList<>();
        int order = 0;

        for (Element item : specList.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }

            Element strong = item.selectFirst("strong");
            if (strong == null) {
                continue;
            }

            String name = strong.text().replaceAll(":\\s*$", "").trim();
            String itemText = item.text();
            String strongText = strong.text();
            String value = itemText.length() > strongText.length()
                    ? itemText.substring(strongText.length()).trim()
                    : "";

            if (name.isBlank() || value.isBlank()) {
                continue;
            }

            if (name.length() > MAX_SPECIFICATION_NAME_LENGTH || value.length() > MAX_SPECIFICATION_VALUE_LENGTH) {
                log.warn("Especificação ignorada por exceder tamanho máximo (nome=\"{}\")", name);
                continue;
            }

            specifications.add(new ExtractedSpecification(name, value, order++));
        }

        return specifications;
    }

}