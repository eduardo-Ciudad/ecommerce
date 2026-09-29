package com.eduardo.ecomerce.service.feed;

import com.eduardo.ecomerce.domain.productimage.ProductImageRepository;
import com.eduardo.ecomerce.domain.productspecification.ProductSpecificationRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoogleFeedService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductSpecificationRepository productSpecificationRepository;
    private final String siteUrl;

    public GoogleFeedService(ProductVariantRepository productVariantRepository,
                             ProductImageRepository productImageRepository,
                             ProductSpecificationRepository productSpecificationRepository,
                             @Value("${app.frontend-url}") String frontendUrl) {
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.productSpecificationRepository = productSpecificationRepository;
        this.siteUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** 3 queries no total, independente do tamanho do catálogo. */
    @Transactional(readOnly = true)
    public String buildGoogleFeed() {
        List<ProductVariant> variants = productVariantRepository.findAllForFeed();

        Map<UUID, List<String>> imagesByProduct = productImageRepository.findAllForFeed().stream()
                .collect(Collectors.groupingBy(
                        ProductImageRepository.FeedImageView::getProductId,
                        Collectors.mapping(ProductImageRepository.FeedImageView::getUrl, Collectors.toList())
                ));

        Map<UUID, String> brandByProduct = new HashMap<>();
        productSpecificationRepository.findBrandsForFeed()
                .forEach(view -> brandByProduct.putIfAbsent(view.getProductId(), view.getValue()));

        List<GoogleFeedItem> items = new ArrayList<>();
        int skippedWithoutImage = 0;
        for (ProductVariant variant : variants) {
            UUID productId = variant.getProduct().getId();
            GoogleFeedItem item = GoogleFeedMapper.toItem(
                    variant,
                    imagesByProduct.getOrDefault(productId, List.of()),
                    brandByProduct.get(productId),
                    siteUrl
            );
            if (item == null) {
                skippedWithoutImage++;
            } else {
                items.add(item);
            }
        }

        log.info("Feed do Google gerado: {} itens, {} variantes puladas por falta de imagem",
                items.size(), skippedWithoutImage);
        return GoogleFeedWriter.write(items, siteUrl);
    }
}