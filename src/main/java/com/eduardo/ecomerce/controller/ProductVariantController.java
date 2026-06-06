package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.productvariant.ProductVariantInput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;
import com.eduardo.ecomerce.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<ProductVariantOutput> create(
            @PathVariable UUID productId,
            @RequestBody @Valid ProductVariantInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productVariantService.create(productId, input));
    }

    @PutMapping("/variants/{id}")
    public ResponseEntity<ProductVariantOutput> update(
            @PathVariable UUID id,
            @RequestBody @Valid ProductVariantInput input) {
        return ResponseEntity.ok(productVariantService.update(id, input));
    }

    @DeleteMapping("/variants/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productVariantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
