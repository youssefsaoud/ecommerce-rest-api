package com.youssef.ecommerce.exception;

public class InvalidCartOperationException extends RuntimeException {

    public InvalidCartOperationException(String message) {
        super(message);
    }
}
