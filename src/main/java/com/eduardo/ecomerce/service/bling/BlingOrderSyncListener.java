package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara o envio ao Bling depois que o pagamento foi commitado.
 * AFTER_COMMIT: rollback do pagamento descarta o evento. @Async: o Bling nunca atrasa a resposta do pagamento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlingOrderSyncListener {

    private final BlingOrderSyncService syncService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        try {
            syncService.sync(event.orderId());
        } catch (Exception e) {
            // o sync já trata os próprios erros; isto é só uma proteção extra — o scheduler retoma
            log.error("Erro inesperado ao enviar pedido {} ao Bling", event.orderId(), e);
        }
    }
}
