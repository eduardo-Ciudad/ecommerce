package com.eduardo.ecomerce.infra.security;

import com.eduardo.ecomerce.controller.CouponController;
import com.eduardo.ecomerce.dto.output.coupon.CouponOutput;
import com.eduardo.ecomerce.dto.output.coupon.CouponValidationOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Atenção: o RateLimitFilter limita /coupons a 10 requisições/min por IP.
 * Todos os testes desta classe compartilham o mesmo contexto (e o mesmo IP do MockMvc),
 * então mantenha o total de requisições abaixo de 10 ou separe em outra classe.
 */
@WebMvcTest(
        controllers = CouponController.class,
        properties = "app.cors.allowed-origins=http://localhost"
)
@Import({SecurityConfig.class, RateLimitFilter.class, JwtFilter.class})
class CouponSecurityTest {

    private static final String VALID_BODY = """
            {"code": "teste10", "discountPercent": 10}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private CouponService couponService;

    @Test
    @DisplayName("Cliente autenticado consegue validar cupom no checkout")
    void clientCanValidateCoupon() throws Exception {
        when(couponService.validateForCheckout("teste10"))
                .thenReturn(new CouponValidationOutput("TESTE10", new BigDecimal("10.00")));

        mockMvc.perform(get("/coupons/validate").param("code", "teste10")
                        .with(user("cliente").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TESTE10"))
                .andExpect(jsonPath("$.discountPercent").value(10.00));
    }

    @Test
    @DisplayName("Cupom inválido no checkout retorna 422")
    void invalidCouponReturnsUnprocessable() throws Exception {
        when(couponService.validateForCheckout("errado"))
                .thenThrow(new BusinessException("Cupom inválido ou expirado"));

        mockMvc.perform(get("/coupons/validate").param("code", "errado")
                        .with(user("cliente").roles("CLIENT")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Validar cupom sem login é bloqueado")
    void validateRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/coupons/validate").param("code", "teste10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cliente não consegue criar cupom")
    void clientCannotCreateCoupon() throws Exception {
        mockMvc.perform(post("/coupons").with(user("cliente").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(couponService, never()).create(any());
    }

    @Test
    @DisplayName("Cliente não consegue listar cupons")
    void clientCannotListCoupons() throws Exception {
        mockMvc.perform(get("/coupons").with(user("cliente").roles("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin cria cupom com sucesso")
    void adminCanCreateCoupon() throws Exception {
        when(couponService.create(any())).thenReturn(
                new CouponOutput(UUID.randomUUID(), "TESTE10", new BigDecimal("10.00"), true, LocalDateTime.now()));

        mockMvc.perform(post("/coupons").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TESTE10"));
    }

    @Test
    @DisplayName("Desconto acima de 90% é rejeitado na validação do DTO")
    void rejectsDiscountAboveLimit() throws Exception {
        mockMvc.perform(post("/coupons").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "TUDO", "discountPercent": 95}
                                """))
                .andExpect(status().isBadRequest());

        verify(couponService, never()).create(any());
    }
}