package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.mercadopago.client.payment.PaymentItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MercadoPagoItemsBuilder {

    public static final String SHIPPING_ITEM_ID = "FRETE";
    private static final String MP_ITEM_CATEGORY = "fashion";
    private static final int MP_ITEM_DESCRIPTION_MAX = 255;

    public List<PaymentItemRequest> build(Order order) {
        List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
        if (orderItems.isEmpty()) {
            return List.of();
        }
        List<PaymentItemRequest> fullPriceItems = orderItems.stream()
                .map(item -> toPaymentItem(item, item.getQuantity(), item.getUnitPrice()))
                .toList();

        List<PaymentItemRequest> items = new ArrayList<>(applyDiscount(orderItems, order.getDiscountAmount()));

        BigDecimal shipping = order.getShippingPrice();
        if (shipping != null && shipping.signum() > 0) {
            items.add(buildShippingItem(order.getShippingMethod(), shipping));
        }

        if (!isConsistent(items, order.getTotal())) {
            log.warn("Itens do Mercado Pago nao fecham com o total do pedido {} (total={}, soma={}); enviando itens sem ajuste",
                    order.getId(), order.getTotal(), sum(items));
            return fullPriceItems;
        }
        return items;
    }

    private List<PaymentItemRequest> applyDiscount(List<OrderItem> orderItems, BigDecimal discount) {
        BigDecimal subtotal = orderItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (discount == null || discount.signum() <= 0 || subtotal.signum() <= 0) {
            return orderItems.stream()
                    .map(item -> toPaymentItem(item, item.getQuantity(), item.getUnitPrice()))
                    .toList();
        }

        List<BigDecimal> discountedUnits = new ArrayList<>();
        BigDecimal discountedSum = BigDecimal.ZERO;
        for (OrderItem item : orderItems) {
            BigDecimal unitDiscount = item.getUnitPrice().multiply(discount)
                    .divide(subtotal, 2, RoundingMode.HALF_UP);
            BigDecimal unit = item.getUnitPrice().subtract(unitDiscount);
            discountedUnits.add(unit);
            discountedSum = discountedSum.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal remainder = subtotal.subtract(discount).subtract(discountedSum);
        int adjustIndex = indexForRemainder(orderItems);

        List<PaymentItemRequest> items = new ArrayList<>();
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            BigDecimal unit = discountedUnits.get(i);

            if (remainder.signum() == 0 || i != adjustIndex) {
                items.add(toPaymentItem(item, item.getQuantity(), unit));
            } else if (item.getQuantity() == 1) {
                items.add(toPaymentItem(item, 1, unit.add(remainder)));
            } else {
                items.add(toPaymentItem(item, item.getQuantity() - 1, unit));
                items.add(toPaymentItem(item, 1, unit.add(remainder)));
            }
        }
        return items;
    }

    private int indexForRemainder(List<OrderItem> orderItems) {
        for (int i = 0; i < orderItems.size(); i++) {
            if (orderItems.get(i).getQuantity() == 1) {
                return i;
            }
        }
        return orderItems.isEmpty() ? -1 : 0;
    }

    private boolean isConsistent(List<PaymentItemRequest> items, BigDecimal total) {
        if (total == null || items.isEmpty()) {
            return false;
        }
        boolean allPositive = items.stream().allMatch(item -> item.getUnitPrice().signum() > 0);
        return allPositive && sum(items).compareTo(total) == 0;
    }

    private BigDecimal sum(List<PaymentItemRequest> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PaymentItemRequest buildShippingItem(String method, BigDecimal price) {
        String title = method == null || method.isBlank() ? "Frete" : "Frete - " + method.toUpperCase();
        return PaymentItemRequest.builder()
                .id(SHIPPING_ITEM_ID)
                .title(title)
                .description(title)
                .categoryId("services")
                .quantity(1)
                .unitPrice(price)
                .build();
    }

    private PaymentItemRequest toPaymentItem(OrderItem item, int quantity, BigDecimal unitPrice) {
        ProductVariant variant = item.getVariant();
        Product product = variant.getProduct();

        String id = variant.getSku() != null && !variant.getSku().isBlank()
                ? variant.getSku()
                : variant.getId().toString();

        return PaymentItemRequest.builder()
                .id(id)
                .title(buildItemTitle(product, variant))
                .description(buildItemDescription(product, variant))
                .pictureUrl(blankToNull(product.getImageUrl()))
                .categoryId(MP_ITEM_CATEGORY)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    private String buildItemDescription(Product product, ProductVariant variant) {
        String text = product.getDescription() == null ? ""
                : Jsoup.parse(product.getDescription()).text().replaceAll("\\s+", " ").trim();

        if (text.isEmpty()) {
            text = buildItemTitle(product, variant);
        }
        return text.length() > MP_ITEM_DESCRIPTION_MAX
                ? text.substring(0, MP_ITEM_DESCRIPTION_MAX - 3) + "..."
                : text;
    }

    private String buildItemTitle(Product product, ProductVariant variant) {
        StringBuilder title = new StringBuilder(product.getName());
        if (variant.getColor() != null && !variant.getColor().isBlank()) {
            title.append(" - ").append(variant.getColor());
        }
        if (variant.getSize() != null && !variant.getSize().isBlank()) {
            title.append(" - Tam. ").append(variant.getSize());
        }
        return title.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
