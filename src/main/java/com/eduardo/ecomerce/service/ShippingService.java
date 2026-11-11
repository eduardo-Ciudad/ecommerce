package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.dto.output.shipping.FreeShippingOutput;
import com.eduardo.ecomerce.dto.output.shipping.ShippingOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.shipping.CorreiosClient;
import com.eduardo.ecomerce.infra.shipping.FreeShippingPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final CorreiosClient correiosClient;
    private final FreeShippingPolicy freeShippingPolicy;

    @Value("${correios.cep-origem}")
    private String cepOrigem;

    private static final int COMPRIMENTO_CM = 20;
    private static final int ALTURA_CM = 7;
    private static final int LARGURA_CM = 16;
    private static final int PESO_PADRAO_GRAMAS = 500;

    private static final String CODIGO_PAC = "03298";
    private static final String CODIGO_SEDEX = "03220";

    public List<ShippingOutput> calculate(String cepDestino) {
        return calculate(cepDestino, null);
    }

    /**
     * Cotação para exibição no carrinho/checkout. productsTotal (produtos já com cupom) só decide
     * se a modalidade aparece como grátis; o valor cobrado é recalculado na criação do pedido.
     */
    public List<ShippingOutput> calculate(String cepDestino, BigDecimal productsTotal) {
        String cepLimpo = cepDestino.replace("-", "");

        ShippingOutput pac = freeShippingPolicy.apply(calcularModalidade(CODIGO_PAC, "PAC", cepLimpo), productsTotal);
        ShippingOutput sedex = freeShippingPolicy.apply(calcularModalidade(CODIGO_SEDEX, "SEDEX", cepLimpo), productsTotal);

        return List.of(pac, sedex);
    }

    public FreeShippingOutput freeShippingRule() {
        return new FreeShippingOutput(freeShippingPolicy.threshold(), freeShippingPolicy.methods());
    }

    public ShippingOutput calculateByMethod(String cepDestino, String method) {
        String cepLimpo = cepDestino.replace("-", "");
        String codigo = "SEDEX".equalsIgnoreCase(method) ? CODIGO_SEDEX : CODIGO_PAC;
        String label = "SEDEX".equalsIgnoreCase(method) ? "SEDEX" : "PAC";
        return calcularModalidade(codigo, label, cepLimpo);
    }

    private ShippingOutput calcularModalidade(String coProduto, String label, String cepDestino) {
        try {
            JsonNode preco = correiosClient.consultarPreco(
                    coProduto, cepOrigem, cepDestino,
                    PESO_PADRAO_GRAMAS, COMPRIMENTO_CM, ALTURA_CM, LARGURA_CM
            );
            JsonNode prazo = correiosClient.consultarPrazo(coProduto, cepOrigem, cepDestino);

            BigDecimal price = new BigDecimal(preco.get("pcFinal").asText().replace(",", "."));
            Integer deadline = prazo.get("prazoEntrega").asInt();

            return new ShippingOutput(coProduto.equals(CODIGO_SEDEX) ? "SEDEX" : "PAC", label, price, deadline);
        } catch (Exception e) {
            log.error("Erro ao consultar frete Correios — modalidade: {}, cep: {}, erro: {}", label, cepDestino, e.getMessage());
            throw new BusinessException("Não foi possível calcular o frete no momento");
        }
    }
}