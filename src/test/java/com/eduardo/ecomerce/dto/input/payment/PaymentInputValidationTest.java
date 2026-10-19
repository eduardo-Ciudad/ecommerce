package com.eduardo.ecomerce.dto.input.payment;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentInputValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static PaymentInput pix(String docType, String docNumber) {
        return new PaymentInput(UUID.randomUUID(), "pix", null, null, null, null, docType, docNumber, null);
    }

    @Test
    void pixComCpfValido_semViolacao() {
        assertThat(validator.validate(pix("CPF", "529.982.247-25"))).isEmpty();
    }

    @Test
    void cnpjValido_semViolacao() {
        assertThat(validator.validate(pix("CNPJ", "11.222.333/0001-81"))).isEmpty();
    }

    @Test
    void semNumeroDeDocumento_umaViolacaoSoDoNotBlank() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix("CPF", null));

        assertThat(violations).hasSize(1);
        ConstraintViolation<PaymentInput> v = violations.iterator().next();
        assertThat(v.getPropertyPath().toString()).isEqualTo("payerDocNumber");
        assertThat(v.getMessage()).isEqualTo("CPF/CNPJ é obrigatório");
    }

    @Test
    void semTipoDeDocumento_umaViolacaoNoTipo() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix(null, "52998224725"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("payerDocType");
    }

    @Test
    void cpfInvalido_violacaoCpfInvalidoNoCampoDoNumero() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix("CPF", "529.982.247-26"));

        assertThat(violations).hasSize(1);
        ConstraintViolation<PaymentInput> v = violations.iterator().next();
        assertThat(v.getPropertyPath().toString()).isEqualTo("payerDocNumber");
        assertThat(v.getMessage()).isEqualTo("CPF inválido");
    }

    @Test
    void tipoCpfComNumeroDeCnpj_violacao() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix("CPF", "11222333000181"));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("CPF inválido");
    }

    @Test
    void cnpjInvalido_violacaoCnpjInvalido() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix("CNPJ", "11222333000182"));

        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("CNPJ inválido");
    }

    @Test
    void tipoDesconhecido_soViolacaoDoPattern() {
        Set<ConstraintViolation<PaymentInput>> violations = validator.validate(pix("RG", "123456789"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("payerDocType");
    }
}
