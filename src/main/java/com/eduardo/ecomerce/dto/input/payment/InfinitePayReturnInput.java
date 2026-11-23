package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Parâmetros que a InfinitePay acrescenta à redirect_url, repassados pela página de retorno. */
public record InfinitePayReturnInput(
        @NotBlank @Size(max = 100) String orderNsu,
        @NotBlank @Size(max = 100) String transactionNsu,
        @NotBlank @Size(max = 100) String slug
) {
}
