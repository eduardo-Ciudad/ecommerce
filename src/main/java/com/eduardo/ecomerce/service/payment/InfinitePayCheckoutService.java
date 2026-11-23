package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.PaymentProvider;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayCheckoutInput;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayCheckoutOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkRequest;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkResponse;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayClient;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayException;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayProperties;
import com.eduardo.ecomerce.infra.validation.DocumentNumbers;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pagamento com cartão pelo Checkout Integrado da InfinitePay (link hospedado + confirmação via payment_check). */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfinitePayCheckoutService {

    static final String PENDING = "pending";
    private static final String CREDIT_CARD = "credit_card";

    private final OrderRepository orderRepository;
    private final InfinitePayClient client;
    private final InfinitePayProperties properties;
    private final InfinitePayItemsBuilder itemsBuilder;

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
