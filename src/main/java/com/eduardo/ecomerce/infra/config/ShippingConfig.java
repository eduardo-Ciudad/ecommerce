package com.eduardo.ecomerce.infra.config;

import com.eduardo.ecomerce.infra.shipping.FreeShippingPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FreeShippingPolicy.class)
public class ShippingConfig {
}
