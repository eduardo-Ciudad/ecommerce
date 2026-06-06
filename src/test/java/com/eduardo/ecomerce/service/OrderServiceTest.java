package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.cart.Cart;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItem;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(user);

        Product product = new Product();
        product.setName("Vestido Rosa");

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSize("P");
        variant.setPrice(new BigDecimal("29.90"));
        variant.setStock(10);

        CartItem cartItem = new CartItem();
        cartItem.setId(UUID.randomUUID());
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(2);

        Order savedOrder = new Order();
        savedOrder.setId(UUID.randomUUID());
        savedOrder.setUser(user);
        savedOrder.setTotal(new BigDecimal("59.80"));
        savedOrder.setStatus(OrderStatus.PENDING);
        savedOrder.setCreatedAt(LocalDateTime.now());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderOutput output = orderService.create(userId);

        assertThat(output.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(output.total()).isEqualByComparingTo(new BigDecimal("59.80"));
        verify(productVariantRepository).save(variant);
        verify(cartItemRepository).deleteByCartId(cartId);
    }

    @Test
    void shouldDecrementStockOnOrderCreation() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(user);

        Product product = new Product();
        product.setName("Calça Jeans");

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("G");
        variant.setPrice(new BigDecimal("49.90"));
        variant.setStock(5);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(3);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(userId);

        assertThat(variant.getStock()).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenCartIsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        Cart cart = new Cart();
        cart.setId(cartId);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> orderService.create(userId));
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(user);

        Product product = new Product();
        product.setName("Blusa");

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");
        variant.setPrice(new BigDecimal("19.90"));
        variant.setStock(1);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(5);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));

        assertThrows(BusinessException.class, () -> orderService.create(userId));
    }
}
