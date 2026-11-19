package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.mercadopago.client.payment.PaymentItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoItemsBuilderTest {

    private MercadoPagoItemsBuilder builder;
    private Order order;

    @BeforeEach
    void setUp() {
        builder = new MercadoPagoItemsBuilder();
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingPrice(BigDecimal.ZERO);
        order.setShippingMethod("PAC");
    }

    @Test
    void build_semCupomComFrete_adicionaItemDeFreteESomaBateComTotal() {
        addItem("SKU-1", 1, "1.00");
        order.setShippingPrice(new BigDecimal("11.63"));
        order.setTotal(new BigDecimal("12.63"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(2);
        PaymentItemRequest frete = items.get(1);
        assertThat(frete.getId()).isEqualTo(MercadoPagoItemsBuilder.SHIPPING_ITEM_ID);
        assertThat(frete.getTitle()).isEqualTo("Frete - PAC");
        assertThat(frete.getQuantity()).isEqualTo(1);
        assertThat(frete.getUnitPrice()).isEqualByComparingTo("11.63");
        assertThat(sum(items)).isEqualByComparingTo(order.getTotal());
    }

    @Test
    void build_freteGratis_naoAdicionaItemDeFrete() {
        addItem("SKU-1", 2, "120.00");
        order.setTotal(new BigDecimal("240.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(1);
        assertThat(items).noneMatch(item -> MercadoPagoItemsBuilder.SHIPPING_ITEM_ID.equals(item.getId()));
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("120.00");
        assertThat(sum(items)).isEqualByComparingTo(order.getTotal());
    }

    @Test
    void build_cupomComVariasLinhas_distribuiDescontoSemPerderCentavo() {
        addItem("SKU-1", 1, "10.00");
        addItem("SKU-2", 1, "10.00");
        addItem("SKU-3", 1, "10.00");
        order.setDiscountAmount(new BigDecimal("1.00"));
        order.setShippingPrice(new BigDecimal("15.00"));
        order.setTotal(new BigDecimal("44.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(4);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("9.66");
        assertThat(items.get(1).getUnitPrice()).isEqualByComparingTo("9.67");
        assertThat(items.get(2).getUnitPrice()).isEqualByComparingTo("9.67");
        assertThat(sum(items)).isEqualByComparingTo(order.getTotal());
    }

    @Test
    void build_cupomComTodasLinhasQuantidadeMaiorQueUm_divideLinhaParaFecharCentavos() {
        addItem("SKU-1", 3, "10.00");
        order.setDiscountAmount(new BigDecimal("1.00"));
        order.setTotal(new BigDecimal("29.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("9.67");
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
        assertThat(items.get(1).getUnitPrice()).isEqualByComparingTo("9.66");
        assertThat(items).allMatch(item -> "SKU-1".equals(item.getId()));
        assertThat(sum(items)).isEqualByComparingTo(order.getTotal());
    }

    @Test
    void build_cupomSemSobraDeCentavos_mantemQuantidadeOriginal() {
        addItem("SKU-1", 2, "50.00");
        order.setDiscountAmount(new BigDecimal("10.00"));
        order.setTotal(new BigDecimal("90.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("45.00");
    }

    @Test
    void build_metodoDeFreteNulo_usaTituloGenerico() {
        addItem("SKU-1", 1, "20.00");
        order.setShippingMethod(null);
        order.setShippingPrice(new BigDecimal("5.00"));
        order.setTotal(new BigDecimal("25.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items.get(1).getTitle()).isEqualTo("Frete");
    }

    @Test
    void build_metodoSedex_usaTituloComMetodo() {
        addItem("SKU-1", 1, "20.00");
        order.setShippingMethod("sedex");
        order.setShippingPrice(new BigDecimal("30.00"));
        order.setTotal(new BigDecimal("50.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items.get(1).getTitle()).isEqualTo("Frete - SEDEX");
    }

    @Test
    void build_somaDivergenteDoTotal_voltaParaItensSemAjusteELogaAviso(CapturedOutput output) {
        addItem("SKU-1", 1, "20.00");
        order.setShippingPrice(new BigDecimal("5.00"));
        order.setTotal(new BigDecimal("999.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("20.00");
        assertThat(output.getOut()).contains("nao fecham com o total do pedido");
    }

    @Test
    void build_pedidoSemItens_retornaListaVazia() {
        order.setTotal(new BigDecimal("10.00"));

        assertThat(builder.build(order)).isEmpty();
    }

    @Test
    void build_skuVazio_usaIdDaVariante() {
        OrderItem item = addItem("", 1, "20.00");
        order.setTotal(new BigDecimal("20.00"));

        List<PaymentItemRequest> items = builder.build(order);

        assertThat(items.get(0).getId()).isEqualTo(item.getVariant().getId().toString());
    }

    private OrderItem addItem(String sku, int quantity, String unitPrice) {
        Product product = new Product();
        product.setName("Conjunto Infantil");

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setSku(sku);
        variant.setSize("4");
        variant.setProduct(product);

        OrderItem item = new OrderItem();
        item.setVariant(variant);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        order.getItems().add(item);
        return item;
    }

    private BigDecimal sum(List<PaymentItemRequest> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
