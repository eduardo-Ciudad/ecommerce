package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BlingOrderSyncListenerTest {

    @Mock
    private BlingOrderSyncService syncService;

    @InjectMocks
    private BlingOrderSyncListener listener;

    @Test
    void onOrderPaid_delegaParaSyncComOrderIdDoEvento() {
        UUID orderId = UUID.randomUUID();

        listener.onOrderPaid(new OrderPaidEvent(orderId));

        verify(syncService).sync(orderId);
    }

    @Test
    void onOrderPaid_excecaoNoSync_engoleELoga(CapturedOutput output) {
        UUID orderId = UUID.randomUUID();
        when(syncService.sync(orderId)).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> listener.onOrderPaid(new OrderPaidEvent(orderId))).doesNotThrowAnyException();
        assertThat(output).contains("Erro inesperado ao enviar pedido").contains(orderId.toString());
    }

    @Test
    void onOrderPaid_asyncEAfterCommit() throws Exception {
        Method method = BlingOrderSyncListener.class.getMethod("onOrderPaid", OrderPaidEvent.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        TransactionalEventListener listenerAnnotation = method.getAnnotation(TransactionalEventListener.class);
        assertThat(listenerAnnotation).isNotNull();
        assertThat(listenerAnnotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listenerAnnotation.fallbackExecution()).isFalse();
    }
}
