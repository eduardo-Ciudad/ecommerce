package com.eduardo.ecomerce.dto.input.order;

import com.eduardo.ecomerce.domain.order.LocalPaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateLocalSaleInputValidationTest {

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

    private static CreateLocalSaleInput input(String name, String phone, String document) {
        return new CreateLocalSaleInput(
                List.of(new CreateLocalSaleInput.Item(UUID.randomUUID(), 1)),
                LocalPaymentMethod.CASH, name, phone, document, null);
    }

    private static Set<String> invalidFields(CreateLocalSaleInput input) {
        Set<ConstraintViolation<CreateLocalSaleInput>> violations = validator.validate(input);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void nomeECelular_semCpf_valido() {
        assertThat(validator.validate(input("Cris vizinha", "(17) 99624-7172", null))).isEmpty();
    }

    @Test
    void comCpf_valido() {
        assertThat(validator.validate(input("Cris vizinha", "17996247172", "529.982.247-25"))).isEmpty();
    }

    @Test
    void semNome_invalido() {
        assertThat(invalidFields(input("  ", "(17) 99624-7172", null))).containsExactly("customerName");
    }

    @Test
    void semCelular_invalido() {
        assertThat(invalidFields(input("Cris vizinha", null, null))).containsExactly("customerPhone");
    }

    @Test
    void celularEmFormatoInvalido_invalido() {
        assertThat(invalidFields(input("Cris vizinha", "9962-71", null))).containsExactly("customerPhone");
    }

    private static Set<String> invalidFieldsForPrice(String price) {
        CreateLocalSaleInput input = new CreateLocalSaleInput(
                List.of(new CreateLocalSaleInput.Item(UUID.randomUUID(), 1, price == null ? null : new BigDecimal(price))),
                LocalPaymentMethod.CASH, "Cris vizinha", "17996247172", null, null);
        return invalidFields(input);
    }

    @Test
    void precoManualNulo_valido() {
        assertThat(invalidFieldsForPrice(null)).isEmpty();
    }

    @Test
    void precoManualPositivo_valido() {
        assertThat(invalidFieldsForPrice("39.90")).isEmpty();
    }

    @Test
    void precoManualZero_invalido() {
        assertThat(invalidFieldsForPrice("0.00")).containsExactly("items[0].unitPrice");
    }

    @Test
    void precoManualNegativo_invalido() {
        assertThat(invalidFieldsForPrice("-1.00")).containsExactly("items[0].unitPrice");
    }

    @Test
    void precoManualComTresCasas_invalido() {
        assertThat(invalidFieldsForPrice("39.999")).containsExactly("items[0].unitPrice");
    }
}
