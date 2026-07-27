package com.eduardo.ecomerce.dto.input.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordInput(
        @NotBlank(message = "Senha atual é obrigatória")
        @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, max = 72, message = "Nova senha deve ter entre 6 e 72 caracteres")
        String newPassword
) {
}
