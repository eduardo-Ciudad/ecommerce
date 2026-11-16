package com.eduardo.ecomerce.domain.product;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SearchTextNormalizer {

    static final int MAX_TOKENS = 5;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "e", "de", "da", "do", "das", "dos", "com", "para", "em", "no", "na"
    );

    private SearchTextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\-\\u2013\\u2014]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static List<String> tokens(String query) {
        List<String> tokens = new ArrayList<>();
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return tokens;
        }
        for (String raw : normalized.split(" ")) {
            String token = raw.replaceAll("^[^\\p{Alnum}]+|[^\\p{Alnum}]+$", "");
            if (token.isEmpty() || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(singular(token));
            if (tokens.size() == MAX_TOKENS) {
                break;
            }
        }
        return tokens;
    }

    private static String singular(String token) {
        return token.length() > 3 && token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
    }
}
