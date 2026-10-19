package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.validation.DocumentNumbers;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentAdditionalInfoPayerRequest;
import com.mercadopago.client.payment.PaymentAdditionalInfoRequest;
import com.mercadopago.client.payment.PaymentItemRequest;
import com.mercadopago.client.payment.PaymentReceiverAddressRequest;
import com.mercadopago.client.payment.PaymentShipmentsRequest;
import com.mercadopago.core.MPRequestOptions;
import org.jsoup.Jsoup;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentClient paymentClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;
    private static final String STATEMENT_DESCRIPTOR = "GABIKIDS";
    private static final String MP_ITEM_CATEGORY = "fashion";
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Set<String> CANCELLABLE_PAYMENT_STATUSES = Set.of("pending", "in_process", "authorized");
    // Status de um pagamento que ainda vale reaproveitar para o pedido. Pagamentos recusados
    // ou cancelados ficam de fora para que uma nova tentativa crie um pagamento novo.
    private static final Set<String> REUSABLE_PAYMENT_STATUSES =
            Set.of("approved", "pending", "in_process", "authorized");

    public enum PaymentCancellationOutcome {
        NOT_CANCELLABLE,
        NOT_FOUND,
        CANCELLED,
        FAILED
    }

    public enum OrphanedPaymentOutcome {
        NOT_FOUND,
        FOUND,
        AMBIGUOUS,
        FAILED
    }

    public record OrphanedPaymentLookup(OrphanedPaymentOutcome outcome, Payment payment) {
        public static OrphanedPaymentLookup notFound() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.NOT_FOUND, null);
        }

        public static OrphanedPaymentLookup found(Payment payment) {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.FOUND, payment);
        }

        public static OrphanedPaymentLookup ambiguous() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.AMBIGUOUS, null);
        }

        public static OrphanedPaymentLookup failed() {
            return new OrphanedPaymentLookup(OrphanedPaymentOutcome.FAILED, null);
        }
    }



    @Transactional
    public PaymentOutput processPayment(PaymentInput input, String payerEmail) {
        Order order = orderRepository.findByIdForUpdate(input.orderId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (!order.getUser().getEmail().equals(payerEmail)) {
            throw new BusinessException("Pedido não pertence ao usuário autenticado");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Pedido não está pendente de pagamento");
        }

        if (order.hasActivePaymentAttempt()) {
            throw new BusinessException("Pedido já possui pagamento em processamento");
        }

        if (!order.canReceivePayment()) {
            throw new BusinessException(
                    "O prazo para pagar este pedido expirou. Faça um novo pedido pelo carrinho.");
        }

        OrphanedPaymentLookup lookup = findExistingPaymentForOrder(order.getId(), order.getTotal());

        Payment payment = switch (lookup.outcome()) {
            case FOUND -> {
                log.info("Pagamento existente encontrado no Mercado Pago para o pedido {} — reaproveitando em vez de criar um novo",
                        order.getId());
                yield lookup.payment();
            }
            case NOT_FOUND -> createNewPayment(order, input, payerEmail);
            case AMBIGUOUS -> throw new BusinessException(
                    "Não foi possível confirmar o pagamento deste pedido. Entre em contato com o suporte.");
            case FAILED -> throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        };

        order.setPaymentId(payment.getId().toString());
        order.setPaymentStatus(payment.getStatus());
        // reflete sempre a última tentativa (a aprovada é sempre a última)
        order.setCustomerDocument(DocumentNumbers.onlyDigits(input.payerDocNumber()));
        order.setPaymentMethod(input.paymentMethod());

        switch (payment.getStatus()) {
            case "approved" -> markOrderPaid(order);
            case "rejected" -> log.warn("Pagamento recusado — Pedido: {}, Payment: {}, status_detail: {}. "
                            + "Pedido segue PENDING para nova tentativa até {}",
                    order.getId(), payment.getId(), payment.getStatusDetail(), order.getPaymentDeadline());
        }

        orderRepository.save(order);

        log.info("Pagamento processado — Pedido: {}, Payment: {}, Status: {}",
                order.getId(), payment.getId(), payment.getStatus());

        String pixQrCode = null;
        String pixQrCodeBase64 = null;

        if ("pix".equals(input.paymentMethod()) &&
                payment.getPointOfInteraction() != null &&
                payment.getPointOfInteraction().getTransactionData() != null) {
            pixQrCode = payment.getPointOfInteraction().getTransactionData().getQrCode();
            pixQrCodeBase64 = payment.getPointOfInteraction().getTransactionData().getQrCodeBase64();
        }

        return new PaymentOutput(
                payment.getId().toString(),
                payment.getStatus(),
                payment.getStatusDetail(),
                input.paymentMethod(),
                order.getTotal(),
                pixQrCode,
                pixQrCodeBase64
        );
    }

    private Payment createNewPayment(Order order, PaymentInput input, String payerEmail) {
        PaymentCreateRequest.PaymentCreateRequestBuilder requestBuilder = PaymentCreateRequest.builder()
                .transactionAmount(order.getTotal())
                .description("GabiKids - Pedido #" + order.getId().toString().substring(0, 8))
                .statementDescriptor(STATEMENT_DESCRIPTOR)
                .externalReference(order.getId().toString())
                .notificationUrl(notificationUrl)
                .payer(buildPayer(order, input, payerEmail))
                .additionalInfo(buildAdditionalInfo(order));

        if ("credit_card".equals(input.paymentMethod())) {
            if (input.token() == null || input.token().isBlank()) {
                throw new BusinessException("Dados do cartão ausentes. Tente novamente.");
            }
            requestBuilder
                    .token(input.token())
                    .installments(input.installments() != null ? input.installments() : 1)
                    .paymentMethodId(input.cardIssuerId())
                    .issuerId(blankToNull(input.issuerId()));
        } else if ("pix".equals(input.paymentMethod())) {
            requestBuilder.paymentMethodId("pix");
        }

        try {
            return paymentClient.create(requestBuilder.build(), buildRequestOptions(input));
        } catch (com.mercadopago.exceptions.MPApiException e) {
            log.error("MP API Error - Status: {}, Content: {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        } catch (Exception e) {
            log.error("Erro ao processar pagamento do pedido {}", order.getId(), e);
            throw new BusinessException("Erro ao processar pagamento. Tente novamente.");
        }
    }

    private PaymentPayerRequest buildPayer(Order order, PaymentInput input, String payerEmail) {
        String[] names = splitName(order.getUser() != null ? order.getUser().getName() : null);

        PaymentPayerRequest.PaymentPayerRequestBuilder payer = PaymentPayerRequest.builder()
                .email(payerEmail)
                .firstName(names[0])
                .lastName(names[1]);

        String docNumber = DocumentNumbers.onlyDigits(input.payerDocNumber());
        if (docNumber != null) {
            payer.identification(IdentificationRequest.builder()
                    .type(input.payerDocType() != null ? input.payerDocType() : "CPF")
                    .number(docNumber)
                    .build());
        }
        return payer.build();
    }

    private PaymentAdditionalInfoRequest buildAdditionalInfo(Order order) {
        List<PaymentItemRequest> items = order.getItems() == null ? List.of()
                : order.getItems().stream().map(this::toPaymentItem).toList();

        String[] buyerNames = splitName(order.getUser() != null ? order.getUser().getName() : null);
        OffsetDateTime registrationDate = order.getUser() != null && order.getUser().getCreatedAt() != null
                ? order.getUser().getCreatedAt().atZone(STORE_ZONE).toOffsetDateTime()
                : null;

        PaymentReceiverAddressRequest receiverAddress = PaymentReceiverAddressRequest.builder()
                .zipCode(DocumentNumbers.onlyDigits(order.getRecipientCep()))
                .streetName(order.getRecipientStreet())
                .streetNumber(order.getRecipientNumber())
                .apartment(blankToNull(order.getRecipientComplement()))
                .cityName(order.getRecipientCity())
                .stateName(order.getRecipientState())
                .build();

        return PaymentAdditionalInfoRequest.builder()
                .items(items)
                .payer(PaymentAdditionalInfoPayerRequest.builder()
                        .firstName(buyerNames[0])
                        .lastName(buyerNames[1])
                        .registrationDate(registrationDate)
                        .build())
                .shipments(PaymentShipmentsRequest.builder()
                        .receiverAddress(receiverAddress)
                        .build())
                .build();
    }

    private PaymentItemRequest toPaymentItem(OrderItem item) {
        ProductVariant variant = item.getVariant();
        Product product = variant.getProduct();

        String id = variant.getSku() != null && !variant.getSku().isBlank()
                ? variant.getSku()
                : variant.getId().toString();

        return PaymentItemRequest.builder()
                .id(id)
                .title(buildItemTitle(product, variant))
                .description(buildItemDescription(product, variant))   // novo
                .pictureUrl(blankToNull(product.getImageUrl()))
                .categoryId(MP_ITEM_CATEGORY)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build();
    }

    private static final int MP_ITEM_DESCRIPTION_MAX = 255;

    private String buildItemDescription(Product product, ProductVariant variant) {
        String text = product.getDescription() == null ? ""
                : Jsoup.parse(product.getDescription()).text().replaceAll("\\s+", " ").trim();

        if (text.isEmpty()) {
            text = buildItemTitle(product, variant);
        }
        return text.length() > MP_ITEM_DESCRIPTION_MAX
                ? text.substring(0, MP_ITEM_DESCRIPTION_MAX - 3) + "..."
                : text;
    }

    private String buildItemTitle(Product product, ProductVariant variant) {
        StringBuilder title = new StringBuilder(product.getName());
        if (variant.getColor() != null && !variant.getColor().isBlank()) {
            title.append(" - ").append(variant.getColor());
        }
        if (variant.getSize() != null && !variant.getSize().isBlank()) {
            title.append(" - Tam. ").append(variant.getSize());
        }
        return title.toString();
    }

    private MPRequestOptions buildRequestOptions(PaymentInput input) {
        String deviceId = blankToNull(input.deviceId());
        Map<String, String> headers = deviceId == null ? Map.of() : Map.of("X-meli-session-id", deviceId);
        return MPRequestOptions.builder().customHeaders(headers).build();
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{null, null};
        }
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int space = normalized.indexOf(' ');
        return space < 0
                ? new String[]{normalized, null}
                : new String[]{normalized.substring(0, space), normalized.substring(space + 1)};
    }

    /**
     * Única passagem para PAID: marca o pedido (paidAt + fila do Bling) e publica o evento.
     * O listener roda AFTER_COMMIT, então um rollback descarta o evento.
     */
    private void markOrderPaid(Order order) {
        order.markAsPaid(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
    }

    /**
     * Completa meio de pagamento e documento a partir do pagamento confirmado pelo webhook
     * (cobre pedido pago cujo processPayment não gravou esses campos, ex.: pagamento órfão).
     * Documento já gravado nunca é sobrescrito.
     */
    private void fillPaymentDataFromWebhook(Order order, Payment payment) {
        if ("pix".equals(payment.getPaymentMethodId())) {
            order.setPaymentMethod("pix");
        } else if ("credit_card".equals(payment.getPaymentTypeId())) {
            order.setPaymentMethod("credit_card");
        }

        if (order.getCustomerDocument() == null
                && payment.getPayer() != null
                && payment.getPayer().getIdentification() != null) {
            order.setCustomerDocument(
                    DocumentNumbers.onlyDigits(payment.getPayer().getIdentification().getNumber()));
        }
    }


    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public OrphanedPaymentLookup findExistingPaymentForOrder(UUID orderId, BigDecimal expectedTotal) {
        try {
            MPSearchRequest searchRequest = MPSearchRequest.builder()
                    .filters(Map.of("external_reference", orderId.toString()))
                    .limit(10)
                    .offset(0)
                    .build();

            List<Payment> results = paymentClient.search(searchRequest).getResults();

            List<Payment> matching = results.stream()
                    .filter(p -> orderId.toString().equals(p.getExternalReference()))
                    .filter(p -> p.getTransactionAmount() != null
                            && p.getTransactionAmount().compareTo(expectedTotal) == 0)
                    .filter(p -> REUSABLE_PAYMENT_STATUSES.contains(p.getStatus()))
                    .toList();

            if (matching.isEmpty()) {
                return OrphanedPaymentLookup.notFound();
            }

            if (matching.size() > 1) {
                log.error("Mais de um pagamento válido encontrado no Mercado Pago para o pedido {} — requer investigação manual",
                        orderId);
                return OrphanedPaymentLookup.ambiguous();
            }

            return OrphanedPaymentLookup.found(matching.get(0));

        } catch (Exception e) {
            log.error("Falha ao buscar pagamento existente no Mercado Pago para o pedido {}", orderId, e);
            return OrphanedPaymentLookup.failed();
        }
    }



    @Transactional
    public void processWebhook(String paymentId) {
        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            String status = payment.getStatus();
            UUID orderId = UUID.fromString(payment.getExternalReference());

            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

            if (paymentId.equals(order.getPaymentId()) && status.equals(order.getPaymentStatus())) {
                log.info("Webhook ignorado - Pedido: {}, Payment: {} já processado com status {}",
                        orderId, paymentId, status);
                return;
            }

            // Aceita o webhook de uma tentativa nova quando a anterior falhou (rejected/cancelled).
            // Enquanto houver tentativa ativa, notificações de outros payments seguem ignoradas.
            boolean paymentIdMatches = order.getPaymentId() == null
                    || paymentId.equals(order.getPaymentId())
                    || !order.hasActivePaymentAttempt();
            if (!paymentIdMatches) {
                log.warn("Webhook ignorado - Pedido: {} está associado ao payment {}, mas recebeu notificação do payment {} (status {})",
                        orderId, order.getPaymentId(), paymentId, status);
                return;
            }

            if (order.getStatus() != OrderStatus.PENDING) {
                log.warn("Webhook recebido para pedido {} fora de PENDING (status atual: {}) - payment: {}, status recebido: {}. Nenhuma alteração aplicada.",
                        orderId, order.getStatus(), paymentId, status);
                return;
            }

            BigDecimal paidAmount = payment.getTransactionAmount();
            if ("approved".equals(status) && paidAmount.compareTo(order.getTotal()) != 0) {
                log.error("Valor divergente no webhook - Pedido: {}, esperado: {}, pago: {}",
                        orderId, order.getTotal(), paidAmount);
                throw new BusinessException("Valor do pagamento não corresponde ao pedido");
            }

            order.setPaymentId(paymentId);
            order.setPaymentStatus(status);

            switch (status) {
                case "approved" -> {
                    fillPaymentDataFromWebhook(order, payment);
                    markOrderPaid(order);
                }
                case "rejected", "cancelled" -> log.info(
                        "Webhook: pagamento {} {} — pedido {} segue PENDING aguardando nova tentativa ou expiração",
                        paymentId, status, orderId);
            }

            orderRepository.save(order);
            log.info("Webhook processado - Pedido: {}, Payment: {}, Status: {}",
                    orderId, paymentId, status);

        } catch (Exception e) {
            log.error("Erro ao processar webhook do payment {}", paymentId, e);
            throw new BusinessException("Erro ao processar notificação de pagamento");
        }
    }

    public PaymentCancellationOutcome tryCancelPendingPayment(String paymentId) {
        try {
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            if (!CANCELLABLE_PAYMENT_STATUSES.contains(payment.getStatus())) {
                log.info("Payment {} não está em status cancelável para expiração (status atual: {})",
                        paymentId, payment.getStatus());
                return PaymentCancellationOutcome.NOT_CANCELLABLE;
            }

            paymentClient.cancel(Long.parseLong(paymentId));
            log.info("Payment {} cancelado no Mercado Pago (expiração de pedido)", paymentId);
            return PaymentCancellationOutcome.CANCELLED;

        } catch (com.mercadopago.exceptions.MPApiException e) {
            if (e.getStatusCode() == 404) {
                log.info("Payment {} não encontrado no Mercado Pago (expiração de pedido) — nada a cancelar",
                        paymentId);
                return PaymentCancellationOutcome.NOT_FOUND;
            }
            log.error("Falha ao tentar cancelar payment {} no Mercado Pago (expiração de pedido) - Status: {}, Content: {}",
                    paymentId, e.getStatusCode(), e.getApiResponse().getContent());
            return PaymentCancellationOutcome.FAILED;

        } catch (Exception e) {
            log.error("Falha ao tentar cancelar payment {} no Mercado Pago (expiração de pedido)", paymentId, e);
            return PaymentCancellationOutcome.FAILED;
        }
    }




}