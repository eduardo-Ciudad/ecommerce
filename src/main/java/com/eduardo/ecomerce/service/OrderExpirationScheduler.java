package com.eduardo.ecomerce.service;


import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final long PENDING_ORDER_EXPIRATION_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expireAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_ORDER_EXPIRATION_MINUTES);

        List<Order> candidates = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Job de expiração de pedidos: {} candidato(s) encontrado(s)", candidates.size());

        for (Order candidate : candidates) {
            try {
                processExpiredOrder(candidate.getId());
            } catch (Exception e) {
                log.error("Falha ao processar expiração do pedido {}", candidate.getId(), e);
            }
        }
    }

    private void processExpiredOrder(UUID orderId) {
        String paymentId = orderService.lockPendingOrderForExpiration(orderId);

        if (paymentId == null) {
            return;
        }

        PaymentService.PaymentCancellationOutcome outcome = paymentService.tryCancelPendingPayment(paymentId);

        if (outcome != PaymentService.PaymentCancellationOutcome.CANCELLED) {
            return;
        }

        orderService.finalizeExpiredOrderCancellation(orderId, paymentId);
    }

}
