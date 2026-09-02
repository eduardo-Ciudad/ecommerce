package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderExpirationSchedulerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private PaymentService paymentService;

    @InjectMocks private OrderExpirationScheduler scheduler;

    @Test
    void expireAbandonedOrders_semCandidatos_naoProcessaNada(CapturedOutput output) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of());

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
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Order first = order(firstId);
        Order second = order(secondId);
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of(first, second));
        when(orderService.lockPendingOrderForExpiration(firstId)).thenThrow(new IllegalStateException("boom"));
        when(orderService.lockPendingOrderForExpiration(secondId)).thenReturn("payment-2");
        when(paymentService.tryCancelPendingPayment("payment-2"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);

        scheduler.expireAbandonedOrders();

        verify(orderService).lockPendingOrderForExpiration(firstId);
        verify(orderService).lockPendingOrderForExpiration(secondId);
        verify(paymentService).tryCancelPendingPayment("payment-2");
        verify(orderService).finalizeExpiredOrderCancellation(secondId, "payment-2");
        assertThat(output).contains("Falha ao processar").contains(firstId.toString());
    }

    @Test
    void processExpiredOrder_lockRetornaNull_interrompeFluxo() {
        UUID orderId = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(orderId)).thenReturn(null);

        scheduler.processExpiredOrder(orderId);

        verify(orderService).lockPendingOrderForExpiration(orderId);
        verifyNoInteractions(paymentService);
        verify(orderService, never()).finalizeExpiredOrderCancellation(any(), any());
    }

    @Test
    void processExpiredOrder_cancelamentoConfirmado_executaTresEtapasNaOrdem() {
        UUID orderId = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(orderId)).thenReturn("payment-1");
        when(paymentService.tryCancelPendingPayment("payment-1"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);

        scheduler.processExpiredOrder(orderId);

        InOrder inOrder = inOrder(orderService, paymentService);
        inOrder.verify(orderService).lockPendingOrderForExpiration(orderId);
        inOrder.verify(paymentService).tryCancelPendingPayment("payment-1");
        inOrder.verify(orderService).finalizeExpiredOrderCancellation(orderId, "payment-1");
    }

    @Test
    void processExpiredOrder_pagamentoNaoCancelavel_naoFinaliza() {
        assertDoesNotFinalize(PaymentService.PaymentCancellationOutcome.NOT_CANCELLABLE);
    }

    @Test
    void processExpiredOrder_falhaAoCancelarPagamento_naoFinaliza() {
        assertDoesNotFinalize(PaymentService.PaymentCancellationOutcome.FAILED);
    }

    @Test
    void processExpiredOrder_estadoLocalMudou_naoRepeteOperacoes() {
        UUID orderId = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(orderId)).thenReturn("payment-1");
        when(paymentService.tryCancelPendingPayment("payment-1"))
                .thenReturn(PaymentService.PaymentCancellationOutcome.CANCELLED);
        when(orderService.finalizeExpiredOrderCancellation(orderId, "payment-1")).thenReturn(false);

        scheduler.processExpiredOrder(orderId);

        verify(orderService).lockPendingOrderForExpiration(orderId);
        verify(paymentService).tryCancelPendingPayment("payment-1");
        verify(orderService).finalizeExpiredOrderCancellation(orderId, "payment-1");
        verifyNoMoreInteractions(orderService, paymentService);
    }

    private void assertDoesNotFinalize(PaymentService.PaymentCancellationOutcome outcome) {
        UUID orderId = UUID.randomUUID();
        when(orderService.lockPendingOrderForExpiration(orderId)).thenReturn("payment-1");
        when(paymentService.tryCancelPendingPayment("payment-1")).thenReturn(outcome);

        scheduler.processExpiredOrder(orderId);

        verify(orderService).lockPendingOrderForExpiration(orderId);
        verify(paymentService).tryCancelPendingPayment("payment-1");
        verify(orderService, never()).finalizeExpiredOrderCancellation(any(), any());
    }

    private Order order(UUID id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }
}
