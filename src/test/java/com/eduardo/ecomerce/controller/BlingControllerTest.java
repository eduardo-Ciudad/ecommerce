package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.output.bling.SyncProductsResult;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.eduardo.ecomerce.infra.exception.GlobalExceptionHandler;
import com.eduardo.ecomerce.service.BlingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BlingControllerTest {

    private BlingService blingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        blingService = mock(BlingService.class);
        mockMvc = standaloneSetup(new BlingController(blingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void callbackWithoutStateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/bling/callback").param("code", "code"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(blingService);
    }

    @Test
    void invalidStateIsValidatedBeforeAuthorizationCodeExchange() throws Exception {
        doThrow(new BlingIntegrationException("state inválido", null))
                .when(blingService).validateState("expired");

        mockMvc.perform(get("/bling/callback")
                        .param("code", "code")
                        .param("state", "expired"))
                .andExpect(status().isInternalServerError());

        verify(blingService).validateState("expired");
        verify(blingService, never()).handleAuthorizationCode(anyString());
    }

    @Test
    void validStateCompletesOAuthFlowInOrder() throws Exception {
        mockMvc.perform(get("/bling/callback")
                        .param("code", "code")
                        .param("state", "valid"))
                .andExpect(status().isOk());

        InOrder order = inOrder(blingService);
        order.verify(blingService).validateState("valid");
        order.verify(blingService).handleAuthorizationCode("code");
    }

    @Test
    void syncProductsWithoutMaxPagesDelegatesToFullSync() throws Exception {
        when(blingService.syncProducts()).thenReturn(new SyncProductsResult(0, 0, 0, 0, 0));

        mockMvc.perform(post("/bling/sync/products"))
                .andExpect(status().isOk());

        verify(blingService).syncProducts();
        verify(blingService, never()).syncProducts(anyInt());
    }
}
