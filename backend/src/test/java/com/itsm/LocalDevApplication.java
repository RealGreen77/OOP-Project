package com.itsm;

import org.springframework.boot.SpringApplication;

/**
 * Runs the full application without PostgreSQL/Docker, using a file-based H2 database in PostgreSQL mode.
 * <pre>
 *   ./mvnw spring-boot:test-run
 * </pre>
 * Intended for quick local demos; production and docker-compose use PostgreSQL.
 */
public class LocalDevApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ItsmApplication.class);
        app.setAdditionalProfiles("local");
        app.run(args);
    }
}
