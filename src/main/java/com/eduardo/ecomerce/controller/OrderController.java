package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.dto.input.order.CreateOrderInput;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.infra.security.SecurityUtils;
import com.eduardo.ecomerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Endpoints para criação e gerenciamento de pedidos")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Criar pedido", description = "Cria um novo pedido a partir dos itens do carrinho do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "422", description = "Erro de regra de negócio, ex: carrinho vazio ou estoque insuficiente")
    })
    public ResponseEntity<OrderOutput> create(@RequestBody @Valid CreateOrderInput input) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(userId, input));
    }

    @GetMapping
    @Operation(summary = "Listar pedidos", description = "Retorna todos os pedidos do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado")
    })
    public ResponseEntity<List<OrderOutput>> findAll() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pedido por ID", description = "Retorna os dados de um pedido específico do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    public ResponseEntity<OrderOutput> findById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.findByUserIdAndOrderId(userId, id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Atualizar status do pedido", description = "Atualiza o status de um pedido. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para atualizar status"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    public ResponseEntity<OrderOutput> updateStatus(@PathVariable UUID id, @RequestBody OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }
}
