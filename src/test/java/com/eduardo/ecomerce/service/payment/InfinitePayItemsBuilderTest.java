package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfinitePayItemsBuilderTest {

    private InfinitePayItemsBuilder builder;
    private Order order;

    @BeforeEach
    void setUp() {
        builder = new InfinitePayItemsBuilder();
        order = new Order();
        order.setId(UUID.fromString("d3aae31d-4465-408c-b942-8273b9bc622b"));
    }

    @Test
    void semDesconto_convertePrecoParaCentavos() {
        addItem("Short Moletinho", "Azul", "4", 1, "49.90");
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).containsExactly(new CreateLinkRequest.Item("Short Moletinho - Azul - Tam. 4", 4990, 1));
    }

    @Test
    void quantidadeMaiorQueUm_mantemQuantidadeNoItem() {
        addItem("Body", null, "M", 3, "29.90");
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).containsExactly(new CreateLinkRequest.Item("Body - Tam. M", 2990, 3));
    }

    @Test
    void comFrete_adicionaItemDeFreteComMetodo() {
        addItem("Vestido", null, null, 1, "79.90");
        order.setShippingMethod("pac");
        order.setShippingPrice(new BigDecimal("11.63"));
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).contains(new CreateLinkRequest.Item("Frete - PAC", 1163, 1));
        assertThat(sum(items)).isEqualTo(9153);
    }

    @Test
    void freteGratis_naoAdicionaItemDeFrete() {
        addItem("Conjunto", null, null, 1, "199.90");
        order.setShippingMethod("SEDEX");
        order.setShippingPrice(BigDecimal.ZERO);
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).hasSize(1);
        assertThat(items).noneMatch(i -> i.description().startsWith("Frete"));
    }

    @Test
    void cupomEmVariosItens_somaFechaComTotalExato() {
        addItem("Short", null, "4", 1, "49.90");
        addItem("Camiseta", null, "6", 2, "29.90");
        order.setDiscountAmount(new BigDecimal("10.97"));
        order.setShippingMethod("PAC");
        order.setShippingPrice(new BigDecimal("15.40"));
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(sum(items)).isEqualTo(toCents(order.getTotal()));
        assertThat(items).noneMatch(i -> i.description().startsWith("Pedido GabiKids"));
    }

    @Test
    void cupomComCentavoQuebrado_divideLinhaSemPerderCentavo() {
        addItem("Meia", null, null, 3, "10.00");
        order.setDiscountAmount(new BigDecimal("1.00"));
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).containsExactly(
                new CreateLinkRequest.Item("Meia", 966, 1),
                new CreateLinkRequest.Item("Meia", 967, 2));
        assertThat(sum(items)).isEqualTo(2900);
    }

    @Test
    void totalDivergenteDosItens_usaItemUnicoComTotal() {
        addItem("Short", null, null, 1, "49.90");
        order.setTotal(new BigDecimal("60.00"));

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).containsExactly(new CreateLinkRequest.Item("Pedido GabiKids #d3aae31d", 6000, 1));
    }

    @Test
    void unidadeAbaixoDeUmReal_usaItemUnico() {
        addItem("Laço", null, null, 1, "1.00");
        addItem("Vestido", null, null, 1, "50.00");
        order.setDiscountAmount(new BigDecimal("5.10"));
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(items).containsExactly(new CreateLinkRequest.Item("Pedido GabiKids #d3aae31d", 4590, 1));
    }

    @Test
    void totalAbaixoDeUmReal_lancaBusinessException() {
        addItem("Laço", null, null, 1, "0.90");
        closeTotal();

        assertThatThrownBy(() -> builder.build(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("R$ 1,00");
    }

    @Test
    void descricao_usaDadosGravadosNoItemETrunca() {
        String nomeLongo = "Conjunto ".repeat(20).trim();
        addItem(nomeLongo, "Rosa", "2", 1, "89.90");
        closeTotal();

        CreateLinkRequest.Item item = builder.build(order).get(0);

        assertThat(item.description()).hasSize(InfinitePayItemsBuilder.DESCRIPTION_MAX).endsWith("...");
        assertThat(item.description()).startsWith("Conjunto Conjunto");
    }

    @ParameterizedTest
    @CsvSource({
            // qtdA, precoA, qtdB, precoB, desconto, frete
            "1, 49.90, 1, 29.90, 0.00, 0.00",
            "2, 33.33, 1, 19.99, 5.00, 12.50",
            "3, 10.00, 3, 10.00, 1.01, 0.00",
            "1, 199.90, 4, 7.77, 20.79, 23.10",
            "5, 12.34, 2, 56.78, 13.57, 9.99",
            "7, 9.99, 1, 1.50, 0.07, 18.00"
    })
    void variasCombinacoes_somaSempreIgualAoTotal(int qtdA, String precoA, int qtdB, String precoB,
                                                  String desconto, String frete) {
        addItem("Item A", null, null, qtdA, precoA);
        addItem("Item B", null, null, qtdB, precoB);
        order.setDiscountAmount(new BigDecimal(desconto));
        order.setShippingMethod("PAC");
        order.setShippingPrice(new BigDecimal(frete));
        closeTotal();

        List<CreateLinkRequest.Item> items = builder.build(order);

        assertThat(sum(items)).isEqualTo(toCents(order.getTotal()));
        assertThat(items).allMatch(i -> i.price() >= 100 && i.quantity() >= 1);
    }

    // ---------- helpers ----------

    private void addItem(String name, String color, String size, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductName(name);
        item.setColor(color);
        item.setSize(size);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        order.getItems().add(item);
    }

    private void closeTotal() {
        BigDecimal subtotal = order.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = order.getShippingPrice() == null ? BigDecimal.ZERO : order.getShippingPrice();
        order.setTotal(subtotal.subtract(order.getDiscountAmount()).add(shipping));
    }

    private static long sum(List<CreateLinkRequest.Item> items) {
        return items.stream().mapToLong(i -> (long) i.price() * i.quantity()).sum();
    }

    private static long toCents(BigDecimal value) {
        return value.movePointRight(2).longValueExact();
    }
}
