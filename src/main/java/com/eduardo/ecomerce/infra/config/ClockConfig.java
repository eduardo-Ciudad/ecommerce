package com.eduardo.ecomerce.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Mesmo fuso usado hoje pelos LocalDateTime.now(); injetável para testes determinísticos. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
