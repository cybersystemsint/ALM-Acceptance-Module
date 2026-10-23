package com.zain.almksazain.services;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a TAG Number create/update/delete fails a business check. Carries every
 * error found, so a bulk upload reports all bad rows at once - and nothing is saved.
 */
public class TagNumberValidationException extends RuntimeException {

    private final List<String> errors;

    public TagNumberValidationException(List<String> errors) {
        super(String.join("\n", errors));
        this.errors = Collections.unmodifiableList(errors);
    }

    public TagNumberValidationException(String error) {
        this(Collections.singletonList(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
