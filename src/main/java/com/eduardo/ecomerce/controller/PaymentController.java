package com.eduardo.ecomerce.controller;


import com.eduardo.ecomerce.dto.input.payment.PaymentInput;
import com.eduardo.ecomerce.dto.output.payment.PaymentOutput;
import com.eduardo.ecomerce.infra.payment.WebhookSignatureValidator;
import com.eduardo.ecomerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final WebhookSignatureValidator webhookSignatureValidator;

    @Operation(summary = "Processar pagamento",
            description = "Processa pagamento via cartão de crédito ou Pix")
    @ApiResponse(responseCode = "200", description = "Pagamento processado")
    @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    @PostMapping("/process")
    public ResponseEntity<PaymentOutput> processPayment(
            @Valid @RequestBody PaymentInput input) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String payerEmail = auth.getName();

        PaymentOutput result = paymentService.processPayment(input, payerEmail);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Webhook do Mercado Pago",
            description = "Recebe notificações de pagamento")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody Map<String, Object> payload) {

        log.info("Webhook recebido - type: {}", payload.get("type"));

        String type = (String) payload.get("type");

        if ("payment".equals(type)) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String paymentId = String.valueOf(data.get("id"));

            if (!webhookSignatureValidator.isValid(xSignature, xRequestId, paymentId)) {
                log.warn("Assinatura de webhook inválida - Payment: {}", paymentId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            paymentService.processWebhook(paymentId);
        }

        return ResponseEntity.ok().build();
    }
}