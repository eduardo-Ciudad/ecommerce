package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.blingtoken.BlingToken;
import com.eduardo.ecomerce.domain.blingtoken.BlingTokenRepository;
import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productimage.ImageSource;
import com.eduardo.ecomerce.domain.productimage.ProductImageRepository;
import com.eduardo.ecomerce.domain.productspecification.ProductSpecificationRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
}
