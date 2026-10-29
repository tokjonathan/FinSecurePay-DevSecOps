package com.finsecure.validator.controller;

import com.finsecure.validator.service.TransactionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP endpoints end to end (JSON in, JSON + status code out)
 * using MockMvc, which simulates requests without opening a real port.
 */
@WebMvcTest(TransactionController.class)
@Import(TransactionValidator.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("GET /health returns UP")
    void healthReturnsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("finsecure-validator"));
    }

    @Test
    @DisplayName("POST /validate with a good transaction returns 200 and valid=true")
    void validTransactionReturns200() throws Exception {
        String body = """
                {"fromAccount":"ACC12345","toAccount":"ACC67890","amount":250.00,"currency":"SGD"}
                """;

        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    @DisplayName("POST /validate with a bad transaction returns 422 and the reasons")
    void invalidTransactionReturns422() throws Exception {
        String body = """
                {"fromAccount":"ACC12345","toAccount":"ACC12345","amount":-5,"currency":"SGD"}
                """;

        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reasons", hasItem("Amount must be greater than 0")))
                .andExpect(jsonPath("$.reasons", hasItem("Sender and receiver cannot be the same account")));
    }

    @Test
    @DisplayName("Account numbers are masked before logging")
    void accountNumbersAreMasked() {
        org.assertj.core.api.Assertions.assertThat(TransactionController.mask("ACC12345678")).isEqualTo("****5678");
        org.assertj.core.api.Assertions.assertThat(TransactionController.mask(null)).isEqualTo("****");
    }

    @Test
    @DisplayName("POST /validate with malformed JSON returns 400")
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
