package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.cart.Cart;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItem;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.dto.output.orderitem.OrderItemOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public OrderOutput create(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado"));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new BusinessException("Carrinho está vazio");
        }

        for (CartItem cartItem : cartItems) {
            if (cartItem.getVariant().getStock() < cartItem.getQuantity()) {
                throw new BusinessException("Estoque insuficiente para a variante: " + cartItem.getVariant().getId());
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cartItems) {
            ProductVariant variant = cartItem.getVariant();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariant(variant);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(variant.getPrice());
            order.getItems().add(orderItem);

            total = total.add(variant.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));

            variant.setStock(variant.getStock() - cartItem.getQuantity());
            productVariantRepository.save(variant);
        }

        order.setTotal(total);
        orderRepository.save(order);

        cartItemRepository.deleteByCartId(cart.getId());

        return toOutput(order);
    }

    @Transactional(readOnly = true)
    public List<OrderOutput> findByUserId(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        return orderRepository.findByUserId(userId).stream().map(this::toOutput).toList();
    }

    @Transactional(readOnly = true)
    public OrderOutput findByUserIdAndOrderId(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        return toOutput(order);
    }

    @Transactional
    public OrderOutput updateStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        order.setStatus(status);
        orderRepository.save(order);
        return toOutput(order);
    }

    private OrderOutput toOutput(Order order) {
        List<OrderItemOutput> items = order.getItems().stream()
                .map(item -> new OrderItemOutput(
                        item.getId(),
                        item.getVariant().getId(),
                        item.getVariant().getProduct().getName(),
                        item.getVariant().getSize(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();
        return new OrderOutput(
                order.getId(),
                order.getUser().getId(),
                order.getTotal(),
                order.getStatus(),
                items,
                order.getCreatedAt()
        );
    }
}
