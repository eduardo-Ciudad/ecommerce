package com.eduardo.ecomerce.controller;


import com.eduardo.ecomerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Integração com Mercado Pago")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Criar checkout", description = "Gera URL de pagamento no Mercado Pago para um pedido")
    @ApiResponse(responseCode = "200", description = "URL de checkout gerada")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @PostMapping("/checkout/{orderId}")
    public ResponseEntity<Map<String, String>> createCheckout(@PathVariable UUID orderId) {
        String checkoutUrl = paymentService.createCheckout(orderId);
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
    }

    @Operation(summary = "Webhook do Mercado Pago", description = "Recebe notificações de pagamento")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(required = false) String orderId,
            @RequestBody Map<String, Object> payload) {

        log.info("Webhook recebido: {}", payload);

        String type = (String) payload.get("type");

        if ("payment".equals(type)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String paymentId = String.valueOf(data.get("id"));

            paymentService.processWebhook(paymentId, UUID.fromString(orderId));
        }

        return ResponseEntity.ok().build();
    }
}
