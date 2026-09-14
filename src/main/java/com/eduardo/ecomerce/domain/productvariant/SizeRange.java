package com.eduardo.ecomerce.domain.productvariant;
import java.util.List;
import java.util.Locale;


import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum SizeRange {

    RN_12_MESES("RN - 12 meses", List.of("P bebê", "M bebê", "G bebê")),
    UM_TRES_ANOS("01 - 03 anos", List.of("1", "2", "3")),
    QUATRO_DEZ_ANOS("04 - 10 anos", List.of("4", "6", "8", "10")),
    DEZ_DEZOITO_ANOS("10 - 18 anos", List.of("12", "14", "16", "18"));

    private static final Map<String, SizeRange> BY_SLUG = Map.of(
            "rn-12-meses", RN_12_MESES,
            "01-03-anos", UM_TRES_ANOS,
            "04-10-anos", QUATRO_DEZ_ANOS,
            "10-18-anos", DEZ_DEZOITO_ANOS
    );

    private final String displayName;
    private final List<String> acceptedSizes;

    SizeRange(String displayName, List<String> acceptedSizes) {
        this.displayName = displayName;
        this.acceptedSizes = acceptedSizes;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getAcceptedSizes() {
        return acceptedSizes;
    }

    public static SizeRange fromParam(String param) {
        if (param == null || param.isBlank()) {
            return null;
        }
        return BY_SLUG.get(param.trim().toLowerCase(Locale.ROOT));
    }
}