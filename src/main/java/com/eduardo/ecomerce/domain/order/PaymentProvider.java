package com.eduardo.ecomerce.domain.order;

/** Gateway que processou o pagamento do pedido. Pedidos antigos e vendas no balcão ficam como MERCADO_PAGO. */
public enum PaymentProvider {
    MERCADO_PAGO,
    INFINITEPAY
}
