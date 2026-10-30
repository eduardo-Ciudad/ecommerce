package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.AddressRepository;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.order.BlingSyncStatus;
import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.order.OrderChannel;
import com.eduardo.ecomerce.domain.order.OrderPaidEvent;
import com.eduardo.ecomerce.domain.order.OrderRepository;
import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.order.CreateLocalSaleInput;
import com.eduardo.ecomerce.dto.input.order.CreateLocalSaleInput.Item;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.email.EmailService;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceLocalSaleTest {

    private static final Instant NOW = Instant.parse("2026-10-30T23:15:00Z");

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private ShippingService shippingService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EmailService emailService;
    @Mock private CouponService couponService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;
    private UUID adminId;
    private ProductVariant vestido;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, cartItemRepository, userRepository,
                productVariantRepository, addressRepository, shippingService, transactionTemplate,
                emailService, couponService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

        adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        admin.setName("Admin GabiKids");
        lenient().when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        vestido = variant("Vestido Rosa", "4", "Rosa", "VR-4", "49.90", 5);
        lenient().when(productVariantRepository.findById(vestido.getId())).thenReturn(Optional.of(vestido));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
    }

    private static ProductVariant variant(String name, String size, String color, String sku, String price, int stock) {
        Product product = new Product();
        product.setName(name);
        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setSku(sku);
        variant.setPrice(new BigDecimal(price));
        variant.setStock(stock);
        return variant;
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void vendaLocal_registraPedidoLocalEntregueComBaixaDeEstoqueESnapshot() {
        CreateLocalSaleInput input = new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 2)), LocalPaymentMethod.STORE_CREDIT, "  Cris vizinha ", null);

        OrderOutput output = orderService.createLocalSale(adminId, input);

        Order order = savedOrder();
        assertThat(order.getChannel()).isEqualTo(OrderChannel.LOCAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaidAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(order.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.PENDING);
        assertThat(order.getPaymentMethod()).isEqualTo("store_credit");
        assertThat(order.getCustomerName()).isEqualTo("Cris vizinha");
        assertThat(order.getUser().getId()).isEqualTo(adminId);
        assertThat(order.getShippingPrice()).isNull();
        assertThat(order.getRecipientCep()).isNull();
        assertThat(order.getTotal()).isEqualByComparingTo("99.80");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");

        OrderItem item = order.getItems().get(0);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("49.90");
        assertThat(item.getProductName()).isEqualTo("Vestido Rosa");
        assertThat(item.getSize()).isEqualTo("4");
        assertThat(item.getSku()).isEqualTo("VR-4");

        assertThat(vestido.getStock()).isEqualTo(3);
        verify(productVariantRepository).save(vestido);

        assertThat(output.channel()).isEqualTo(OrderChannel.LOCAL);
        assertThat(output.customerName()).isEqualTo("Cris vizinha");
        assertThat(output.paymentMethod()).isEqualTo("store_credit");
    }

    @Test
    void vendaLocal_publicaOrderPaidEventParaIrAoBling() {
        orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 1)), LocalPaymentMethod.CASH, null, null));

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(savedOrder().getId());
    }

    @Test
    void vendaLocal_anonima_nomeEmBrancoViraNull() {
        orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 1)), LocalPaymentMethod.PIX, "   ", null));

        assertThat(savedOrder().getCustomerName()).isNull();
    }

    @Test
    void vendaLocal_descontoEmReais_abateDoTotal() {
        orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 2)), LocalPaymentMethod.CARD_MACHINE, null, new BigDecimal("9.80")));

        Order order = savedOrder();
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("9.80");
        assertThat(order.getTotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void vendaLocal_descontoIgualOuMaiorQueOsItens_rejeitaSemSalvarNemPublicar() {
        CreateLocalSaleInput input = new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 1)), LocalPaymentMethod.CASH, null, new BigDecimal("49.90"));

        assertThatThrownBy(() -> orderService.createLocalSale(adminId, input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Desconto");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void vendaLocal_varianteRepetida_somaQuantidadesNumItemSo() {
        orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 1), new Item(vestido.getId(), 2)),
                LocalPaymentMethod.CASH, null, null));

        Order order = savedOrder();
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(vestido.getStock()).isEqualTo(2);
    }

    @Test
    void vendaLocal_estoqueInsuficienteSomandoRepetidas_rejeitaSemBaixarEstoque() {
        CreateLocalSaleInput input = new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 3), new Item(vestido.getId(), 3)),
                LocalPaymentMethod.CASH, null, null);

        assertThatThrownBy(() -> orderService.createLocalSale(adminId, input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Estoque insuficiente");

        assertThat(vestido.getStock()).isEqualTo(5);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void vendaLocal_varianteInexistente_404() {
        UUID missing = UUID.randomUUID();
        when(productVariantRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(missing, 1)), LocalPaymentMethod.CASH, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void vendaLocal_naoEnviaEmailDeNovoPedido() {
        orderService.createLocalSale(adminId, new CreateLocalSaleInput(
                List.of(new Item(vestido.getId(), 1)), LocalPaymentMethod.PAYMENT_LINK, null, null));

        verifyNoInteractions(emailService);
    }
}
