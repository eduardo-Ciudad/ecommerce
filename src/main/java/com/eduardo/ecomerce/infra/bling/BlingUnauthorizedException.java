package com.eduardo.ecomerce.infra.bling;

public class BlingUnauthorizedException extends BlingIntegrationException {
    public BlingUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public BlingUnauthorizedException(String message, Throwable cause, Integer httpStatus, String blingErrorType, String blingErrorDescription) {
        super(message, cause, httpStatus, blingErrorType, blingErrorDescription);
    }
}