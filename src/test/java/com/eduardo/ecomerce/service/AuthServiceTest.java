package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.passwordtoken.PasswordToken;
import com.eduardo.ecomerce.domain.passwordtoken.PasswordTokenRepository;
import com.eduardo.ecomerce.domain.passwordtoken.PasswordTokenType;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.domain.user.UserRole;
import com.eduardo.ecomerce.dto.input.login.LoginInput;
import com.eduardo.ecomerce.dto.input.password.ChangePasswordInput;
import com.eduardo.ecomerce.dto.input.password.ForgetPasswordInput;
import com.eduardo.ecomerce.dto.input.password.ResetPasswordInput;
import com.eduardo.ecomerce.dto.input.register.RegisterInput;
import com.eduardo.ecomerce.dto.input.token.RefreshTokenInput;
import com.eduardo.ecomerce.dto.output.auth.AuthOutput;
import com.eduardo.ecomerce.dto.output.message.MessageOutput;
import com.eduardo.ecomerce.email.EmailService;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordTokenRepository passwordTokenRepository;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setName("Eduardo");
        user.setEmail("eduardo@email.com");
        user.setPassword("hashedPassword");
        user.setRole(UserRole.CLIENT);
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register — deve registrar usuário, gerar token de verificação e enviar email")
    void register_success() {
        RegisterInput input = new RegisterInput("Eduardo", "eduardo@email.com", "123456");

        when(userRepository.existsByEmail(input.email())).thenReturn(false);
        when(passwordEncoder.encode(input.password())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        MessageOutput output = authService.register(input);

        assertThat(output.message()).isEqualTo("Cadastro realizado! Verifique seu email para ativar sua conta.");
        verify(userRepository).save(any(User.class));
        verify(passwordTokenRepository).save(any());
        verify(emailService).sendVerificationEmail(eq("eduardo@email.com"), any());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("register — deve normalizar o email antes de checar duplicidade")
    void register_normalizesEmail() {
        RegisterInput input = new RegisterInput("Eduardo", "  Eduardo@Email.com  ", "123456");

        when(userRepository.existsByEmail("eduardo@email.com")).thenReturn(false);
        when(passwordEncoder.encode(input.password())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        authService.register(input);

        verify(userRepository).existsByEmail("eduardo@email.com");
    }

    @Test
    @DisplayName("register — deve lançar BusinessException quando email já existe")
    void register_emailAlreadyExists() {
        RegisterInput input = new RegisterInput("Eduardo", "eduardo@email.com", "123456");

        when(userRepository.existsByEmail(input.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email já cadastrado");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService, passwordTokenRepository);
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("login — deve autenticar e retornar tokens")
    void login_success() {
        LoginInput input = new LoginInput("eduardo@email.com", "123456");

        when(userRepository.findByEmail(input.email())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        AuthOutput output = authService.login(input);

        assertThat(output.accessToken()).isEqualTo("access-token");
        assertThat(output.refreshToken()).isEqualTo("refresh-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login — deve lançar BusinessException quando credenciais são inválidas")
    void login_invalidCredentials() {
        LoginInput input = new LoginInput("eduardo@email.com", "senhaerrada");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Credenciais inválidas");

        verify(jwtService, never()).generateToken(any());
    }

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("refresh — deve retornar novo par de tokens com refresh token válido")
    void refresh_success() {
        RefreshTokenInput input = new RefreshTokenInput("valid-refresh-token");

        when(jwtService.extractUsername(input.refreshToken())).thenReturn("eduardo@email.com");
        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(input.refreshToken(), user)).thenReturn(true);
        when(jwtService.extractTokenType(input.refreshToken())).thenReturn("refresh");
        when(jwtService.generateToken(user)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("new-refresh-token");

        AuthOutput output = authService.refresh(input);

        assertThat(output.accessToken()).isEqualTo("new-access-token");
        assertThat(output.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("refresh — deve lançar BusinessException quando token está expirado")
    void refresh_expiredToken() {
        RefreshTokenInput input = new RefreshTokenInput("expired-token");

        when(jwtService.extractUsername(input.refreshToken())).thenReturn("eduardo@email.com");
        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(input.refreshToken(), user)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Refresh token inválido ou expirado");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("refresh — deve lançar BusinessException quando access token é enviado no lugar do refresh")
    void refresh_wrongTokenType() {
        RefreshTokenInput input = new RefreshTokenInput("valid-access-token");

        when(jwtService.extractUsername(input.refreshToken())).thenReturn("eduardo@email.com");
        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(input.refreshToken(), user)).thenReturn(true);
        when(jwtService.extractTokenType(input.refreshToken())).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("refresh — deve lançar BusinessException quando usuário não existe")
    void refresh_userNotFound() {
        RefreshTokenInput input = new RefreshTokenInput("valid-refresh-token");

        when(jwtService.extractUsername(input.refreshToken())).thenReturn("inexistente@email.com");
        when(userRepository.findByEmail("inexistente@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Usuário não encontrado");
    }

    // -------------------------------------------------------------------------
    // verifyEmail
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("verifyEmail — deve ativar a conta e retornar tokens com token válido")
    void verifyEmail_success() {
        PasswordToken token = new PasswordToken(
                user, "valid-token", PasswordTokenType.EMAIL_VERIFICATION, LocalDateTime.now().plusHours(1)
        );

        when(passwordTokenRepository.findByTokenAndUsedFalse("valid-token")).thenReturn(Optional.of(token));
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        AuthOutput output = authService.verifyEmail("valid-token");

        assertThat(output.accessToken()).isEqualTo("access-token");
        assertThat(output.refreshToken()).isEqualTo("refresh-token");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordTokenRepository).save(token);
    }

    @Test
    @DisplayName("verifyEmail — deve lançar BusinessException quando token não existe ou já foi usado")
    void verifyEmail_invalidOrUsedToken() {
        when(passwordTokenRepository.findByTokenAndUsedFalse("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido ou já utilizado");

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("verifyEmail — deve lançar BusinessException quando token está expirado")
    void verifyEmail_expiredToken() {
        PasswordToken token = new PasswordToken(
                user, "expired-token", PasswordTokenType.EMAIL_VERIFICATION, LocalDateTime.now().minusHours(1)
        );

        when(passwordTokenRepository.findByTokenAndUsedFalse("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token expirado. Solicite novamente");

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("verifyEmail — deve lançar BusinessException quando o token é de outro tipo (ex: RESET)")
    void verifyEmail_wrongTokenType() {
        PasswordToken token = new PasswordToken(
                user, "reset-token", PasswordTokenType.RESET, LocalDateTime.now().plusHours(1)
        );

        when(passwordTokenRepository.findByTokenAndUsedFalse("reset-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("reset-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido");

        verifyNoInteractions(jwtService);
    }

    // -------------------------------------------------------------------------
    // requestPasswordChange
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("requestPasswordChange — deve gerar token CHANGE e enviar email de confirmação")
    void requestPasswordChange_success() {
        ChangePasswordInput input = new ChangePasswordInput("senhaAtual", "senhaNova123");

        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senhaAtual", user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("senhaNova123", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("senhaNova123")).thenReturn("hashedNovaSenha");

        authService.requestPasswordChange(input, "eduardo@email.com");

        ArgumentCaptor<PasswordToken> captor = ArgumentCaptor.forClass(PasswordToken.class);
        verify(passwordTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PasswordTokenType.CHANGE);
        assertThat(captor.getValue().getNewPasswordHash()).isEqualTo("hashedNovaSenha");
        verify(emailService).sendPasswordChangeEmail(eq("eduardo@email.com"), any());
    }

    @Test
    @DisplayName("requestPasswordChange — deve lançar BusinessException quando a senha atual está incorreta")
    void requestPasswordChange_wrongCurrentPassword() {
        ChangePasswordInput input = new ChangePasswordInput("senhaErrada", "senhaNova123");

        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senhaErrada", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.requestPasswordChange(input, "eduardo@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Senha atual está incorreta");

        verifyNoInteractions(emailService, passwordTokenRepository);
    }

    @Test
    @DisplayName("requestPasswordChange — deve lançar BusinessException quando a nova senha é igual à atual")
    void requestPasswordChange_samePassword() {
        ChangePasswordInput input = new ChangePasswordInput("senhaAtual", "senhaAtual");

        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senhaAtual", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.requestPasswordChange(input, "eduardo@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A nova senha deve ser diferente da senha atual");

        verifyNoInteractions(emailService, passwordTokenRepository);
    }

    @Test
    @DisplayName("requestPasswordChange — deve lançar BusinessException quando usuário não existe")
    void requestPasswordChange_userNotFound() {
        ChangePasswordInput input = new ChangePasswordInput("senhaAtual", "senhaNova123");

        when(userRepository.findByEmail("inexistente@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordChange(input, "inexistente@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Usuário não encontrado");
    }

    // -------------------------------------------------------------------------
    // confirmPasswordChange
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("confirmPasswordChange — deve aplicar a nova senha já hasheada e marcar token como usado")
    void confirmPasswordChange_success() {
        PasswordToken token = new PasswordToken(
                user, "change-token", PasswordTokenType.CHANGE, LocalDateTime.now().plusHours(1)
        );
        token.setNewPasswordHash("hashedNovaSenha");

        when(passwordTokenRepository.findByTokenAndUsedFalse("change-token")).thenReturn(Optional.of(token));

        authService.confirmPasswordChange("change-token");

        assertThat(user.getPassword()).isEqualTo("hashedNovaSenha");
        assertThat(token.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordTokenRepository).save(token);
    }

    @Test
    @DisplayName("confirmPasswordChange — deve lançar BusinessException quando o token é de outro tipo (ex: RESET)")
    void confirmPasswordChange_wrongTokenType() {
        PasswordToken token = new PasswordToken(
                user, "reset-token", PasswordTokenType.RESET, LocalDateTime.now().plusHours(1)
        );

        when(passwordTokenRepository.findByTokenAndUsedFalse("reset-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.confirmPasswordChange("reset-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido");

        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // forgotPassword
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("forgotPassword — deve gerar token RESET e enviar email quando usuário existe")
    void forgotPassword_userExists() {
        ForgetPasswordInput input = new ForgetPasswordInput("eduardo@email.com");

        when(userRepository.findByEmail("eduardo@email.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(input);

        ArgumentCaptor<PasswordToken> captor = ArgumentCaptor.forClass(PasswordToken.class);
        verify(passwordTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PasswordTokenType.RESET);
        verify(emailService).sendPasswordResetEmail(eq("eduardo@email.com"), any());
    }

    @Test
    @DisplayName("forgotPassword — não deve lançar exceção nem enviar email quando usuário não existe (evita enumeração)")
    void forgotPassword_userNotFound() {
        ForgetPasswordInput input = new ForgetPasswordInput("inexistente@email.com");

        when(userRepository.findByEmail("inexistente@email.com")).thenReturn(Optional.empty());

        authService.forgotPassword(input);

        verifyNoInteractions(emailService, passwordTokenRepository);
    }

    // -------------------------------------------------------------------------
    // resetPassword
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("resetPassword — deve trocar a senha com token RESET válido")
    void resetPassword_success() {
        PasswordToken token = new PasswordToken(
                user, "reset-token", PasswordTokenType.RESET, LocalDateTime.now().plusHours(1)
        );
        ResetPasswordInput input = new ResetPasswordInput("reset-token", "novaSenha123");

        when(passwordTokenRepository.findByTokenAndUsedFalse("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hashedNovaSenha");

        authService.resetPassword(input);

        assertThat(user.getPassword()).isEqualTo("hashedNovaSenha");
        assertThat(token.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordTokenRepository).save(token);
    }

    @Test
    @DisplayName("resetPassword — deve lançar BusinessException quando o token é de outro tipo (ex: EMAIL_VERIFICATION)")
    void resetPassword_wrongTokenType() {
        PasswordToken token = new PasswordToken(
                user, "verify-token", PasswordTokenType.EMAIL_VERIFICATION, LocalDateTime.now().plusHours(1)
        );
        ResetPasswordInput input = new ResetPasswordInput("verify-token", "novaSenha123");

        when(passwordTokenRepository.findByTokenAndUsedFalse("verify-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword — deve lançar BusinessException quando o token expirou")
    void resetPassword_expiredToken() {
        PasswordToken token = new PasswordToken(
                user, "expired-token", PasswordTokenType.RESET, LocalDateTime.now().minusMinutes(1)
        );
        ResetPasswordInput input = new ResetPasswordInput("expired-token", "novaSenha123");

        when(passwordTokenRepository.findByTokenAndUsedFalse("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token expirado. Solicite novamente");

        verify(userRepository, never()).save(any());
    }
}