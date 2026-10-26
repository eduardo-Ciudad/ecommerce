package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.output.admin.AdminStatsOutput;
import com.eduardo.ecomerce.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Números agregados do painel administrativo")
public class AdminController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    @Operation(summary = "Estatísticas do dashboard",
            description = "Contadores de produtos/variantes, pedidos pendentes, falhas no Bling, faturamento do mês e vendas dos últimos 30 dias (horário de Brasília)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é admin")
    })
    public ResponseEntity<AdminStatsOutput> stats() {
        return ResponseEntity.ok(adminStatsService.getStats());
    }
}
