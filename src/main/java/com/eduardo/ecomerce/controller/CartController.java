package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.cartitem.CartItemInput;
import com.eduardo.ecomerce.dto.output.cart.CartOutput;
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

    @GetMapping("/{userId}")
    public ResponseEntity<CartOutput> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(cartService.findByUserId(userId));
    }

    @PostMapping("/{userId}/items")
    public ResponseEntity<CartOutput> addItem(
            @PathVariable UUID userId,
            @RequestBody @Valid CartItemInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(userId, input));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<CartOutput> updateItem(@PathVariable UUID id, @RequestBody Integer quantity) {
        return ResponseEntity.ok(cartService.updateItem(id, quantity));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID id) {
        cartService.removeItem(id);
        return ResponseEntity.noContent().build();
    }
}
