package com.eduardo.ecomerce.service.bling;

public class BlingSyncAlreadyRunningException extends RuntimeException {

    public BlingSyncAlreadyRunningException() {
        super("Já existe uma sincronização com o Bling em andamento.");
    }
}
