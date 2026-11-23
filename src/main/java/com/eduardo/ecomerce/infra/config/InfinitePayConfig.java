package com.eduardo.ecomerce.infra.config;

import com.eduardo.ecomerce.infra.infinitepay.InfinitePayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InfinitePayProperties.class)
public class InfinitePayConfig {
}
