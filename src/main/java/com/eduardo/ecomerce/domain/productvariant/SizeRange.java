package com.eduardo.ecomerce.domain.productvariant;
import java.util.List;
import java.util.Locale;

public enum SizeRange {

    RN_12_MESES("RN - 12 meses", List.of("P bebê", "M bebê", "G bebê")),
    UM_TRES_ANOS("01 - 03 anos", List.of("1", "2", "3")),
    QUATRO_DEZ_ANOS("04 - 10 anos", List.of("4", "6", "8", "10")),
    DEZ_DEZOITO_ANOS("10 - 18 anos", List.of("12", "14", "16", "18"));

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
        String normalized = param.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (SizeRange range : values()) {
            if (range.name().equals(normalized)) {
                return range;
            }
        }
        return null;
    }
}