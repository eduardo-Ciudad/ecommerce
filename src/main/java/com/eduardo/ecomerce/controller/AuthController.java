package com.eduardo.ecomerce.controller;


import com.eduardo.ecomerce.dto.input.login.LoginInput;
import com.eduardo.ecomerce.dto.input.register.RegisterInput;
import com.eduardo.ecomerce.dto.output.auth.AuthOutput;
import com.eduardo.ecomerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthOutput> register(@RequestBody @Valid RegisterInput input) {
        AuthOutput output = authService.register(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthOutput> login(@RequestBody @Valid LoginInput input) {
        AuthOutput output = authService.login(input);
        return ResponseEntity.ok(output);
    }
}
