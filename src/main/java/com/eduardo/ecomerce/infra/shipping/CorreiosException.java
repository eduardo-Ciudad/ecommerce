package com.eduardo.ecomerce.infra.shipping;

public class CorreiosException extends RuntimeException {
    public CorreiosException(String message, Throwable cause) {
        super(message, cause);
    }
}
