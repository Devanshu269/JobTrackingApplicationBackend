package com.jobtracker.exception;

import jakarta.validation.constraints.AssertFalse;

public class EmailAlreadyExistsException extends RuntimeException{

    public EmailAlreadyExistsException() {
        super("Email already exists");
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

}
