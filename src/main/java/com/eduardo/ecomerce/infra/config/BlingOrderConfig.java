package com.eduardo.ecomerce.infra.config;

import com.eduardo.ecomerce.infra.bling.BlingOrderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BlingOrderProperties.class)
public class BlingOrderConfig {
}
