package com.eduardo.ecomerce.domain.passwordtoken;


import jakarta.persistence.*;
import lombok.*;
import com.eduardo.ecomerce.domain.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordTokenType type;

    @Column(name = "new_password_hash")
    private String newPasswordHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PasswordToken(User user, String token, PasswordTokenType type, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.type = type;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
