package com.finsecure.validator.model;

import java.math.BigDecimal;

/**
 * The JSON body sent to POST /validate. Spring converts the JSON into this object automatically.
 * BigDecimal (not double) is used for money so that values like 0.1 + 0.2 are exact.
 *  
 * Example:
 * { "fromAccount": "ACC12345", "toAccount": "ACC67890", "amount": 250.00, "currency": "SGD" }
 *
 */
public record TransactionRequest(
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency
) {
}
