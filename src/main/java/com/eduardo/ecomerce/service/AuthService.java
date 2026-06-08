package com.eduardo.ecomerce.service;


import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.domain.user.UserRole;
import com.eduardo.ecomerce.dto.input.login.LoginInput;
import com.eduardo.ecomerce.dto.input.register.RegisterInput;
import com.eduardo.ecomerce.dto.output.auth.AuthOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

        return new AuthOutput(accessToken, refreshToken);
    }
}