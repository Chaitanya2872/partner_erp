package com.iotiq.partnererp.modules.payments.dto;

import java.util.List;

/**
 * Options for the payment screens and the caller's payment actions.
 *
 * @param referenceOptional methods for which the reference may be left empty
 */
public record PaymentMeta(List<Option> methods, List<Option> entryTypes, List<Option> states,
                          List<String> referenceOptional, int maxReason, int maxNotes, boolean canWrite,
                          boolean canReverse, boolean canExport) {

    public record Option(String value, String label) {
    }
}
