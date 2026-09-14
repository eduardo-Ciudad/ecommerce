package com.eduardo.ecomerce.dto.output.product;

import com.eduardo.ecomerce.dto.output.productimage.ProductImageOutput;
import com.eduardo.ecomerce.dto.output.productspecification.ProductSpecificationOutput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductOutput(
        UUID id,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        List<ProductImageOutput> images,
        List<ProductSpecificationOutput> specifications,
        Boolean active,
        List<ProductVariantOutput> variants,
        LocalDateTime createdAt,
        Long blingProductId
) { }
