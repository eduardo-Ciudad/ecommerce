package com.eduardo.ecomerce.domain.product;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "bling_product_id", unique = true)
    private Long blingProductId;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "search_text", length = 300)
    private String searchText;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        refreshSearchText();
    }

    @PreUpdate
    private void preUpdate() {
        refreshSearchText();
    }

    private void refreshSearchText() {
        String categoryName = category == null ? "" : category.getName();
        String text = SearchTextNormalizer.normalize(name + " " + categoryName);
        this.searchText = text.length() > 300 ? text.substring(0, 300) : text;
    }
}
