package com.eduardo.ecomerce.dto.input.coupon;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CouponInput(
        @NotBlank(message = "Código é obrigatório")
        @Size(min = 3, max = 50, message = "Código deve ter entre 3 e 50 caracteres")
        @Pattern(regexp = "^\\s*[A-Za-z0-9_-]+\\s*$",
                message = "Código deve conter apenas letras, números, hífen ou underline (sem espaços ou acentos)")
        String code,

        @NotNull(message = "Percentual de desconto é obrigatório")
        @DecimalMin(value = "1.00", message = "Desconto mínimo é 1%")
        @DecimalMax(value = "90.00", message = "Desconto máximo é 90%")
        @Digits(integer = 2, fraction = 2, message = "Percentual deve ter no máximo 2 casas decimais")
        BigDecimal discountPercent,

        Boolean active
) {
}
