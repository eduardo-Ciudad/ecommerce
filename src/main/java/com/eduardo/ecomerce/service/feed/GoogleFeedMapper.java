package com.eduardo.ecomerce.service.feed;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import org.jsoup.Jsoup;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class GoogleFeedMapper {

    static final int MAX_TITLE = 150;
    static final int MAX_DESCRIPTION = 5000;
    static final int MAX_ADDITIONAL_IMAGES = 10;

    private GoogleFeedMapper() {
    }


    public static GoogleFeedItem toItem(ProductVariant variant, List<String> images, String brandSpec, String siteUrl) {
        Product product = variant.getProduct();

        List<String> allImages = new ArrayList<>(images == null ? List.of() : images);
        if (allImages.isEmpty() && product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            allImages.add(product.getImageUrl());
        }
        if (allImages.isEmpty()) {
            return null;
        }

        String size = GoogleAttributeResolver.cleanSize(variant.getSize());
        String color = blankToNull(variant.getColor());
        String gtin = blankToNull(variant.getGtin());
        String brand = GoogleAttributeResolver.brand(brandSpec, product.getName());
        Category category = product.getCategory();
        int stock = variant.getStock() == null ? 0 : variant.getStock();

        String title = truncate(joinTitle(product.getName(), size, color), MAX_TITLE);

        return new GoogleFeedItem(
                variant.getId().toString(),
                product.getId().toString(),
                title,
                truncate(plainDescription(product.getDescription(), title), MAX_DESCRIPTION),
                siteUrl + "/product.html?id=" + product.getId() + "&variant=" + variant.getId(),
                allImages.get(0),
                allImages.subList(1, Math.min(allImages.size(), 1 + MAX_ADDITIONAL_IMAGES)),
                stock > 0 ? "in_stock" : "out_of_stock",
                variant.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString() + " BRL",
                "new",
                brand,
                gtin,
                gtin == null ? "no" : null,
                productType(category),
                color,
                size,
                GoogleAttributeResolver.gender(category),
                GoogleAttributeResolver.ageGroup(size, category)
        );
    }

    static String joinTitle(String name, String size, String color) {
        StringBuilder title = new StringBuilder(name.trim());
        if (size != null) {
            title.append(" – Tam. ").append(size);
        }
        if (color != null) {
            title.append(" – ").append(color);
        }
        return title.toString();
    }

    static String plainDescription(String description, String fallback) {
        if (description == null || description.isBlank()) {
            return fallback;
        }
        String text = Jsoup.parse(description).text().replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? fallback : text;
    }

    static String productType(Category category) {
        if (category == null) {
            return null;
        }
        Category parent = category.getParent();
        return parent == null ? category.getName() : parent.getName() + " > " + category.getName();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}