package com.eduardo.ecomerce.domain.order;

/**
 * Formas de pagamento da venda local (balcão). O código vai para orders.payment_method e
 * não colide com os valores do site ("pix" | "credit_card").
 */
public enum LocalPaymentMethod {
    CASH("cash", "Dinheiro"),
    PIX("local_pix", "Pix"),
    CARD_MACHINE("card_machine", "Maquininha"),
    PAYMENT_LINK("payment_link", "Link de pagamento"),
    STORE_CREDIT("store_credit", "Fiado");

    private final String code;
    private final String label;

    LocalPaymentMethod(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** Rótulo para o código gravado no pedido, ou null se não for uma forma local. */
    public static String labelFor(String code) {
        for (LocalPaymentMethod method : values()) {
            if (method.code.equals(code)) {
                return method.label;
            }
        }
        return null;
    }
}
