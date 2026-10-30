package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.BlingSyncStatus;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private OrderRepository orderRepository;

    /** Container roda em UTC: clock e paidAt ficam em UTC, como em produção. */
    private AdminStatsService service(String nowUtc) {
        Clock clock = Clock.fixed(Instant.parse(nowUtc), ZoneOffset.UTC);
        return new AdminStatsService(productRepository, variantRepository, orderRepository, clock);
    }

    private void paidOrdersReturn(Order... orders) {
        when(orderRepository.findByStatusInAndPaidAtGreaterThanEqualAndPaidAtLessThan(anyCollection(), any(), any()))
                .thenReturn(List.of(orders));
    }

    private Order localSale(LocalDateTime paidAtUtc, String total) {
        Order order = paidOrder(paidAtUtc, total);
        order.setChannel(com.eduardo.ecomerce.domain.order.OrderChannel.LOCAL);
        order.setStatus(OrderStatus.DELIVERED);
        return order;
    }

    @Test
    void getStats_separaFaturamentoDoMesPorCanalMantendoOTotal() {
        paidOrdersReturn(
                paidOrder(LocalDateTime.of(2026, 10, 10, 15, 0), "100.00"),
                paidOrder(LocalDateTime.of(2026, 10, 11, 15, 0), "50.00"),
                localSale(LocalDateTime.of(2026, 10, 12, 15, 0), "30.00"));

        AdminStatsOutput.OrderStats orders = service("2026-10-26T21:00:00Z").getStats().orders();

        assertThat(orders.paidThisMonth()).isEqualTo(3);
        assertThat(orders.revenueThisMonth()).isEqualByComparingTo("180.00");
        assertThat(orders.site().paidThisMonth()).isEqualTo(2);
        assertThat(orders.site().revenueThisMonth()).isEqualByComparingTo("150.00");
        assertThat(orders.site().averageTicketThisMonth()).isEqualByComparingTo("75.00");
        assertThat(orders.local().paidThisMonth()).isEqualTo(1);
        assertThat(orders.local().revenueThisMonth()).isEqualByComparingTo("30.00");
        assertThat(orders.local().averageTicketThisMonth()).isEqualByComparingTo("30.00");
    }

    @Test
    void getStats_serieDiariaSeparaCanaisNoMesmoDia() {
        paidOrdersReturn(
                paidOrder(LocalDateTime.of(2026, 10, 20, 15, 0), "40.00"),
                localSale(LocalDateTime.of(2026, 10, 20, 16, 0), "25.00"),
                localSale(LocalDateTime.of(2026, 10, 20, 17, 0), "15.00"));

        AdminStatsOutput stats = service("2026-10-26T21:00:00Z").getStats();

        assertThat(stats.dailySales())
                .filteredOn(d -> d.date().equals(LocalDate.of(2026, 10, 20)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.orders()).isEqualTo(3);
                    assertThat(d.revenue()).isEqualByComparingTo("80.00");
                    assertThat(d.site().orders()).isEqualTo(1);
                    assertThat(d.site().revenue()).isEqualByComparingTo("40.00");
                    assertThat(d.local().orders()).isEqualTo(2);
                    assertThat(d.local().revenue()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void getStats_semVendaLocal_recorteLocalZerado() {
        paidOrdersReturn(paidOrder(LocalDateTime.of(2026, 10, 10, 15, 0), "100.00"));

        AdminStatsOutput.OrderStats orders = service("2026-10-26T21:00:00Z").getStats().orders();

        assertThat(orders.local().paidThisMonth()).isZero();
        assertThat(orders.local().revenueThisMonth()).isEqualByComparingTo("0");
        assertThat(orders.local().averageTicketThisMonth()).isEqualByComparingTo("0");
    }

    private Order paidOrder(LocalDateTime paidAtUtc, String total) {
        Order order = new Order();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(paidAtUtc);
        order.setTotal(new BigDecimal(total));
        return order;
    }

    @Test
    void getStats_montaContadoresDeProdutosEPedidosAPartirDosRepositories() {
        when(productRepository.countByActive(true)).thenReturn(631L);
        when(productRepository.countByActive(false)).thenReturn(12L);
        when(productRepository.countActiveWithoutImage()).thenReturn(40L);
        when(variantRepository.countOfActiveProducts()).thenReturn(1980L);
        when(variantRepository.countOutOfStockOfActiveProducts()).thenReturn(310L);
        when(variantRepository.countWithoutGtinOfActiveProducts()).thenReturn(95L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(2L);
        when(orderRepository.countByBlingSyncStatus(BlingSyncStatus.FAILED)).thenReturn(1L);
        paidOrdersReturn();

        AdminStatsOutput stats = service("2026-10-26T21:00:00Z").getStats();

        assertThat(stats.products()).isEqualTo(new AdminStatsOutput.ProductStats(631, 12, 40, 1980, 310, 95));
        assertThat(stats.orders().pending()).isEqualTo(2);
        assertThat(stats.orders().blingFailed()).isEqualTo(1);
    }

    @Test
    void getStats_consultaPedidosPagosDesdeOInicioDaJanelaEmHorarioDeBrasilia() {
        paidOrdersReturn();

        // 26/10 18h em Brasília: série começa 27/09, antes do início do mês (01/10)
        service("2026-10-26T21:00:00Z").getStats();

        verify(orderRepository).findByStatusInAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
                LocalDateTime.of(2026, 9, 27, 3, 0),
                LocalDateTime.of(2026, 10, 27, 3, 0));
    }

    @Test
    void getStats_pedidoPagoAs22hDoUltimoDiaDoMesEmBrasilia_contaNoMesCorreto() {
        // 31/10 22h em Brasília = 01/11 01h UTC; agora é 31/10 23h em Brasília
        paidOrdersReturn(paidOrder(LocalDateTime.of(2026, 11, 1, 1, 0), "100.00"));

        AdminStatsOutput stats = service("2026-11-01T02:00:00Z").getStats();

        assertThat(stats.orders().paidThisMonth()).isEqualTo(1);
        assertThat(stats.orders().revenueThisMonth()).isEqualByComparingTo("100.00");
        AdminStatsOutput.DailySales last = stats.dailySales().get(stats.dailySales().size() - 1);
        assertThat(last.date()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(last.orders()).isEqualTo(1);
    }

    @Test
    void getStats_pedidoDoMesAnterior_entraNaSerieDiariaMasNaoNoFaturamentoDoMes() {
        // 30/09 15h em Brasília; agora é 05/10
        paidOrdersReturn(
                paidOrder(LocalDateTime.of(2026, 9, 30, 18, 0), "80.00"),
                paidOrder(LocalDateTime.of(2026, 10, 3, 18, 0), "50.00"));

        AdminStatsOutput stats = service("2026-10-05T15:00:00Z").getStats();

        assertThat(stats.orders().paidThisMonth()).isEqualTo(1);
        assertThat(stats.orders().revenueThisMonth()).isEqualByComparingTo("50.00");
        assertThat(stats.dailySales())
                .filteredOn(d -> d.date().equals(LocalDate.of(2026, 9, 30)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.orders()).isEqualTo(1);
                    assertThat(d.revenue()).isEqualByComparingTo("80.00");
                });
    }

    @Test
    void getStats_semPedidosPagos_ticketMedioZeroSemErro() {
        paidOrdersReturn();

        AdminStatsOutput stats = service("2026-10-26T21:00:00Z").getStats();

        assertThat(stats.orders().paidThisMonth()).isZero();
        assertThat(stats.orders().revenueThisMonth()).isEqualByComparingTo("0");
        assertThat(stats.orders().averageTicketThisMonth()).isEqualByComparingTo("0");
    }

    @Test
    void getStats_serieDiaria_sempreTem30DiasOrdenadosComDiasVaziosZerados() {
        paidOrdersReturn(paidOrder(LocalDateTime.of(2026, 10, 20, 15, 0), "30.00"));

        AdminStatsOutput stats = service("2026-10-26T21:00:00Z").getStats();

        List<AdminStatsOutput.DailySales> series = stats.dailySales();
        assertThat(series).hasSize(30);
        assertThat(series.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(series.get(29).date()).isEqualTo(LocalDate.of(2026, 10, 26));
        for (int i = 1; i < series.size(); i++) {
            assertThat(series.get(i).date()).isEqualTo(series.get(i - 1).date().plusDays(1));
        }
        assertThat(series).filteredOn(d -> d.orders() == 0).hasSize(29)
                .allSatisfy(d -> assertThat(d.revenue()).isEqualByComparingTo("0"));
    }

    @Test
    void getStats_ticketMedio_arredondaHalfUpEmDuasCasas() {
        paidOrdersReturn(
                paidOrder(LocalDateTime.of(2026, 10, 10, 15, 0), "10.00"),
                paidOrder(LocalDateTime.of(2026, 10, 11, 15, 0), "10.00"),
                paidOrder(LocalDateTime.of(2026, 10, 12, 15, 0), "10.01"));

        AdminStatsOutput stats = service("2026-10-26T21:00:00Z").getStats();

        // 30.01 / 3 = 10.0033...
        assertThat(stats.orders().averageTicketThisMonth()).isEqualTo(new BigDecimal("10.00"));
        assertThat(stats.orders().revenueThisMonth()).isEqualTo(new BigDecimal("30.01"));
    }
}
