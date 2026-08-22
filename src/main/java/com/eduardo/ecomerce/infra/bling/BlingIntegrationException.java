package com.eduardo.ecomerce.infra.bling;

public class BlingIntegrationException extends RuntimeException {

    private final Integer httpStatus;
    private final String blingErrorType;
    private final String blingErrorDescription;

    public BlingIntegrationException(String message, Throwable cause) {
        this(message, cause, null, null, null);
    }

    public BlingIntegrationException(String message, Throwable cause, Integer httpStatus, String blingErrorType, String blingErrorDescription) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.blingErrorType = blingErrorType;
        this.blingErrorDescription = blingErrorDescription;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getBlingErrorType() {
        return blingErrorType;
    }

    public String getBlingErrorDescription() {
        return blingErrorDescription;
    }
}