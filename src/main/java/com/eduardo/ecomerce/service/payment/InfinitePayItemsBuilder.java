package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta os itens do link da InfinitePay. Lá não existe campo de total: o valor cobrado é a soma dos itens,
 * então a soma precisa ser exatamente o total do pedido, em centavos. Quando não for possível detalhar
 * os itens sem quebrar essa regra, envia um item único com o total.
 */
@Slf4j
@Component
public class InfinitePayItemsBuilder {

    static final int MIN_UNIT_CENTS = 100;
    static final int DESCRIPTION_MAX = 100;

    public List<CreateLinkRequest.Item> build(Order order) {
        long totalCents = toCents(order.getTotal());
        if (totalCents < MIN_UNIT_CENTS) {
            throw new BusinessException("O valor mínimo para pagamento com cartão é R$ 1,00.");
        }

        List<CreateLinkRequest.Item> items = buildDetailed(order);
        if (items.isEmpty()
                || sum(items) != totalCents
                || items.stream().anyMatch(item -> item.price() < MIN_UNIT_CENTS)) {
            log.warn("Itens da InfinitePay não fecham com o total do pedido {} (total={} centavos, soma={}); enviando item único",
                    order.getId(), totalCents, sum(items));
            return List.of(summaryItem(order, totalCents));
        }
        return items;
    }

    private List<CreateLinkRequest.Item> buildDetailed(Order order) {
        List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
        if (orderItems.isEmpty()) {
            return List.of();
        }

        long[] lineCents = new long[orderItems.size()];
        long subtotalCents = 0;
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            lineCents[i] = toCents(item.getUnitPrice()) * item.getQuantity();
            subtotalCents += lineCents[i];
        }

        long discountCents = order.getDiscountAmount() == null ? 0 : toCents(order.getDiscountAmount());
        if (discountCents < 0 || discountCents > subtotalCents || subtotalCents == 0) {
            return List.of();
        }
        long[] lineDiscounts = distributeDiscount(lineCents, subtotalCents, discountCents);

        List<CreateLinkRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            int quantity = item.getQuantity();
            long finalLine = lineCents[i] - lineDiscounts[i];
            long unit = finalLine / quantity;
            int unitsWithExtraCent = (int) (finalLine % quantity);
            String description = describe(item);

            if (quantity - unitsWithExtraCent > 0) {
                items.add(new CreateLinkRequest.Item(description, Math.toIntExact(unit), quantity - unitsWithExtraCent));
            }
            if (unitsWithExtraCent > 0) {
                items.add(new CreateLinkRequest.Item(description, Math.toIntExact(unit + 1), unitsWithExtraCent));
            }
        }

        long shippingCents = order.getShippingPrice() == null ? 0 : toCents(order.getShippingPrice());
        if (shippingCents > 0) {
            items.add(new CreateLinkRequest.Item(shippingDescription(order.getShippingMethod()),
                    Math.toIntExact(shippingCents), 1));
        }
        return items;
    }

    /** Desconto proporcional ao valor de cada linha; os centavos que sobram vão um a um para as primeiras linhas. */
    private long[] distributeDiscount(long[] lineCents, long subtotalCents, long discountCents) {
        long[] discounts = new long[lineCents.length];
        long distributed = 0;
        for (int i = 0; i < lineCents.length; i++) {
            discounts[i] = discountCents * lineCents[i] / subtotalCents;
            distributed += discounts[i];
        }
        long remaining = discountCents - distributed;
        for (int i = 0; remaining > 0 && i < lineCents.length; i++) {
            if (discounts[i] < lineCents[i]) {
                discounts[i]++;
                remaining--;
            }
        }
        return discounts;
    }

    private String describe(OrderItem item) {
        ProductVariant variant = item.getVariant();
        String name = firstNonBlank(item.getProductName(),
                variant != null && variant.getProduct() != null ? variant.getProduct().getName() : null, "Produto");
        String color = firstNonBlank(item.getColor(), variant != null ? variant.getColor() : null, null);
        String size = firstNonBlank(item.getSize(), variant != null ? variant.getSize() : null, null);

        StringBuilder description = new StringBuilder(name);
        if (color != null) {
            description.append(" - ").append(color);
        }
        if (size != null) {
            description.append(" - Tam. ").append(size);
        }
        return truncate(description.toString());
    }

    private static String shippingDescription(String method) {
        return method == null || method.isBlank() ? "Frete" : "Frete - " + method.toUpperCase();
    }

    private static CreateLinkRequest.Item summaryItem(Order order, long totalCents) {
        String description = order.getId() == null
                ? "Pedido GabiKids"
                : "Pedido GabiKids #" + order.getId().toString().substring(0, 8);
        return new CreateLinkRequest.Item(description, Math.toIntExact(totalCents), 1);
    }

    private static String truncate(String text) {
        return text.length() > DESCRIPTION_MAX ? text.substring(0, DESCRIPTION_MAX - 3) + "..." : text;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private static long sum(List<CreateLinkRequest.Item> items) {
        return items.stream().mapToLong(item -> (long) item.price() * item.quantity()).sum();
    }

    private static long toCents(BigDecimal value) {
        return value == null ? 0 : value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
