package com.eduardo.ecomerce.service.payment;

/** Resultado da confirmação de um pagamento da InfinitePay (webhook ou página de retorno). */
public enum InfinitePayConfirmation {
    PAID,
    ALREADY_PAID,
    NOT_PAID,
    REQUIRES_ATTENTION
}
