package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.payment.InfinitePayCheckoutInput;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayReturnInput;
import com.eduardo.ecomerce.dto.input.payment.InfinitePayWebhookPayload;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayCheckoutOutput;
import com.eduardo.ecomerce.dto.output.payment.InfinitePayConfirmationOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.infinitepay.InfinitePayException;
import com.eduardo.ecomerce.service.payment.InfinitePayCheckoutService;
import com.eduardo.ecomerce.service.payment.InfinitePayConfirmation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/payments/infinitepay")
@RequiredArgsConstructor
@Tag(name = "Payments - InfinitePay", description = "Pagamento com cartão pelo Checkout Integrado da InfinitePay")
public class InfinitePayController {

    private final InfinitePayCheckoutService checkoutService;

    @Operation(summary = "Criar link de pagamento", description = "Cria (ou reaproveita) o link do checkout da InfinitePay para o pedido")
    @PostMapping("/checkout")
    public ResponseEntity<InfinitePayCheckoutOutput> checkout(
            @Valid @RequestBody InfinitePayCheckoutInput input, Principal principal) {
        return ResponseEntity.ok(checkoutService.createCheckout(input, principal.getName()));
    }

    /**
     * Webhook sem assinatura: a confirmação real é feita no payment_check.
     * Só responde 400 quando reenviar pode resolver (falha temporária); erros definitivos respondem 200.
     */
    @Operation(summary = "Webhook da InfinitePay", description = "Recebe o aviso de pagamento aprovado")
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody InfinitePayWebhookPayload payload) {
        log.info("Webhook InfinitePay recebido — pedido {}, transação {}", payload.orderNsu(), payload.transactionNsu());
        try {
            InfinitePayConfirmation result =
                    checkoutService.confirm(payload.orderNsu(), payload.transactionNsu(), payload.invoiceSlug());
            log.info("Webhook InfinitePay processado — pedido {}: {}", payload.orderNsu(), result);
            return ResponseEntity.ok().build();
        } catch (InfinitePayException e) {
            log.error("Webhook InfinitePay — falha ao consultar a InfinitePay (pedido {}); aguardando reenvio",
                    payload.orderNsu(), e);
            return ResponseEntity.badRequest().build();
        } catch (BusinessException | EntityNotFoundException e) {
            log.warn("Webhook InfinitePay descartado — pedido {}: {}", payload.orderNsu(), e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook InfinitePay — erro inesperado (pedido {}); aguardando reenvio", payload.orderNsu(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Confirmar pagamento no retorno", description = "Usado pela página de retorno após o pagamento na InfinitePay")
    @PostMapping("/confirm")
    public ResponseEntity<InfinitePayConfirmationOutput> confirm(
            @Valid @RequestBody InfinitePayReturnInput input, Principal principal) {
        InfinitePayConfirmation result = checkoutService.confirmForCustomer(input, principal.getName());
        return ResponseEntity.ok(new InfinitePayConfirmationOutput(result));
    }
}
