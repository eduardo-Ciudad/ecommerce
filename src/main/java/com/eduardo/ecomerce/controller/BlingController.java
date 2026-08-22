package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.output.bling.AuthorizationUrlOutput;
import com.eduardo.ecomerce.service.BlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

 @Slf4j
@RestController
@RequestMapping("/bling")
@RequiredArgsConstructor
public class BlingController {

    private final BlingService blingService;

    @GetMapping("/authorize")
    public ResponseEntity<AuthorizationUrlOutput> authorize() {
        String authorizationUrl = blingService.buildAuthorizationUrl();
        return ResponseEntity.ok(new AuthorizationUrlOutput(authorizationUrl));
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam String code,
            @RequestParam(required = false) String state
    ) {
        if (state != null) {
            blingService.validateState(state);
        } else {
            log.warn("Callback OAuth do Bling recebido sem state");
        }

        blingService.handleAuthorizationCode(code);

        return ResponseEntity.ok("Integração com o Bling autorizada com sucesso.");
    }

    @PostMapping("/sync/categories")
    public ResponseEntity<String> syncCategories() {
        blingService.syncCategories();
        return ResponseEntity.ok("Sincronização de categorias concluída.");
    }

    @PostMapping("/sync/products")
    public ResponseEntity<String> syncProducts(
            @RequestParam(defaultValue = "1") int maxPages
    ) {
        blingService.syncProducts(maxPages);
        return ResponseEntity.ok("Sincronização de produtos concluída (máx. " + maxPages + " página(s) da listagem).");
    }

     @GetMapping("/debug/inspect")
     public ResponseEntity<String> debugInspect(
             @RequestParam(required = false) Long sampleProductId
     ) {
         blingService.debugInspectBlingContract(sampleProductId);
         return ResponseEntity.ok("Diagnóstico executado, verifique os logs da aplicação.");
     }
}