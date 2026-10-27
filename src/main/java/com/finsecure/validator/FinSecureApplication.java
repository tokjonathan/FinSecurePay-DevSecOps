package com.finsecure.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Starting this class boots an embedded web server on port 8080
 * and registers every @RestController / @Service in this package.
 */
@SpringBootApplication
public class FinSecureApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinSecureApplication.class, args);
    }
}
