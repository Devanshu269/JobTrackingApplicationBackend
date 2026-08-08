package com.jobtracker.exception;

public class InvalidExchangeCodeException extends RuntimeException {

    public InvalidExchangeCodeException() {
        super("Invalid or expired exchange code");
    }
}