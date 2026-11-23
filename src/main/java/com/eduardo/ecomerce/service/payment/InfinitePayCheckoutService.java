package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.PaymentProvider;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayCheckoutInput;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayCheckoutOutput;
import com.eduardo.ecomerce.email.EmailService;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkRequest;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkResponse;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayClient;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayException;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayProperties;
import com.eduardo.ecomerce.infra.infinitepay.PaymentCheckRequest;
import com.eduardo.ecomerce.infra.infinitepay.PaymentCheckResponse;
import com.eduardo.ecomerce.infra.validation.DocumentNumbers;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Pagamento com cartão pelo Checkout Integrado da InfinitePay (link hospedado + confirmação via payment_check). */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfinitePayCheckoutService {

    static final String PENDING = "pending";
    static final String APPROVED = "approved";
    private static final String CREDIT_CARD = "credit_card";

    private final OrderRepository orderRepository;
    private final InfinitePayClient client;
    private final InfinitePayProperties properties;
    private final InfinitePayItemsBuilder itemsBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public InfinitePayCheckoutOutput createCheckout(InfinitePayCheckoutInput input, String payerEmail) {
        if (!properties.enabled()) {
            throw new BusinessException("Pagamento com cartão indisponível no momento.");
        }

        Order order = orderRepository.findByIdForUpdate(input.orderId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (!order.getUser().getEmail().equals(payerEmail)) {
            throw new BusinessException("Pedido não pertence ao usuário autenticado");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Pedido não está pendente de pagamento");
        }
        if (hasReusableLink(order)) {
            log.info("Reaproveitando link da InfinitePay ainda ativo para o pedido {}", order.getId());
            return new InfinitePayCheckoutOutput(order.getCheckoutUrl());
        }
        if (order.hasActivePaymentAttempt()) {
            throw new BusinessException("Pedido já possui pagamento em processamento");
        }
        if (!order.canReceivePayment()) {
            throw new BusinessException(
                    "O prazo para pagar este pedido expirou. Faça um novo pedido pelo carrinho.");
        }

        CreateLinkResponse response;
        try {
            response = client.createLink(buildRequest(order, input, payerEmail));
        } catch (InfinitePayException e) {
            log.error("Falha ao criar link da InfinitePay para o pedido {}: {}", order.getId(), e.getMessage());
            throw new BusinessException("Não foi possível iniciar o pagamento. Tente novamente.");
        }

        order.setPaymentProvider(PaymentProvider.INFINITEPAY);
        order.setCheckoutUrl(response.url());
        order.setPaymentStatus(PENDING);
        order.setPaymentMethod(CREDIT_CARD);
        order.setPaymentId(blankToNull(response.slug()));
        order.setCustomerDocument(DocumentNumbers.onlyDigits(input.payerDocNumber()));
        order.setCustomerPhone(DocumentNumbers.onlyDigits(input.payerPhone()));
        orderRepository.save(order);

        log.info("Link da InfinitePay criado para o pedido {} (slug: {})", order.getId(), order.getPaymentId());
        return new InfinitePayCheckoutOutput(response.url());
    }

    /**
     * Ponto único de confirmação (webhook e página de retorno). Só marca como pago após o payment_check;
     * idempotente por transaction_nsu. Falha de comunicação com a InfinitePay sobe como exceção (o webhook reenvia).
     */
    @Transactional
    public InfinitePayConfirmation confirm(String orderNsu, String transactionNsu, String slug) {
        if (isBlank(orderNsu) || isBlank(transactionNsu) || isBlank(slug)) {
            throw new BusinessException("Dados de confirmação do pagamento incompletos");
        }
        UUID orderId = parseOrderId(orderNsu);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (order.getPaymentProvider() != PaymentProvider.INFINITEPAY) {
            log.warn("Confirmação da InfinitePay recebida para o pedido {} de outro provedor ({})",
                    orderId, order.getPaymentProvider());
            throw new BusinessException("Pedido não foi pago pela InfinitePay");
        }

        if (order.getStatus() == OrderStatus.PAID && transactionNsu.equals(order.getPaymentTransactionId())) {
            log.info("Confirmação da InfinitePay ignorada — pedido {} já pago pela transação {}", orderId, transactionNsu);
            return InfinitePayConfirmation.ALREADY_PAID;
        }

        PaymentCheckResponse check = client.checkPayment(
                new PaymentCheckRequest(properties.handle(), orderNsu, transactionNsu, slug));

        if (!check.success() || !check.paid()) {
            log.info("payment_check da InfinitePay não confirmou o pagamento do pedido {} (success={}, paid={})",
                    orderId, check.success(), check.paid());
            return InfinitePayConfirmation.NOT_PAID;
        }

        long expectedCents = toCents(order.getTotal());
        if (check.amount() == null || check.amount() != expectedCents) {
            log.error("Valor divergente na InfinitePay — pedido {}, esperado {} centavos, amount {}",
                    orderId, expectedCents, check.amount());
            throw new BusinessException("Valor do pagamento não corresponde ao pedido");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.error("Pagamento da InfinitePay confirmado para o pedido {} fora de PENDING (status {}) — transação {}",
                    orderId, order.getStatus(), transactionNsu);
            emailService.sendAdminAlert("Pagamento recebido em pedido " + order.getStatus(),
                    "A InfinitePay confirmou um pagamento para um pedido que não está mais pendente.\n\n"
                            + "Pedido: " + orderId + "\n"
                            + "Status atual: " + order.getStatus() + "\n"
                            + "Transação: " + transactionNsu + "\n"
                            + "Valor: R$ " + order.getTotal() + "\n\n"
                            + "Verifique no app da InfinitePay se é preciso estornar o pagamento.");
            return InfinitePayConfirmation.REQUIRES_ATTENTION;
        }

        order.setPaymentTransactionId(transactionNsu);
        order.setPaymentId(slug);
        order.setPaymentStatus(APPROVED);
        order.setPaymentInstallments(check.installments());
        if (!isBlank(check.captureMethod())) {
            order.setPaymentMethod(check.captureMethod());
        }
        order.markAsPaid(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
        orderRepository.save(order);

        log.info("Pagamento da InfinitePay confirmado — pedido {}, transação {}, {}x",
                orderId, transactionNsu, check.installments());
        return InfinitePayConfirmation.PAID;
    }

    private static UUID parseOrderId(String orderNsu) {
        try {
            return UUID.fromString(orderNsu);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Pedido inválido na confirmação do pagamento");
        }
    }

    private static long toCents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasReusableLink(Order order) {
        LocalDateTime deadline = order.getPaymentDeadline();
        return order.getPaymentProvider() == PaymentProvider.INFINITEPAY
                && order.getCheckoutUrl() != null
                && PENDING.equals(order.getPaymentStatus())
                && deadline != null
                && LocalDateTime.now().isBefore(deadline);
    }

    private CreateLinkRequest buildRequest(Order order, InfinitePayCheckoutInput input, String payerEmail) {
        return new CreateLinkRequest(
                properties.handle(),
                itemsBuilder.build(order),
                order.getId().toString(),
                returnUrl(),
                properties.webhookUrl(),
                new CreateLinkRequest.Customer(
                        resolveCustomerName(order), payerEmail, "+55" + DocumentNumbers.onlyDigits(input.payerPhone())),
                new CreateLinkRequest.Address(
                        DocumentNumbers.onlyDigits(order.getRecipientCep()),
                        order.getRecipientStreet(),
                        order.getRecipientNumber(),
                        order.getRecipientNeighborhood(),
                        order.getRecipientCity(),
                        order.getRecipientState(),
                        blankToNull(order.getRecipientComplement())),
                new CreateLinkRequest.Options(
                        properties.assuredInstallments(),
                        false,
                        true,
                        validUntil(order),
                        List.of(CREDIT_CARD)));
    }

    /** Prazo do pedido com offset explícito: o container roda em UTC, o instante fica correto em qualquer fuso. */
    private static String validUntil(Order order) {
        return order.getPaymentDeadline().atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String returnUrl() {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + properties.returnPath();
    }

    private static String resolveCustomerName(Order order) {
        if (order.getUser() != null && order.getUser().getName() != null && !order.getUser().getName().isBlank()) {
            return order.getUser().getName();
        }
        return blankToNull(order.getRecipientName());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
