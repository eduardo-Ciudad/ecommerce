package com.eduardo.ecomerce.dto.input.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordInput(
        @NotBlank(message = "Token é obrigatório")
        String token,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, max = 72, message = "Nova senha deve ter entre 6 e 72 caracteres")        String newPassword
) {
}
