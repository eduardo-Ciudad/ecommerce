package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlingOrderRetrySchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 16, 0);

    @Mock private OrderRepository orderRepository;
    @Mock private BlingOrderSyncService syncService;

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private BlingOrderRetryScheduler scheduler(boolean enabled) {
        BlingOrderProperties properties = new BlingOrderProperties(
                enabled, 206333320L, 15L,
                new BlingOrderProperties.PaymentMethods(9042991L, 11412042L),
                5, Duration.ofMinutes(15));
        return new BlingOrderRetryScheduler(orderRepository, syncService, properties, clock);
    }

    @Test
    void retryPending_desabilitado_naoConsultaFila() {
        scheduler(false).retryPending();

        verifyNoInteractions(orderRepository, syncService);
    }

    @Test
    void retryPending_habilitado_buscaFilaComLimiteEStaleESincronizaNaOrdem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(orderRepository.findIdsForBlingRetry(5, NOW.minusMinutes(15))).thenReturn(List.of(first, second));

        scheduler(true).retryPending();

        InOrder inOrder = inOrder(syncService);
        inOrder.verify(syncService).sync(first);
        inOrder.verify(syncService).sync(second);
    }

    @Test
    void retryPending_filaVazia_naoChamaSync() {
        when(orderRepository.findIdsForBlingRetry(anyInt(), any())).thenReturn(List.of());

        scheduler(true).retryPending();

        verifyNoInteractions(syncService);
    }

    @Test
    void retryPending_excecaoEmUmPedido_segueParaOsProximos() {
        UUID failing = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(orderRepository.findIdsForBlingRetry(5, NOW.minusMinutes(15))).thenReturn(List.of(failing, next));
        when(syncService.sync(failing)).thenThrow(new RuntimeException("boom"));

        scheduler(true).retryPending();

        verify(syncService).sync(next);
    }
}
