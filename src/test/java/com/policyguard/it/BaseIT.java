package com.policyguard.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for all Testcontainers integration tests.
 *
 * <p>Uses the singleton-container pattern: {@link #POSTGRES} and {@link #REDIS}
 * are started exactly once (static initialiser) and shared across every subclass
 * that runs in the same JVM. Ryuk cleans them up on JVM exit.
 *
 * <p>Imports {@link PresidioStubConfig} so every IT test gets a no-op
 * {@link com.policyguard.service.pii.PresidioClient} — Presidio is not required.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "stub"})
@Import(PresidioStubConfig.class)
public abstract class BaseIT {

    @SuppressWarnings({"resource", "unchecked"})
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16")
                                   .asCompatibleSubstituteFor("postgres"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host",     REDIS::getHost);
        registry.add("spring.data.redis.port",     () -> REDIS.getMappedPort(6379));
    }
}
