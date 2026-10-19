package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.infra.bling.BlingClient;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import com.eduardo.ecomerce.service.BlingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BlingOrderSyncServiceTest {

    private static final String TOKEN = "token";
    private static final Long STORE_ID = 206333320L;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 16, 0);
    private static final Long CONTACT_ID = 555L;
    private static final Long SALE_ORDER_ID = 999L;

    @Mock private OrderRepository orderRepository;
    @Mock private BlingClient blingClient;
    @Mock private BlingService blingService;
    @Mock private BlingOrderPayloadMapper mapper;

    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private BlingOrderSyncService service;
    private UUID orderId;
    private Order order;
    private ObjectNode salePayload;
    private ObjectNode contactPayload;

    @BeforeEach
    void setUp() {
        service = newService(true);

        orderId = UUID.randomUUID();
        order = new Order();
        order.setId(orderId);
        order.setCustomerDocument("52998224725");

        salePayload = json.createObjectNode().put("numeroLoja", orderId.toString());
        contactPayload = json.createObjectNode().put("nome", "Maria");

        // callBling executa a função com um token fixo (o refresh em 401 é testado no BlingServiceTest)
        lenient().when(blingService.callBling(any())).thenAnswer(invocation -> {
            Function<String, JsonNode> call = invocation.getArgument(0);
            return call.apply(TOKEN);
        });
        lenient().when(orderRepository.claimForBlingSync(eq(orderId), any(), any())).thenReturn(1);
        lenient().when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));
        lenient().when(mapper.toSaleOrder(order, CONTACT_ID)).thenReturn(salePayload);
        lenient().when(mapper.toContact(order)).thenReturn(contactPayload);
    }

    private BlingOrderSyncService newService(boolean enabled) {
        BlingOrderProperties properties = new BlingOrderProperties(
                enabled, STORE_ID, 15L,
                new BlingOrderProperties.PaymentMethods(9042991L, 11412042L),
                5, Duration.ofMinutes(15));
        return new BlingOrderSyncService(orderRepository, blingClient, blingService, mapper, properties, clock);
    }

    private JsonNode node(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void noExistingSaleOrder() {
        when(blingClient.findSaleOrdersByStoreNumber(TOKEN, STORE_ID, orderId.toString()))
                .thenReturn(node("{\"data\":[]}"));
    }

    private void existingContact() {
        when(blingClient.findContactsByDocument(TOKEN, "52998224725"))
                .thenReturn(node("{\"data\":[{\"id\":" + CONTACT_ID + "}]}"));
    }

    private void saleOrderCreated() {
        when(blingClient.createSaleOrder(TOKEN, salePayload))
                .thenReturn(node("{\"data\":{\"id\":" + SALE_ORDER_ID + ",\"alertas\":[]}}"));
    }

    // ---------- guardas ----------

    @Test
    void sync_desabilitado_retornaDisabledSemTocarNoBanco() {
        BlingSyncOutcome outcome = newService(false).sync(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.DISABLED);
        verifyNoInteractions(orderRepository, blingClient, blingService);
    }

    @Test
    void sync_claimNaoPegou_retornaSkippedSemChamarBling() {
        when(orderRepository.claimForBlingSync(eq(orderId), any(), any())).thenReturn(0);

        BlingSyncOutcome outcome = service.sync(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.SKIPPED);
        verifyNoInteractions(blingClient, blingService);
        verify(orderRepository, never()).findWithItemsById(any());
    }

    @Test
    void sync_claimUsaAgoraEAgoraMenosStaleAfter() {
        when(orderRepository.claimForBlingSync(eq(orderId), any(), any())).thenReturn(0);

        service.sync(orderId);

        verify(orderRepository).claimForBlingSync(orderId, NOW, NOW.minusMinutes(15));
    }

    // ---------- caminho feliz ----------

    @Test
    void sync_contatoExistente_criaPedidoGravaIdLancaEstoqueEMarcaSent() {
        noExistingSaleOrder();
        existingContact();
        saleOrderCreated();

        BlingSyncOutcome outcome = service.sync(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.SENT);
        InOrder inOrder = inOrder(blingClient, orderRepository);
        inOrder.verify(blingClient).findSaleOrdersByStoreNumber(TOKEN, STORE_ID, orderId.toString());
        inOrder.verify(blingClient).findContactsByDocument(TOKEN, "52998224725");
        inOrder.verify(blingClient).createSaleOrder(TOKEN, salePayload);
        inOrder.verify(orderRepository).saveBlingOrderId(orderId, SALE_ORDER_ID);
        inOrder.verify(blingClient).launchSaleOrderStock(TOKEN, SALE_ORDER_ID);
        inOrder.verify(orderRepository).markBlingSent(orderId, NOW);
        verify(blingClient, never()).createContact(anyString(), any());
        verify(orderRepository, never()).markBlingFailed(any(), any());
    }

    @Test
    void sync_contatoNaoEncontrado_criaContatoEUsaIdRetornado() {
        noExistingSaleOrder();
        when(blingClient.findContactsByDocument(TOKEN, "52998224725")).thenReturn(node("{\"data\":[]}"));
        when(blingClient.createContact(TOKEN, contactPayload))
                .thenReturn(node("{\"data\":{\"id\":" + CONTACT_ID + "}}"));
        saleOrderCreated();

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.SENT);
        verify(blingClient).createContact(TOKEN, contactPayload);
        verify(mapper).toSaleOrder(order, CONTACT_ID);
    }

    @Test
    void sync_pedidoSemDocumento_criaContatoSemBuscar() {
        order.setCustomerDocument(null);
        noExistingSaleOrder();
        when(blingClient.createContact(TOKEN, contactPayload))
                .thenReturn(node("{\"data\":{\"id\":" + CONTACT_ID + "}}"));
        saleOrderCreated();

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.SENT);
        verify(blingClient, never()).findContactsByDocument(anyString(), anyString());
    }

    @Test
    void sync_pedidoJaExisteNoBling_reaproveitaSemCriar() {
        when(blingClient.findSaleOrdersByStoreNumber(TOKEN, STORE_ID, orderId.toString()))
                .thenReturn(node("{\"data\":[{\"id\":" + SALE_ORDER_ID + "}]}"));

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.SENT);
        verify(orderRepository).saveBlingOrderId(orderId, SALE_ORDER_ID);
        verify(blingClient, never()).createSaleOrder(anyString(), any());
        verify(blingClient, never()).createContact(anyString(), any());
        verify(blingClient).launchSaleOrderStock(TOKEN, SALE_ORDER_ID);
        verify(orderRepository).markBlingSent(orderId, NOW);
    }

    @Test
    void sync_pedidoJaTemBlingOrderId_soLancaEstoque() {
        order.setBlingOrderId(SALE_ORDER_ID);

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.SENT);
        verify(blingClient, never()).findSaleOrdersByStoreNumber(anyString(), anyLong(), anyString());
        verify(blingClient, never()).createSaleOrder(anyString(), any());
        verify(orderRepository, never()).saveBlingOrderId(any(), any());
        verify(blingClient).launchSaleOrderStock(TOKEN, SALE_ORDER_ID);
        verify(orderRepository).markBlingSent(orderId, NOW);
    }

    @Test
    void sync_respostaComAlertas_sentComWarn(CapturedOutput output) {
        noExistingSaleOrder();
        existingContact();
        when(blingClient.createSaleOrder(TOKEN, salePayload)).thenReturn(node(
                "{\"data\":{\"id\":" + SALE_ORDER_ID + ",\"alertas\":[{\"msg\":\"Estoque insuficiente\"}]}}"));

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.SENT);
        assertThat(output).contains("com alertas").contains("Estoque insuficiente");
    }

    @Test
    void sync_todasAsChamadasPassamPeloCallBling() {
        noExistingSaleOrder();
        existingContact();
        saleOrderCreated();

        service.sync(orderId);

        // busca pedido, busca contato, cria pedido, lança estoque
        verify(blingService, times(4)).callBling(any());
    }

    // ---------- falhas ----------

    @Test
    void sync_createSaleOrder400_failedComMensagemSemGravarIdNemLancarEstoque() {
        noExistingSaleOrder();
        existingContact();
        when(blingClient.createSaleOrder(TOKEN, salePayload)).thenThrow(new BlingIntegrationException(
                "Falha ao consultar a API do Bling (HTTP 400): itens[0].produto.id: Produto não encontrado",
                null, 400, "VALIDATION_ERROR", "x"));

        BlingSyncOutcome outcome = service.sync(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.FAILED);
        verify(orderRepository).markBlingFailed(orderId,
                "Falha ao consultar a API do Bling (HTTP 400): itens[0].produto.id: Produto não encontrado");
        verify(orderRepository, never()).saveBlingOrderId(any(), any());
        verify(blingClient, never()).launchSaleOrderStock(anyString(), anyLong());
        verify(orderRepository, never()).markBlingSent(any(), any());
    }

    @Test
    void sync_falhaNoLancamentoDeEstoque_failedMasIdJaGravado() {
        noExistingSaleOrder();
        existingContact();
        saleOrderCreated();
        when(blingClient.launchSaleOrderStock(TOKEN, SALE_ORDER_ID))
                .thenThrow(new BlingIntegrationException("HTTP 500", null, 500, null, null));

        BlingSyncOutcome outcome = service.sync(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.FAILED);
        InOrder inOrder = inOrder(orderRepository);
        inOrder.verify(orderRepository).saveBlingOrderId(orderId, SALE_ORDER_ID);
        inOrder.verify(orderRepository).markBlingFailed(orderId, "HTTP 500");
        verify(orderRepository, never()).markBlingSent(any(), any());
    }

    @Test
    void sync_mapperLancaExcecao_failedComMensagem() {
        noExistingSaleOrder();
        existingContact();
        when(mapper.toSaleOrder(order, CONTACT_ID))
                .thenThrow(new IllegalArgumentException("Meio de pagamento sem forma de pagamento no Bling: null"));

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.FAILED);
        verify(orderRepository).markBlingFailed(orderId, "Meio de pagamento sem forma de pagamento no Bling: null");
        verify(blingClient, never()).createSaleOrder(anyString(), any());
    }

    @Test
    void sync_respostaSemDataId_failed() {
        noExistingSaleOrder();
        existingContact();
        when(blingClient.createSaleOrder(TOKEN, salePayload)).thenReturn(node("{\"data\":{}}"));

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.FAILED);
        verify(orderRepository).markBlingFailed(eq(orderId), contains("sem data.id"));
        verify(orderRepository, never()).saveBlingOrderId(any(), any());
    }

    @Test
    void sync_contatoCriadoSemId_failed() {
        noExistingSaleOrder();
        when(blingClient.findContactsByDocument(TOKEN, "52998224725")).thenReturn(node("{\"data\":[]}"));
        when(blingClient.createContact(TOKEN, contactPayload)).thenReturn(node("{\"data\":{}}"));

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.FAILED);
        verify(orderRepository).markBlingFailed(eq(orderId), contains("contato"));
    }

    @Test
    void sync_mensagemLonga_gravadaTruncadaEm500() {
        noExistingSaleOrder();
        existingContact();
        String longMessage = "x".repeat(800);
        when(blingClient.createSaleOrder(TOKEN, salePayload))
                .thenThrow(new BlingIntegrationException(longMessage, null));

        service.sync(orderId);

        verify(orderRepository).markBlingFailed(orderId, "x".repeat(500));
    }

    @Test
    void sync_excecaoSemMensagem_gravaNomeDaClasse() {
        noExistingSaleOrder();
        existingContact();
        when(blingClient.createSaleOrder(TOKEN, salePayload)).thenThrow(new NullPointerException());

        service.sync(orderId);

        verify(orderRepository).markBlingFailed(orderId, "NullPointerException");
    }

    @Test
    void sync_pedidoNaoEncontradoAposClaim_failed() {
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

        assertThat(service.sync(orderId)).isEqualTo(BlingSyncOutcome.FAILED);
        verify(orderRepository).markBlingFailed(eq(orderId), contains("não encontrado"));
        verifyNoInteractions(blingClient);
    }

    @Test
    void sync_nenhumaExcecaoEscapa() {
        // doThrow: when(...) executaria a resposta padrão do setUp com argumento nulo
        doThrow(new RuntimeException("Bling fora do ar")).when(blingService).callBling(any());

        assertThatCode(() -> service.sync(orderId)).doesNotThrowAnyException();
        verify(orderRepository).markBlingFailed(orderId, "Bling fora do ar");
    }

    // ---------- resend (admin) ----------

    @Test
    void resend_desabilitado_lancaBusinessExceptionSemResetar() {
        assertThatThrownBy(() -> newService(false).resend(orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desligado");
        verifyNoInteractions(orderRepository, blingClient, blingService);
    }

    @Test
    void resend_pedidoInexistente_lancaResourceNotFound() {
        when(orderRepository.existsById(orderId)).thenReturn(false);

        assertThatThrownBy(() -> service.resend(orderId)).isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).resetBlingSyncForResend(any());
        verifyNoInteractions(blingClient);
    }

    @Test
    void resend_pedidoNaoElegivel_lancaBusinessExceptionSemSync() {
        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(orderRepository.resetBlingSyncForResend(orderId)).thenReturn(0);

        assertThatThrownBy(() -> service.resend(orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode ser reenviado");
        verify(orderRepository, never()).claimForBlingSync(any(), any(), any());
        verifyNoInteractions(blingClient);
    }

    @Test
    void resend_elegivel_resetaESincronizaRetornandoResultado() {
        when(orderRepository.existsById(orderId)).thenReturn(true);
        when(orderRepository.resetBlingSyncForResend(orderId)).thenReturn(1);
        order.setBlingOrderId(SALE_ORDER_ID);

        BlingSyncOutcome outcome = service.resend(orderId);

        assertThat(outcome).isEqualTo(BlingSyncOutcome.SENT);
        InOrder inOrder = inOrder(orderRepository);
        inOrder.verify(orderRepository).resetBlingSyncForResend(orderId);
        inOrder.verify(orderRepository).claimForBlingSync(eq(orderId), any(), any());
        inOrder.verify(orderRepository).markBlingSent(orderId, NOW);
    }

    @Test
    void truncate_nuloECurtoSemAlteracao() {
        assertThat(BlingOrderSyncService.truncate(null)).isNull();
        assertThat(BlingOrderSyncService.truncate("curto")).isEqualTo("curto");
        assertThat(BlingOrderSyncService.truncate("y".repeat(500))).hasSize(500);
    }
}
