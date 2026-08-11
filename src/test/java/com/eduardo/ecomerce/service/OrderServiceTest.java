package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.Address;
import com.eduardo.ecomerce.domain.address.AddressRepository;
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
import com.eduardo.ecomerce.dto.input.order.CreateOrderInput;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
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

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ShippingService shippingService;

    @InjectMocks
    private OrderService orderService;

    private Address address;
    private CreateOrderInput input;

    private Address buildAddress(UUID id) {
        Address a = new Address();
        a.setId(id);
        a.setCep("15046-806");
        a.setStreet("Rua Teste");
        a.setNumber("100");
        a.setNeighborhood("Centro");
        a.setCity("Rio Preto");
        a.setState("SP");
        return a;
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setName("Eduardo");
        user.setEmailVerified(true);

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

        address = buildAddress(addressId);
        input = new CreateOrderInput(addressId, "PAC");
        ShippingOutput shipping = new ShippingOutput("PAC", "PAC", new BigDecimal("15.00"), 7);

        Order savedOrder = new Order();
        savedOrder.setId(UUID.randomUUID());
        savedOrder.setUser(user);
        savedOrder.setTotal(new BigDecimal("74.80"));
        savedOrder.setStatus(OrderStatus.PENDING);
        savedOrder.setCreatedAt(LocalDateTime.now());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(shippingService.calculateByMethod(address.getCep(), input.shippingMethod())).thenReturn(shipping);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderOutput output = orderService.create(userId, input);

        assertThat(output.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(output.total()).isEqualByComparingTo(new BigDecimal("74.80"));
        verify(productVariantRepository).save(variant);
        verify(cartItemRepository).deleteByCartId(cartId);
    }

    @Test
    void shouldIncludeShippingPriceInOrderTotal() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmailVerified(true);

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(user);

        Product product = new Product();
        product.setName("Body Bebê");

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("U");
        variant.setPrice(new BigDecimal("40.00"));
        variant.setStock(5);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(1);

        Address a = buildAddress(addressId);
        CreateOrderInput orderInput = new CreateOrderInput(addressId, "SEDEX");
        ShippingOutput shipping = new ShippingOutput("SEDEX", "SEDEX", new BigDecimal("22.50"), 3);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(a));
        when(shippingService.calculateByMethod(a.getCep(), orderInput.shippingMethod())).thenReturn(shipping);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderOutput output = orderService.create(userId, orderInput);

        assertThat(output.total()).isEqualByComparingTo(new BigDecimal("62.50"));
        assertThat(output.shippingMethod()).isEqualTo("SEDEX");
        assertThat(output.shippingPrice()).isEqualByComparingTo(new BigDecimal("22.50"));
    }

    @Test
    void shouldDecrementStockOnOrderCreation() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmailVerified(true);

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

        Address a = buildAddress(addressId);
        CreateOrderInput orderInput = new CreateOrderInput(addressId, "PAC");
        ShippingOutput shipping = new ShippingOutput("PAC", "PAC", BigDecimal.ZERO, 7);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(a));
        when(shippingService.calculateByMethod(a.getCep(), orderInput.shippingMethod())).thenReturn(shipping);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(userId, orderInput);

        assertThat(variant.getStock()).isEqualTo(2);
    }

    @Test
    void shouldThrowWhenCartIsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        Cart cart = new Cart();
        cart.setId(cartId);

        CreateOrderInput orderInput = new CreateOrderInput(UUID.randomUUID(), "PAC");

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> orderService.create(userId, orderInput));
    }

    @Test
    void shouldThrowWhenStockInsufficient() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        Cart cart = new Cart();
        cart.setId(cartId);

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

        CreateOrderInput orderInput = new CreateOrderInput(UUID.randomUUID(), "PAC");

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));

        assertThrows(BusinessException.class, () -> orderService.create(userId, orderInput));
    }

    @Test
    void shouldThrowWhenEmailNotVerified() {
        UUID userId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmailVerified(false);

        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setUser(user);

        Product product = new Product();
        product.setName("Meia");

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("U");
        variant.setPrice(new BigDecimal("9.90"));
        variant.setStock(10);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setVariant(variant);
        cartItem.setQuantity(1);

        CreateOrderInput orderInput = new CreateOrderInput(UUID.randomUUID(), "PAC");

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(cartItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> orderService.create(userId, orderInput));

        verify(addressRepository, org.mockito.Mockito.never()).findByIdAndUserId(any(), any());
    }
}
