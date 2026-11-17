package com.iotiq.partnererp.common.web;

/**
 * Body of an archive (soft delete) call: why the record is being archived.
 */
public record ArchiveRequest(String reason) {

    public static final int MIN_REASON = 10;
    public static final int MAX_REASON = 500;

    /** The trimmed reason; 400 on {@code reason} when it is missing, shorter than 10 or longer than 500 characters. */
    public static String requireReason(ArchiveRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.length() < MIN_REASON) {
            throw com.iotiq.partnererp.common.exception.ValidationException.of("reason",
                    "Say why it is being archived (at least " + MIN_REASON + " characters).");
        }
        if (reason.length() > MAX_REASON) {
            throw com.iotiq.partnererp.common.exception.ValidationException.of("reason",
                    "The reason must be at most " + MAX_REASON + " characters.");
        }
        return reason;
    }
}
