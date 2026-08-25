package com.zain.almksazain.services;

import java.util.List;

import com.zain.almksazain.model.UplChangeRequest;

public class UplChangeRequestBatchResult {
    private final List<UplChangeRequest> created;
    private final List<UplChangeRequestFailure> failures;

    public UplChangeRequestBatchResult(List<UplChangeRequest> created, List<UplChangeRequestFailure> failures) {
        this.created = created;
        this.failures = failures;
    }

    public List<UplChangeRequest> getCreated() {
        return created;
    }

    public List<UplChangeRequestFailure> getFailures() {
        return failures;
    }
}
