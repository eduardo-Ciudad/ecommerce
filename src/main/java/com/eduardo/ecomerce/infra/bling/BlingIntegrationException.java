package com.eduardo.ecomerce.infra.bling;

public class BlingIntegrationException extends RuntimeException {
    public BlingIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}