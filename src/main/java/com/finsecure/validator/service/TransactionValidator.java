package com.finsecure.validator.service;

import com.finsecure.validator.model.TransactionRequest;
import com.finsecure.validator.model.ValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * business logic
 * checks transaction against FinSecure Pay's rules.
 *
 * Every rule is checked (not just the first failure) so the caller sees all problems at once.
 *
 * The max amount and allowed currencies are not hard-coded. 
 * They come from configuration (application.properties locally, a Kubernetes ConfigMap in the cluster - Task 3),
 * they can change without rebuilding the app.
 */
@Service
public class TransactionValidator {

    /** Account IDs look like ACC followed by 5-10 digits, e.g. ACC12345. */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^ACC\\d{5,10}$");
    private final BigDecimal maxAmount;
    private final Set<String> allowedCurrencies;

    public TransactionValidator(
            @Value("${validation.max-amount:10000}") BigDecimal maxAmount,
            @Value("${validation.allowed-currencies:SGD,USD,EUR,MYR}") String allowedCurrencies) {
        this.maxAmount = maxAmount;
        this.allowedCurrencies = Arrays.stream(allowedCurrencies.split(","))
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public ValidationResult validate(TransactionRequest tx) {
        List<String> reasons = new ArrayList<>();

        if (tx == null) {
            return ValidationResult.of(List.of("Transaction body is required"));
        }

        // Rule 1 & 2: both accounts present and correctly formatted
        checkAccount("fromAccount", tx.fromAccount(), reasons);
        checkAccount("toAccount", tx.toAccount(), reasons);

        // Rule 3: cannot send money to yourself
        if (tx.fromAccount() != null && tx.fromAccount().equals(tx.toAccount())) {
            reasons.add("Sender and receiver cannot be the same account");
        }

        // Rules 4-6: amount present, positive, within limit, at most 2 decimal places
        BigDecimal amount = tx.amount();
        if (amount == null) {
            reasons.add("Amount is required");
        } else {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                reasons.add("Amount must be greater than 0");
            }
            if (amount.compareTo(maxAmount) > 0) {
                reasons.add("Amount exceeds the maximum allowed of " + maxAmount.toPlainString());
            }
            if (amount.stripTrailingZeros().scale() > 2) {
                reasons.add("Amount cannot have more than 2 decimal places");
            }
        }

        // Rule 7: currency must be one we support
        String currency = tx.currency();
        if (currency == null || currency.isBlank()) {
            reasons.add("Currency is required");
        } else if (!allowedCurrencies.contains(currency.trim().toUpperCase(Locale.ROOT))) {
            reasons.add("Currency " + currency + " is not supported");
        }

        return ValidationResult.of(reasons);
    }

    private void checkAccount(String field, String value, List<String> reasons) {
        if (value == null || value.isBlank()) {
            reasons.add(field + " is required");
        } else if (!ACCOUNT_PATTERN.matcher(value).matches()) {
            reasons.add(field + " must be in the format ACC followed by 5-10 digits");
        }
    }
}
