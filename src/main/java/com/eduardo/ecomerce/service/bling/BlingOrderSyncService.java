package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import com.eduardo.ecomerce.service.BlingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Envia um pedido pago do site para o Bling: contato -> pedido de venda -> lançamento de estoque.
 * Nunca lança exceção para quem chama: qualquer erro vira FAILED com a mensagem gravada no pedido.
 * Roda sem transação aberta; o claim e os mark* do repositório têm transação própria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlingOrderSyncService {

    static final int MAX_ERROR_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final BlingClient blingClient;
    private final BlingService blingService;
    private final BlingOrderPayloadMapper mapper;
    private final BlingOrderProperties properties;
    private final Clock clock;

    public BlingSyncOutcome sync(UUID orderId) {
        if (!properties.enabled()) {
            return BlingSyncOutcome.DISABLED;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (orderRepository.claimForBlingSync(orderId, now, now.minus(properties.staleAfter())) == 0) {
            log.debug("Envio ao Bling ignorado — pedido {} não elegível ou já em processamento", orderId);
            return BlingSyncOutcome.SKIPPED;
        }

        try {
            Order order = orderRepository.findWithItemsById(orderId)
                    .orElseThrow(() -> new IllegalStateException("Pedido " + orderId + " não encontrado"));

            Long blingOrderId = order.getBlingOrderId();
            if (blingOrderId == null) {
                blingOrderId = findExistingSaleOrder(order);
                if (blingOrderId != null) {
                    log.info("Pedido {} já existia no Bling (id {}) — reaproveitando", orderId, blingOrderId);
                    orderRepository.saveBlingOrderId(orderId, blingOrderId);
                }
            }
            if (blingOrderId == null) {
                blingOrderId = createSaleOrder(order);
                // gravado antes do estoque: um retry nunca recria o pedido
                orderRepository.saveBlingOrderId(orderId, blingOrderId);
            }

            Long saleOrderId = blingOrderId;
            blingService.callBling(token -> blingClient.launchSaleOrderStock(token, saleOrderId));

            orderRepository.markBlingSent(orderId, now);
            log.info("Pedido {} enviado ao Bling (pedido de venda {}) com estoque lançado", orderId, saleOrderId);
            return BlingSyncOutcome.SENT;
        } catch (Exception e) {
            log.error("Falha ao enviar pedido {} ao Bling", orderId, e);
            orderRepository.markBlingFailed(orderId, truncate(errorMessage(e)));
            return BlingSyncOutcome.FAILED;
        }
    }

    private Long findExistingSaleOrder(Order order) {
        JsonNode response = blingService.callBling(token -> blingClient.findSaleOrdersByStoreNumber(
                token, properties.storeId(), order.getId().toString()));
        return firstId(response);
    }

    private Long createSaleOrder(Order order) {
        Long contactId = resolveContact(order);
        ObjectNode payload = mapper.toSaleOrder(order, contactId);

        JsonNode response = blingService.callBling(token -> blingClient.createSaleOrder(token, payload));
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.path("id").canConvertToLong() || data.path("id").asLong() == 0) {
            throw new IllegalStateException("Resposta do Bling sem data.id ao criar pedido de venda");
        }

        JsonNode alerts = data.path("alertas");
        if (alerts.isArray() && !alerts.isEmpty()) {
            log.warn("Bling criou o pedido de venda {} do pedido {} com alertas: {}",
                    data.path("id").asLong(), order.getId(), alerts);
        }
        return data.path("id").asLong();
    }

    private Long resolveContact(Order order) {
        String document = order.getCustomerDocument();
        if (document != null && !document.isBlank()) {
            JsonNode found = blingService.callBling(token -> blingClient.findContactsByDocument(token, document));
            Long existingId = firstId(found);
            if (existingId != null) {
                return existingId;
            }
        }

        ObjectNode contactPayload = mapper.toContact(order);
        JsonNode created = blingService.callBling(token -> blingClient.createContact(token, contactPayload));
        JsonNode id = created == null ? null : created.path("data").path("id");
        if (id == null || !id.canConvertToLong() || id.asLong() == 0) {
            throw new IllegalStateException("Resposta do Bling sem data.id ao criar contato");
        }
        return id.asLong();
    }

    /** id do primeiro item de data[] (listagens do Bling), ou null se vazio. */
    private static Long firstId(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }
        JsonNode id = data.get(0).path("id");
        return id.canConvertToLong() && id.asLong() != 0 ? id.asLong() : null;
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
