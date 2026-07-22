package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.OrderRepository;

import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;

import com.mercadopago.resources.payment.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private PaymentService paymentService;

    private UUID orderId;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "notificationUrl", "http://teste.com/payments/webhook");

        orderId = UUID.randomUUID();
        order = new Order();
        order.setId(orderId);
        order.setTotal(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.PENDING);
    }

    // ---------- processPayment ----------

    @Test
    void processPayment_cartaoAprovado_atualizaPedidoParaPago() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        Payment mpPayment = mockPayment("987654321", "approved", "accredited", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(mpPayment);

        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.status()).isEqualTo("approved");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("987654321");
        assertThat(order.getPaymentStatus()).isEqualTo("approved");
    }

    @Test
    void processPayment_cartaoRejeitado_atualizaPedidoParaCancelado() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        Payment mpPayment = mockPayment("987654322", "rejected", "cc_rejected_insufficient_amount", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(mpPayment);

        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.status()).isEqualTo("rejected");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void processPayment_pix_retornaQrCodeNaResposta() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "pix", null, null, null);

        Payment mpPayment = mockPixPayment("987654323", "pending", orderId, "00020126...codigo-pix", "base64-qr-image");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(mpPayment);

        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.pixQrCode()).isEqualTo("00020126...codigo-pix");
        assertThat(result.pixQrCodeBase64()).isEqualTo("base64-qr-image");
        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void processPayment_pedidoNaoEncontrado_lancaExcecao() {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- processWebhook ----------

    @Test
    void processWebhook_pagamentoAprovadoComValorCorreto_atualizaPedidoParaPago() throws Exception {
        Payment mpPayment = mockPayment("111", "approved", "accredited", orderId);

        when(paymentClient.get(111L)).thenReturn(mpPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("111");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("111");
    }

    @Test
    void processWebhook_valorDivergente_lancaBusinessException() throws Exception {
        Payment mpPayment = mockPayment("222", "approved", "accredited", orderId);
        when(mpPayment.getTransactionAmount()).thenReturn(new BigDecimal("999.99")); // difere de order.getTotal()

        when(paymentClient.get(222L)).thenReturn(mpPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processWebhook("222"))
                .hasCauseInstanceOf(BusinessException.class);

        // pedido não deve ter sido alterado
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void processWebhook_pagamentoJaProcessadoComMesmoStatus_ignoraReprocessamento() throws Exception {
        order.setPaymentId("333");
        order.setPaymentStatus("approved");
        order.setStatus(OrderStatus.PAID);

        Payment mpPayment = mockPayment("333", "approved", "accredited", orderId);

        when(paymentClient.get(333L)).thenReturn(mpPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("333");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void processWebhook_pedidoNaoEncontrado_lancaExcecao() throws Exception {
        Payment mpPayment = mockPayment("444", "approved", "accredited", orderId);

        when(paymentClient.get(444L)).thenReturn(mpPayment);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processWebhook("444"))
                .isInstanceOf(RuntimeException.class);
    }

    // ---------- helpers ----------

    private Payment mockPayment(String id, String status, String statusDetail, UUID externalReference) {
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        lenient().when(payment.getId()).thenReturn(Long.parseLong(id));
        lenient().when(payment.getStatus()).thenReturn(status);
        lenient().when(payment.getStatusDetail()).thenReturn(statusDetail);
        lenient().when(payment.getExternalReference()).thenReturn(externalReference.toString());
        lenient().when(payment.getTransactionAmount()).thenReturn(new BigDecimal("150.00"));
        return payment;
    }

    private Payment mockPixPayment(String id, String status, UUID externalReference, String qrCode, String qrCodeBase64) {
        Payment payment = mockPayment(id, status, null, externalReference);

        PaymentTransactionData transactionData =
                org.mockito.Mockito.mock(PaymentTransactionData.class);
        when(transactionData.getQrCode()).thenReturn(qrCode);
        when(transactionData.getQrCodeBase64()).thenReturn(qrCodeBase64);

        PaymentPointOfInteraction pointOfInteraction =
                org.mockito.Mockito.mock(PaymentPointOfInteraction.class);
        when(pointOfInteraction.getTransactionData()).thenReturn(transactionData);

        when(payment.getPointOfInteraction()).thenReturn(pointOfInteraction);

        return payment;
    }
}