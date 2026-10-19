package com.eduardo.ecomerce.dto.input.payment;

import com.eduardo.ecomerce.infra.validation.DocumentNumbers;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PayerDocumentValidator implements ConstraintValidator<ValidPayerDocument, PaymentInput> {

    @Override
    public boolean isValid(PaymentInput input, ConstraintValidatorContext context) {
        if (input == null || isBlank(input.payerDocType()) || isBlank(input.payerDocNumber())) {
            // campo ausente é responsabilidade do @NotBlank (evita erro duplicado)
            return true;
        }

        String digits = DocumentNumbers.onlyDigits(input.payerDocNumber());
        boolean valid;
        String message;
        switch (input.payerDocType()) {
            case "CPF" -> {
                valid = DocumentNumbers.isValidCpf(digits);
                message = "CPF inválido";
            }
            case "CNPJ" -> {
                valid = DocumentNumbers.isValidCnpj(digits);
                message = "CNPJ inválido";
            }
            default -> {
                // tipo desconhecido é responsabilidade do @Pattern
                return true;
            }
        }

        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("payerDocNumber")
                    .addConstraintViolation();
        }
        return valid;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
