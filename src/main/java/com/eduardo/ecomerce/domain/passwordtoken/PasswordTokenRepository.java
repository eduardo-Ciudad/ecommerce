package com.eduardo.ecomerce.domain.passwordtoken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, UUID> {
    Optional<PasswordToken> findByTokenAndUsedFalse(String token);
    int deleteByExpiresAtBeforeOrUsedTrue(LocalDateTime now);
}
