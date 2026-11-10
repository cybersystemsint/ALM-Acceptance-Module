package com.zain.almksazain.services;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a Serial Number create/update/delete fails a business check. Carries every
 * error found, so a bulk upload reports all bad rows at once - and nothing is saved.
 */
public class SerialNumberValidationException extends RuntimeException {

    private final List<String> errors;

    public SerialNumberValidationException(List<String> errors) {
        super(String.join("\n", errors));
        this.errors = Collections.unmodifiableList(errors);
    }

    public SerialNumberValidationException(String error) {
        this(Collections.singletonList(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
