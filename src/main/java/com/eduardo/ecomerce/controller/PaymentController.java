package com.eduardo.ecomerce.controller;


import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pagamentos via Mercado Pago")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Processar pagamento",
            description = "Processa pagamento via cartão de crédito ou Pix")
    @ApiResponse(responseCode = "200", description = "Pagamento processado")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @PostMapping("/process")
    public ResponseEntity<PaymentOutput> processPayment(
            @Valid @RequestBody PaymentInput input) {

        // Pega o email do usuário autenticado
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String payerEmail = auth.getName();

        PaymentOutput result = paymentService.processPayment(input, payerEmail);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Webhook do Mercado Pago",
            description = "Recebe notificações de pagamento")
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