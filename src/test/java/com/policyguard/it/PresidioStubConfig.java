package com.policyguard.it;

import java.util.List;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.policyguard.service.pii.PresidioClient;

/**
 * Replaces {@link PresidioClient} under {@code @Profile("test")} with a no-op
 * Mockito mock that returns zero entities — i.e. text passes through unredacted.
 * Imported by {@link BaseIT} so it applies to the entire IT suite.
 */
@TestConfiguration
@Profile("test")
public class PresidioStubConfig {

    @Bean
    @Primary
    public PresidioClient presidioClient() {
        PresidioClient mock = Mockito.mock(PresidioClient.class);
        Mockito.lenient()
               .when(mock.analyze(Mockito.anyString(), Mockito.anyString()))
               .thenReturn(List.of());
        return mock;
    }
}
