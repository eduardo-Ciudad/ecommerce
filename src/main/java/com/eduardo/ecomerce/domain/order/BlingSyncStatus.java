package com.eduardo.ecomerce.domain.order;

/**
 * Estado do envio do pedido do site para o Bling (pedido de venda + lançamento de estoque).
 * Nulo no pedido significa "fora do sync" (pedidos anteriores à feature).
 */
public enum BlingSyncStatus {
    /** Pago, aguardando envio. */
    PENDING,
    /** Envio em andamento (claim feito por um processo). */
    PROCESSING,
    /** Pedido criado e estoque lançado no Bling. */
    SENT,
    /** Última tentativa falhou; elegível a retry até o limite de tentativas. */
    FAILED
}
