package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reenvia ao Bling pedidos PENDING/FAILED abaixo do limite de tentativas e PROCESSING travados. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlingOrderRetryScheduler {

    private final OrderRepository orderRepository;
    private final BlingOrderSyncService syncService;
    private final BlingOrderProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${bling.orders.retry-interval:PT10M}", initialDelayString = "PT2M")
    public void retryPending() {
        if (!properties.enabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<UUID> ids = orderRepository.findIdsForBlingRetry(
                properties.maxAttempts(), now.minus(properties.staleAfter()));
        if (ids.isEmpty()) {
            return;
        }

        log.info("Retry de envio ao Bling: {} pedido(s) na fila", ids.size());
        for (UUID id : ids) {
            try {
                syncService.sync(id);
            } catch (Exception e) {
                log.error("Erro inesperado no retry do pedido {} ao Bling — seguindo para o próximo", id, e);
            }
        }
    }
}
