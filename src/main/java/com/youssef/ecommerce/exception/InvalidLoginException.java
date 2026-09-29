package com.youssef.ecommerce.exception;

public class InvalidLoginException extends RuntimeException {

    public InvalidLoginException(String message) {
        super(message);
    }
}
