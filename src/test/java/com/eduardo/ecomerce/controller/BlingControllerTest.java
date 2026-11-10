package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunStatus;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncStep;
import com.eduardo.ecomerce.dto.output.bling.BlingSyncStatusOutput;
import com.eduardo.ecomerce.dto.output.bling.SyncProductsResult;
import com.eduardo.ecomerce.infra.bling.BlingIntegrationException;
import com.eduardo.ecomerce.infra.exception.GlobalExceptionHandler;
import com.eduardo.ecomerce.service.BlingService;
import com.eduardo.ecomerce.service.bling.BlingFullSyncService;
import com.eduardo.ecomerce.service.bling.BlingSyncAlreadyRunningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BlingControllerTest {

    private BlingService blingService;
    private BlingFullSyncService blingFullSyncService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        blingService = mock(BlingService.class);
        blingFullSyncService = mock(BlingFullSyncService.class);
        mockMvc = standaloneSetup(new BlingController(blingService, blingFullSyncService))
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

    @Test
    void startFullSyncReturnsAcceptedWithTheRunningStatus() throws Exception {
        when(blingFullSyncService.start()).thenReturn(syncStatus(BlingSyncRunStatus.RUNNING, BlingSyncStep.CATEGORIES));

        mockMvc.perform(post("/bling/sync/full"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.step").value("CATEGORIES"));

        verify(blingFullSyncService).start();
        verifyNoInteractions(blingService);
    }

    @Test
    void startFullSyncWhileAnotherIsRunningReturnsConflict() throws Exception {
        when(blingFullSyncService.start()).thenThrow(new BlingSyncAlreadyRunningException());

        mockMvc.perform(post("/bling/sync/full"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void syncStatusReturnsTheLatestRun() throws Exception {
        when(blingFullSyncService.getLatestStatus())
                .thenReturn(Optional.of(syncStatus(BlingSyncRunStatus.SUCCESS, BlingSyncStep.DONE)));

        mockMvc.perform(get("/bling/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.processedItems").value(10));
    }

    @Test
    void syncStatusReturnsNoContentWhenNoSyncEverRan() throws Exception {
        when(blingFullSyncService.getLatestStatus()).thenReturn(Optional.empty());

        mockMvc.perform(get("/bling/sync/status"))
                .andExpect(status().isNoContent());
    }

    private static BlingSyncStatusOutput syncStatus(BlingSyncRunStatus status, BlingSyncStep step) {
        return new BlingSyncStatusOutput(
                UUID.randomUUID(), status, step, LocalDateTime.of(2026, 11, 10, 14, 0), null,
                29, 10, 10, 8, 1, 1, 0, 0, null, List.of()
        );
    }
}
