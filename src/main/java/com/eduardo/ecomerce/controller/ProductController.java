package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.product.ProductInput;
import com.eduardo.ecomerce.dto.output.product.ProductOutput;
import com.eduardo.ecomerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductOutput> create(@RequestBody @Valid ProductInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(input));
    }

    @GetMapping
    public ResponseEntity<List<ProductOutput>> findAll() {
        return ResponseEntity.ok(productService.findAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductOutput> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductOutput> update(@PathVariable UUID id, @RequestBody @Valid ProductInput input) {
        return ResponseEntity.ok(productService.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
