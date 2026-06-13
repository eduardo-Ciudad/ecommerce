package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.productvariant.ProductVariantInput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;
import com.eduardo.ecomerce.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Variantes de Produto", description = "Endpoints para gerenciamento de variantes de produto (tamanho, preço e estoque)")
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @PostMapping("/products/{productId}/variants")
    @Operation(summary = "Adicionar variante", description = "Adiciona uma nova variante (tamanho, preço e estoque) a um produto existente. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Variante criada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para adicionar variante"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    })
    public ResponseEntity<ProductVariantOutput> create(
            @PathVariable UUID productId,
            @RequestBody @Valid ProductVariantInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productVariantService.create(productId, input));
    }

    @PutMapping("/variants/{id}")
    @Operation(summary = "Atualizar variante", description = "Atualiza os dados de uma variante de produto. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Variante atualizada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para atualizar variante"),
            @ApiResponse(responseCode = "404", description = "Variante não encontrada")
    })
    public ResponseEntity<ProductVariantOutput> update(
            @PathVariable UUID id,
            @RequestBody @Valid ProductVariantInput input) {
        return ResponseEntity.ok(productVariantService.update(id, input));
    }

    @DeleteMapping("/variants/{id}")
    @Operation(summary = "Remover variante", description = "Remove uma variante de produto pelo ID. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Variante removida com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover variante"),
            @ApiResponse(responseCode = "404", description = "Variante não encontrada")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productVariantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
