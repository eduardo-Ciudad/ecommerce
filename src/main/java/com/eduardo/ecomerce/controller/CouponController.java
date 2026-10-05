package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.coupon.CouponInput;
import com.eduardo.ecomerce.dto.output.common.PageResponse;
import com.eduardo.ecomerce.dto.output.coupon.CouponOutput;
import com.eduardo.ecomerce.dto.output.coupon.CouponValidationOutput;
import com.eduardo.ecomerce.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Tag(name = "Cupons", description = "Gerenciamento de cupons de desconto e validação no checkout")
public class CouponController {

    private final CouponService couponService;


    @GetMapping("/validate")
    @Operation(summary = "Validar cupom", description = "Verifica se o cupom existe e está ativo, retornando o percentual de desconto para prévia no checkout")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupom válido"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "422", description = "Cupom inválido ou expirado")
    })
    public ResponseEntity<CouponValidationOutput> validate(@RequestParam String code) {
        return ResponseEntity.ok(couponService.validateForCheckout(code));
    }


    @PostMapping
    @Operation(summary = "Criar cupom", description = "Cria um novo cupom de desconto. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Cupom criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "422", description = "Código de cupom já existe")
    })
    public ResponseEntity<CouponOutput> create(@RequestBody @Valid CouponInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(input));
    }

    @GetMapping
    @Operation(summary = "Listar cupons", description = "Lista todos os cupons, ativos e inativos, do mais recente ao mais antigo. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    public ResponseEntity<PageResponse<CouponOutput>> findAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(couponService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cupom por ID", description = "Retorna os dados de um cupom. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupom encontrado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Cupom não encontrado")
    })
    public ResponseEntity<CouponOutput> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(couponService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cupom", description = "Atualiza código, percentual e/ou status do cupom. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cupom atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Cupom não encontrado"),
            @ApiResponse(responseCode = "422", description = "Código de cupom já existe")
    })
    public ResponseEntity<CouponOutput> update(@PathVariable UUID id, @RequestBody @Valid CouponInput input) {
        return ResponseEntity.ok(couponService.update(id, input));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar cupom", description = "Desativa o cupom (não remove do banco). Pode ser reativado via PUT. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Cupom desativado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão"),
            @ApiResponse(responseCode = "404", description = "Cupom não encontrado")
    })
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        couponService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}