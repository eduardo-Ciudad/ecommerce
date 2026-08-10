package com.eduardo.ecomerce.controller;


import com.eduardo.ecomerce.dto.input.login.LoginInput;
import com.eduardo.ecomerce.dto.input.password.ChangePasswordInput;
import com.eduardo.ecomerce.dto.input.password.ForgetPasswordInput;
import com.eduardo.ecomerce.dto.input.password.ResetPasswordInput;
import com.eduardo.ecomerce.dto.input.register.RegisterInput;
import com.eduardo.ecomerce.dto.input.token.RefreshTokenInput;
import com.eduardo.ecomerce.dto.output.auth.AuthOutput;
import com.eduardo.ecomerce.dto.output.message.MessageOutput;
import com.eduardo.ecomerce.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Endpoints de registro, login e renovação de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registrar novo usuário", description = "Cria uma nova conta e envia email de verificação")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Cadastro realizado, email de verificação enviado")
    })
    public ResponseEntity<MessageOutput> register(@RequestBody @Valid RegisterInput input) {
        MessageOutput output = authService.register(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário", description = "Autentica o usuário com email e senha e retorna um par de access e refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    public ResponseEntity<AuthOutput> login(@RequestBody @Valid LoginInput input) {
        AuthOutput output = authService.login(input);
        return ResponseEntity.ok(output);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Gera um novo par de access e refresh token a partir de um refresh token válido")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "422", description = "Refresh token inválido ou expirado")
    })
    public ResponseEntity<AuthOutput> refresh(@RequestBody @Valid RefreshTokenInput input) {
        return ResponseEntity.ok(authService.refresh(input));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Solicitar alteração de senha", description = "Usuário autenticado solicita troca de senha; um email de confirmação é enviado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email de confirmação enviado"),
            @ApiResponse(responseCode = "422", description = "Senha atual incorreta")
    })
    public ResponseEntity<MessageOutput> changePassword(@RequestBody @Valid ChangePasswordInput input) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        authService.requestPasswordChange(input, email);
        return ResponseEntity.ok(new MessageOutput("Email de confirmação enviado. Verifique sua caixa de entrada."));
    }

    @GetMapping("/confirm-password-change")
    @Operation(summary = "Confirmar alteração de senha", description = "Confirma a troca de senha a partir do token enviado por email")
    public ResponseEntity<MessageOutput> confirmPasswordChange(@RequestParam String token) {
        authService.confirmPasswordChange(token);
        return ResponseEntity.ok(new MessageOutput("Senha alterada com sucesso!"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Esqueci minha senha", description = "Envia um link de recuperação de senha para o email informado, se cadastrado")
    public ResponseEntity<MessageOutput> forgotPassword(@RequestBody @Valid ForgetPasswordInput input) {
        authService.forgotPassword(input);
        return ResponseEntity.ok(new MessageOutput("Se o email estiver cadastrado, você receberá um link de recuperação."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Redefinir senha", description = "Redefine a senha a partir de um token válido de recuperação")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "422", description = "Token inválido ou expirado")
    })
    public ResponseEntity<MessageOutput> resetPassword(@RequestBody @Valid ResetPasswordInput input) {
        authService.resetPassword(input);
        return ResponseEntity.ok(new MessageOutput("Senha redefinida com sucesso! Você já pode fazer login."));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verificar email", description = "Ativa a conta do usuário a partir do token enviado por email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verificado com sucesso"),
            @ApiResponse(responseCode = "422", description = "Token inválido ou expirado")
    })
    public ResponseEntity<AuthOutput> verifyEmail(@RequestParam String token) {
        AuthOutput output = authService.verifyEmail(token);
        return ResponseEntity.ok(output);
    }
}
