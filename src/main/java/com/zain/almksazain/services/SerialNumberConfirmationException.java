package com.zain.almksazain.services;

import java.util.Collections;
import java.util.List;

/**
 * Thrown, before anything is saved, when a save would also change the Validation Rule of other
 * rows of the same serial number and the caller hasn't confirmed that yet. The UI shows the
 * changes and sends the same request again with confirmValidationRuleChange = true.
 */
public class SerialNumberConfirmationException extends RuntimeException {

    /** One serial number whose other rows would get a new Validation Rule. */
    public static class RuleChange {
        public final String serialNumber;
        public final String validationRule;
        public final int otherRecords;

        public RuleChange(String serialNumber, String validationRule, int otherRecords) {
            this.serialNumber = serialNumber;
            this.validationRule = validationRule;
            this.otherRecords = otherRecords;
        }
    }

    private final List<RuleChange> changes;

    public SerialNumberConfirmationException(String message, List<RuleChange> changes) {
        super(message);
        this.changes = Collections.unmodifiableList(changes);
    }

    public List<RuleChange> getChanges() {
        return changes;
    }
}
