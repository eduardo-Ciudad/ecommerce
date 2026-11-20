package com.eduardo.ecomerce.dto.input.register;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterInputValidationTest {

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

    private static RegisterInput comNome(String name) {
        return new RegisterInput(name, "cliente@teste.com", "123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria", "  Maria  ", "Maria "})
    void nomeSemSobrenome_rejeitado(String name) {
        Set<ConstraintViolation<RegisterInput>> violations = validator.validate(comNome(name));

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Informe nome e sobrenome");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria Silva", "Ana Paula Souza", "  Maria   Silva  "})
    void nomeComSobrenome_aceito(String name) {
        assertThat(validator.validate(comNome(name))).isEmpty();
    }
}
