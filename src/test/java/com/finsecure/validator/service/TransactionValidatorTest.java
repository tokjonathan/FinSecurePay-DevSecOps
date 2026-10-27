package com.finsecure.validator.service;

import com.finsecure.validator.model.TransactionRequest;
import com.finsecure.validator.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the business rules - no web server needed, so they run in milliseconds.
 *
 * TIP for the "failing test stops the pipeline" demo (Task 4):
 * change one expected value below (e.g. isTrue() -> isFalse()), commit, push,
 * and show Jenkins failing at the Test stage before Deploy.
 */
class TransactionValidatorTest {

    // Same defaults as application.properties: max 10,000; SGD/USD/EUR/MYR
    private final TransactionValidator validator =
            new TransactionValidator(new BigDecimal("10000"), "SGD,USD,EUR,MYR");

    private static TransactionRequest tx(String from, String to, String amount, String currency) {
        return new TransactionRequest(from, to, amount == null ? null : new BigDecimal(amount), currency);
    }

    // ---------- Successful validation cases ----------

    @Test
    @DisplayName("A normal transaction passes every rule")
    void validTransactionPasses() {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC67890", "250.00", "SGD"));

        assertThat(result.valid()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("An amount exactly at the limit is allowed")
    void amountAtLimitPasses() {
        assertThat(validator.validate(tx("ACC12345", "ACC67890", "10000", "USD")).valid()).isTrue();
    }

    @Test
    @DisplayName("Currency check is case-insensitive")
    void lowercaseCurrencyPasses() {
        assertThat(validator.validate(tx("ACC12345", "ACC67890", "10", "eur")).valid()).isTrue();
    }

    // ---------- Failed validation cases ----------

    @ParameterizedTest(name = "amount {0} is rejected")
    @ValueSource(strings = {"0", "-50", "-0.01"})
    @DisplayName("Zero or negative amounts are rejected")
    void nonPositiveAmountFails(String amount) {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC67890", amount, "SGD"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).contains("Amount must be greater than 0");
    }

    @Test
    @DisplayName("Amounts above the limit are rejected")
    void amountOverLimitFails() {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC67890", "10000.01", "SGD"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).anyMatch(r -> r.startsWith("Amount exceeds the maximum"));
    }

    @Test
    @DisplayName("More than 2 decimal places is rejected")
    void tooManyDecimalsFails() {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC67890", "10.123", "SGD"));

        assertThat(result.reasons()).contains("Amount cannot have more than 2 decimal places");
    }

    @Test
    @DisplayName("Unsupported currency is rejected")
    void unsupportedCurrencyFails() {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC67890", "100", "JPY"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).contains("Currency JPY is not supported");
    }

    @Test
    @DisplayName("Sending to the same account is rejected")
    void sameAccountFails() {
        ValidationResult result = validator.validate(tx("ACC12345", "ACC12345", "100", "SGD"));

        assertThat(result.reasons()).contains("Sender and receiver cannot be the same account");
    }

    @Test
    @DisplayName("Badly formatted account IDs are rejected")
    void badAccountFormatFails() {
        ValidationResult result = validator.validate(tx("12345", "ACC1", "100", "SGD"));

        assertThat(result.reasons()).containsExactlyInAnyOrder(
                "fromAccount must be in the format ACC followed by 5-10 digits",
                "toAccount must be in the format ACC followed by 5-10 digits");
    }

    @Test
    @DisplayName("Missing fields are all reported at once")
    void missingFieldsFail() {
        ValidationResult result = validator.validate(tx(null, null, null, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).contains(
                "fromAccount is required", "toAccount is required",
                "Amount is required", "Currency is required");
    }

    @Test
    @DisplayName("Limits come from configuration, not hard-coded values")
    void configurableLimit() {
        TransactionValidator strict = new TransactionValidator(new BigDecimal("500"), "SGD");

        assertThat(strict.validate(tx("ACC12345", "ACC67890", "600", "SGD")).valid()).isFalse();
        assertThat(strict.validate(tx("ACC12345", "ACC67890", "100", "USD")).valid()).isFalse();
    }
}
