package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.user.User;

import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;

import com.mercadopago.resources.payment.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private OrderService orderService;

    private PaymentService paymentService;

    private UUID orderId;
    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        paymentService = spy(new PaymentService(orderRepository, orderService, paymentClient));
        ReflectionTestUtils.setField(paymentService, "notificationUrl", "http://teste.com/payments/webhook");

        orderId = UUID.randomUUID();

        user = new User();
        user.setEmail("cliente@teste.com");

        order = new Order();
        order.setId(orderId);
        order.setTotal(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);

        lenient().doReturn(PaymentService.OrphanedPaymentLookup.notFound())
                .when(paymentService).findExistingPaymentForOrder(any(), any());
    }

    // ---------- processPayment ----------

    @Test
    void processPayment_cartaoAprovado_atualizaPedidoParaPago() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        Payment mpPayment = mockPayment("987654321", "approved", "accredited", orderId);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
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

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(mpPayment);

        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.status()).isEqualTo("rejected");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).restoreStock(order);
    }

    @Test
    void processPayment_pix_retornaQrCodeNaResposta() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "pix", null, null, null);

        Payment mpPayment = mockPixPayment("987654323", "pending", orderId, "00020126...codigo-pix", "base64-qr-image");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(mpPayment);

        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.pixQrCode()).isEqualTo("00020126...codigo-pix");
        assertThat(result.pixQrCodeBase64()).isEqualTo("base64-qr-image");
        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void processPayment_pedidoNaoEncontrado_lancaExcecao() {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido não pertence ao usuário autenticado")
    void processPayment_pedidoNaoPertenceAoUsuario_lancaBusinessException() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "outro@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido não pertence ao usuário autenticado");

        verify(paymentClient, never()).create(any());
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido já possui pagamento em processamento")
    void processPayment_pedidoJaPossuiPagamento_lancaBusinessException() throws Exception {
        order.setPaymentId("existing-payment-id");
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido já possui pagamento em processamento");

        verify(paymentClient, never()).create(any());
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido não está pendente")
    void processPayment_pedidoNaoPendente_lancaBusinessException() throws Exception {
        order.setStatus(OrderStatus.PAID);
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido não está pendente de pagamento");

        verify(paymentClient, never()).create(any());
    }

    @Test
    void findExistingPaymentForOrder_semResultados_retornaNotFound() throws Exception {
        MPResultsResourcesPage<Payment> page = mockResultsPage(List.of());
        when(paymentClient.search(any(MPSearchRequest.class))).thenReturn(page);
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, new BigDecimal("150.00"));

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.NOT_FOUND);
        assertThat(result.payment()).isNull();
        verify(paymentClient).search(argThat(request ->
                orderId.toString().equals(request.getFilters().get("external_reference"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "rejected", "pending", "in_process", "authorized"})
    void findExistingPaymentForOrder_resultadoValido_retornaFound(String status) throws Exception {
        Payment payment = mockPayment("801", status, "detail", orderId);
        stubSearchResults(List.of(payment));
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, new BigDecimal("150.0"));

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.FOUND);
        assertThat(result.payment()).isSameAs(payment);
    }

    @Test
    void findExistingPaymentForOrder_externalReferenceDiferente_retornaNotFound() throws Exception {
        Payment payment = mockPayment("802", "approved", "detail", UUID.randomUUID());
        assertLookupNotFoundFor(List.of(payment));
    }

    @Test
    void findExistingPaymentForOrder_valorDiferente_retornaNotFound() throws Exception {
        Payment payment = mockPayment("803", "approved", "detail", orderId);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("149.99"));
        assertLookupNotFoundFor(List.of(payment));
    }

    @Test
    void findExistingPaymentForOrder_valorNulo_retornaNotFound() throws Exception {
        Payment payment = mockPayment("804", "approved", "detail", orderId);
        when(payment.getTransactionAmount()).thenReturn(null);
        assertLookupNotFoundFor(List.of(payment));
    }

    @Test
    void findExistingPaymentForOrder_statusDesconhecido_retornaNotFound() throws Exception {
        Payment payment = mockPayment("805", "refunded", "detail", orderId);
        assertLookupNotFoundFor(List.of(payment));
    }

    @Test
    void findExistingPaymentForOrder_resultadosMistos_retornaUnicoValido() throws Exception {
        Payment wrongReference = mockPayment("806", "approved", "detail", UUID.randomUUID());
        Payment wrongAmount = mockPayment("807", "approved", "detail", orderId);
        when(wrongAmount.getTransactionAmount()).thenReturn(new BigDecimal("1.00"));
        Payment wrongStatus = mockPayment("808", "refunded", "detail", orderId);
        Payment valid = mockPayment("809", "pending", "detail", orderId);
        stubSearchResults(List.of(wrongReference, wrongAmount, wrongStatus, valid));
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, order.getTotal());

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.FOUND);
        assertThat(result.payment()).isSameAs(valid);
    }

    @Test
    void findExistingPaymentForOrder_maisDeUmResultadoValido_retornaAmbiguousELogaErro(CapturedOutput output)
            throws Exception {
        Payment first = mockPayment("810", "approved", "detail", orderId);
        Payment second = mockPayment("811", "pending", "detail", orderId);
        stubSearchResults(List.of(first, second));
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, order.getTotal());

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.AMBIGUOUS);
        assertThat(result.payment()).isNull();
        assertThat(output).contains("Mais de um pagamento v").contains(orderId.toString());
    }

    @Test
    void findExistingPaymentForOrder_buscaLancaExcecao_retornaFailedELogaErro(CapturedOutput output)
            throws Exception {
        when(paymentClient.search(any())).thenThrow(new RuntimeException("search failed"));
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, order.getTotal());

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.FAILED);
        assertThat(result.payment()).isNull();
        assertThat(output).contains("Falha ao buscar pagamento existente").contains(orderId.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "rejected"})
    void processPayment_pagamentoOrfao_reaproveitaSemCriar(String status) throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");
        Payment payment = mockPayment("820", status, "detail", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        doReturn(PaymentService.OrphanedPaymentLookup.found(payment))
                .when(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());

        PaymentOutput result = paymentService.processPayment(input, user.getEmail());

        assertThat(result.id()).isEqualTo("820");
        assertThat(order.getPaymentId()).isEqualTo("820");
        assertThat(order.getPaymentStatus()).isEqualTo(status);
        assertThat(order.getStatus()).isEqualTo(
                "approved".equals(status) ? OrderStatus.PAID : OrderStatus.CANCELLED);
        verify(paymentClient, never()).create(any());
        verify(orderRepository).save(order);
        if ("rejected".equals(status)) {
            verify(orderService).restoreStock(order);
        } else {
            verify(orderService, never()).restoreStock(any());
        }
    }

    @Test
    void processPayment_lookupNotFound_criaNovoPagamento() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");
        Payment payment = mockPayment("821", "approved", "detail", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any())).thenReturn(payment);

        paymentService.processPayment(input, user.getEmail());

        verify(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());
        verify(paymentClient).create(argThat(request ->
                orderId.toString().equals(request.getExternalReference())
                        && order.getTotal().compareTo(request.getTransactionAmount()) == 0));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AMBIGUOUS", "FAILED"})
    void processPayment_lookupInconclusivo_lancaSemCriarNemAlterarPedido(String outcome) throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        PaymentService.OrphanedPaymentLookup lookup = "AMBIGUOUS".equals(outcome)
                ? PaymentService.OrphanedPaymentLookup.ambiguous()
                : PaymentService.OrphanedPaymentLookup.failed();
        doReturn(lookup).when(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());

        assertThatThrownBy(() -> paymentService.processPayment(input, user.getEmail()))
                .isInstanceOf(BusinessException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isNull();
        assertThat(order.getPaymentStatus()).isNull();
        verify(paymentClient, never()).create(any());
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    // ---------- processWebhook ----------

    @Test
    void processWebhook_pagamentoAprovadoComValorCorreto_atualizaPedidoParaPago() throws Exception {
        Payment mpPayment = mockPayment("111", "approved", "accredited", orderId);

        when(paymentClient.get(111L)).thenReturn(mpPayment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("111");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("111");
    }

    @Test
    @DisplayName("Webhook com valor divergente deve lançar exceção")
    void processWebhook_valorDivergente_lancaBusinessException() throws Exception {
        Payment mpPayment = mockPayment("222", "approved", "accredited", orderId);
        when(mpPayment.getTransactionAmount()).thenReturn(new BigDecimal("999.99"));

        when(paymentClient.get(222L)).thenReturn(mpPayment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processWebhook("222"))
                .isInstanceOf(BusinessException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Webhook idempotente: mesmo paymentId + status já processado deve ser ignorado")
    void processWebhook_pagamentoJaProcessadoComMesmoStatus_ignoraReprocessamento() throws Exception {
        order.setPaymentId("333");
        order.setPaymentStatus("approved");
        order.setStatus(OrderStatus.PAID);

        Payment mpPayment = mockPayment("333", "approved", "accredited", orderId);

        when(paymentClient.get(333L)).thenReturn(mpPayment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("333");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void processWebhook_pedidoNaoEncontrado_lancaExcecao() throws Exception {
        Payment mpPayment = mockPayment("444", "approved", "accredited", orderId);

        when(paymentClient.get(444L)).thenReturn(mpPayment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processWebhook("444"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void processPayment_pagamentoAprovado_naoDevolveEstoque() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        Payment payment = mockPayment("501", "approved", "accredited", orderId);
        when(paymentClient.create(any())).thenReturn(payment);

        paymentService.processPayment(input, "cliente@teste.com");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).findByIdForUpdate(orderId);
        verify(orderRepository, never()).findById(orderId);
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void processWebhook_paymentIdEStatusJaRegistrados_ignora() throws Exception {
        order.setPaymentId("601");
        order.setPaymentStatus("approved");
        order.setStatus(OrderStatus.PAID);
        stubGetPayment("601", "approved", "accredited");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("601");

        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "rejected"})
    void processWebhook_paymentIdDiferente_ignoraELogaAviso(String status, CapturedOutput output) throws Exception {
        order.setPaymentId("original");
        order.setPaymentStatus("pending");
        stubGetPayment("602", status, "detail");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("602");

        assertThat(order.getPaymentId()).isEqualTo("original");
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
        assertThat(output).contains("Webhook ignorado").contains("original").contains("602");
    }

    @Test
    void processWebhook_pedidoForaDePending_ignoraELogaAviso(CapturedOutput output) throws Exception {
        order.setPaymentId("603");
        order.setPaymentStatus("approved");
        order.setStatus(OrderStatus.PAID);
        stubGetPayment("603", "rejected", "rejected");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("603");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo("approved");
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
        assertThat(output).contains("fora de PENDING").contains("PAID");
    }

    @Test
    void processWebhook_rejectedEmPedidoPending_cancelaEDevolveEstoque() throws Exception {
        stubGetPayment("604", "rejected", "rejected");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("604");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentId()).isEqualTo("604");
        assertThat(order.getPaymentStatus()).isEqualTo("rejected");
        verify(orderService).restoreStock(order);
        verify(orderRepository).save(order);
    }

    @Test
    void processWebhook_approvedComValorDivergente_lancaSemAlterarPedido() throws Exception {
        Payment payment = mockPayment("605", "approved", "accredited", orderId);
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("149.99"));
        when(paymentClient.get(605L)).thenReturn(payment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processWebhook("605"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Erro ao processar");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isNull();
        assertThat(order.getPaymentStatus()).isNull();
        verify(orderRepository, never()).save(any());
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void processWebhook_paymentIdNulo_associaPrimeiroWebhookValido() throws Exception {
        stubGetPayment("606", "pending", "pending");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("606");

        assertThat(order.getPaymentId()).isEqualTo("606");
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(order);
        verify(orderService, never()).restoreStock(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "authorized"})
    void tryCancelPendingPayment_statusCancelavel_cancela(String status) throws Exception {
        stubGetPayment("701", status, "detail");

        PaymentService.PaymentCancellationOutcome result = paymentService.tryCancelPendingPayment("701");

        assertThat(result).isEqualTo(PaymentService.PaymentCancellationOutcome.CANCELLED);
        verify(paymentClient).cancel(701L);
    }

    @Test
    void tryCancelPendingPayment_statusNaoCancelavel_naoChamaCancel() throws Exception {
        stubGetPayment("702", "approved", "accredited");

        PaymentService.PaymentCancellationOutcome result = paymentService.tryCancelPendingPayment("702");

        assertThat(result).isEqualTo(PaymentService.PaymentCancellationOutcome.NOT_CANCELLABLE);
        verify(paymentClient, never()).cancel(anyLong());
    }

    @Test
    void tryCancelPendingPayment_falhaNoGet_retornaFailedELogaErro(CapturedOutput output) throws Exception {
        when(paymentClient.get(703L)).thenThrow(new RuntimeException("get failed"));

        PaymentService.PaymentCancellationOutcome result = paymentService.tryCancelPendingPayment("703");

        assertThat(result).isEqualTo(PaymentService.PaymentCancellationOutcome.FAILED);
        verify(paymentClient, never()).cancel(anyLong());
        assertThat(output).contains("Falha ao tentar cancelar payment 703");
    }

    @Test
    void tryCancelPendingPayment_falhaNoCancel_retornaFailedELogaErro(CapturedOutput output) throws Exception {
        stubGetPayment("704", "pending", "pending");
        when(paymentClient.cancel(704L)).thenThrow(new RuntimeException("cancel failed"));

        PaymentService.PaymentCancellationOutcome result = paymentService.tryCancelPendingPayment("704");

        assertThat(result).isEqualTo(PaymentService.PaymentCancellationOutcome.FAILED);
        verify(paymentClient).cancel(704L);
        assertThat(output).contains("Falha ao tentar cancelar payment 704");
    }

    // ---------- helpers ----------

    private Payment stubGetPayment(String id, String status, String statusDetail) throws Exception {
        Payment payment = mockPayment(id, status, statusDetail, orderId);
        when(paymentClient.get(Long.parseLong(id))).thenReturn(payment);
        return payment;
    }

    @SuppressWarnings("unchecked")
    private MPResultsResourcesPage<Payment> mockResultsPage(List<Payment> payments) {
        MPResultsResourcesPage<Payment> page = mock(MPResultsResourcesPage.class);
        when(page.getResults()).thenReturn(payments);
        return page;
    }

    private void stubSearchResults(List<Payment> payments) throws Exception {
        MPResultsResourcesPage<Payment> page = mockResultsPage(payments);
        when(paymentClient.search(any())).thenReturn(page);
    }

    private void assertLookupNotFoundFor(List<Payment> payments) throws Exception {
        stubSearchResults(payments);
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, order.getTotal());

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.NOT_FOUND);
        assertThat(result.payment()).isNull();
    }

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
