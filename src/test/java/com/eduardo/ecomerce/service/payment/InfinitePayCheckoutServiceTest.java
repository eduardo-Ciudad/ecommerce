package com.eduardo.ecomerce.service.payment;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.order.PaymentProvider;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayCheckoutInput;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayCheckoutOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkRequest;
import com.eduardo.ecomerce.infra.infinitepay.CreateLinkResponse;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayClient;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayException;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayProperties;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfinitePayCheckoutServiceTest {

    private static final String EMAIL = "maria@teste.com";
    private static final String LINK_URL = "https://checkout.infinitepay.io/vanda/abc";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InfinitePayClient client;

    private InfinitePayCheckoutService service;
    private Order order;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        service = newService(true);

        orderId = UUID.fromString("d3aae31d-4465-408c-b942-8273b9bc622b");
        User user = new User();
        user.setEmail(EMAIL);
        user.setName("Maria Silva");

        order = new Order();
        order.setId(orderId);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setRecipientCep("15015-000");
        order.setRecipientStreet("Rua A");
        order.setRecipientNumber("10");
        order.setRecipientNeighborhood("Centro");
        order.setRecipientCity("São José do Rio Preto");
        order.setRecipientState("SP");
        order.setRecipientComplement("");

        OrderItem item = new OrderItem();
        item.setProductName("Short Moletinho");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("49.90"));
        order.getItems().add(item);
        order.setTotal(new BigDecimal("49.90"));
    }

    @Test
    void createCheckout_desabilitado_lancaSemChamarApi() {
        service = newService(false);

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("indisponível");
        verifyNoInteractions(client, orderRepository);
    }

    @Test
    void createCheckout_pedidoNaoEncontrado_lanca() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(client);
    }

    @Test
    void createCheckout_pedidoDeOutroUsuario_lanca() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createCheckout(input(), "outro@teste.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence");
        verifyNoInteractions(client);
    }

    @Test
    void createCheckout_pedidoNaoPendente_lanca() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não está pendente");
        verifyNoInteractions(client);
    }

    @Test
    void createCheckout_pedidoExpirado_lancaSemChamarApi() {
        order.setCreatedAt(LocalDateTime.now().minusMinutes(Order.PAYMENT_WINDOW_MINUTES + 1));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expirou");
        verifyNoInteractions(client);
    }

    @Test
    void createCheckout_linkAtivoExistente_reaproveitaSemChamarApi() {
        order.setPaymentProvider(PaymentProvider.INFINITEPAY);
        order.setCheckoutUrl(LINK_URL);
        order.setPaymentStatus("pending");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        InfinitePayCheckoutOutput output = service.createCheckout(input(), EMAIL);

        assertThat(output.checkoutUrl()).isEqualTo(LINK_URL);
        verifyNoInteractions(client);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createCheckout_tentativaMercadoPagoAtiva_bloqueia() {
        order.setPaymentId("190703246494");
        order.setPaymentStatus("pending");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("em processamento");
        verifyNoInteractions(client);
    }

    @Test
    void createCheckout_montaPayloadCompleto() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        ArgumentCaptor<CreateLinkRequest> captor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        when(client.createLink(captor.capture())).thenReturn(new CreateLinkResponse(LINK_URL, null));

        service.createCheckout(input(), EMAIL);

        CreateLinkRequest request = captor.getValue();
        assertThat(request.handle()).isEqualTo("vanda-julio-dias-898");
        assertThat(request.orderNsu()).isEqualTo(orderId.toString());
        assertThat(request.redirectUrl()).isEqualTo("https://www.gabikidstore.com/pedido-confirmado.html");
        assertThat(request.webhookUrl()).isEqualTo("https://api.gabikidstore.com/payments/infinitepay/webhook");
        assertThat(request.items()).containsExactly(new CreateLinkRequest.Item("Short Moletinho", 4990, 1));
        assertThat(request.customer()).isEqualTo(
                new CreateLinkRequest.Customer("Maria Silva", EMAIL, "+5517999990000"));
        assertThat(request.address()).isEqualTo(new CreateLinkRequest.Address(
                "15015000", "Rua A", "10", "Centro", "São José do Rio Preto", "SP", null));

        String expectedValidUntil = order.getPaymentDeadline().atZone(ZoneId.systemDefault())
                .toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(request.options()).isEqualTo(new CreateLinkRequest.Options(
                3, false, true, expectedValidUntil, List.of("credit_card")));
    }

    @Test
    void createCheckout_sucesso_gravaProvedorUrlStatusDocumentoETelefone() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(client.createLink(any())).thenReturn(new CreateLinkResponse(LINK_URL, null));

        InfinitePayCheckoutOutput output = service.createCheckout(input(), EMAIL);

        assertThat(output.checkoutUrl()).isEqualTo(LINK_URL);
        assertThat(order.getPaymentProvider()).isEqualTo(PaymentProvider.INFINITEPAY);
        assertThat(order.getCheckoutUrl()).isEqualTo(LINK_URL);
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
        assertThat(order.getPaymentMethod()).isEqualTo("credit_card");
        assertThat(order.getCustomerDocument()).isEqualTo("52998224725");
        assertThat(order.getCustomerPhone()).isEqualTo("17999990000");
        assertThat(order.getPaymentId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.hasActivePaymentAttempt()).isTrue();
        verify(orderRepository).save(order);
    }

    @Test
    void createCheckout_respostaComSlug_gravaSlugComoPaymentId() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(client.createLink(any())).thenReturn(new CreateLinkResponse(LINK_URL, "abc"));

        service.createCheckout(input(), EMAIL);

        assertThat(order.getPaymentId()).isEqualTo("abc");
    }

    @Test
    void createCheckout_falhaNaApi_lancaBusinessExceptionSemAlterarPedido() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(client.createLink(any())).thenThrow(new InfinitePayException("Falha na API da InfinitePay (HTTP 500) em /links"));

        assertThatThrownBy(() -> service.createCheckout(input(), EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não foi possível iniciar o pagamento");

        assertThat(order.getPaymentProvider()).isEqualTo(PaymentProvider.MERCADO_PAGO);
        assertThat(order.getCheckoutUrl()).isNull();
        assertThat(order.getPaymentStatus()).isNull();
        verify(orderRepository, never()).save(any());
    }

    // ---------- helpers ----------

    private InfinitePayCheckoutService newService(boolean enabled) {
        InfinitePayProperties properties = new InfinitePayProperties(
                enabled, "https://api.checkout.infinitepay.io", "vanda-julio-dias-898", "inchk_teste", 3,
                "https://api.gabikidstore.com/payments/infinitepay/webhook", "/pedido-confirmado.html");
        InfinitePayCheckoutService created = new InfinitePayCheckoutService(
                orderRepository, client, properties, new InfinitePayItemsBuilder());
        ReflectionTestUtils.setField(created, "frontendUrl", "https://www.gabikidstore.com/");
        return created;
    }

    private InfinitePayCheckoutInput input() {
        return new InfinitePayCheckoutInput(orderId, "CPF", "529.982.247-25", "(17) 99999-0000");
    }
}
