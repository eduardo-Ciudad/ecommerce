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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthOutput register(RegisterInput input) {
        if (userRepository.existsByEmail(input.email())) {
            throw new BusinessException("Email já cadastrado");
        }

        User user = new User();
        user.setName(input.name());
        user.setEmail(input.email());
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

}