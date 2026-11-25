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
import com.eduardo.ecomerce.domain.productspecification.ProductSpecificationRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.output.bling.ProductResyncResult;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import com.eduardo.ecomerce.infra.bling.BlingUnauthorizedException;
import com.eduardo.ecomerce.service.bling.SyncProgressListener;
import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemKind;
import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.util.Arrays.sizeOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlingServiceTest {

    @Mock private BlingTokenRepository blingTokenRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductSpecificationRepository productSpecificationRepository;
    @Mock private BlingImageSyncService blingImageSyncService;
    @Mock private BlingClient blingClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BlingService service;

    @BeforeEach
    void setUp() {
        service = new BlingService(
                blingTokenRepository,
                categoryRepository,
                productRepository,
                productVariantRepository,
                productImageRepository,
                productSpecificationRepository,
                blingImageSyncService,
                blingClient,
                transactionManager,
                "https://bling.example/authorize",
                "client-id"
        );
    }

    @Test
    void syncProductsWithoutLimitFetchesUntilAnEmptyPageAndReconcilesEverySeenParent() throws Exception {
        validToken();
        when(blingClient.listProducts("token", 1)).thenReturn(json("""
                {"data":[{"id":101,"nome":"Pai 1","codigo":"P1","preco":"10.00","formato":"V"}]}
                """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
                {"data":[{"id":202,"nome":"Pai 2","codigo":"P2","preco":"20.00","formato":"V"}]}
                """));
        when(blingClient.listProducts("token", 3)).thenReturn(json("""
                {"data":[]}
                """));

        service.syncProducts();

        verify(blingClient).listProducts("token", 1);
        verify(blingClient).listProducts("token", 2);
        verify(blingClient).listProducts("token", 3);
        verify(productRepository).deactivateMissingFromBling(Set.of(101L, 202L));
    }

    @Test
    void emptyProductListingDoesNotDeactivateAnything() throws Exception {
        validToken();
        when(blingClient.listProducts("token", 1)).thenReturn(json("""
                {"data":[]}
                """));

        service.syncProducts();

        verify(productRepository, never()).deactivateMissingFromBling(any());
    }

    @Test
    void extractImagesSkipsEntriesWithMissingOrBlankLink() throws Exception {
        JsonNode missingLink = json("{" +
                "\"data\":{\"midia\":{\"imagens\":{\"internas\":[{}]}}}}" );
        JsonNode blankLink = json("{" +
                "\"data\":{\"midia\":{\"imagens\":{\"internas\":[{\"link\":\"\"}]}}}}" );

        List<?> imagesFromMissingLink = (List<?>) ReflectionTestUtils.invokeMethod(service, "extractImages", missingLink);
        List<?> imagesFromBlankLink = (List<?>) ReflectionTestUtils.invokeMethod(service, "extractImages", blankLink);

        assertThat(imagesFromMissingLink).isEmpty();
        assertThat(imagesFromBlankLink).isEmpty();
    }

    @Test
    void blankImageFromBlingClearsExistingCoverImage() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);
        Product existing = new Product();
        existing.setBlingProductId(303L);
        existing.setName("Produto existente");
        existing.setCategory(category);
        existing.setImageUrl("https://cdn.example/original.jpg");

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[{"id":303,"nome":"Produto","codigo":"SKU-303","preco":"30.00","formato":"S"}]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 303L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":4},
            "midia":{"imagens":{"internas":[{"link":""}]}}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(303L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        service.syncProducts();

        assertThat(existing.getImageUrl()).isNull();
        verify(productImageRepository).deleteByProductIdAndSource(existing.getId(), ImageSource.BLING);
        verify(productRepository).save(existing);
    }


    @Test
    void extractVariationAttributesReadsColorAndSizeWhenColorComesFirst() throws Exception {
        Object attributes = extractVariationAttributes("Cor:Preto;Tamanho:4");

        assertThat(sizeOf(attributes)).isEqualTo("4");
        assertThat(colorOf(attributes)).isEqualTo("Preto");
    }

    @Test
    void extractVariationAttributesReadsSizeOnlyVariationWithoutColor() throws Exception {
        Object attributes = extractVariationAttributes("tamanho:10");

        assertThat(sizeOf(attributes)).isEqualTo("10");
        assertThat(colorOf(attributes)).isNull();
    }

    @Test
    void extractVariationAttributesIgnoresCaseAndAccentsInKeysButPreservesValues() throws Exception {
        Object attributes = extractVariationAttributes("TAMANHO: 6 ;Côr: Rosa Neon ");

        assertThat(sizeOf(attributes)).isEqualTo("6");
        assertThat(colorOf(attributes)).isEqualTo("Rosa Neon");
    }

    @Test
    void extractVariationAttributesTreatsPrintAsColorAndKeepsColonsInsideValues() throws Exception {
        Object attributes = extractVariationAttributes("Estampa:Dino:Azul;Tamanho:8");

        assertThat(sizeOf(attributes)).isEqualTo("8");
        assertThat(colorOf(attributes)).isEqualTo("Dino:Azul");
    }

    @Test
    void extractVariationAttributesReadsPrintAndSizeFromRealBlingFormat() throws Exception {
        Object attributes = extractVariationAttributes("estampa:vermelha poá;tamanho:1");

        assertThat(sizeOf(attributes)).isEqualTo("1");
        assertThat(colorOf(attributes)).isEqualTo("vermelha poá");
    }

    @Test
    void extractVariationAttributesReadsPrintWhenSizeComesFirst() throws Exception {
        Object attributes = extractVariationAttributes("tamanho:4;estampa:bananas");

        assertThat(sizeOf(attributes)).isEqualTo("4");
        assertThat(colorOf(attributes)).isEqualTo("bananas");
    }

    @Test
    void extractVariationAttributesCombinesColorAndPrintWhenBothArePresent() throws Exception {
        Object attributes = extractVariationAttributes("Cor:Rosa;Estampa:Dino;Tamanho:2");

        assertThat(sizeOf(attributes)).isEqualTo("2");
        assertThat(colorOf(attributes)).isEqualTo("Rosa / Dino");
    }

    @Test
    void extractVariationAttributesStillIgnoresUnknownAttributes() throws Exception {
        Object attributes = extractVariationAttributes("Voltagem:220v;Tamanho:8");

        assertThat(sizeOf(attributes)).isEqualTo("8");
        assertThat(colorOf(attributes)).isNull();
    }

    @Test
    void extractVariationAttributesReturnsEmptyWhenVariationIsMissing() throws Exception {
        Object attributes = ReflectionTestUtils.invokeMethod(service, "extractVariationAttributes",
                json("{\"data\":{}}"));

        assertThat(sizeOf(attributes)).isNull();
        assertThat(colorOf(attributes)).isNull();
    }

    @Test
    void syncProductsPersistsColorAndSizeOnVariantFromBlingVariation() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":500,"nome":"Short Moletinho","codigo":"PAI-500","preco":"34.99","formato":"V"},
              {"id":501,"nome":"Short Moletinho Preto 4","codigo":"SKU-501","preco":"34.99","formato":"S","idProdutoPai":500}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 501L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":3},
            "variacao":{"nome":"Cor:Preto;Tamanho:4"}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findByBlingVariationId(501L)).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("SKU-501")).thenReturn(Optional.empty());

        service.syncProducts();

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        ProductVariant saved = captor.getValue();
        assertThat(saved.getSize()).isEqualTo("4");
        assertThat(saved.getColor()).isEqualTo("Preto");
        assertThat(saved.getBlingVariationId()).isEqualTo(501L);
        assertThat(saved.getStock()).isEqualTo(3);
    }

    @Test
    void syncProductsPersistsPrintAsColorOnVariant() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":600,"nome":"Bermuda Ciclista Estampada","codigo":"PAI-600","preco":"24.99","formato":"V"},
              {"id":601,"nome":"Bermuda Ciclista Vermelha Poa 1","codigo":"SKU-601","preco":"24.99","formato":"S","idProdutoPai":600}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 601L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":2},
            "variacao":{"nome":"estampa:vermelha poá;tamanho:1"}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(600L)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findByBlingVariationId(601L)).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("SKU-601")).thenReturn(Optional.empty());

        service.syncProducts();

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        ProductVariant saved = captor.getValue();
        assertThat(saved.getSize()).isEqualTo("1");
        assertThat(saved.getColor()).isEqualTo("vermelha poá");
        assertThat(saved.getBlingVariationId()).isEqualTo(601L);
    }

    private Object extractVariationAttributes(String variationName) {
        JsonNode detail = objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode()
                        .set("variacao", objectMapper.createObjectNode().put("nome", variationName)));
        return ReflectionTestUtils.invokeMethod(service, "extractVariationAttributes", detail);
    }

    private String sizeOf(Object attributes) {
        return ReflectionTestUtils.invokeMethod(attributes, "size");
    }

    private String colorOf(Object attributes) {
        return ReflectionTestUtils.invokeMethod(attributes, "color");
    }

    // ---------- callBling (usado pelo sync de pedidos) ----------

    @Test
    void callBling_tokenValido_executaChamadaComTokenERetornaResposta() {
        validToken();
        JsonNode expected = objectMapper.createObjectNode().put("ok", true);
        List<String> tokensUsed = new ArrayList<>();

        JsonNode result = service.callBling(token -> {
            tokensUsed.add(token);
            return expected;
        });

        assertThat(result).isSameAs(expected);
        assertThat(tokensUsed).containsExactly("token");
        verify(blingClient, never()).refreshAccessToken(any());
    }

    @Test
    void callBling_401NaPrimeiraChamada_renovaTokenETentaDeNovoComTokenNovo() throws Exception {
        validToken();
        when(blingClient.refreshAccessToken("refresh")).thenReturn(json("""
                {"access_token":"new-token","refresh_token":"new-refresh","expires_in":21600}
                """));
        when(blingTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JsonNode expected = objectMapper.createObjectNode().put("ok", true);
        List<String> tokensUsed = new ArrayList<>();

        JsonNode result = service.callBling(token -> {
            tokensUsed.add(token);
            if ("token".equals(token)) {
                throw new BlingUnauthorizedException("expirado", null, 401, "invalid_token", "expirado");
            }
            return expected;
        });

        assertThat(result).isSameAs(expected);
        assertThat(tokensUsed).containsExactly("token", "new-token");
        verify(blingClient).refreshAccessToken("refresh");
    }

    // ---------- resyncProduct (botão do admin: ressincronizar um produto) ----------

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Product existingProduct(Long blingProductId, Category category) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setBlingProductId(blingProductId);
        product.setName("Nome antigo");
        product.setCategory(category);
        return product;
    }

    private Category category77() {
        Category category = new Category();
        category.setBlingCategoryId(77L);
        return category;
    }

    @Test
    void resyncProductUsesOnlyParentDetailAndSyncsEveryEmbeddedVariation() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = category77();
        Product product = existingProduct(700L, category);

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 700L)).thenReturn(json("""
            {"data":{"id":700,"nome":"Conjunto Dino Skatista","codigo":"PAI-700","preco":54.99,"formato":"V",
              "variacoes":[
                {"id":801,"nome":"Conjunto Dino cor:vermelho e chumbo;tamanho:1","codigo":"SKU-801","preco":54.99,"formato":"S",
                 "estoque":{"saldoVirtualTotal":1},"gtin":"7900257380456","categoria":{"id":77},
                 "variacao":{"nome":"cor:vermelho e chumbo;tamanho:1","produtoPai":{"id":700}}},
                {"id":802,"nome":"Conjunto Dino cor:vermelho e chumbo;tamanho:2","codigo":"SKU-802","preco":54.99,"formato":"S",
                 "estoque":{"saldoVirtualTotal":2},"gtin":"7900257380463","categoria":{"id":77},
                 "variacao":{"nome":"cor:vermelho e chumbo;tamanho:2","produtoPai":{"id":700}}}
              ]}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(700L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByBlingVariationId(any())).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku(any())).thenReturn(Optional.empty());

        ProductResyncResult result = service.resyncProduct(PRODUCT_ID);

        verify(blingClient, times(1)).getProductById(any(), any());
        verify(blingClient).getProductById("token", 700L);
        verify(blingClient, never()).listProducts(any(), anyInt());
        verify(productRepository, never()).deactivateMissingFromBling(any());

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ProductVariant::getBlingVariationId).containsExactly(801L, 802L);
        assertThat(captor.getAllValues()).extracting(ProductVariant::getSku).containsExactly("SKU-801", "SKU-802");
        assertThat(captor.getAllValues()).extracting(ProductVariant::getSize).containsExactly("1", "2");
        assertThat(captor.getAllValues()).extracting(ProductVariant::getColor)
                .containsExactly("vermelho e chumbo", "vermelho e chumbo");
        assertThat(captor.getAllValues()).extracting(ProductVariant::getStock).containsExactly(1, 2);
        assertThat(captor.getAllValues()).extracting(ProductVariant::getGtin)
                .containsExactly("7900257380456", "7900257380463");
        assertThat(captor.getAllValues().get(0).getPrice()).isEqualByComparingTo("54.99");

        assertThat(product.getName()).isEqualTo("Conjunto Dino Skatista");
        assertThat(result).isEqualTo(new ProductResyncResult(PRODUCT_ID, "Conjunto Dino Skatista", 2, 0, 0, 0));
    }

    @Test
    void resyncProductResetsMediaOnceAndKeepsOneGalleryPerColor() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = category77();
        Product product = existingProduct(500L, category);

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 500L)).thenReturn(json("""
            {"data":{"id":500,"nome":"Short Moletinho","formato":"V","variacoes":[
              {"id":501,"codigo":"SKU-501","preco":34.99,"categoria":{"id":77},"estoque":{"saldoVirtualTotal":3},
               "variacao":{"nome":"Cor:Preto;Tamanho:4"},
               "midia":{"imagens":{"internas":[{"link":"https://bling.example/preto.jpg?sig=1"}]}}},
              {"id":502,"codigo":"SKU-502","preco":34.99,"categoria":{"id":77},"estoque":{"saldoVirtualTotal":2},
               "variacao":{"nome":"Cor:Rosa;Tamanho:4"},
               "midia":{"imagens":{"internas":[{"link":"https://bling.example/rosa.jpg?sig=1"}]}}}
            ]}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.of(product));
        when(blingImageSyncService.syncImages(eq(500L), eq("Preto"), any())).thenReturn(List.of(
                new BlingImageSyncService.UploadedImage("https://r2.example/preto.jpg", "https://r2.example/preto_thumb.jpg", 0)));
        when(blingImageSyncService.syncImages(eq(500L), eq("Rosa"), any())).thenReturn(List.of(
                new BlingImageSyncService.UploadedImage("https://r2.example/rosa.jpg", "https://r2.example/rosa_thumb.jpg", 1)));

        service.resyncProduct(PRODUCT_ID);

        verify(blingImageSyncService).syncImages(500L, "Rosa",
                List.of(new BlingImageSyncService.SourceImage("https://bling.example/rosa.jpg?sig=1", 1)));
        verify(productImageRepository, times(1)).deleteByProductIdAndSource(PRODUCT_ID, ImageSource.BLING);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ProductImage::getColor).containsExactly("Preto", "Rosa");
        assertThat(product.getImageUrl()).isEqualTo("https://r2.example/preto.jpg");
    }

    @Test
    void resyncProductKeepsGoingWhenOneVariationFailsAndZeroesUsingEveryListedVariation() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = category77();
        Product product = existingProduct(700L, category);

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 700L)).thenReturn(json("""
            {"data":{"id":700,"nome":"Conjunto","formato":"V","variacoes":[
              {"id":801,"codigo":"SKU-801","preco":10,"categoria":{"id":77},"estoque":{"saldoVirtualTotal":1},
               "variacao":{"nome":"tamanho:1"}},
              {"id":802,"codigo":"SKU-802","preco":10,"categoria":{"id":77},"estoque":{"saldoVirtualTotal":1},
               "variacao":{"nome":"tamanho:2"}}
            ]}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(700L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByBlingVariationId(801L)).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("SKU-801")).thenReturn(Optional.empty());
        when(productVariantRepository.findByBlingVariationId(802L)).thenThrow(new IllegalStateException("banco fora"));
        when(productVariantRepository.zeroStockOfVariantsMissingFromBling(PRODUCT_ID, Set.of(801L, 802L))).thenReturn(1);

        ProductResyncResult result = service.resyncProduct(PRODUCT_ID);

        verify(productVariantRepository).zeroStockOfVariantsMissingFromBling(PRODUCT_ID, Set.of(801L, 802L));
        assertThat(result).isEqualTo(new ProductResyncResult(PRODUCT_ID, "Conjunto", 1, 0, 1, 1));
    }

    @Test
    void resyncProductDoesNotTouchVariantsWhenBlingReturnsNoVariations() throws Exception {
        validToken();
        Product product = existingProduct(700L, category77());

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 700L)).thenReturn(json("""
            {"data":{"id":700,"nome":"Conjunto","formato":"V","variacoes":[]}}
            """));

        ProductResyncResult result = service.resyncProduct(PRODUCT_ID);

        verify(productVariantRepository, never()).zeroStockOfVariantsMissingFromBling(any(), any());
        verify(productVariantRepository, never()).save(any());
        assertThat(result).isEqualTo(new ProductResyncResult(PRODUCT_ID, "Conjunto", 0, 0, 0, 0));
    }

    @Test
    void resyncProductUpdatesSimpleProductWithoutZeroingVariants() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = category77();
        Product product = existingProduct(900L, category);

        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 900L)).thenReturn(json("""
            {"data":{"id":900,"nome":"Body Liso","codigo":"SKU-900","preco":19.90,"formato":"S",
              "categoria":{"id":77},"estoque":{"saldoVirtualTotal":4}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(900L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findBySku("SKU-900")).thenReturn(Optional.empty());

        ProductResyncResult result = service.resyncProduct(PRODUCT_ID);

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        assertThat(captor.getValue().getStock()).isEqualTo(4);
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-900");
        assertThat(product.getName()).isEqualTo("Body Liso");
        verify(productVariantRepository, never()).zeroStockOfVariantsMissingFromBling(any(), any());
        assertThat(result).isEqualTo(new ProductResyncResult(PRODUCT_ID, "Body Liso", 1, 0, 0, 0));
    }

    @Test
    void resyncProductThrowsNotFoundWhenProductDoesNotExist() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resyncProduct(PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(blingClient);
    }

    @Test
    void resyncProductRejectsProductNotLinkedToBling() {
        Product product = existingProduct(null, category77());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.resyncProduct(PRODUCT_ID))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(blingClient);
    }

    @Test
    void resyncProductThrowsNotFoundWhenBlingAnswers404() {
        validToken();
        Product product = existingProduct(700L, category77());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 700L))
                .thenThrow(new BlingIntegrationException("não encontrado", null, 404, null, null));

        assertThatThrownBy(() -> service.resyncProduct(PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void resyncProductPropagatesOtherBlingErrors() {
        validToken();
        Product product = existingProduct(700L, category77());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(blingClient.getProductById("token", 700L))
                .thenThrow(new BlingIntegrationException("fora do ar", null, 503, null, null));

        assertThatThrownBy(() -> service.resyncProduct(PRODUCT_ID))
                .isInstanceOf(BlingIntegrationException.class);
    }

    @Test
    void syncProductsUpdatesNameOfExistingParentProduct() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = category77();
        Product product = existingProduct(500L, category);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":500,"nome":"Nome novo","codigo":"PAI-500","preco":"34.99","formato":"V"},
              {"id":501,"nome":"Nome novo 4","codigo":"SKU-501","preco":"34.99","formato":"S","idProdutoPai":500}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 501L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":1},"variacao":{"nome":"Tamanho:4"}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByBlingVariationId(501L)).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("SKU-501")).thenReturn(Optional.empty());

        service.syncProducts();

        assertThat(product.getName()).isEqualTo("Nome novo");
    }

    private void validToken() {
        BlingToken token = new BlingToken();
        token.setAccessToken("token");
        token.setRefreshToken("refresh");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(blingTokenRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(token));
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    @Test
    void isValidGtinAcceptsRealGtinsOfEveryLength() {
        assertThat(BlingService.isValidGtin("7900257197337")).isTrue();  // EAN-13 real (Brandili)
        assertThat(BlingService.isValidGtin("96385074")).isTrue();       // GTIN-8
        assertThat(BlingService.isValidGtin("036000291452")).isTrue();   // UPC-12
        assertThat(BlingService.isValidGtin("00012345600012")).isTrue(); // GTIN-14
    }

    @Test
    void isValidGtinRejectsWrongCheckDigitGarbageAndZeros() {
        assertThat(BlingService.isValidGtin("7900257197338")).isFalse(); // dígito verificador errado
        assertThat(BlingService.isValidGtin("")).isFalse();
        assertThat(BlingService.isValidGtin("123456")).isFalse();        // tamanho inválido
        assertThat(BlingService.isValidGtin("0000000000000")).isFalse();
        assertThat(BlingService.isValidGtin(null)).isFalse();
    }

    @Test
    void extractGtinStripsWhitespaceAndReturnsNullWhenInvalidOrMissing() {
        assertThat(extractGtin("\t7900257197337")).isEqualTo("7900257197337");
        assertThat(extractGtin("SEM GTIN")).isNull();
        assertThat(extractGtin("7900257197338")).isNull();

        JsonNode semCampo = objectMapper.createObjectNode().set("data", objectMapper.createObjectNode());
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "extractGtin", semCampo)).isNull();
    }

    private String extractGtin(String gtin) {
        JsonNode detail = objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode().put("gtin", gtin));
        return ReflectionTestUtils.invokeMethod(service, "extractGtin", detail);
    }

    @Test
    void syncProductsKeepsOneGalleryPerColorAndResetsParentMediaOnlyOnce() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);
        Product product = new Product();
        product.setBlingProductId(500L);
        product.setCategory(category);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":500,"nome":"Short Moletinho","codigo":"PAI-500","preco":"34.99","formato":"V"},
              {"id":501,"nome":"Short Preto 4","codigo":"SKU-501","preco":"34.99","formato":"S","idProdutoPai":500},
              {"id":502,"nome":"Short Rosa 4","codigo":"SKU-502","preco":"34.99","formato":"S","idProdutoPai":500}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 501L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":3},
            "variacao":{"nome":"Cor:Preto;Tamanho:4"},
            "midia":{"imagens":{"internas":[{"link":"https://bling.example/preto.jpg?sig=1"}]}}}}
            """));
        when(blingClient.getProductById("token", 502L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":2},
            "variacao":{"nome":"Cor:Rosa;Tamanho:4"},
            "midia":{"imagens":{"internas":[{"link":"https://bling.example/rosa.jpg?sig=1"}]}}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.of(product));
        when(blingImageSyncService.syncImages(eq(500L), eq("Preto"), any())).thenReturn(List.of(
                new BlingImageSyncService.UploadedImage("https://r2.example/preto.jpg", "https://r2.example/preto_thumb.jpg", 0)));
        when(blingImageSyncService.syncImages(eq(500L), eq("Rosa"), any())).thenReturn(List.of(
                new BlingImageSyncService.UploadedImage("https://r2.example/rosa.jpg", "https://r2.example/rosa_thumb.jpg", 1)));

        service.syncProducts();

        // Rosa continua a numeração depois do Preto
        verify(blingImageSyncService).syncImages(500L, "Rosa",
                List.of(new BlingImageSyncService.SourceImage("https://bling.example/rosa.jpg?sig=1", 1)));
        // a 2ª cor NÃO apaga a galeria da 1ª
        verify(productImageRepository, times(1)).deleteByProductIdAndSource(product.getId(), ImageSource.BLING);

        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ProductImage::getColor).containsExactly("Preto", "Rosa");
        assertThat(captor.getAllValues()).extracting(ProductImage::getDisplayOrder).containsExactly(0, 1);
        assertThat(product.getImageUrl()).isEqualTo("https://r2.example/preto.jpg");
    }

    @Test
    void syncProductsImportsSameImageOnlyOnceAcrossSizesOfSameColor() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);
        Product product = new Product();
        product.setBlingProductId(500L);
        product.setCategory(category);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":500,"nome":"Short Moletinho","codigo":"PAI-500","preco":"34.99","formato":"V"},
              {"id":501,"nome":"Short Preto 4","codigo":"SKU-501","preco":"34.99","formato":"S","idProdutoPai":500},
              {"id":502,"nome":"Short Preto 6","codigo":"SKU-502","preco":"34.99","formato":"S","idProdutoPai":500}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 501L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":3},
            "variacao":{"nome":"Cor:Preto;Tamanho:4"},
            "midia":{"imagens":{"internas":[{"link":"https://bling.example/preto.jpg?sig=A"}]}}}}
            """));
        when(blingClient.getProductById("token", 502L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":2},
            "variacao":{"nome":"Cor:Preto;Tamanho:6"},
            "midia":{"imagens":{"internas":[{"link":"https://bling.example/preto.jpg?sig=B"}]}}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.of(product));
        when(blingImageSyncService.syncImages(eq(500L), eq("Preto"), any())).thenReturn(List.of(
                new BlingImageSyncService.UploadedImage("https://r2.example/preto.jpg", "https://r2.example/preto_thumb.jpg", 0)));

        service.syncProducts();

        verify(blingImageSyncService, times(1)).syncImages(eq(500L), eq("Preto"), any());
        verify(productImageRepository, times(1)).save(any(ProductImage.class));
    }

    @Test
    void syncProductsWithListenerReportsTotalEachItemAndDeactivatedCount() throws Exception {
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        validToken();
        Category category = new Category();
        category.setBlingCategoryId(77L);

        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":500,"nome":"Short Moletinho","codigo":"PAI-500","preco":"34.99","formato":"V"},
              {"id":501,"nome":"Short Moletinho Preto 4","codigo":"SKU-501","preco":"34.99","formato":"S","idProdutoPai":500},
              {"id":600,"nome":"Body Liso","codigo":"SKU-600","preco":"20.00","formato":"S"}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 501L)).thenReturn(json("""
            {"data":{"categoria":{"id":77},"estoque":{"saldoVirtualTotal":3},
            "variacao":{"nome":"Cor:Preto;Tamanho:4"}}}
            """));
        when(blingClient.getProductById("token", 600L)).thenReturn(json("""
            {"data":{"estoque":{"saldoVirtualTotal":1}}}
            """));
        when(categoryRepository.findByBlingCategoryId(77L)).thenReturn(Optional.of(category));
        when(productRepository.findByBlingProductId(500L)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.findByBlingVariationId(501L)).thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("SKU-501")).thenReturn(Optional.empty());
        when(productRepository.deactivateMissingFromBling(Set.of(500L, 600L))).thenReturn(2);
        RecordingListener listener = new RecordingListener();

        service.syncProducts(listener);

        assertThat(listener.total).isEqualTo(2);
        assertThat(listener.items).containsExactly(
                "501|Short Moletinho Preto 4|VARIANT|SYNCED",
                "600|Body Liso|STANDALONE|SKIPPED"
        );
        assertThat(listener.reconciling).isTrue();
        assertThat(listener.deactivated).isEqualTo(2);
    }

    @Test
    void syncProductsWithListenerReportsFailedWhenItemThrowsAndKeepsGoing() throws Exception {
        validToken();
        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[
              {"id":700,"nome":"Quebrado","codigo":"SKU-700","preco":"10.00","formato":"S"},
              {"id":701,"nome":"Sem categoria","codigo":"SKU-701","preco":"10.00","formato":"S"}
            ]}
            """));
        when(blingClient.listProducts("token", 2)).thenReturn(json("""
            {"data":[]}
            """));
        when(blingClient.getProductById("token", 700L)).thenThrow(new IllegalStateException("boom"));
        when(blingClient.getProductById("token", 701L)).thenReturn(json("""
            {"data":{}}
            """));
        RecordingListener listener = new RecordingListener();

        service.syncProducts(listener);

        assertThat(listener.items).containsExactly(
                "700|Quebrado|STANDALONE|FAILED",
                "701|Sem categoria|STANDALONE|SKIPPED"
        );
    }

    @Test
    void syncProductsWithLimitedPagesStillSkipsReconcile() throws Exception {
        validToken();
        when(blingClient.listProducts("token", 1)).thenReturn(json("""
            {"data":[]}
            """));

        service.syncProducts(1);

        verify(productRepository, never()).deactivateMissingFromBling(any());
    }

    @Test
    void syncCategoriesReturnsNumberOfCategoriesProcessed() throws Exception {
        validToken();
        when(blingClient.getCategories("token", 1, 100)).thenReturn(json("""
            {"data":[{"id":1,"descricao":"Meninas"},{"id":2,"descricao":"Vestidos","categoriaPai":{"id":1}}]}
            """));
        when(blingClient.getCategories("token", 2, 100)).thenReturn(json("""
            {"data":[]}
            """));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = service.syncCategories();

        assertThat(processed).isEqualTo(2);
    }

    private static class RecordingListener implements SyncProgressListener {
        int total;
        boolean reconciling;
        int deactivated;
        final List<String> items = new ArrayList<>();

        @Override
        public void onTotal(int totalItems) {
            this.total = totalItems;
        }

        @Override
        public void onItem(Long blingId, String name, ItemKind kind, ItemOutcome outcome) {
            items.add(blingId + "|" + name + "|" + kind + "|" + outcome);
        }

        @Override
        public void onReconciling() {
            this.reconciling = true;
        }

        @Override
        public void onDeactivated(int count) {
            this.deactivated = count;
        }
    }
}
