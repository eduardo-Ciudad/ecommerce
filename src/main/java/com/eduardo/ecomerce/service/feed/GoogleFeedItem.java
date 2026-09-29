package com.eduardo.ecomerce.service.feed;

import java.util.List;

public record GoogleFeedItem(
        String id,
        String itemGroupId,
        String title,
        String description,
        String link,
        String imageLink,
        List<String> additionalImageLinks,
        String availability,
        String price,
        String condition,
        String brand,
        String gtin,
        String identifierExists,
        String productType,
        String color,
        String size,
        String gender,
        String ageGroup
) {
}