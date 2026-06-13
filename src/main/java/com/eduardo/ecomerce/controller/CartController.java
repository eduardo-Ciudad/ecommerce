package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.cartitem.CartItemInput;
import com.eduardo.ecomerce.dto.output.cart.CartOutput;
import com.eduardo.ecomerce.infra.security.SecurityUtils;
import com.eduardo.ecomerce.service.CartService;
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
@RequestMapping("/cart")
@RequiredArgsConstructor
@Tag(name = "Carrinho", description = "Endpoints para gerenciamento do carrinho de compras do usuário autenticado")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Buscar carrinho", description = "Retorna o carrinho de compras do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Carrinho retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    public ResponseEntity<CartOutput> getCart() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(cartService.findByUserId(userId));
    }

    @PostMapping("/items")
    @Operation(summary = "Adicionar item ao carrinho", description = "Adiciona um item (variante de produto) ao carrinho do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item adicionado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "422", description = "Erro de regra de negócio, ex: estoque insuficiente")
    })
    public ResponseEntity<CartOutput> addItem(@RequestBody @Valid CartItemInput input) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(userId, input));
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "Atualizar quantidade do item", description = "Atualiza a quantidade de um item específico no carrinho")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item atualizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho")
    })
    public ResponseEntity<CartOutput> updateItem(@PathVariable UUID id, @RequestBody @Valid CartItemInput input) {
        return ResponseEntity.ok(cartService.updateItem(id, input.quantity()));
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Remover item do carrinho", description = "Remove um item específico do carrinho pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "404", description = "Item não encontrado no carrinho")
    })
    public ResponseEntity<Void> removeItem(@PathVariable UUID id) {
        cartService.removeItem(id);
        return ResponseEntity.noContent().build();
    }
}
