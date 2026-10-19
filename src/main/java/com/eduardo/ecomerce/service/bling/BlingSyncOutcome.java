package com.eduardo.ecomerce.service.bling;

/** Resultado de uma tentativa de envio do pedido ao Bling. */
public enum BlingSyncOutcome {
    /** Feature desligada (bling.orders.enabled=false). */
    DISABLED,
    /** Pedido não elegível ou já sendo enviado por outro processo (claim não pegou). */
    SKIPPED,
    /** Pedido criado e estoque lançado no Bling. */
    SENT,
    /** Falhou; erro gravado no pedido e elegível a retry até o limite. */
    FAILED
}
