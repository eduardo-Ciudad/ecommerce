package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.AddressRepository;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.order.*;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.productvariant.*;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.*;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderServiceExpirationTest {
    @Mock OrderRepository orderRepository;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock UserRepository userRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock AddressRepository addressRepository;
    @Mock ShippingService shippingService;
    @Mock TransactionTemplate transactionTemplate;
    @Mock TransactionStatus transactionStatus;

    @Mock
    EmailService emailService;
    OrderService service;

    @Mock
    CouponService couponService;

    final java.time.Clock clock = java.time.Clock.systemUTC();


    @BeforeEach
    void setUp() {
        service = spy(new OrderService(orderRepository, cartRepository, cartItemRepository, userRepository,
                productVariantRepository, addressRepository, shippingService, transactionTemplate, emailService,
                couponService, mock(org.springframework.context.ApplicationEventPublisher.class),
                clock,
                new com.eduardo.ecomerce.infra.shipping.FreeShippingPolicy(new java.math.BigDecimal("199.00"), java.util.Set.of("PAC", "SEDEX"))));
        lenient().doAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0))
                .doInTransaction(transactionStatus)).when(transactionTemplate).execute(any());
    }

    @Test
    void lockPendingOrderForExpiration_pendingSemPaymentId_retornaSnapshotSemAlterarPedido() {
        Order order = order(OrderStatus.PENDING, null);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        Optional<OrderService.ExpiringOrderSnapshot> result = service.lockPendingOrderForExpiration(order.getId());

        assertThat(result).contains(new OrderService.ExpiringOrderSnapshot(
                order.getId(), null, order.getTotal(), PaymentProvider.MERCADO_PAGO));
        assertUnchanged(order, OrderStatus.PENDING, null);
    }

    @Test
    void lockPendingOrderForExpiration_pendingComPaymentId_retornaSnapshotSemAlterarPedido() {
        Order order = order(OrderStatus.PENDING, "payment-1");
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        Optional<OrderService.ExpiringOrderSnapshot> result = service.lockPendingOrderForExpiration(order.getId());

        assertThat(result).contains(new OrderService.ExpiringOrderSnapshot(
                order.getId(), "payment-1", order.getTotal(), PaymentProvider.MERCADO_PAGO));
        assertUnchanged(order, OrderStatus.PENDING, "payment-1");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rejected", "cancelled"})
    void lockPendingOrderForExpiration_tentativaFalha_retornaSnapshotSemPaymentId(String status) {
        Order order = order(OrderStatus.PENDING, "payment-1");
        order.setPaymentStatus(status);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        Optional<OrderService.ExpiringOrderSnapshot> result = service.lockPendingOrderForExpiration(order.getId());

        assertThat(result).contains(new OrderService.ExpiringOrderSnapshot(
                order.getId(), null, order.getTotal(), PaymentProvider.MERCADO_PAGO));
        assertUnchanged(order, OrderStatus.PENDING, "payment-1");
    }

    @Test
    void lockPendingOrderForExpiration_pedidoInexistente_retornaOptionalVazio() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThat(service.lockPendingOrderForExpiration(id)).isEmpty();
        verify(orderRepository, never()).save(any());
        verify(service, never()).restoreStock(any());
    }

    @Test
    void lockPendingOrderForExpiration_pedidoNaoPending_retornaOptionalVazio() {
        Order order = order(OrderStatus.PAID, null);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.lockPendingOrderForExpiration(order.getId())).isEmpty();
        assertUnchanged(order, OrderStatus.PAID, null);
    }

    @Test
    void cancelExpiredOrderWithoutPayment_pendingSemPaymentId_cancelaEDevolveEstoque() {
        Order order = order(OrderStatus.PENDING, null);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.cancelExpiredOrderWithoutPayment(order.getId());

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(variant(order).getStock()).isEqualTo(7);
        verify(orderRepository).save(order);
        verify(service).restoreStock(order);
        verify(productVariantRepository).save(variant(order));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"rejected", "cancelled"})
    void cancelExpiredOrderWithoutPayment_tentativaFalha_cancelaEDevolveEstoque(String status) {
        Order order = order(OrderStatus.PENDING, "payment-1");
        order.setPaymentStatus(status);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.cancelExpiredOrderWithoutPayment(order.getId());

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentId()).isEqualTo("payment-1");
        assertThat(variant(order).getStock()).isEqualTo(7);
        verify(orderRepository).save(order);
        verify(service).restoreStock(order);
    }

    @Test
    void cancelExpiredOrderWithoutPayment_tentativaAtiva_retornaFalse(CapturedOutput output) {
        Order order = order(OrderStatus.PENDING, "payment-1");
        order.setPaymentStatus("pending");
        assertCancelWithoutPaymentRejected(order);
        assertThat(output).contains("mudou de estado");
    }

    @Test
    void cancelExpiredOrderWithoutPayment_statusMudou_retornaFalseELogaAviso(CapturedOutput output) {
        assertCancelWithoutPaymentRejected(order(OrderStatus.PAID, null));
        assertThat(output).contains("mudou de estado").contains("pagamento");
    }

    @Test
    void cancelExpiredOrderWithoutPayment_paymentIdFoiPreenchido_retornaFalseELogaAviso(CapturedOutput output) {
        assertCancelWithoutPaymentRejected(order(OrderStatus.PENDING, "payment-1"));
        assertThat(output).contains("mudou de estado").contains("pagamento");
    }

    @Test
    void cancelExpiredOrderWithoutPayment_pedidoInexistente_retornaFalse(CapturedOutput output) {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThat(service.cancelExpiredOrderWithoutPayment(id)).isFalse();
        verify(orderRepository, never()).save(any());
        verify(service, never()).restoreStock(any());
        assertThat(output).contains("mudou de estado");
    }

    @Test
    void finalizeExpiredOrderCancellation_paymentIdNulo_adotaIdCancelaEDevolveEstoque() {
        assertSuccessfulFinalization(null);
    }

    @Test
    void finalizeExpiredOrderCancellation_paymentIdIgual_cancelaEDevolveEstoque() {
        assertSuccessfulFinalization("payment-1");
    }

    @Test
    void finalizeExpiredOrderCancellation_paymentIdDiferente_retornaFalseELogaAviso(CapturedOutput output) {
        assertFinalizationRejected(order(OrderStatus.PENDING, "payment-2"));
        assertThat(output).contains("mudou de estado").contains("Requer investiga");
    }

    @Test
    void finalizeExpiredOrderCancellation_statusMudou_retornaFalseELogaAviso(CapturedOutput output) {
        assertFinalizationRejected(order(OrderStatus.PAID, "payment-1"));
        assertThat(output).contains("mudou de estado").contains("Requer investiga");
    }

    @Test
    void finalizeExpiredOrderCancellation_pedidoInexistente_retornaFalse(CapturedOutput output) {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThat(service.finalizeExpiredOrderCancellation(id, "payment-1")).isFalse();
        verify(orderRepository, never()).save(any());
        verify(service, never()).restoreStock(any());
        assertThat(output).contains("mudou de estado");
    }

    private void assertSuccessfulFinalization(String currentId) {
        Order order = order(OrderStatus.PENDING, currentId);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.finalizeExpiredOrderCancellation(order.getId(), "payment-1")).isTrue();
        assertThat(order.getPaymentId()).isEqualTo("payment-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(variant(order).getStock()).isEqualTo(7);
        verify(orderRepository).save(order);
        verify(service).restoreStock(order);
    }

    private void assertCancelWithoutPaymentRejected(Order order) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.cancelExpiredOrderWithoutPayment(order.getId())).isFalse();
        assertThat(variant(order).getStock()).isEqualTo(5);
        verify(orderRepository, never()).save(any());
        verify(service, never()).restoreStock(any());
    }

    private void assertFinalizationRejected(Order order) {
        OrderStatus status = order.getStatus();
        String paymentId = order.getPaymentId();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.finalizeExpiredOrderCancellation(order.getId(), "payment-1")).isFalse();
        assertUnchanged(order, status, paymentId);
    }

    // ---------- InfinitePay ----------

    @Test
    void lockPendingOrderForExpiration_pedidoInfinitePay_retornaProvedor() {
        Order order = infinitePayOrder(LocalDateTime.now(clock).minusMinutes(45));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        Optional<OrderService.ExpiringOrderSnapshot> result = service.lockPendingOrderForExpiration(order.getId());

        assertThat(result).get().extracting(OrderService.ExpiringOrderSnapshot::provider)
                .isEqualTo(PaymentProvider.INFINITEPAY);
    }

    @Test
    void expireInfinitePayOrder_aposCarencia_cancelaEDevolveEstoque() {
        Order order = infinitePayOrder(LocalDateTime.now(clock).minusMinutes(
                Order.PAYMENT_WINDOW_MINUTES + OrderService.INFINITEPAY_CONFIRMATION_GRACE_MINUTES + 1));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.expireInfinitePayOrder(order.getId());

        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo("cancelled");
        assertThat(order.hasActivePaymentAttempt()).isFalse();
        assertThat(variant(order).getStock()).isEqualTo(7);
        verify(orderRepository).save(order);
        verify(service).restoreStock(order);
    }

    @Test
    void expireInfinitePayOrder_dentroDaCarencia_naoAltera() {
        Order order = infinitePayOrder(LocalDateTime.now(clock).minusMinutes(Order.PAYMENT_WINDOW_MINUTES + 2));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.expireInfinitePayOrder(order.getId());

        assertThat(result).isFalse();
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
        assertUnchanged(order, OrderStatus.PENDING, null);
    }

    @Test
    void expireInfinitePayOrder_pedidoJaPago_naoAltera() {
        Order order = infinitePayOrder(LocalDateTime.now(clock).minusMinutes(60));
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.expireInfinitePayOrder(order.getId());

        assertThat(result).isFalse();
        assertUnchanged(order, OrderStatus.PAID, null);
    }

    @Test
    void expireInfinitePayOrder_pedidoMercadoPago_naoAltera() {
        Order order = order(OrderStatus.PENDING, null);
        order.setCreatedAt(LocalDateTime.now(clock).minusMinutes(60));
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        boolean result = service.expireInfinitePayOrder(order.getId());

        assertThat(result).isFalse();
        assertUnchanged(order, OrderStatus.PENDING, null);
    }

    private Order infinitePayOrder(LocalDateTime createdAt) {
        Order order = order(OrderStatus.PENDING, null);
        order.setPaymentProvider(PaymentProvider.INFINITEPAY);
        order.setCheckoutUrl("https://checkout.infinitepay.io/vanda/abc");
        order.setPaymentStatus("pending");
        order.setCreatedAt(createdAt);
        return order;
    }

    private void assertUnchanged(Order order, OrderStatus status, String paymentId) {
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        assertThat(variant(order).getStock()).isEqualTo(5);
        verify(orderRepository, never()).save(any());
        verify(service, never()).restoreStock(any());
    }

    private Order order(OrderStatus status, String paymentId) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotal(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setPaymentId(paymentId);
        ProductVariant variant = new ProductVariant();
        variant.setStock(5);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setVariant(variant);
        item.setQuantity(2);
        order.getItems().add(item);
        return order;
    }

    private ProductVariant variant(Order order) {
        return order.getItems().get(0).getVariant();
    }
}
