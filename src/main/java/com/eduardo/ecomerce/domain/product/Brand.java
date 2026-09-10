package com.eduardo.ecomerce.domain.product;

import java.util.List;
import java.util.Locale;

public enum Brand {

    ELIAN("Elian", List.of("Elian")),
    BRANDILI("Brandili", List.of("Brandili")),
    COLORITTA("Colorittá", List.of("Colorittá", "Colorittà")),
    FAKINI("Fakini", List.of("Fakini", "Fakini Forfun", "Forfun", "Forfun / Fakini", "Fakini / For Fun", "For Fun")),
    MUNDI("Mundi", List.of("Mundi")),
    MALWEE_KIDS("Malwee kids", List.of("Malwee Kids"));

    private final String displayName;
    private final List<String> specificationValues;

    Brand(String displayName, List<String> specificationValues) {
        this.displayName = displayName;
        this.specificationValues = specificationValues;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSpecificationValues() {
        return specificationValues;
    }

    public static Brand fromParam(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        String normalized = param.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Brand brand : values()) {
            if (brand.name().equals(normalized)) {
                return brand;
            }
        }
        return null;
    }
}