package com.zain.almksazain.services;

/**
 * Thrown when an aging report needs a full row load but the server is already
 * running as many of those loads as it allows (see
 * DccPoCombinedService.loadAndFilterAgingRows). Controllers turn it into a
 * 503 so the grid can ask the user to retry instead of piling on more work.
 */
public class AgingReportBusyException extends RuntimeException {

    public AgingReportBusyException(String message) {
        super(message);
    }
}
