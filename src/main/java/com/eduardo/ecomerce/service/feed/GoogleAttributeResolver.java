package com.eduardo.ecomerce.service.feed;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.product.Brand;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;


public final class GoogleAttributeResolver {

    public static final String GENDER_FEMALE = "female";
    public static final String GENDER_MALE = "male";
    public static final String GENDER_UNISEX = "unisex";

    public static final String AGE_NEWBORN = "newborn";
    public static final String AGE_INFANT = "infant";
    public static final String AGE_TODDLER = "toddler";
    public static final String AGE_KIDS = "kids";

    private static final Pattern NUMERIC_SIZE = Pattern.compile("\\d{1,2}");
    private static final Pattern MONTHS_SIZE = Pattern.compile("\\d{1,2}\\s*(m|mes|meses)");
    private static final Pattern CODE_SUFFIX = Pattern.compile("\\s*-?\\s*c[oó]d\\.?\\s*\\d+.*$", Pattern.CASE_INSENSITIVE);


    private static final List<String> EXTRA_NAME_BRANDS = List.of(
            "Alakazoo", "H!ts", "Trick Nick", "Rovi Kids", "Rovitex", "Minty",
            "Play Denim", "Alenice", "Milon"
    );

    private GoogleAttributeResolver() {
    }

    public static String gender(Category category) {
        String root = normalize(rootName(category));
        if (root == null || root.contains("unissex")) {
            return GENDER_UNISEX;
        }
        if (root.contains("menina")) {
            return GENDER_FEMALE;
        }
        if (root.contains("menino")) {
            return GENDER_MALE;
        }
        return GENDER_UNISEX;
    }


    public static String ageGroup(String size, Category category) {
        String s = normalize(cleanSize(size));
        if (s != null) {
            if (s.equals("rn") || s.startsWith("rn ")) {
                return AGE_NEWBORN;
            }
            if (s.contains("bebe") || MONTHS_SIZE.matcher(s).matches()) {
                return AGE_INFANT;
            }
            if (NUMERIC_SIZE.matcher(s).matches()) {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= 4) {
                    return AGE_TODDLER;
                }
                if (n >= 5) {
                    return AGE_KIDS;
                }
            }
        }
        String root = normalize(rootName(category));
        return root != null && root.startsWith("bebe") ? AGE_INFANT : AGE_KIDS;
    }


    public static String brand(String brandSpecification, String productName) {
        if (brandSpecification != null && !brandSpecification.isBlank()) {
            return brandSpecification.trim();
        }
        String name = normalize(productName);
        if (name == null) {
            return null;
        }
        for (Brand brand : Brand.values()) {
            for (String value : brand.getSpecificationValues()) {
                if (containsWord(name, value)) {
                    return brand.getDisplayName();
                }
            }
        }
        for (String brand : EXTRA_NAME_BRANDS) {
            if (containsWord(name, brand)) {
                return brand;
            }
        }
        return null;
    }


    public static String cleanSize(String size) {
        if (size == null) {
            return null;
        }
        String cleaned = CODE_SUFFIX.matcher(size).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean containsWord(String normalizedText, String word) {
        String needle = normalize(word);
        return needle != null
                && Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])")
                .matcher(normalizedText).find();
    }

    static String rootName(Category category) {
        if (category == null) {
            return null;
        }
        Category current = category;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current.getName();
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}