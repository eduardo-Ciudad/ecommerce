package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.passwordtoken.PasswordTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final PasswordTokenRepository passwordTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredTokens() {
        int deleted = passwordTokenRepository.deleteByExpiresAtBeforeOrUsedTrue(LocalDateTime.now());
        log.info("Cleanup de tokens: {} removidos", deleted);
    }
}
