package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.domain.user.UserRole;
import com.eduardo.ecomerce.dto.input.login.LoginInput;
import com.eduardo.ecomerce.dto.input.register.RegisterInput;
import com.eduardo.ecomerce.dto.input.token.RefreshTokenInput;
import com.eduardo.ecomerce.dto.output.auth.AuthOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("register — deve registrar usuário e retornar tokens")
    void register_success() {
        RegisterInput input = new RegisterInput("Eduardo", "eduardo@email.com", "123456");

        when(userRepository.existsByEmail(input.email())).thenReturn(false);
        when(passwordEncoder.encode(input.password())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthOutput output = authService.register(input);

        assertThat(output.accessToken()).isEqualTo("access-token");
        assertThat(output.refreshToken()).isEqualTo("refresh-token");
        verify(userRepository).save(any(User.class));
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
    @DisplayName("login — deve lançar exceção quando credenciais são inválidas")
    void login_invalidCredentials() {
        LoginInput input = new LoginInput("eduardo@email.com", "senhaerrada");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(input))
                .isInstanceOf(BadCredentialsException.class);

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
}