package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.BlingSyncStatus;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput.ChannelDay;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput.ChannelStats;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput.DailySales;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput.OrderStats;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput.ProductStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");
    static final int DAILY_SALES_DAYS = 30;
    /** Pedido que já foi pago continua contando no faturamento depois de enviado/entregue. */
    static final Set<OrderStatus> PAID_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminStatsOutput getStats() {
        LocalDate today = LocalDate.now(clock.withZone(STORE_ZONE));
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate seriesStart = today.minusDays(DAILY_SALES_DAYS - 1);
        LocalDate windowStart = monthStart.isBefore(seriesStart) ? monthStart : seriesStart;

        List<Order> paidOrders = orderRepository.findByStatusInAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                PAID_STATUSES, toClockTime(windowStart), toClockTime(today.plusDays(1)));

        return new AdminStatsOutput(productStats(), orderStats(paidOrders, monthStart),
                dailySales(paidOrders, seriesStart, today));
    }

    private ProductStats productStats() {
        return new ProductStats(
                productRepository.countByActive(true),
                productRepository.countByActive(false),
                productRepository.countActiveWithoutImage(),
                variantRepository.countOfActiveProducts(),
                variantRepository.countOutOfStockOfActiveProducts(),
                variantRepository.countWithoutGtinOfActiveProducts());
    }

    private OrderStats orderStats(List<Order> paidOrders, LocalDate monthStart) {
        Totals all = new Totals();
        Totals site = new Totals();
        Totals local = new Totals();
        for (Order order : paidOrders) {
            if (!toStoreDate(order.getPaidAt()).isBefore(monthStart)) {
                all.add(order);
                (order.isLocalSale() ? local : site).add(order);
            }
        }

        return new OrderStats(
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByBlingSyncStatus(BlingSyncStatus.FAILED),
                all.count,
                all.revenue(),
                all.averageTicket(),
                site.toChannelStats(),
                local.toChannelStats());
    }

    private List<DailySales> dailySales(List<Order> paidOrders, LocalDate seriesStart, LocalDate today) {
        Map<LocalDate, Totals[]> days = new LinkedHashMap<>();
        for (LocalDate day = seriesStart; !day.isAfter(today); day = day.plusDays(1)) {
            days.put(day, new Totals[]{new Totals(), new Totals(), new Totals()});
        }
        for (Order order : paidOrders) {
            Totals[] totals = days.get(toStoreDate(order.getPaidAt()));
            if (totals != null) {
                totals[0].add(order);
                totals[order.isLocalSale() ? 2 : 1].add(order);
            }
        }
        List<DailySales> series = new ArrayList<>(days.size());
        days.forEach((day, totals) -> series.add(new DailySales(
                day, totals[0].count, totals[0].revenue(), totals[1].toChannelDay(), totals[2].toChannelDay())));
        return series;
    }

    /** Acumulador de quantidade e faturamento de um recorte (mês ou dia, por canal). */
    private static final class Totals {
        private long count;
        private BigDecimal sum = BigDecimal.ZERO;

        void add(Order order) {
            count++;
            sum = sum.add(order.getTotal());
        }

        BigDecimal revenue() {
            return sum.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal averageTicket() {
            return count == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        ChannelStats toChannelStats() {
            return new ChannelStats(count, revenue(), averageTicket());
        }

        ChannelDay toChannelDay() {
            return new ChannelDay(count, revenue());
        }
    }

    /** paidAt é gravado com LocalDateTime.now(clock), ou seja, na zona do clock (UTC no container). */
    private LocalDateTime toClockTime(LocalDate storeDate) {
        return storeDate.atStartOfDay(STORE_ZONE).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private LocalDate toStoreDate(LocalDateTime clockTime) {
        return clockTime.atZone(clock.getZone()).withZoneSameInstant(STORE_ZONE).toLocalDate();
    }
}
