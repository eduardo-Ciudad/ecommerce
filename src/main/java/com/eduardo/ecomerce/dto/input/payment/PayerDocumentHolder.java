package com.eduardo.ecomerce.dto.input.payment;

/** Entrada que carrega o documento do pagador, validado por {@link ValidPayerDocument}. */
public interface PayerDocumentHolder {

    String payerDocType();

    String payerDocNumber();
}
