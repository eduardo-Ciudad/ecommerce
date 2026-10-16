package com.eduardo.ecomerce.domain.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private Order pendingOrder(String paymentId, String paymentStatus) {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    // ---------- hasActivePaymentAttempt ----------

    @Test
    void hasActivePaymentAttempt_semPaymentId_false() {
        assertThat(pendingOrder(null, null).hasActivePaymentAttempt()).isFalse();
    }

    @Test
    void hasActivePaymentAttempt_paymentIdComStatusNulo_trueSemLancarExcecao() {
        // regressão: Set.of(...).contains(null) lançava NullPointerException
        assertThat(pendingOrder("pay-1", null).hasActivePaymentAttempt()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "authorized", "approved"})
    void hasActivePaymentAttempt_statusNaoFinalOuAprovado_true(String status) {
        assertThat(pendingOrder("pay-1", status).hasActivePaymentAttempt()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void hasActivePaymentAttempt_statusDeFalha_false(String status) {
        assertThat(pendingOrder("pay-1", status).hasActivePaymentAttempt()).isFalse();
    }

    // ---------- canReceivePayment ----------

    @Test
    void canReceivePayment_pendingSemPagamentoDentroDaJanela_true() {
        assertThat(pendingOrder(null, null).canReceivePayment()).isTrue();
    }

    @Test
    void canReceivePayment_aposRecusaDentroDaJanela_true() {
        assertThat(pendingOrder("pay-1", "rejected").canReceivePayment()).isTrue();
    }

    @Test
    void canReceivePayment_comTentativaAtiva_false() {
        assertThat(pendingOrder("pay-1", "pending").canReceivePayment()).isFalse();
    }

    @Test
    void canReceivePayment_foraDaJanela_false() {
        Order order = pendingOrder(null, null);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(Order.PAYMENT_WINDOW_MINUTES + 1));
        assertThat(order.canReceivePayment()).isFalse();
    }

    @Test
    void canReceivePayment_pedidoNaoPending_false() {
        Order order = pendingOrder(null, null);
        order.setStatus(OrderStatus.CANCELLED);
        assertThat(order.canReceivePayment()).isFalse();
    }

    @Test
    void canReceivePayment_semCreatedAt_falseSemLancarExcecao() {
        Order order = pendingOrder(null, null);
        order.setCreatedAt(null);
        assertThat(order.canReceivePayment()).isFalse();
        assertThat(order.getPaymentDeadline()).isNull();
    }

    // ---------- getPaymentDeadline ----------

    @Test
    void getPaymentDeadline_createdAtMaisJanela() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 16, 12, 0);
        Order order = pendingOrder(null, null);
        order.setCreatedAt(created);
        assertThat(order.getPaymentDeadline()).isEqualTo(created.plusMinutes(Order.PAYMENT_WINDOW_MINUTES));
    }
}
