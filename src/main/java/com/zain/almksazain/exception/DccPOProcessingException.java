package com.zain.almksazain.exception;

public class DccPOProcessingException extends RuntimeException {
    public DccPOProcessingException(String message) {
        super(message);
    }

    public DccPOProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}