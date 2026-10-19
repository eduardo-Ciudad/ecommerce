package com.eduardo.ecomerce.infra.validation;

/**
 * Utilitário puro para CPF/CNPJ: normalização (só dígitos) e checagem dos dígitos verificadores.
 */
public final class DocumentNumbers {

    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentNumbers() {
    }

    /** Remove tudo que não é dígito. Nulo, vazio ou sem nenhum dígito vira null. */
    public static String onlyDigits(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Espera só dígitos (use onlyDigits antes). */
    public static boolean isValidCpf(String digits) {
        if (digits == null || !digits.matches("\\d{11}") || allSameDigit(digits)) {
            return false;
        }
        int first = cpfCheckDigit(digits, 9);
        int second = cpfCheckDigit(digits, 10);
        return first == digitAt(digits, 9) && second == digitAt(digits, 10);
    }

    /** Espera só dígitos (use onlyDigits antes). */
    public static boolean isValidCnpj(String digits) {
        if (digits == null || !digits.matches("\\d{14}") || allSameDigit(digits)) {
            return false;
        }
        int first = cnpjCheckDigit(digits, CNPJ_WEIGHTS_1);
        int second = cnpjCheckDigit(digits, CNPJ_WEIGHTS_2);
        return first == digitAt(digits, 12) && second == digitAt(digits, 13);
    }

    private static int cpfCheckDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(digits, i) * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    private static int cnpjCheckDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digitAt(digits, i) * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static boolean allSameDigit(String digits) {
        return digits.chars().distinct().count() == 1;
    }

    private static int digitAt(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
