package com.eduardo.ecomerce.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlingImageSyncServiceTest {

    @Test
    void objectKeyKeepsLegacyPathWhenThereIsNoColor() {
        assertThat(BlingImageSyncService.objectKey(500L, null, 0, false)).isEqualTo("products/500/0.jpg");
        assertThat(BlingImageSyncService.objectKey(500L, "  ", 0, true)).isEqualTo("products/500/0_thumb.jpg");
    }

    @Test
    void objectKeyAddsSlugifiedColorFolder() {
        assertThat(BlingImageSyncService.objectKey(500L, "Salmão neon", 3, false))
                .isEqualTo("products/500/salmao-neon/3.jpg");
        assertThat(BlingImageSyncService.objectKey(500L, "Azul Marinho", 1, true))
                .isEqualTo("products/500/azul-marinho/1_thumb.jpg");
    }
}