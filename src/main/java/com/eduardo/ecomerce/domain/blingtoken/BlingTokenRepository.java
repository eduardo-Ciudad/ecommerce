package com.eduardo.ecomerce.domain.blingtoken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BlingTokenRepository extends JpaRepository<BlingToken, UUID> {

    Optional<BlingToken> findFirstByOrderByUpdatedAtDesc();

}
