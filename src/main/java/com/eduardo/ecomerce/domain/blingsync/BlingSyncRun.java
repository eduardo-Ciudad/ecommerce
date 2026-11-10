package com.eduardo.ecomerce.domain.blingsync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma execução do sync completo com o Bling disparada pelo admin. */
@Entity
@Table(name = "bling_sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class BlingSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlingSyncRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlingSyncStep step;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "categories_synced", nullable = false)
    private int categoriesSynced;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "processed_items", nullable = false)
    private int processedItems;

    @Column(name = "variants_synced", nullable = false)
    private int variantsSynced;

    @Column(name = "variants_skipped", nullable = false)
    private int variantsSkipped;

    @Column(name = "standalone_synced", nullable = false)
    private int standaloneSynced;

    @Column(name = "standalone_skipped", nullable = false)
    private int standaloneSkipped;

    @Column(nullable = false)
    private int deactivated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public static BlingSyncRun start(LocalDateTime now) {
        BlingSyncRun run = new BlingSyncRun();
        run.setStatus(BlingSyncRunStatus.RUNNING);
        run.setStep(BlingSyncStep.CATEGORIES);
        run.setStartedAt(now);
        return run;
    }

    public void finishSuccess(LocalDateTime now) {
        this.status = BlingSyncRunStatus.SUCCESS;
        this.step = BlingSyncStep.DONE;
        this.finishedAt = now;
    }

    /** Mantém o step em que parou, para o admin saber em que etapa falhou. */
    public void finishFailure(LocalDateTime now, String errorMessage) {
        this.status = BlingSyncRunStatus.FAILED;
        this.finishedAt = now;
        this.errorMessage = errorMessage;
    }

    public boolean isRunning() {
        return status == BlingSyncRunStatus.RUNNING;
    }
}
