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
import com.eduardo.ecomerce.email.EmailService;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final PasswordTokenRepository passwordTokenRepository;

    @Value("${app.password-token.expiration-hours:1}")
    private long tokenExpirationHours;

    public AuthOutput register(RegisterInput input) {

        String normalizedEmail = input.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException("Email já cadastrado");
        }

        User user = new User();
        user.setName(input.name());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(input.password()));
        user.setRole(UserRole.CLIENT);

        userRepository.save(user);
        log.info("Novo usuário registrado: {}", input.email());

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthOutput(accessToken, refreshToken);
    }

    public AuthOutput login(LoginInput input) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(input.email(), input.password())
        );

        User user = userRepository.findByEmail(input.email())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        log.info("Login realizado: {}", input.email());

        return new AuthOutput(accessToken, refreshToken);
    }



    public AuthOutput refresh(RefreshTokenInput input) {
        String email = jwtService.extractUsername(input.refreshToken());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        if (!jwtService.isTokenValid(input.refreshToken(), user)) {
            throw new BusinessException("Refresh token inválido ou expirado");
        }

        String typ = jwtService.extractTokenType(input.refreshToken());
        if (!"refresh".equals(typ)) {
            throw new BusinessException("Token inválido");
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        log.info("Refresh token utilizado: {}", email);

        return new AuthOutput(newAccessToken, newRefreshToken);
    }

    public void requestPasswordChange(ChangePasswordInput input, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        if (!passwordEncoder.matches(input.currentPassword(), user.getPassword())) {
            throw new BusinessException("Senha atual está incorreta");
        }

        if (passwordEncoder.matches(input.newPassword(), user.getPassword())) {
            throw new BusinessException("A nova senha deve ser diferente da senha atual");
        }

        String token = UUID.randomUUID().toString().replace("-", "");

        PasswordToken passwordToken = new PasswordToken(
                user,
                token,
                PasswordTokenType.CHANGE,
                LocalDateTime.now().plusHours(tokenExpirationHours)
        );
        passwordToken.setNewPasswordHash(passwordEncoder.encode(input.newPassword()));
        passwordTokenRepository.save(passwordToken);

        emailService.sendPasswordChangeEmail(user.getEmail(), token);
        log.info("Solicitação de alteração de senha enviada para {}", email);
    }

    public void confirmPasswordChange(String token) {
        PasswordToken passwordToken = findValidToken(token, PasswordTokenType.CHANGE);

        User user = passwordToken.getUser();
        user.setPassword(passwordToken.getNewPasswordHash());
        userRepository.save(user);

        passwordToken.setUsed(true);
        passwordTokenRepository.save(passwordToken);
        log.info("Senha alterada com sucesso para usuário {}", user.getEmail());
    }

    public void forgotPassword(ForgetPasswordInput input) {
        userRepository.findByEmail(input.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString().replace("-", "");

            PasswordToken passwordToken = new PasswordToken(
                    user,
                    token,
                    PasswordTokenType.RESET,
                    LocalDateTime.now().plusHours(tokenExpirationHours)
            );
            passwordTokenRepository.save(passwordToken);

            emailService.sendPasswordResetEmail(user.getEmail(), token);
        });
        // não revela se o email existe ou não — mesma resposta em ambos os casos
    }

    public void resetPassword(ResetPasswordInput input) {
        PasswordToken passwordToken = findValidToken(input.token(), PasswordTokenType.RESET);

        User user = passwordToken.getUser();
        user.setPassword(passwordEncoder.encode(input.newPassword()));
        userRepository.save(user);

        passwordToken.setUsed(true);
        passwordTokenRepository.save(passwordToken);
        log.info("Senha redefinida via reset para usuário {}", user.getEmail());
    }

    private PasswordToken findValidToken(String token, PasswordTokenType expectedType) {
        PasswordToken passwordToken = passwordTokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BusinessException("Token inválido ou já utilizado"));

        if (passwordToken.isExpired()) {
            throw new BusinessException("Token expirado. Solicite novamente");
        }

        if (passwordToken.getType() != expectedType) {
            throw new BusinessException("Token inválido");
        }

        return passwordToken;
    }
}