package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.cart.Cart;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItem;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.cartitem.CartItemInput;
import com.eduardo.ecomerce.dto.output.cart.CartOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void shouldAddItemSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CartItemInput input = new CartItemInput(variantId, 2);

        Product product = new Product();
        product.setActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSize("M");
        variant.setPrice(new BigDecimal("29.90"));
        variant.setStock(10);

        User user = new User();
        user.setId(userId);

        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setUser(user);
        cart.setCreatedAt(LocalDateTime.now());

        CartItem savedItem = new CartItem();
        savedItem.setId(UUID.randomUUID());
        savedItem.setCart(cart);
        savedItem.setVariant(variant);
        savedItem.setQuantity(2);

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);
        when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(savedItem));

        CartOutput output = cartService.addItem(userId, input);

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenProductInactive() {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CartItemInput input = new CartItemInput(variantId, 1);

        Product product = new Product();
        product.setActive(false);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setStock(5);

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(BusinessException.class, () -> cartService.addItem(userId, input));
    }

    @Test
    void shouldThrowWhenNoStock() {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CartItemInput input = new CartItemInput(variantId, 1);

        Product product = new Product();
        product.setActive(true);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setStock(0);

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        assertThrows(BusinessException.class, () -> cartService.addItem(userId, input));
    }

    @Test
    void shouldThrowWhenQuantityIsZero() {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CartItemInput input = new CartItemInput(variantId, 0);

        assertThrows(BusinessException.class, () -> cartService.addItem(userId, input));
    }
}
