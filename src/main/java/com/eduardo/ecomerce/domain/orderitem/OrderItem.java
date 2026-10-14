package com.eduardo.ecomerce.domain.orderitem;

import com.eduardo.ecomerce.domain.order.Order;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(length = 100)
    private String size;

    @Column(length = 100)
    private String color;

    @Column(length = 100)
    private String sku;

    @Column(length = 14)
    private String gtin;
}
