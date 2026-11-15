package com.itsm;

import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full application: Flyway migrations + Hibernate schema validation + demo data seeding.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ITServiceRepository serviceRepository;
    @Autowired IncidentRepository incidentRepository;

    @Test
    @DisplayName("Context starts, schema validates and demo data is seeded")
    void contextLoadsAndSeeds() {
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(15);
        assertThat(categoryRepository.count()).isEqualTo(9);
        assertThat(serviceRepository.count()).isEqualTo(10);
        assertThat(incidentRepository.count()).isGreaterThanOrEqualTo(45);
    }
}
