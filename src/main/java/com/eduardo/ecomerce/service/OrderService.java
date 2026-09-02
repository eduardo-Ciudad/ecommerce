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
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.order.CreateOrderInput;
import com.eduardo.ecomerce.dto.output.common.PageResponse;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.dto.output.orderitem.OrderItemOutput;
import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AddressRepository addressRepository;
    private final ShippingService shippingService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public OrderOutput create(UUID userId, CreateOrderInput input) {
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

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException("Verifique seu email antes de finalizar a compra");
        }

        Address address = addressRepository.findByIdAndUserId(input.addressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado"));

        ShippingOutput shipping = shippingService.calculateByMethod(address.getCep(), input.shippingMethod());

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        order.setShippingMethod(shipping.method());
        order.setShippingPrice(shipping.price());
        order.setShippingDeadlineDays(shipping.deadlineDays());

        order.setRecipientName(user.getName());
        order.setRecipientCep(address.getCep());
        order.setRecipientStreet(address.getStreet());
        order.setRecipientNumber(address.getNumber());
        order.setRecipientComplement(address.getComplement());
        order.setRecipientNeighborhood(address.getNeighborhood());
        order.setRecipientCity(address.getCity());
        order.setRecipientState(address.getState());

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

        total = total.add(shipping.price());

        order.setTotal(total);
        orderRepository.save(order);
        log.info("Pedido criado — usuário: {}, orderId: {}", userId, order.getId());

        cartItemRepository.deleteByCartId(cart.getId());

        return toOutput(order);
    }




    public PageResponse<OrderOutput> findByUserId(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        return PageResponse.from(orderRepository.findByUserId(userId, pageable).map(this::toOutput));
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

        OrderStatus current = order.getStatus();
        if (current == status) {
            throw new BusinessException("O pedido já esta com o status" + current + "para" + status);
        }
        if (!current.canTransition(status)) {
            throw new BusinessException( "Transição inválida: não é possível mudar de " + current + " para " + status);
        }

        order.setStatus(status);
        orderRepository.save(order);
        log.info("Status do pedido atualizado — orderId: {}, status: {}", id, status);
        return toOutput(order);
    }

    public String lockPendingOrderForExpiration(UUID orderId) {
        return transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

            if (order == null || order.getStatus() != OrderStatus.PENDING) {
                return null;
            }

            if (order.getPaymentId() == null) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
                restoreStock(order);
                log.info("Pedido {} expirado sem pagamento associado — cancelado e estoque devolvido", orderId);
                return null;
            }

            return order.getPaymentId();
        });
    }

    public boolean finalizeExpiredOrderCancellation(UUID orderId, String expectedPaymentId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);

            boolean stillMatches = order != null
                    && order.getStatus() == OrderStatus.PENDING
                    && expectedPaymentId.equals(order.getPaymentId());

            if (!stillMatches) {
                log.warn("Pedido {} mudou de estado durante a expiração — payment {} foi cancelado no Mercado " +
                        "Pago, mas o pedido não foi alterado localmente. Requer investigação.", orderId, expectedPaymentId);
                return false;
            }

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            restoreStock(order);
            log.info("Pedido {} cancelado por expiração — payment {} confirmado cancelado no Mercado Pago",
                    orderId, expectedPaymentId);
            return true;
        }));
    }

    public void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = item.getVariant();
            variant.setStock(variant.getStock() + item.getQuantity());
            productVariantRepository.save(variant);
        }
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
                order.getPaymentStatus(),
                order.getCheckoutUrl(),
                order.getShippingMethod(),
                order.getShippingPrice(),
                order.getShippingDeadlineDays(),
                order.getRecipientName(),
                order.getRecipientCep(),
                order.getRecipientStreet(),
                order.getRecipientNumber(),
                order.getRecipientComplement(),
                order.getRecipientNeighborhood(),
                order.getRecipientCity(),
                order.getRecipientState(),
                items,
                order.getCreatedAt()
        );
    }
}
