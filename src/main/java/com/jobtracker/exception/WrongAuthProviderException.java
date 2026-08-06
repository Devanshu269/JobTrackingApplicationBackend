package com.jobtracker.exception;

public class WrongAuthProviderException extends RuntimeException {

    public WrongAuthProviderException() {
        super("This account uses a different sign-in method");
    }

    public WrongAuthProviderException(String message) {
        super(message);
    }
}