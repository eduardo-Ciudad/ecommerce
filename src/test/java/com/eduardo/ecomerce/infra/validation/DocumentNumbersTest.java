package com.eduardo.ecomerce.infra.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentNumbersTest {

    // ---------- onlyDigits ----------

    @Test
    void onlyDigits_comMascara_retornaSoDigitos() {
        assertThat(DocumentNumbers.onlyDigits("529.982.247-25")).isEqualTo("52998224725");
        assertThat(DocumentNumbers.onlyDigits("11.222.333/0001-81")).isEqualTo("11222333000181");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ".-/", "abc"})
    void onlyDigits_semNenhumDigito_retornaNull(String value) {
        assertThat(DocumentNumbers.onlyDigits(value)).isNull();
    }

    // ---------- CPF ----------

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735"})
    void isValidCpf_cpfValido_true(String cpf) {
        assertThat(DocumentNumbers.isValidCpf(cpf)).isTrue();
    }

    @Test
    void isValidCpf_comMascaraAposOnlyDigits_true() {
        assertThat(DocumentNumbers.isValidCpf(DocumentNumbers.onlyDigits("529.982.247-25"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "52998224726", // 2º DV errado
            "52998224715", // 1º DV errado
            "11111111111", // todos iguais (passaria no cálculo)
            "00000000000",
            "5299822472",  // 10 dígitos
            "529982247250", // 12 dígitos
            "529.982.247-25" // com máscara: exige só dígitos
    })
    void isValidCpf_invalido_false(String cpf) {
        assertThat(DocumentNumbers.isValidCpf(cpf)).isFalse();
    }

    @Test
    void isValidCpf_nulo_false() {
        assertThat(DocumentNumbers.isValidCpf(null)).isFalse();
    }

    // ---------- CNPJ ----------

    @ParameterizedTest
    @ValueSource(strings = {"11222333000181", "42941150000140"})
    void isValidCnpj_cnpjValido_true(String cnpj) {
        assertThat(DocumentNumbers.isValidCnpj(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11222333000182", // DV errado
            "11111111111111", // todos iguais
            "1122233300018",  // 13 dígitos
            "52998224725"     // CPF não é CNPJ
    })
    void isValidCnpj_invalido_false(String cnpj) {
        assertThat(DocumentNumbers.isValidCnpj(cnpj)).isFalse();
    }

    @Test
    void isValidCnpj_nulo_false() {
        assertThat(DocumentNumbers.isValidCnpj(null)).isFalse();
    }
}
