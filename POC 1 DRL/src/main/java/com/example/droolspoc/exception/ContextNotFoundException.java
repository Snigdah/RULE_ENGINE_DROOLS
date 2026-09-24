package com.example.droolspoc.exception;

/**
 * Thrown when a required context row (UserLimit or Product) is not found in
 * the database for the request's keys.
 */
public class ContextNotFoundException extends RuntimeException {

    public ContextNotFoundException(String message) {
        super(message);
    }
}
