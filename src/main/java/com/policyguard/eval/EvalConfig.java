package com.policyguard.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyguard.fixtures.GoldSetLoader;
import com.policyguard.fixtures.PolicyFixtureFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides {@link PolicyFixtureFactory} and {@link GoldSetLoader} as Spring
 * beans so {@link EvalHarness} can be constructor-injected.
 */
@Configuration
public class EvalConfig {

    @Bean
    public PolicyFixtureFactory policyFixtureFactory() {
        return new PolicyFixtureFactory();
    }

    @Bean
    public GoldSetLoader goldSetLoader(ObjectMapper objectMapper) {
        return new GoldSetLoader(objectMapper);
    }
}
