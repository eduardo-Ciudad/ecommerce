package com.eduardo.ecomerce.domain.passwordtoken;

import com.eduardo.ecomerce.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordTokenRepository extends JpaRepository<PasswordToken, UUID> {
    Optional<PasswordToken> findByTokenAndUsedFalse(String token);
    int deleteByExpiresAtBeforeOrUsedTrue(LocalDateTime now);

    Optional<PasswordToken> findTopByUserAndTypeOrderByCreatedAtDesc(User user, PasswordTokenType type);

    @Modifying
    @Query("UPDATE PasswordToken t SET t.used = true WHERE t.user = :user AND t.type = :type AND t.used = false")
    int markUnusedAsUsed(@Param("user") User user, @Param("type") PasswordTokenType type);
}
