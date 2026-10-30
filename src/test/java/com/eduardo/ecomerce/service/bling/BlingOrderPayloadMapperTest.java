package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import com.eduardo.ecomerce.domain.order.OrderChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class BlingOrderPayloadMapperTest {

    private static final Long CONTACT_ID = 555L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 19, 15, 30);

    private BlingOrderPayloadMapper mapper;
    private Order order;
    private OrderItem shortItem;
    private OrderItem bodyItem;

    @BeforeEach
    void setUp() {
        mapper = new BlingOrderPayloadMapper(properties(), new ObjectMapper(), Clock.systemUTC());

        User user = new User();
        user.setName("Maria Compradora");
        user.setEmail("maria@teste.com");

        order = new Order();
        order.setId(UUID.fromString("c697c4c5-9e7b-49f8-a9b7-8a3f244d6726"));
        order.setUser(user);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(PAID_AT);
        order.setPaymentId("1234567890");
        order.setPaymentMethod("pix");
        order.setCustomerDocument("52998224725");
        order.setShippingMethod("PAC");
        order.setShippingPrice(new BigDecimal("18.50"));
        order.setShippingDeadlineDays(8);
        order.setCouponCode("PROMO10");
        order.setDiscountAmount(new BigDecimal("9.98"));
        order.setRecipientName("Joana Destinatária");
        order.setRecipientCep("15000-000");
        order.setRecipientStreet("Rua das Flores");
        order.setRecipientNumber("100");
        order.setRecipientComplement("Apto 12");
        order.setRecipientNeighborhood("Centro");
        order.setRecipientCity("São José do Rio Preto");
        order.setRecipientState("SP");

        shortItem = item(16702195792L, "SKU-1", "Short Moletinho", 2, "49.90",
                "Short Moletinho Brandili", "Azul", "4", "SKU-1");
        bodyItem = item(222L, "SKU-2", "Body", 1, "29.90",
                "Body Manga Curta", null, "P", "SKU-2");
        order.setItems(new ArrayList<>(List.of(shortItem, bodyItem)));
        // 2 x 49.90 + 29.90 - 9.98 + 18.50
        order.setTotal(new BigDecimal("138.22"));
    }

    private OrderItem item(Long blingVariationId, String variantSku, String productName, int qty, String unitPrice,
                           String snapshotName, String color, String size, String snapshotSku) {
        Product product = new Product();
        product.setName(productName);
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setBlingVariationId(blingVariationId);
        variant.setSku(variantSku);

        OrderItem item = new OrderItem();
        item.setVariant(variant);
        item.setQuantity(qty);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setProductName(snapshotName);
        item.setColor(color);
        item.setSize(size);
        item.setSku(snapshotSku);
        return item;
    }

    private static BlingOrderProperties properties() {
        return new BlingOrderProperties(
                true, 206333320L, 15L,
                new BlingOrderProperties.PaymentMethods(9042991L, 11412042L),
                5, Duration.ofMinutes(15),
                new BlingOrderProperties.Local(777L, 9L, new BlingOrderProperties.LocalPaymentMethods(
                        1001L, 1002L, 1003L, 1004L, 1005L)));
    }

    private void asLocalSale(String paymentMethod, String customerName) {
        order.setChannel(OrderChannel.LOCAL);
        order.setStatus(OrderStatus.DELIVERED);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentId(null);
        order.setCustomerDocument(null);
        order.setCustomerName(customerName);
        order.setCouponCode(null);
        order.setShippingMethod(null);
        order.setShippingPrice(null);
        order.setShippingDeadlineDays(null);
        order.setRecipientName(null);
        order.setRecipientCep(null);
        order.setRecipientStreet(null);
        order.setRecipientNumber(null);
        order.setRecipientComplement(null);
        order.setRecipientNeighborhood(null);
        order.setRecipientCity(null);
        order.setRecipientState(null);
        // 2 x 49.90 + 29.90 - 9.98
        order.setTotal(new BigDecimal("119.72"));
    }

    // ================= venda local =================

    @Test
    void vendaLocal_situacaoAtendidoMesmaLojaEContatoInformado() {
        asLocalSale("store_credit", "Cris vizinha");

        JsonNode sale = sale();

        assertThat(sale.path("situacao").path("id").asLong()).isEqualTo(9L);
        assertThat(sale.path("loja").path("id").asLong()).isEqualTo(206333320L);
        assertThat(sale.path("numeroLoja").asText()).isEqualTo(order.getId().toString());
        assertThat(sale.path("contato").path("id").asLong()).isEqualTo(CONTACT_ID);
    }

    @Test
    void vendaLocal_semTransporteEDataPrevistaIgualAData() {
        asLocalSale("cash", null);

        JsonNode sale = sale();

        assertThat(sale.has("transporte")).isFalse();
        assertThat(sale.path("dataPrevista").asText()).isEqualTo(sale.path("data").asText());
    }

    @Test
    void vendaLocal_parcelaComFormaDoBalcaoEValorTotal() {
        asLocalSale("store_credit", "Cris vizinha");

        JsonNode installment = sale().path("parcelas").get(0);

        assertThat(installment.path("formaPagamento").path("id").asLong()).isEqualTo(1005L);
        assertThat(installment.path("valor").decimalValue()).isEqualByComparingTo("119.72");
        assertThat(installment.path("observacoes").asText()).isEqualTo("Venda loja física · Fiado");
    }

    @Test
    void vendaLocal_descontoEmReais() {
        asLocalSale("card_machine", null);

        JsonNode discount = sale().path("desconto");

        assertThat(discount.path("valor").decimalValue()).isEqualByComparingTo("9.98");
        assertThat(discount.path("unidade").asText()).isEqualTo("REAL");
    }

    @Test
    void vendaLocal_observacoesComNomeEPagamentoSemMercadoPago() {
        asLocalSale("local_pix", "Cris vizinha");

        String notes = sale().path("observacoesInternas").asText();

        assertThat(notes).contains("Venda loja física", "Cliente: Cris vizinha", "Pagamento: Pix");
        assertThat(notes).doesNotContain("Mercado Pago");
    }

    @Test
    void vendaLocal_anonima_observacaoClienteNaoInformado() {
        asLocalSale("cash", null);

        assertThat(sale().path("observacoesInternas").asText()).contains("Cliente: não informado");
    }

    @Test
    void vendaLocal_totalBate_semWarnDeDivergencia(CapturedOutput output) {
        asLocalSale("cash", null);

        sale();

        assertThat(output).doesNotContain("Total divergente");
    }

    // ================= data na zona da loja =================

    @Test
    void data_pagoAsNoveEMeiaDaNoiteEmBrasilia_usaODiaDaLojaNaoODiaUtc() {
        // 01:30 UTC de 31/10 = 22:30 de 30/10 em São Paulo
        order.setPaidAt(LocalDateTime.of(2026, 10, 31, 1, 30));

        JsonNode sale = sale();

        assertThat(sale.path("data").asText()).isEqualTo("2026-10-30");
        assertThat(sale.path("dataSaida").asText()).isEqualTo("2026-10-30");
        assertThat(sale.path("parcelas").get(0).path("dataVencimento").asText()).isEqualTo("2026-10-30");
    }

    @Test
    void data_clockJaNaZonaDaLoja_naoConverteDuasVezes() {
        mapper = new BlingOrderPayloadMapper(properties(), new ObjectMapper(), Clock.system(ZoneId.of("America/Sao_Paulo")));
        order.setPaidAt(LocalDateTime.of(2026, 10, 30, 23, 50));

        assertThat(sale().path("data").asText()).isEqualTo("2026-10-30");
    }

    private JsonNode sale() {
        return mapper.toSaleOrder(order, CONTACT_ID);
    }

    // ================= toSaleOrder =================

    @Test
    void toSaleOrder_identificacao_numeroLojaLojaSituacaoEContato() {
        JsonNode sale = sale();

        assertThat(sale.path("numeroLoja").asText()).isEqualTo("c697c4c5-9e7b-49f8-a9b7-8a3f244d6726");
        assertThat(sale.path("loja").path("id").asLong()).isEqualTo(206333320L);
        assertThat(sale.path("situacao").path("id").asLong()).isEqualTo(15L);
        assertThat(sale.path("contato").path("id").asLong()).isEqualTo(CONTACT_ID);
    }

    @Test
    void toSaleOrder_datas_doDiaDoPagamentoMaisPrazo() {
        JsonNode sale = sale();

        assertThat(sale.path("data").asText()).isEqualTo("2026-10-19");
        assertThat(sale.path("dataSaida").asText()).isEqualTo("2026-10-19");
        assertThat(sale.path("dataPrevista").asText()).isEqualTo("2026-10-27");
    }

    @Test
    void toSaleOrder_prazoNulo_dataPrevistaIgualAData() {
        order.setShippingDeadlineDays(null);

        JsonNode sale = sale();

        assertThat(sale.path("dataPrevista").asText()).isEqualTo("2026-10-19");
        assertThat(sale.path("transporte").has("prazoEntrega")).isFalse();
    }

    @Test
    void toSaleOrder_semPaidAt_lancaIllegalState() {
        order.setPaidAt(null);

        assertThatThrownBy(this::sale)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(order.getId().toString());
    }

    @Test
    void toSaleOrder_itens_mapeiaProdutoCodigoDescricaoQuantidadeValor() {
        JsonNode first = sale().path("itens").get(0);

        assertThat(first.path("produto").path("id").asLong()).isEqualTo(16702195792L);
        assertThat(first.path("codigo").asText()).isEqualTo("SKU-1");
        assertThat(first.path("descricao").asText()).isEqualTo("Short Moletinho Brandili - Azul - 4");
        assertThat(first.path("quantidade").asInt()).isEqualTo(2);
        assertThat(first.path("valor").decimalValue()).isEqualTo(new BigDecimal("49.90"));
        assertThat(first.path("unidade").asText()).isEqualTo("UN");
        assertThat(sale().path("itens")).hasSize(2);
    }

    @Test
    void toSaleOrder_descricaoSemCor_omiteParteNula() {
        JsonNode second = sale().path("itens").get(1);

        assertThat(second.path("descricao").asText()).isEqualTo("Body Manga Curta - P");
    }

    @Test
    void toSaleOrder_itemSemSnapshot_usaNomeDoProdutoDaVariante() {
        bodyItem.setProductName(null);
        bodyItem.setSize(null);

        JsonNode second = sale().path("itens").get(1);

        assertThat(second.path("descricao").asText()).isEqualTo("Body");
    }

    @Test
    void toSaleOrder_skuSnapshotVazio_usaSkuDaVariante() {
        bodyItem.setSku(" ");
        bodyItem.getVariant().setSku("SKU-VARIANTE");

        assertThat(sale().path("itens").get(1).path("codigo").asText()).isEqualTo("SKU-VARIANTE");
    }

    @Test
    void toSaleOrder_itemSemBlingVariationId_semProdutoEComWarn(CapturedOutput output) {
        bodyItem.getVariant().setBlingVariationId(null);

        JsonNode second = sale().path("itens").get(1);

        assertThat(second.has("produto")).isFalse();
        assertThat(second.path("codigo").asText()).isEqualTo("SKU-2");
        assertThat(output).contains("Item sem blingVariationId").contains(order.getId().toString());
    }

    @Test
    void toSaleOrder_semDesconto_naoEnviaDesconto() {
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setCouponCode(null);
        order.setTotal(new BigDecimal("148.20"));

        assertThat(sale().has("desconto")).isFalse();
    }

    @Test
    void toSaleOrder_comDesconto_enviaValorEmReal() {
        JsonNode discount = sale().path("desconto");

        assertThat(discount.path("valor").decimalValue()).isEqualTo(new BigDecimal("9.98"));
        assertThat(discount.path("unidade").asText()).isEqualTo("REAL");
    }

    @Test
    void toSaleOrder_transporte_freteServicoPrazoEEtiqueta() {
        JsonNode transport = sale().path("transporte");

        assertThat(transport.path("fretePorConta").asInt()).isZero();
        assertThat(transport.path("frete").decimalValue()).isEqualTo(new BigDecimal("18.50"));
        assertThat(transport.path("prazoEntrega").asInt()).isEqualTo(8);
        assertThat(transport.path("volumes").get(0).path("servico").asText()).isEqualTo("PAC");

        JsonNode label = transport.path("etiqueta");
        assertThat(label.path("nome").asText()).isEqualTo("Joana Destinatária");
        assertThat(label.path("endereco").asText()).isEqualTo("Rua das Flores");
        assertThat(label.path("numero").asText()).isEqualTo("100");
        assertThat(label.path("complemento").asText()).isEqualTo("Apto 12");
        assertThat(label.path("bairro").asText()).isEqualTo("Centro");
        assertThat(label.path("municipio").asText()).isEqualTo("São José do Rio Preto");
        assertThat(label.path("uf").asText()).isEqualTo("SP");
        assertThat(label.path("cep").asText()).isEqualTo("15000000");
    }

    @Test
    void toSaleOrder_freteNuloEMetodoNulo_freteZeroSemVolumes() {
        order.setShippingPrice(null);
        order.setShippingMethod(null);
        order.setTotal(new BigDecimal("119.72"));

        JsonNode transport = sale().path("transporte");

        assertThat(transport.path("frete").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transport.has("volumes")).isFalse();
    }

    @Test
    void toSaleOrder_complementoNulo_naoEnviaCampoNulo() {
        order.setRecipientComplement(null);

        JsonNode label = sale().path("transporte").path("etiqueta");

        assertThat(label.has("complemento")).isFalse();
    }

    @Test
    void toSaleOrder_parcelaPix_totalEFormaPix() {
        JsonNode installment = sale().path("parcelas").get(0);

        assertThat(sale().path("parcelas")).hasSize(1);
        assertThat(installment.path("dataVencimento").asText()).isEqualTo("2026-10-19");
        assertThat(installment.path("valor").decimalValue()).isEqualTo(new BigDecimal("138.22"));
        assertThat(installment.path("formaPagamento").path("id").asLong()).isEqualTo(9042991L);
        assertThat(installment.path("observacoes").asText()).isEqualTo("Mercado Pago 1234567890");
    }

    @Test
    void toSaleOrder_parcelaCartao_formaCartao() {
        order.setPaymentMethod("credit_card");

        assertThat(sale().path("parcelas").get(0).path("formaPagamento").path("id").asLong())
                .isEqualTo(11412042L);
    }

    @Test
    void toSaleOrder_meioDePagamentoDesconhecido_lancaExcecao() {
        order.setPaymentMethod(null);

        assertThatThrownBy(this::sale).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toSaleOrder_observacoesInternas_comCupom() {
        assertThat(sale().path("observacoesInternas").asText()).isEqualTo(
                "Pedido do site GabiKids #c697c4c5-9e7b-49f8-a9b7-8a3f244d6726"
                        + " · Pagamento Mercado Pago 1234567890 (pix) · Cupom PROMO10");
    }

    @Test
    void toSaleOrder_observacoesInternas_semCupom() {
        order.setCouponCode(null);

        assertThat(sale().path("observacoesInternas").asText()).doesNotContain("Cupom");
    }

    @Test
    void toSaleOrder_totalCoerente_semWarn(CapturedOutput output) {
        sale();

        assertThat(output).doesNotContain("Total divergente");
    }

    @Test
    void toSaleOrder_totalDivergente_warnComOsDoisValoresESemExcecao(CapturedOutput output) {
        order.setTotal(new BigDecimal("150.00"));

        JsonNode sale = sale();

        assertThat(sale.path("parcelas").get(0).path("valor").decimalValue()).isEqualTo(new BigDecimal("150.00"));
        assertThat(output).contains("Total divergente").contains("150.00").contains("138.22");
    }

    // ================= toContact =================

    @Test
    void toContact_cpf_pessoaFisicaComDocumento() {
        JsonNode contact = mapper.toContact(order);

        assertThat(contact.path("tipo").asText()).isEqualTo("F");
        assertThat(contact.path("numeroDocumento").asText()).isEqualTo("52998224725");
        assertThat(contact.path("situacao").asText()).isEqualTo("A");
        assertThat(contact.path("indicadorIe").asInt()).isEqualTo(9);
    }

    @Test
    void toContact_cnpj_pessoaJuridica() {
        order.setCustomerDocument("11222333000181");

        assertThat(mapper.toContact(order).path("tipo").asText()).isEqualTo("J");
    }

    @Test
    void toContact_semDocumento_semNumeroDocumentoETipoF() {
        order.setCustomerDocument(null);

        JsonNode contact = mapper.toContact(order);

        assertThat(contact.has("numeroDocumento")).isFalse();
        assertThat(contact.path("tipo").asText()).isEqualTo("F");
    }

    @Test
    void toContact_nomeDoComprador() {
        assertThat(mapper.toContact(order).path("nome").asText()).isEqualTo("Maria Compradora");
    }

    @Test
    void toContact_usuarioSemNome_usaDestinatario() {
        order.getUser().setName(" ");

        assertThat(mapper.toContact(order).path("nome").asText()).isEqualTo("Joana Destinatária");
    }

    @Test
    void toContact_emailEEnderecoCompleto() {
        JsonNode contact = mapper.toContact(order);
        JsonNode address = contact.path("endereco").path("geral");

        assertThat(contact.path("email").asText()).isEqualTo("maria@teste.com");
        assertThat(address.path("endereco").asText()).isEqualTo("Rua das Flores");
        assertThat(address.path("numero").asText()).isEqualTo("100");
        assertThat(address.path("complemento").asText()).isEqualTo("Apto 12");
        assertThat(address.path("bairro").asText()).isEqualTo("Centro");
        assertThat(address.path("cep").asText()).isEqualTo("15000000");
        assertThat(address.path("municipio").asText()).isEqualTo("São José do Rio Preto");
        assertThat(address.path("uf").asText()).isEqualTo("SP");
    }
}
