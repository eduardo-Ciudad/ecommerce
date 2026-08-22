package com.eduardo.ecomerce.infra.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter("127.0.0.1");
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Deve aplicar rate limit para /auth")
    void rateLimitAppliedToAuth() throws Exception {
        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr("192.168.1.1");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("192.168.1.1");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Deve aplicar rate limit para /payments")
    void rateLimitAppliedToPayments() throws Exception {
        for (int i = 0; i < 5; i++) {
            var request = new MockHttpServletRequest("POST", "/payments/process");
            request.setRemoteAddr("10.0.0.1");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        var request = new MockHttpServletRequest("POST", "/payments/process");
        request.setRemoteAddr("10.0.0.1");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Deve aplicar rate limit para /orders")
    void rateLimitAppliedToOrders() throws Exception {
        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/orders");
            request.setRemoteAddr("10.0.0.2");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        var request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("10.0.0.2");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Deve aplicar rate limit para /cart")
    void rateLimitAppliedToCart() throws Exception {
        for (int i = 0; i < 30; i++) {
            var request = new MockHttpServletRequest("POST", "/cart/items");
            request.setRemoteAddr("10.0.0.3");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        var request = new MockHttpServletRequest("POST", "/cart/items");
        request.setRemoteAddr("10.0.0.3");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Endpoints fora do mapa não devem ser limitados")
    void noRateLimitForUnmappedEndpoints() throws Exception {
        for (int i = 0; i < 50; i++) {
            var request = new MockHttpServletRequest("GET", "/products");
            request.setRemoteAddr("10.0.0.4");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(filterChain, times(50)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Deve extrair IP de X-Forwarded-For quando presente")
    void extractsIpFromXForwardedFor() throws Exception {
        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);

        // Different real IP via XFF should still have tokens
        var request2 = new MockHttpServletRequest("POST", "/auth/login");
        request2.setRemoteAddr("127.0.0.1");
        request2.addHeader("X-Forwarded-For", "198.51.100.1");
        var response2 = new MockHttpServletResponse();
        filter.doFilterInternal(request2, response2, filterChain);

        assertThat(response2.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Deve extrair IP de X-Real-IP como fallback quando X-Forwarded-For ausente")
    void extractsIpFromXRealIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Real-IP", "198.51.100.99");
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.99");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Deve ignorar headers de IP enviados por origem não confiável")
    void ignoresSpoofedForwardedHeadersFromUntrustedOrigin() throws Exception {
        filter = new RateLimitFilter("127.0.0.1");

        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr("198.51.100.10");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            request.addHeader("X-Real-IP", "192.0.2." + i);
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.250");
        request.addHeader("X-Real-IP", "192.0.2.250");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Rate limits de endpoints diferentes devem ser independentes")
    void rateLimitsAreIndependentPerEndpoint() throws Exception {
        String ip = "10.0.0.5";

        // Esgotar o bucket de /payments (5 requests)
        for (int i = 0; i < 5; i++) {
            var request = new MockHttpServletRequest("POST", "/payments/process");
            request.setRemoteAddr(ip);
            var response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // /auth do mesmo IP ainda deve funcionar
        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
