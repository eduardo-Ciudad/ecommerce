package com.eduardo.ecomerce.domain.cart;

import com.eduardo.ecomerce.domain.cartitem.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<CartItem> findByIdAndCartUserId(UUID id, UUID userId);
    Optional<Cart> findByUserId(UUID userId);
}
