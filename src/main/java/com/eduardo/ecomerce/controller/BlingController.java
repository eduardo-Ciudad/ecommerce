package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.service.BlingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/bling")
@RequiredArgsConstructor
public class BlingController {

    private final BlingService blingService;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        String authorizationUrl = blingService.buildAuthorizationUrl();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
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
}