package com.eduardo.ecomerce.dto.input.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgetPasswordInput(
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {
}
