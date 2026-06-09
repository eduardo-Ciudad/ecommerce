package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.cartitem.CartItemInput;
import com.eduardo.ecomerce.dto.output.cart.CartOutput;
import com.eduardo.ecomerce.infra.security.SecurityUtils;
import com.eduardo.ecomerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartOutput> getCart() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(cartService.findByUserId(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartOutput> addItem(@RequestBody @Valid CartItemInput input) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(userId, input));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<CartOutput> updateItem(@PathVariable UUID id, @RequestBody @Valid CartItemInput input) {
        return ResponseEntity.ok(cartService.updateItem(id, input.quantity()));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id) {
        cartService.removeItem(id);
        return ResponseEntity.noContent().build();
    }
}
