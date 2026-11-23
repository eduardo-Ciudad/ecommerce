package com.eduardo.ecomerce.infra.infinitepay;

public class InfinitePayException extends RuntimeException {

    private final Integer httpStatus;

    public InfinitePayException(String message) {
        this(message, null, null);
    }

    public InfinitePayException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public InfinitePayException(String message, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
