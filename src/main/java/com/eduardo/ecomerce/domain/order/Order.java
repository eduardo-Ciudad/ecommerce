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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
