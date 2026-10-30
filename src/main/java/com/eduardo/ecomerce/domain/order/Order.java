package com.eduardo.ecomerce.domain.order;

import com.eduardo.ecomerce.domain.orderitem.OrderItem;
import com.eduardo.ecomerce.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "payment_status", length = 50)
    private String paymentStatus;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "shipping_method", length = 20)
    private String shippingMethod;

    @Column(name = "shipping_price", precision = 10, scale = 2)
    private BigDecimal shippingPrice;

    @Column(name = "shipping_deadline_days")
    private Integer shippingDeadlineDays;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_cep", length = 9)
    private String recipientCep;

    @Column(name = "recipient_street", length = 200)
    private String recipientStreet;

    @Column(name = "recipient_number", length = 20)
    private String recipientNumber;

    @Column(name = "recipient_complement", length = 100)
    private String recipientComplement;

    @Column(name = "recipient_neighborhood", length = 100)
    private String recipientNeighborhood;

    @Column(name = "recipient_city", length = 100)
    private String recipientCity;

    @Column(name = "recipient_state", length = 2)
    private String recipientState;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "customer_document", length = 14)
    private String customerDocument;

    @Column(name = "bling_order_id", unique = true)
    private Long blingOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bling_sync_status", length = 20)
    private BlingSyncStatus blingSyncStatus;

    @Column(name = "bling_sync_attempts", nullable = false)
    private int blingSyncAttempts = 0;

    @Column(name = "bling_sync_error", length = 500)
    private String blingSyncError;

    @Column(name = "bling_sync_started_at")
    private LocalDateTime blingSyncStartedAt;

    @Column(name = "bling_synced_at")
    private LocalDateTime blingSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderChannel channel = OrderChannel.SITE;

    /** Só venda local: texto livre para a loja saber para quem vendeu (pode ficar vazio). */
    @Column(name = "customer_name", length = 100)
    private String customerName;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static final long PAYMENT_WINDOW_MINUTES = 30;

    private static final Set<String> FAILED_PAYMENT_STATUSES = Set.of("rejected", "cancelled");

    /**
     * true se existe um pagamento no Mercado Pago que ainda pode ser (ou já foi) aprovado.
     * Pagamentos com status final de falha (rejected/cancelled) não contam como tentativa ativa.
     */
    public boolean hasActivePaymentAttempt() {
        // Set.of(...).contains(null) lança NPE: paymentStatus pode ser nulo com paymentId preenchido
        // (ex.: pagamento órfão adotado pelo job de expiração), e nesse caso a tentativa conta como ativa.
        boolean failed = paymentStatus != null && FAILED_PAYMENT_STATUSES.contains(paymentStatus);
        return paymentId != null && !failed;
    }

    /** Pedido ainda pode receber uma nova tentativa de pagamento (dentro da janela de reserva). */
    public boolean canReceivePayment() {
        LocalDateTime deadline = getPaymentDeadline();
        return status == OrderStatus.PENDING
                && !hasActivePaymentAttempt()
                && deadline != null
                && LocalDateTime.now().isBefore(deadline);
    }

    /**
     * Única porta de entrada para PAID: marca o pagamento e coloca o pedido na fila de envio ao Bling.
     * paidAt vem de fora para o teste conseguir verificar a data exata.
     */
    public void markAsPaid(LocalDateTime paidAt) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Só pedido PENDING pode ser marcado como pago (atual: " + status + ")");
        }
        this.status = OrderStatus.PAID;
        this.paidAt = paidAt;
        this.blingSyncStatus = BlingSyncStatus.PENDING;
    }

    /**
     * Venda de balcão: a mercadoria sai na hora, então o pedido passa por PAID (fila do Bling)
     * e já fica DELIVERED. Só vale para pedido LOCAL.
     */
    public void completeLocalSale(LocalDateTime paidAt) {
        if (channel != OrderChannel.LOCAL) {
            throw new IllegalStateException("Só pedido LOCAL pode ser concluído como venda de balcão");
        }
        markAsPaid(paidAt);
        this.status = OrderStatus.DELIVERED;
    }

    public boolean isLocalSale() {
        return channel == OrderChannel.LOCAL;
    }

    public LocalDateTime getPaymentDeadline() {
        return createdAt == null ? null : createdAt.plusMinutes(PAYMENT_WINDOW_MINUTES);
    }
}
