package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.BlingSyncStatus;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.user.User;

import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.core.MPRequestOptions;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 2026-10-19 15:30 em São Paulo
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T18:30:00Z"), ZONE);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 10, 19, 15, 30);

    private PaymentService paymentService;

    private UUID orderId;
    private Order order;
    private User user;

    @BeforeEach
    void setUp() {
        paymentService = spy(new PaymentService(orderRepository, orderService, paymentClient, eventPublisher, clock));
        ReflectionTestUtils.setField(paymentService, "notificationUrl", "http://teste.com/payments/webhook");

        orderId = UUID.randomUUID();

        user = new User();
        user.setEmail("cliente@teste.com");

        order = new Order();
        order.setId(orderId);
        order.setTotal(new BigDecimal("150.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());

        lenient().doReturn(PaymentService.OrphanedPaymentLookup.notFound())
                .when(paymentService).findExistingPaymentForOrder(any(), any());
    }

    // ---------- processPayment ----------

    @Test
    void processPayment_cartaoAprovado_atualizaPedidoParaPago() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        Payment payment = mockPayment("987654321", "approved", "accredited", orderId);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);
        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.status()).isEqualTo("approved");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo("987654321");
        assertThat(order.getPaymentStatus()).isEqualTo("approved");
    }

    @Test
    void processPayment_cartaoRejeitado_mantemPedidoPendingSemDevolverEstoque() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        Payment payment = mockPayment("987654322", "rejected", "cc_rejected_insufficient_amount", orderId);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);
        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.status()).isEqualTo("rejected");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isEqualTo("987654322");
        assertThat(order.getPaymentStatus()).isEqualTo("rejected");
        assertThat(order.hasActivePaymentAttempt()).isFalse();
        assertThat(order.canReceivePayment()).isTrue();
        verify(orderRepository).save(order);
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void processPayment_pix_retornaQrCodeNaResposta() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "pix", null, null, null, null, null, null, null);

        Payment payment = mockPixPayment("987654323", "pending", orderId, "00020126...codigo-pix", "base64-qr-image");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);
        PaymentOutput result = paymentService.processPayment(input, "cliente@teste.com");

        assertThat(result.pixQrCode()).isEqualTo("00020126...codigo-pix");
        assertThat(result.pixQrCodeBase64()).isEqualTo("base64-qr-image");
        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void processPayment_pedidoNaoEncontrado_lancaExcecao() {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido não pertence ao usuário autenticado")
    void processPayment_pedidoNaoPertenceAoUsuario_lancaBusinessException() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "outro@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido não pertence ao usuário autenticado");

        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido já possui pagamento em processamento")
    void processPayment_pedidoJaPossuiPagamento_lancaBusinessException() throws Exception {
        order.setPaymentId("existing-payment-id");
        order.setPaymentStatus("pending");
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido já possui pagamento em processamento");

        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
    }

    @Test
    @DisplayName("Deve rejeitar pagamento se o pedido não está pendente")
    void processPayment_pedidoNaoPendente_lancaBusinessException() throws Exception {
        order.setStatus(OrderStatus.PAID);
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, "cliente@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido não está pendente de pagamento");

        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
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
    @ValueSource(strings = {"approved", "pending", "in_process", "authorized"})
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
    @ValueSource(strings = {"approved", "pending"})
    void processPayment_pagamentoOrfao_reaproveitaSemCriar(String status) throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        Payment payment = mockPayment("820", status, "detail", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        doReturn(PaymentService.OrphanedPaymentLookup.found(payment))
                .when(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());

        PaymentOutput result = paymentService.processPayment(input, user.getEmail());

        assertThat(result.id()).isEqualTo("820");
        assertThat(order.getPaymentId()).isEqualTo("820");
        assertThat(order.getPaymentStatus()).isEqualTo(status);
        assertThat(order.getStatus()).isEqualTo(
                "approved".equals(status) ? OrderStatus.PAID : OrderStatus.PENDING);
        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        verify(orderRepository).save(order);
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void processPayment_lookupNotFound_criaNovoPagamento() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        Payment payment = mockPayment("821", "approved", "detail", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);
        paymentService.processPayment(input, user.getEmail());

        verify(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());
        verify(paymentClient).create(argThat(request ->
                orderId.toString().equals(request.getExternalReference())
                        && order.getTotal().compareTo(request.getTransactionAmount()) == 0),
                any(MPRequestOptions.class));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AMBIGUOUS", "FAILED"})
    void processPayment_lookupInconclusivo_lancaSemCriarNemAlterarPedido(String outcome) throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
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
        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
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
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        Payment payment = mockPayment("501", "approved", "accredited", orderId);
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

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

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void processWebhook_falhaEmPedidoPending_mantemPendingSemDevolverEstoque(String status) throws Exception {
        stubGetPayment("604", status, status);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("604");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isEqualTo("604");
        assertThat(order.getPaymentStatus()).isEqualTo(status);
        assertThat(order.canReceivePayment()).isTrue();
        verify(orderService, never()).restoreStock(any());
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

    // ---------- issue #7: nova tentativa de pagamento ----------

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void processPayment_aposTentativaFalha_permiteNovaTentativa(String previousStatus) throws Exception {
        order.setPaymentId("900");
        order.setPaymentStatus(previousStatus);
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_novo", 1, "master", null, null, null, null);
        Payment payment = mockPayment("901", "approved", "accredited", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

        PaymentOutput result = paymentService.processPayment(input, user.getEmail());

        assertThat(result.id()).isEqualTo("901");
        assertThat(order.getPaymentId()).isEqualTo("901");
        assertThat(order.getPaymentStatus()).isEqualTo("approved");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentClient).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        verify(orderService, never()).restoreStock(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "authorized", "approved"})
    void processPayment_comTentativaAtiva_bloqueiaSemCriarPagamento(String activeStatus) throws Exception {
        order.setPaymentId("910");
        order.setPaymentStatus(activeStatus);
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, user.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Pedido já possui pagamento em processamento");

        assertThat(order.getPaymentId()).isEqualTo("910");
        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void processPayment_foraDaJanelaDePagamento_bloqueiaSemCriarPagamento() throws Exception {
        order.setCreatedAt(LocalDateTime.now().minusMinutes(Order.PAYMENT_WINDOW_MINUTES + 1));
        PaymentInput input = new PaymentInput(orderId, "pix", null, null, null, null, null, null, null);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, user.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prazo para pagar este pedido expirou");

        verify(paymentService, never()).findExistingPaymentForOrder(any(), any());
        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void processPayment_foraDaJanelaAposRecusa_bloqueia() throws Exception {
        order.setPaymentId("920");
        order.setPaymentStatus("rejected");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(Order.PAYMENT_WINDOW_MINUTES + 5));
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(input, user.getEmail()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prazo para pagar este pedido expirou");

        verify(paymentClient, never()).create(any(PaymentCreateRequest.class), any(MPRequestOptions.class));
    }

    @Test
    void processPayment_novaTentativaTambemRecusada_continuaPendingComUltimoPayment() throws Exception {
        order.setPaymentId("930");
        order.setPaymentStatus("rejected");
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null, null, null, null);
        Payment payment = mockPayment("931", "rejected", "cc_rejected_other_reason", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

        PaymentOutput result = paymentService.processPayment(input, user.getEmail());

        assertThat(result.status()).isEqualTo("rejected");
        assertThat(result.statusDetail()).isEqualTo("cc_rejected_other_reason");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isEqualTo("931");
        verify(orderService, never()).restoreStock(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void findExistingPaymentForOrder_tentativaFalha_naoEhReaproveitada(String status) throws Exception {
        Payment failed = mockPayment("940", status, "detail", orderId);
        assertLookupNotFoundFor(List.of(failed));
    }

    @Test
    void findExistingPaymentForOrder_recusadaMaisPendente_retornaApenasAPendente() throws Exception {
        Payment rejected = mockPayment("941", "rejected", "cc_rejected_other_reason", orderId);
        Payment pending = mockPayment("942", "pending", "pending_waiting_transfer", orderId);
        stubSearchResults(List.of(rejected, pending));
        doCallRealMethod().when(paymentService).findExistingPaymentForOrder(any(), any());

        PaymentService.OrphanedPaymentLookup result =
                paymentService.findExistingPaymentForOrder(orderId, order.getTotal());

        assertThat(result.outcome()).isEqualTo(PaymentService.OrphanedPaymentOutcome.FOUND);
        assertThat(result.payment()).isSameAs(pending);
    }

    @Test
    void processWebhook_tentativaNovaAposRecusa_aceitaEAprova() throws Exception {
        order.setPaymentId("950");
        order.setPaymentStatus("rejected");
        stubGetPayment("951", "approved", "accredited");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("951");

        assertThat(order.getPaymentId()).isEqualTo("951");
        assertThat(order.getPaymentStatus()).isEqualTo("approved");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
        verify(orderService, never()).restoreStock(any());
    }

    @Test
    void processWebhook_tentativaAntigaRecusadaComTentativaNovaAtiva_ignora(CapturedOutput output) throws Exception {
        order.setPaymentId("961");
        order.setPaymentStatus("pending");
        stubGetPayment("960", "rejected", "cc_rejected_other_reason");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("960");

        assertThat(order.getPaymentId()).isEqualTo("961");
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        assertThat(output).contains("Webhook ignorado").contains("961").contains("960");
    }

    // ---------- helpers ----------

    // ---------- markAsPaid + OrderPaidEvent + dados do pagador (sync Bling) ----------

    @Test
    void processPayment_aprovado_marcaPaidComPaidAtEBlingPendingEPublicaEvento() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null,
                "CPF", "529.982.247-25", null);
        Payment payment = mockPayment("1001", "approved", "accredited", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

        paymentService.processPayment(input, "cliente@teste.com");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(FIXED_NOW);
        assertThat(order.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.PENDING);
        verify(eventPublisher, times(1)).publishEvent(new OrderPaidEvent(orderId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pix", "credit_card"})
    void processPayment_gravaCustomerDocumentSoDigitosEPaymentMethod(String method) throws Exception {
        PaymentInput input = new PaymentInput(orderId, method, "tok_123", 1, "visa", null,
                "CPF", "529.982.247-25", null);
        Payment payment = mockPayment("1002", "pending", null, orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

        paymentService.processPayment(input, "cliente@teste.com");

        assertThat(order.getCustomerDocument()).isEqualTo("52998224725");
        assertThat(order.getPaymentMethod()).isEqualTo(method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "pending", "in_process"})
    void processPayment_naoAprovado_naoMarcaPaidNemPublicaEvento(String status) throws Exception {
        PaymentInput input = new PaymentInput(orderId, "pix", null, null, null, null,
                "CPF", "52998224725", null);
        Payment payment = mockPayment("1003", status, null, orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentClient.create(any(PaymentCreateRequest.class), any(MPRequestOptions.class))).thenReturn(payment);

        paymentService.processPayment(input, "cliente@teste.com");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaidAt()).isNull();
        assertThat(order.getBlingSyncStatus()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processPayment_pagamentoOrfaoAprovado_publicaEvento() throws Exception {
        PaymentInput input = new PaymentInput(orderId, "credit_card", "tok_123", 1, "visa", null,
                "CPF", "52998224725", null);
        Payment payment = mockPayment("1004", "approved", "accredited", orderId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        doReturn(PaymentService.OrphanedPaymentLookup.found(payment))
                .when(paymentService).findExistingPaymentForOrder(orderId, order.getTotal());

        paymentService.processPayment(input, "cliente@teste.com");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(eventPublisher).publishEvent(new OrderPaidEvent(orderId));
    }

    @Test
    void processWebhook_aprovado_marcaPaidComPaidAtEPublicaEvento() throws Exception {
        stubGetPayment("1101", "approved", "accredited");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1101");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(FIXED_NOW);
        assertThat(order.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.PENDING);
        verify(eventPublisher, times(1)).publishEvent(new OrderPaidEvent(orderId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled", "pending"})
    void processWebhook_naoAprovado_naoPublicaEvento(String status) throws Exception {
        stubGetPayment("1102", status, status);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1102");

        assertThat(order.getPaidAt()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processWebhook_jaProcessado_naoPublicaEvento() throws Exception {
        stubGetPayment("1103", "approved", "accredited");
        order.setStatus(OrderStatus.PAID);
        order.setPaymentId("1103");
        order.setPaymentStatus("approved");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1103");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processWebhook_pedidoForaDePending_naoPublicaEvento() throws Exception {
        stubGetPayment("1104", "approved", "accredited");
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1104");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processWebhook_pix_preenchePaymentMethodPix() throws Exception {
        Payment payment = stubGetPayment("1105", "approved", "accredited");
        when(payment.getPaymentMethodId()).thenReturn("pix");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1105");

        assertThat(order.getPaymentMethod()).isEqualTo("pix");
    }

    @Test
    void processWebhook_cartao_preenchePaymentMethodCreditCard() throws Exception {
        Payment payment = stubGetPayment("1106", "approved", "accredited");
        when(payment.getPaymentMethodId()).thenReturn("visa");
        when(payment.getPaymentTypeId()).thenReturn("credit_card");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1106");

        assertThat(order.getPaymentMethod()).isEqualTo("credit_card");
    }

    @Test
    void processWebhook_semDocumento_usaIdentificacaoDoPagador() throws Exception {
        Payment payment = mockPaymentWithPayerDocument("1107", "529.982.247-25");
        when(paymentClient.get(1107L)).thenReturn(payment);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1107");

        assertThat(order.getCustomerDocument()).isEqualTo("52998224725");
    }

    @Test
    void processWebhook_comDocumentoJaGravado_naoSobrescreve() throws Exception {
        Payment payment = mockPaymentWithPayerDocument("1108", "111.444.777-35");
        when(paymentClient.get(1108L)).thenReturn(payment);
        order.setCustomerDocument("52998224725");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        paymentService.processWebhook("1108");

        assertThat(order.getCustomerDocument()).isEqualTo("52998224725");
    }

    private Payment mockPaymentWithPayerDocument(String id, String document) {
        // deep stubs: payment.getPayer().getIdentification().getNumber()
        Payment payment = mock(Payment.class, RETURNS_DEEP_STUBS);
        lenient().when(payment.getId()).thenReturn(Long.parseLong(id));
        lenient().when(payment.getStatus()).thenReturn("approved");
        lenient().when(payment.getStatusDetail()).thenReturn("accredited");
        lenient().when(payment.getExternalReference()).thenReturn(orderId.toString());
        lenient().when(payment.getTransactionAmount()).thenReturn(new BigDecimal("150.00"));
        lenient().when(payment.getPaymentMethodId()).thenReturn("pix");
        lenient().when(payment.getPayer().getIdentification().getNumber()).thenReturn(document);
        return payment;
    }

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
