package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.order.BlingSyncStatus;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput;
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
        long paidThisMonth = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (Order order : paidOrders) {
            if (!toStoreDate(order.getPaidAt()).isBefore(monthStart)) {
                paidThisMonth++;
                revenue = revenue.add(order.getTotal());
            }
        }
        BigDecimal averageTicket = paidThisMonth == 0
                ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(BigDecimal.valueOf(paidThisMonth), 2, RoundingMode.HALF_UP);

        return new OrderStats(
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByBlingSyncStatus(BlingSyncStatus.FAILED),
                paidThisMonth,
                revenue.setScale(2, RoundingMode.HALF_UP),
                averageTicket);
    }

    private List<DailySales> dailySales(List<Order> paidOrders, LocalDate seriesStart, LocalDate today) {
        Map<LocalDate, long[]> counts = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> revenues = new LinkedHashMap<>();
        for (LocalDate day = seriesStart; !day.isAfter(today); day = day.plusDays(1)) {
            counts.put(day, new long[1]);
            revenues.put(day, BigDecimal.ZERO);
        }
        for (Order order : paidOrders) {
            LocalDate day = toStoreDate(order.getPaidAt());
            if (counts.containsKey(day)) {
                counts.get(day)[0]++;
                revenues.merge(day, order.getTotal(), BigDecimal::add);
            }
        }
        List<DailySales> series = new ArrayList<>(counts.size());
        counts.forEach((day, count) -> series.add(
                new DailySales(day, count[0], revenues.get(day).setScale(2, RoundingMode.HALF_UP))));
        return series;
    }

    /** paidAt é gravado com LocalDateTime.now(clock), ou seja, na zona do clock (UTC no container). */
    private LocalDateTime toClockTime(LocalDate storeDate) {
        return storeDate.atStartOfDay(STORE_ZONE).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private LocalDate toStoreDate(LocalDateTime clockTime) {
        return clockTime.atZone(clock.getZone()).withZoneSameInstant(STORE_ZONE).toLocalDate();
    }
}
