package com.policyguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.policyguard.config.PolicyguardProperties;

@SpringBootApplication
@EnableConfigurationProperties(PolicyguardProperties.class)
public class PolicyguardApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyguardApplication.class, args);
    }
}
