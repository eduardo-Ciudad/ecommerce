package com.eduardo.ecomerce.dto.output.shipping;

import java.math.BigDecimal;
import java.util.Set;

public record FreeShippingOutput(
        BigDecimal threshold,
        Set<String> methods
) {}
