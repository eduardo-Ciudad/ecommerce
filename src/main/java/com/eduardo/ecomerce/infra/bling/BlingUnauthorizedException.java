package com.eduardo.ecomerce.infra.bling;

public class BlingUnauthorizedException extends BlingIntegrationException {
    public BlingUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}