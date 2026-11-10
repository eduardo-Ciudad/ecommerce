package com.eduardo.ecomerce.domain.blingsync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlingSyncRunRepository extends JpaRepository<BlingSyncRun, UUID> {

    Optional<BlingSyncRun> findTopByOrderByStartedAtDesc();

    List<BlingSyncRun> findByStatus(BlingSyncRunStatus status);
}
