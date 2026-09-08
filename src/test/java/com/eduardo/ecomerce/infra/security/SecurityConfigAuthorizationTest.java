package com.eduardo.ecomerce.infra.security;

import com.eduardo.ecomerce.controller.AuthController;
import com.eduardo.ecomerce.controller.PaymentController;
import com.eduardo.ecomerce.controller.ProductController;
import com.eduardo.ecomerce.controller.OrderController;
import com.eduardo.ecomerce.dto.output.common.PageResponse;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.infra.payment.WebhookSignatureValidator;
import com.eduardo.ecomerce.service.AuthService;
import com.eduardo.ecomerce.service.OrderService;
import com.eduardo.ecomerce.service.PaymentService;
import com.eduardo.ecomerce.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;

@WebMvcTest(
        controllers = {ProductController.class, AuthController.class, PaymentController.class, OrderController.class},
        properties = "app.cors.allowed-origins=http://localhost"
)
@Import({SecurityConfig.class, RateLimitFilter.class, JwtFilter.class})
class SecurityConfigAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private WebhookSignatureValidator webhookSignatureValidator;

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizationMatrix")
    @DisplayName("Deve preservar a matriz de autorização dos principais endpoints")
    void preservesAuthorizationMatrix(
            String scenario,
            MockHttpServletRequestBuilder request,
            int expectedStatus
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("GET /orders/admin deve bloquear cliente sem papel ADMIN")
    void adminOrdersRejectsNonAdminUser() throws Exception {
        mockMvc.perform(get("/orders/admin").with(user("cliente").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /orders/admin deve retornar pedidos de usuários distintos ao ADMIN")
    void adminOrdersReturnsOrdersFromDifferentUsers() throws Exception {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        OrderOutput firstOrder = orderOutput(UUID.randomUUID(), firstUser);
        OrderOutput secondOrder = orderOutput(UUID.randomUUID(), secondUser);
        when(orderService.findAll(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResponse<>(List.of(firstOrder, secondOrder), 0, 20, 2, 1));

        mockMvc.perform(get("/orders/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(firstUser.toString()))
                .andExpect(jsonPath("$.content[1].userId").value(secondUser.toString()));
    }

    private static OrderOutput orderOutput(UUID orderId, UUID userId) {
        return new OrderOutput(
                orderId, userId, java.math.BigDecimal.TEN,
                com.eduardo.ecomerce.domain.order.OrderStatus.PENDING,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), java.time.LocalDateTime.now()
        );
    }

    private static Stream<Arguments> authorizationMatrix() {
        return Stream.of(
                Arguments.of(
                        "produto público sem autenticação",
                        get("/products"),
                        HttpStatus.OK.value()
                ),
                Arguments.of(
                        "alteração de senha sem autenticação",
                        post("/auth/change-password"),
                        // O comportamento atual é 403; retornar 401 seria mais apropriado,
                        // mas exigiria uma mudança funcional fora do escopo desta correção.
                        HttpStatus.FORBIDDEN.value()
                ),
                Arguments.of(
                        "criação de produto por usuário sem papel ADMIN",
                        post("/products").with(user("cliente").roles("USER")),
                        HttpStatus.FORBIDDEN.value()
                ),
                Arguments.of(
                        "webhook de pagamento sem autenticação",
                        post("/payments/webhook"),
                        HttpStatus.BAD_REQUEST.value()
                )
        );
    }
}
