package com.codeops.courier.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development-only data seeder that runs on application startup.
 * Will be populated with seed data in CC-016.
 */
@Component
@Profile("dev")
@Slf4j
public class DataSeeder implements CommandLineRunner {

    /**
     * Seeds development data on application startup.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    public void run(String... args) {
        log.info("DataSeeder: No seed data configured yet.");
    }
}
