package com.finsecure.validator.model;

import java.util.List;

/**
 * The JSON returned by POST /validate.
 *
 * valid   = true when every rule passed
 * reasons = human-readable list of every rule that failed (empty when valid)
 */
public record ValidationResult(boolean valid, List<String> reasons) {

    public static ValidationResult of(List<String> reasons) {
        return new ValidationResult(reasons.isEmpty(), List.copyOf(reasons));
    }
}
