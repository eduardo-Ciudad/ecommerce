package com.eduardo.ecomerce.dto.output.bling;

import java.util.UUID;

public record ProductResyncResult(
        UUID productId,
        String name,
        int variantsSynced,
        int variantsSkipped,
        int variantsFailed,
        int variantsZeroed
) {
}
