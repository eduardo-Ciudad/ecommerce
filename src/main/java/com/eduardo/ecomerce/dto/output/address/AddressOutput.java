package com.eduardo.ecomerce.dto.output.address;

import java.util.UUID;

public record AddressOutput(
        UUID id,
        String label,
        String cep,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        Boolean isDefault
) {
}
