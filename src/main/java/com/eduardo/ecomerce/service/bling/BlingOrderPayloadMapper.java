package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import com.eduardo.ecomerce.infra.validation.DocumentNumbers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Função pura Order -> JSON da API v3 do Bling (contato e pedido de venda). Sem I/O.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlingOrderPayloadMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int NAO_CONTRIBUINTE_ICMS = 9;
    private static final int FRETE_POR_CONTA_REMETENTE = 0;
    /** paidAt é gravado na zona do clock (UTC no container); a data do pedido no Bling é a da loja. */
    static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final BlingOrderProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ---------- Contato ----------

    public ObjectNode toContact(Order order) {
        ObjectNode contact = objectMapper.createObjectNode();
        String document = DocumentNumbers.onlyDigits(order.getCustomerDocument());

        contact.put("nome", buyerName(order));
        contact.put("tipo", document != null && document.length() == 14 ? "J" : "F");
        contact.put("situacao", "A");
        if (document != null) {
            contact.put("numeroDocumento", document);
        }
        contact.put("indicadorIe", NAO_CONTRIBUINTE_ICMS);
        if (order.getUser() != null && order.getUser().getEmail() != null) {
            contact.put("email", order.getUser().getEmail());
        }

        ObjectNode geral = contact.putObject("endereco").putObject("geral");
        putText(geral, "endereco", order.getRecipientStreet());
        putText(geral, "numero", order.getRecipientNumber());
        putText(geral, "complemento", order.getRecipientComplement());
        putText(geral, "bairro", order.getRecipientNeighborhood());
        putText(geral, "cep", DocumentNumbers.onlyDigits(order.getRecipientCep()));
        putText(geral, "municipio", order.getRecipientCity());
        putText(geral, "uf", order.getRecipientState());
        return contact;
    }

    // ---------- Pedido de venda ----------

    public ObjectNode toSaleOrder(Order order, Long contactId) {
        if (order.getPaidAt() == null) {
            throw new IllegalStateException("Pedido " + order.getId() + " sem paidAt: não pode ser enviado ao Bling");
        }
        LocalDate date = storeDate(order);
        LocalDate expected = order.getShippingDeadlineDays() != null
                ? date.plusDays(order.getShippingDeadlineDays())
                : date;
        BigDecimal shipping = order.getShippingPrice() != null ? order.getShippingPrice() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;

        ObjectNode sale = objectMapper.createObjectNode();
        sale.put("numeroLoja", order.getId().toString());
        sale.put("data", date.format(DATE));
        sale.put("dataSaida", date.format(DATE));
        sale.put("dataPrevista", expected.format(DATE));
        sale.putObject("loja").put("id", properties.storeId());
        sale.putObject("situacao").put("id",
                order.isLocalSale() ? properties.localSituationId() : properties.situationId());
        sale.putObject("contato").put("id", contactId);

        ArrayNode items = sale.putArray("itens");
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderItem item : order.getItems() == null ? List.<OrderItem>of() : order.getItems()) {
            items.add(toItem(order, item));
            itemsTotal = itemsTotal.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (discount.signum() > 0) {
            ObjectNode discountNode = sale.putObject("desconto");
            discountNode.put("valor", discount);
            discountNode.put("unidade", "REAL");
        }

        if (!order.isLocalSale()) {
            sale.set("transporte", toTransport(order, shipping));
        }

        ObjectNode installment = sale.putArray("parcelas").addObject();
        installment.put("dataVencimento", date.format(DATE));
        installment.put("valor", order.getTotal());
        installment.putObject("formaPagamento").put("id", properties.paymentMethodIdFor(order.getPaymentMethod()));
        installment.put("observacoes", order.isLocalSale()
                ? "Venda loja física · " + localPaymentLabel(order)
                : "Mercado Pago " + order.getPaymentId());

        sale.put("observacoesInternas", internalNotes(order));

        warnIfTotalDiverges(order, itemsTotal.subtract(discount).add(shipping));
        return sale;
    }

    private ObjectNode toItem(Order order, OrderItem item) {
        ProductVariant variant = item.getVariant();
        ObjectNode node = objectMapper.createObjectNode();

        Long blingProductId = variant != null ? variant.getBlingVariationId() : null;
        if (blingProductId != null) {
            node.putObject("produto").put("id", blingProductId);
        } else {
            log.warn("Item sem blingVariationId no pedido {} (variante {}): enviado sem produto.id, "
                    + "o Bling não vai baixar estoque deste item", order.getId(), variant != null ? variant.getId() : null);
        }

        String sku = firstNonBlank(item.getSku(), variant != null ? variant.getSku() : null);
        if (sku != null) {
            node.put("codigo", sku);
        }
        node.put("descricao", description(item));
        node.put("quantidade", item.getQuantity());
        node.put("valor", item.getUnitPrice());
        node.put("unidade", "UN");
        return node;
    }

    private ObjectNode toTransport(Order order, BigDecimal shipping) {
        ObjectNode transport = objectMapper.createObjectNode();
        transport.put("fretePorConta", FRETE_POR_CONTA_REMETENTE);
        transport.put("frete", shipping);
        if (order.getShippingDeadlineDays() != null) {
            transport.put("prazoEntrega", order.getShippingDeadlineDays());
        }
        if (order.getShippingMethod() != null) {
            transport.putArray("volumes").addObject().put("servico", order.getShippingMethod());
        }

        ObjectNode label = transport.putObject("etiqueta");
        putText(label, "nome", order.getRecipientName());
        putText(label, "endereco", order.getRecipientStreet());
        putText(label, "numero", order.getRecipientNumber());
        putText(label, "complemento", order.getRecipientComplement());
        putText(label, "municipio", order.getRecipientCity());
        putText(label, "uf", order.getRecipientState());
        putText(label, "cep", DocumentNumbers.onlyDigits(order.getRecipientCep()));
        putText(label, "bairro", order.getRecipientNeighborhood());
        return transport;
    }

    private String description(OrderItem item) {
        String name = firstNonBlank(item.getProductName(),
                item.getVariant() != null && item.getVariant().getProduct() != null
                        ? item.getVariant().getProduct().getName()
                        : null);
        List<String> parts = new ArrayList<>();
        parts.add(name);
        parts.add(item.getColor());
        parts.add(item.getSize());
        return String.join(" - ", parts.stream().filter(Objects::nonNull).filter(p -> !p.isBlank()).toList());
    }

    private String internalNotes(Order order) {
        if (order.isLocalSale()) {
            StringBuilder notes = new StringBuilder()
                    .append("Venda loja física GabiKids #").append(order.getId())
                    .append(" · Cliente: ").append(firstNonBlank(order.getCustomerName(), "não informado"))
                    .append(" · Pagamento: ").append(localPaymentLabel(order));
            return notes.toString();
        }
        StringBuilder notes = new StringBuilder()
                .append("Pedido do site GabiKids #").append(order.getId())
                .append(" · Pagamento Mercado Pago ").append(order.getPaymentId())
                .append(" (").append(order.getPaymentMethod()).append(")");
        if (order.getCouponCode() != null && !order.getCouponCode().isBlank()) {
            notes.append(" · Cupom ").append(order.getCouponCode());
        }
        return notes.toString();
    }

    private LocalDate storeDate(Order order) {
        return order.getPaidAt().atZone(clock.getZone()).withZoneSameInstant(STORE_ZONE).toLocalDate();
    }

    private static String localPaymentLabel(Order order) {
        String label = LocalPaymentMethod.labelFor(order.getPaymentMethod());
        return label != null ? label : order.getPaymentMethod();
    }

    private String buyerName(Order order) {
        User user = order.getUser();
        return firstNonBlank(user != null ? user.getName() : null, order.getRecipientName());
    }

    private void warnIfTotalDiverges(Order order, BigDecimal computed) {
        if (order.getTotal() == null || computed.compareTo(order.getTotal()) != 0) {
            log.warn("Total divergente no payload do Bling — pedido {}: order.total={}, calculado={} "
                    + "(itens - desconto + frete)", order.getId(), order.getTotal(), computed);
        }
    }

    /** Não escreve chave com valor nulo/vazio (evita "campo": null no payload). */
    private static void putText(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
