package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.AddressRepository;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderServiceExpirationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ShippingService shippingService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = spy(new OrderService(orderRepository, cartRepository, cartItemRepository, userRepository,
                productVariantRepository, addressRepository, shippingService, transactionTemplate));
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    void restoreStock_pedidoComMultiplosItens_devolveQuantidadeParaCadaVariante() {
        Order order = new Order();
        ProductVariant first = variant(5);
        ProductVariant second = variant(10);
        order.getItems().add(item(order, first, 2));
        order.getItems().add(item(order, second, 4));

        orderService.restoreStock(order);

        assertThat(first.getStock()).isEqualTo(7);
        assertThat(second.getStock()).isEqualTo(14);
        verify(productVariantRepository).save(first);
        verify(productVariantRepository).save(second);
    }

    @Test
    void restoreStock_pedidoSemItens_naoPersisteVariantes() {
        orderService.restoreStock(new Order());

        verifyNoInteractions(productVariantRepository);
    }

    @Test
    void updateStatus_transicaoParaCancelled_devolveEstoque() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.updateStatus(orderId, OrderStatus.CANCELLED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(orderService).restoreStock(order);
    }

    @Test
    void updateStatus_transicaoNaoCancelada_naoDevolveEstoque() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.updateStatus(orderId, OrderStatus.SHIPPED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(order);
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void updateStatus_usaBuscaComLockPessimista() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(completeOrder(orderId, OrderStatus.PAID)));
        orderService.updateStatus(orderId, OrderStatus.SHIPPED);

        verify(orderRepository).findByIdForUpdate(orderId);
        verify(orderRepository, never()).findById(orderId);
    }

    @Test
    void updateStatus_mesmoStatus_lancaBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.PENDING))
                .isInstanceOf(BusinessException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void updateStatus_transicaoNaoPermitida_lancaBusinessException() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.SHIPPED))
                .isInstanceOf(BusinessException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void updateStatus_pedidoInexistente_lancaResourceNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lockPendingOrderForExpiration_pendingSemPaymentId_cancelaEDevolveEstoque() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String paymentId = orderService.lockPendingOrderForExpiration(orderId);

        assertThat(paymentId).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(orderService).restoreStock(order);
    }

    @Test
    void lockPendingOrderForExpiration_pendingComPaymentId_retornaIdSemAlterarPedido() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        order.setPaymentId("payment-1");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        String paymentId = orderService.lockPendingOrderForExpiration(orderId);

        assertThat(paymentId).isEqualTo("payment-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void lockPendingOrderForExpiration_pedidoInexistente_retornaNull() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThat(orderService.lockPendingOrderForExpiration(orderId)).isNull();

        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void lockPendingOrderForExpiration_pedidoNaoPending_retornaNull() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThat(orderService.lockPendingOrderForExpiration(orderId)).isNull();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void finalizeExpiredOrderCancellation_estadoAindaCorresponde_cancelaEDevolveEstoque() {
        UUID orderId = UUID.randomUUID();
        Order order = completeOrder(orderId, OrderStatus.PENDING);
        order.setPaymentId("payment-1");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        boolean result = orderService.finalizeExpiredOrderCancellation(orderId, "payment-1");

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(orderService).restoreStock(order);
    }

    @Test
    void finalizeExpiredOrderCancellation_statusMudou_naoAlteraELogaAviso(CapturedOutput output) {
        Order order = completeOrder(UUID.randomUUID(), OrderStatus.PAID);
        order.setPaymentId("payment-1");
        assertFinalizationRejected(order, "payment-1");
        assertThat(output).contains("mudou de estado").contains("Requer investiga");
    }

    @Test
    void finalizeExpiredOrderCancellation_paymentIdMudou_naoAlteraELogaAviso(CapturedOutput output) {
        Order order = completeOrder(UUID.randomUUID(), OrderStatus.PENDING);
        order.setPaymentId("payment-2");
        assertFinalizationRejected(order, "payment-1");
        assertThat(output).contains("mudou de estado").contains("Requer investiga");
    }

    @Test
    void finalizeExpiredOrderCancellation_pedidoInexistente_naoAlteraELogaAviso(CapturedOutput output) {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThat(orderService.finalizeExpiredOrderCancellation(orderId, "payment-1")).isFalse();

        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
        assertThat(output).contains("mudou de estado").contains("Requer investiga");
    }

    private void assertFinalizationRejected(Order order, String expectedPaymentId) {
        int stockBefore = order.getItems().get(0).getVariant().getStock();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = orderService.finalizeExpiredOrderCancellation(order.getId(), expectedPaymentId);

        assertThat(result).isFalse();
        assertThat(order.getItems().get(0).getVariant().getStock()).isEqualTo(stockBefore);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    private Order completeOrder(UUID id, OrderStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setTotal(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        ProductVariant variant = variant(5);
        order.getItems().add(item(order, variant, 2));
        return order;
    }

    private ProductVariant variant(int stock) {
        Product product = new Product();
        product.setName("Produto");
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize("M");
        variant.setStock(stock);
        return variant;
    }

    private OrderItem item(Order order, ProductVariant variant, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setVariant(variant);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.TEN);
        return item;
    }
}
