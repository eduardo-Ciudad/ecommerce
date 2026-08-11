package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.shipping.CorreiosClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    private static final String CODIGO_PAC = "03298";
    private static final String CODIGO_SEDEX = "03220";
    private static final String CEP_ORIGEM = "15046806";

    @Mock
    private CorreiosClient correiosClient;

    @InjectMocks
    private ShippingService shippingService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shippingService, "cepOrigem", CEP_ORIGEM);
    }

    private ObjectNode preco(String pcFinal) {
        return mapper.createObjectNode().put("pcFinal", pcFinal);
    }

    private ObjectNode prazo(int prazoEntrega) {
        return mapper.createObjectNode().put("prazoEntrega", prazoEntrega);
    }

    // -------------------------------------------------------------------------
    // calculate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculate — deve retornar PAC e SEDEX com preço e prazo corretos")
    void calculate_success() {
        when(correiosClient.consultarPreco(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("25,50"));
        when(correiosClient.consultarPrazo(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(7));
        when(correiosClient.consultarPreco(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("45,90"));
        when(correiosClient.consultarPrazo(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(2));

        List<ShippingOutput> result = shippingService.calculate("01310-100");

        assertThat(result).hasSize(2);

        ShippingOutput pac = result.get(0);
        assertThat(pac.method()).isEqualTo("PAC");
        assertThat(pac.price()).isEqualByComparingTo(new BigDecimal("25.50"));
        assertThat(pac.deadlineDays()).isEqualTo(7);

        ShippingOutput sedex = result.get(1);
        assertThat(sedex.method()).isEqualTo("SEDEX");
        assertThat(sedex.price()).isEqualByComparingTo(new BigDecimal("45.90"));
        assertThat(sedex.deadlineDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculate — deve remover o hífen do CEP de destino antes de consultar os Correios")
    void calculate_stripsHyphenFromCep() {
        when(correiosClient.consultarPreco(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("20,00"));
        when(correiosClient.consultarPrazo(anyString(), anyString(), anyString()))
                .thenReturn(prazo(5));

        shippingService.calculate("01310-100");

        verify(correiosClient, times(2))
                .consultarPreco(anyString(), eq(CEP_ORIGEM), eq("01310100"), anyInt(), anyInt(), anyInt(), anyInt());
        verify(correiosClient, times(2))
                .consultarPrazo(anyString(), eq(CEP_ORIGEM), eq("01310100"));
    }

    @Test
    @DisplayName("calculate — deve embrulhar falha dos Correios em BusinessException")
    void calculate_correiosFailure_wrapsInBusinessException() {
        when(correiosClient.consultarPreco(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> shippingService.calculate("01310-100"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não foi possível calcular o frete no momento");
    }

    @Test
    @DisplayName("calculate — deve embrulhar resposta malformada dos Correios em BusinessException")
    void calculate_malformedResponse_wrapsInBusinessException() {
        when(correiosClient.consultarPreco(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(mapper.createObjectNode()); // sem o campo "pcFinal"

        assertThatThrownBy(() -> shippingService.calculate("01310-100"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não foi possível calcular o frete no momento");
    }

    // -------------------------------------------------------------------------
    // calculateByMethod
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculateByMethod — deve calcular SEDEX quando método é SEDEX")
    void calculateByMethod_sedex() {
        when(correiosClient.consultarPreco(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("38,00"));
        when(correiosClient.consultarPrazo(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(3));

        ShippingOutput output = shippingService.calculateByMethod("15046-806", "SEDEX");

        assertThat(output.method()).isEqualTo("SEDEX");
        assertThat(output.price()).isEqualByComparingTo(new BigDecimal("38.00"));
        assertThat(output.deadlineDays()).isEqualTo(3);
        verify(correiosClient, never()).consultarPreco(eq(CODIGO_PAC), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("calculateByMethod — deve ser case-insensitive para o método SEDEX")
    void calculateByMethod_sedexLowercase() {
        when(correiosClient.consultarPreco(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("38,00"));
        when(correiosClient.consultarPrazo(eq(CODIGO_SEDEX), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(3));

        ShippingOutput output = shippingService.calculateByMethod("15046-806", "sedex");

        assertThat(output.method()).isEqualTo("SEDEX");
    }

    @Test
    @DisplayName("calculateByMethod — deve calcular PAC quando método é PAC")
    void calculateByMethod_pac() {
        when(correiosClient.consultarPreco(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("18,90"));
        when(correiosClient.consultarPrazo(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(8));

        ShippingOutput output = shippingService.calculateByMethod("15046-806", "PAC");

        assertThat(output.method()).isEqualTo("PAC");
        assertThat(output.price()).isEqualByComparingTo(new BigDecimal("18.90"));
    }

    @Test
    @DisplayName("calculateByMethod — deve cair para PAC quando o método é desconhecido")
    void calculateByMethod_unknownMethodDefaultsToPac() {
        when(correiosClient.consultarPreco(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(preco("18,90"));
        when(correiosClient.consultarPrazo(eq(CODIGO_PAC), eq(CEP_ORIGEM), anyString()))
                .thenReturn(prazo(8));

        ShippingOutput output = shippingService.calculateByMethod("15046-806", "expresso-magico");

        assertThat(output.method()).isEqualTo("PAC");
    }

    @Test
    @DisplayName("calculateByMethod — deve embrulhar falha dos Correios em BusinessException")
    void calculateByMethod_correiosFailure_wrapsInBusinessException() {
        when(correiosClient.consultarPreco(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("indisponível"));

        assertThatThrownBy(() -> shippingService.calculateByMethod("15046-806", "PAC"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não foi possível calcular o frete no momento");
    }
}
