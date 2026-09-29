package com.example.droolspoc.exception;

/**
 * Thrown when a DRL file fails to compile. Used to reject a bad upload before
 * it is saved, so the running rules are never broken.
 */
public class RuleCompilationException extends RuntimeException {

    public RuleCompilationException(String message) {
        super(message);
    }
}
