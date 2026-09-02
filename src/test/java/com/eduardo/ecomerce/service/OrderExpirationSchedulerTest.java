package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.*;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderExpirationSchedulerTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderService orderService;
    @Mock PaymentService paymentService;
    @InjectMocks OrderExpirationScheduler scheduler;

    @Test
    void expireAbandonedOrders_semCandidatos_naoProcessaNada(CapturedOutput output) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of());

        scheduler.expireAbandonedOrders();

        LocalDateTime after = LocalDateTime.now().minusMinutes(30);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
        verifyNoInteractions(orderService, paymentService);
        assertThat(output).doesNotContain("Falha ao processar");
    }

    @Test
    void expireAbandonedOrders_falhaEmUmPedido_continuaComOsDemais(CapturedOutput output) {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of(order(first), order(second)));
        when(orderService.lockPendingOrderForExpiration(first)).thenThrow(new IllegalStateException("boom"));
        when(orderService.lockPendingOrderForExpiration(second)).thenReturn(snapshot(second, "payment-2"));
        when(paymentService.tryCancelPendingPayment("payment-2"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);

        scheduler.expireAbandonedOrders();

        verify(orderService).lockPendingOrderForExpiration(first);
        verify(orderService).lockPendingOrderForExpiration(second);
        verify(orderService).finalizeExpiredOrderCancellation(second, "payment-2");
        assertThat(output).contains("Falha ao processar").contains(first.toString());
    }

    @Test
    void processExpiredOrder_lockRetornaOptionalVazio_interrompeFluxo() {
        UUID id = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(Optional.empty());
        scheduler.processExpiredOrder(id);
        verify(orderService).lockPendingOrderForExpiration(id);
        verifyNoInteractions(paymentService);
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void processExpiredOrder_snapshotComPaymentId_cancelaSemFazerLookup() {
        UUID id = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(snapshot(id, "payment-1"));
        when(paymentService.tryCancelPendingPayment("payment-1"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);

        scheduler.processExpiredOrder(id);

        InOrder order = inOrder(orderService, paymentService);
        order.verify(orderService).lockPendingOrderForExpiration(id);
        order.verify(paymentService).tryCancelPendingPayment("payment-1");
        order.verify(orderService).finalizeExpiredOrderCancellation(id, "payment-1");
        verify(paymentService, never()).findExistingPaymentForOrder(any(), any());
        verify(orderService, never()).cancelExpiredOrderWithoutPayment(any());
    }

    @Test
    void processExpiredOrder_semPaymentIdLookupNotFound_cancelaSomenteLocalmente() {
        UUID id = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(snapshot(id, null));
        when(paymentService.findExistingPaymentForOrder(id, new BigDecimal("100.00")))
                .thenReturn(PaymentService.OrphanedPaymentLookup.notFound());

        scheduler.processExpiredOrder(id);

        InOrder order = inOrder(orderService, paymentService);
        order.verify(orderService).lockPendingOrderForExpiration(id);
        order.verify(paymentService).findExistingPaymentForOrder(id, new BigDecimal("100.00"));
        order.verify(orderService).cancelExpiredOrderWithoutPayment(id);
        verify(paymentService, never()).tryCancelPendingPayment(any());
        verify(orderService, never()).finalizeExpiredOrderCancellation(any(), any());
    }

    @Test
    void processExpiredOrder_semPaymentIdLookupFound_adotaIdECancelaNaOrdem() {
        UUID id = UUID.randomUUID();
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(9001L);
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(snapshot(id, null));
        when(paymentService.findExistingPaymentForOrder(id, new BigDecimal("100.00")))
                .thenReturn(PaymentService.OrphanedPaymentLookup.found(payment));
        when(paymentService.tryCancelPendingPayment("9001"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);

        scheduler.processExpiredOrder(id);

        InOrder order = inOrder(orderService, paymentService);
        order.verify(orderService).lockPendingOrderForExpiration(id);
        order.verify(paymentService).findExistingPaymentForOrder(id, new BigDecimal("100.00"));
        order.verify(paymentService).tryCancelPendingPayment("9001");
        order.verify(orderService).finalizeExpiredOrderCancellation(id, "9001");
        verify(orderService, never()).cancelExpiredOrderWithoutPayment(any());
    }

    @Test
    void processExpiredOrder_semPaymentIdLookupAmbiguous_interrompeFluxo() {
        assertLookupStops(PaymentService.OrphanedPaymentLookup.ambiguous());
    }

    @Test
    void processExpiredOrder_semPaymentIdLookupFailed_interrompeFluxo() {
        assertLookupStops(PaymentService.OrphanedPaymentLookup.failed());
    }

    @Test
    void processExpiredOrder_pagamentoNaoCancelavel_naoFinaliza() {
        assertCancellationDoesNotFinalize(PaymentService.PaymentCancellationOutcome.NOT_CANCELLABLE);
    }

    @Test
    void processExpiredOrder_falhaAoCancelarPagamento_naoFinaliza() {
        assertCancellationDoesNotFinalize(PaymentService.PaymentCancellationOutcome.FAILED);
    }

    private void assertLookupStops(PaymentService.OrphanedPaymentLookup lookup) {
        UUID id = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(snapshot(id, null));
        when(paymentService.findExistingPaymentForOrder(id, new BigDecimal("100.00"))).thenReturn(lookup);
        scheduler.processExpiredOrder(id);
        verify(orderService, never()).cancelExpiredOrderWithoutPayment(any());
        verify(paymentService, never()).tryCancelPendingPayment(any());
        verify(orderService, never()).finalizeExpiredOrderCancellation(any(), any());
    }

    private void assertCancellationDoesNotFinalize(PaymentService.PaymentCancellationOutcome outcome) {
        UUID id = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(id)).thenReturn(snapshot(id, "payment-1"));
        when(paymentService.tryCancelPendingPayment("payment-1")).thenReturn(outcome);
        scheduler.processExpiredOrder(id);
        verify(orderService, never()).finalizeExpiredOrderCancellation(any(), any());
    }

    private Optional<OrderService.ExpiringOrderSnapshot> snapshot(UUID id, String paymentId) {
        return Optional.of(new OrderService.ExpiringOrderSnapshot(id, paymentId, new BigDecimal("100.00")));
    }

    private Order order(UUID id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }
}
