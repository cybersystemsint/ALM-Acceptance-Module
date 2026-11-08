package com.zain.almksazain.services;

/** Thrown for any rule in the UPL edit/delete/approval flow that fails a business check. */
public class UplValidationException extends RuntimeException {
    public UplValidationException(String message) {
        super(message);
    }
}
