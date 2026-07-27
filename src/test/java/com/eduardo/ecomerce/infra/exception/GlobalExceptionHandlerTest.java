package com.eduardo.ecomerce.infra.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("EntityNotFoundException deve retornar 404 com mensagem")
    void entityNotFound_returns404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleEntityNotFound(new EntityNotFoundException("Pedido não encontrado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Pedido não encontrado");
    }

    @Test
    @DisplayName("IllegalArgumentException deve retornar 400 com mensagem")
    void illegalArgument_returns400() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Arquivo não pode ser vazio"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Arquivo não pode ser vazio");
    }

    @Test
    @DisplayName("Exception genérica deve retornar 500 com mensagem genérica, sem stack trace")
    void genericException_returns500_withoutStackTrace() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGeneric(new RuntimeException("NullPointerException at com.internal.Class:42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Erro interno do servidor");
        assertThat(response.getBody().get("error")).doesNotContain("NullPointerException");
        assertThat(response.getBody().get("error")).doesNotContain("com.internal");
    }

    @Test
    @DisplayName("ResourceNotFoundException deve retornar 404")
    void resourceNotFound_returns404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new ResourceNotFoundException("Categoria não encontrada"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Categoria não encontrada");
    }

    @Test
    @DisplayName("BusinessException deve retornar 422")
    void businessException_returns422() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBusiness(new BusinessException("Estoque insuficiente"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("error", "Estoque insuficiente");
    }
}
