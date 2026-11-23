package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.payment.InfinitePayCheckoutInput;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayReturnInput;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayCheckoutOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.GlobalExceptionHandler;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayException;
import com.eduardo.ecomerce.service.payment.InfinitePayCheckoutService;
import com.eduardo.ecomerce.service.payment.InfinitePayConfirmation;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InfinitePayControllerTest {

    private static final String EMAIL = "maria@teste.com";
    private static final UUID ORDER_ID = UUID.fromString("d3aae31d-4465-408c-b942-8273b9bc622b");
    private static final String WEBHOOK_BODY = """
            {"invoice_slug":"abc","amount":4990,"paid_amount":5120,"installments":3,
             "capture_method":"credit_card","transaction_nsu":"trx-1",
             "order_nsu":"d3aae31d-4465-408c-b942-8273b9bc622b","receipt_url":"https://x","items":[]}
            """;

    private InfinitePayCheckoutService service;
    private MockMvc mockMvc;
    private final Principal principal = () -> EMAIL;

    @BeforeEach
    void setUp() {
        service = mock(InfinitePayCheckoutService.class);
        mockMvc = standaloneSetup(new InfinitePayController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------- checkout ----------

    @Test
    void checkout_valido_retornaUrl() throws Exception {
        when(service.createCheckout(any(InfinitePayCheckoutInput.class), eqEmail()))
                .thenReturn(new InfinitePayCheckoutOutput("https://checkout.infinitepay.io/vanda/abc"));

        mockMvc.perform(post("/payments/infinitepay/checkout").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","payerDocType":"CPF","payerDocNumber":"529.982.247-25",
                                 "payerPhone":"(17) 99999-0000"}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.infinitepay.io/vanda/abc"));
    }

    @Test
    void checkout_cpfInvalido_retorna400SemChamarServico() throws Exception {
        mockMvc.perform(post("/payments/infinitepay/checkout").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":"%s","payerDocType":"CPF","payerDocNumber":"111.111.111-11",
                                 "payerPhone":"(17) 99999-0000"}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.payerDocNumber").value("CPF inválido"));

        verifyNoInteractions(service);
    }

    // ---------- webhook ----------

    @Test
    void webhook_pago_retorna200EChamaConfirmComSlugDaFatura() throws Exception {
        when(service.confirm(ORDER_ID.toString(), "trx-1", "abc")).thenReturn(InfinitePayConfirmation.PAID);

        performWebhook().andExpect(status().isOk());

        verify(service).confirm(ORDER_ID.toString(), "trx-1", "abc");
    }

    @Test
    void webhook_naoPago_retorna200() throws Exception {
        when(service.confirm(anyString(), anyString(), anyString())).thenReturn(InfinitePayConfirmation.NOT_PAID);

        performWebhook().andExpect(status().isOk());
    }

    @Test
    void webhook_falhaNaInfinitePay_retorna400ParaReenvio() throws Exception {
        when(service.confirm(anyString(), anyString(), anyString()))
                .thenThrow(new InfinitePayException("Falha de comunicação com a InfinitePay"));

        performWebhook().andExpect(status().isBadRequest());
    }

    @Test
    void webhook_erroDeNegocio_retorna200SemReenvio() throws Exception {
        when(service.confirm(anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException("Valor do pagamento não corresponde ao pedido"));

        performWebhook().andExpect(status().isOk());
    }

    @Test
    void webhook_pedidoNaoEncontrado_retorna200() throws Exception {
        when(service.confirm(anyString(), anyString(), anyString()))
                .thenThrow(new EntityNotFoundException("Pedido não encontrado"));

        performWebhook().andExpect(status().isOk());
    }

    @Test
    void webhook_erroInesperado_retorna400() throws Exception {
        when(service.confirm(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("erro inesperado"));

        performWebhook().andExpect(status().isBadRequest());
    }

    // ---------- confirm (página de retorno) ----------

    @Test
    void confirm_retornaStatusDaConfirmacao() throws Exception {
        when(service.confirmForCustomer(new InfinitePayReturnInput(ORDER_ID.toString(), "trx-1", "abc"), EMAIL))
                .thenReturn(InfinitePayConfirmation.PAID);

        mockMvc.perform(post("/payments/infinitepay/confirm").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderNsu":"%s","transactionNsu":"trx-1","slug":"abc"}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    private org.springframework.test.web.servlet.ResultActions performWebhook() throws Exception {
        return mockMvc.perform(post("/payments/infinitepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WEBHOOK_BODY));
    }

    private static String eqEmail() {
        return org.mockito.ArgumentMatchers.eq(EMAIL);
    }
}
