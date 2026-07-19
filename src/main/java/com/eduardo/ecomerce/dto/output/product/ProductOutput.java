package com.eduardo.ecomerce.dto.output.product;

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
        Boolean active,
        List<ProductVariantOutput> variants,
        LocalDateTime createdAt
) { }
