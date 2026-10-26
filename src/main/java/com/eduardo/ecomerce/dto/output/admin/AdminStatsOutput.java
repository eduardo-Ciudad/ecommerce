package com.eduardo.ecomerce.dto.output.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Números agregados do dashboard do admin. Datas e "mês" seguem o horário de Brasília. */
public record AdminStatsOutput(ProductStats products, OrderStats orders, List<DailySales> dailySales) {

    public record ProductStats(long active, long inactive, long activeWithoutImage,
                               long variants, long variantsOutOfStock, long variantsWithoutGtin) {}

    public record OrderStats(long pending, long blingFailed, long paidThisMonth,
                             BigDecimal revenueThisMonth, BigDecimal averageTicketThisMonth) {}

    public record DailySales(LocalDate date, long orders, BigDecimal revenue) {}
}
