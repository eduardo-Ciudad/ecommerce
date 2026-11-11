package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.output.shipping.FreeShippingOutput;
import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import com.eduardo.ecomerce.infra.exception.GlobalExceptionHandler;
import com.eduardo.ecomerce.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ShippingControllerTest {

    private ShippingService shippingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        shippingService = mock(ShippingService.class);
        mockMvc = standaloneSetup(new ShippingController(shippingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void calculate_comSubtotal_repassaParaOServicoEDevolveFreteGratis() throws Exception {
        when(shippingService.calculate("15046-806", new BigDecimal("210.00"))).thenReturn(List.of(
                new ShippingOutput("PAC", "PAC", BigDecimal.ZERO, 7, true)));

        mockMvc.perform(get("/shipping/calculate").param("cep", "15046-806").param("subtotal", "210.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(0))
                .andExpect(jsonPath("$[0].freeShipping").value(true));
    }

    @Test
    void calculate_semSubtotal_repassaNulo() throws Exception {
        when(shippingService.calculate("15046-806", null)).thenReturn(List.of(
                new ShippingOutput("PAC", "PAC", new BigDecimal("25.50"), 7)));

        mockMvc.perform(get("/shipping/calculate").param("cep", "15046-806"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].freeShipping").value(false));

        verify(shippingService).calculate("15046-806", null);
    }

    @Test
    void freeShipping_devolveThresholdEModalidades() throws Exception {
        when(shippingService.freeShippingRule())
                .thenReturn(new FreeShippingOutput(new BigDecimal("199.00"), Set.of("PAC")));

        mockMvc.perform(get("/shipping/free-shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold").value(199.00))
                .andExpect(jsonPath("$.methods[0]").value("PAC"));
    }
}
