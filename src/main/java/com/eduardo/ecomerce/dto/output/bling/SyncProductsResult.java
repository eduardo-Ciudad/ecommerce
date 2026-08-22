package com.eduardo.ecomerce.dto.output.bling;

public record SyncProductsResult(
        int parentProductsFound,
        int variantsSynced,
        int variantsSkipped,
        int standaloneSynced,
        int standaloneSkipped
) {
}