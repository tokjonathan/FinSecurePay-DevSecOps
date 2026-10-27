package com.finsecure.validator.controller;

import com.finsecure.validator.model.TransactionRequest;
import com.finsecure.validator.model.ValidationResult;
import com.finsecure.validator.service.TransactionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP layer that handles mapping URLs to methods. 
 */
@RestController
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionValidator validator;
    private final String appVersion;

    public TransactionController(TransactionValidator validator,
                                 @Value("${app.version:dev}") String appVersion) {
        this.validator = validator;
        this.appVersion = appVersion;
    }

    /**
     * GET /health - simple "am I alive" check.
     * Returning the version makes rollouts and rollbacks easy to prove (Task 4):
     * curl the endpoint before and after and the version changes.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "finsecure-validator",
                "version", appVersion,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * POST /validate - checks a transaction.
     * 200 OK                   -> transaction is valid
     * 422 Unprocessable Entity -> transaction broke one or more rules
     * Using different status codes lets Prometheus count failed validations (Task 6).
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validate(@RequestBody TransactionRequest tx) {
        ValidationResult result = validator.validate(tx);

        if (result.valid()) {
            log.info("Transaction VALID from={} to={} amount={} {}",
                    mask(tx.fromAccount()), mask(tx.toAccount()), tx.amount(), tx.currency());
            return ResponseEntity.ok(result);
        }

        log.warn("Transaction REJECTED from={} reasons={}", mask(tx.fromAccount()), result.reasons());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }

    /**
     * Security: never write full account numbers to logs, since logs are shipped to
     * central logging where many people can read them. ACC12345678 -> ****5678
     */
    static String mask(String account) {
        if (account == null || account.length() <= 4) {
            return "****";
        }
        return "****" + account.substring(account.length() - 4);
    }
}
