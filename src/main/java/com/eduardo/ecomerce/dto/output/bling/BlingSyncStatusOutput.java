package com.eduardo.ecomerce.dto.output.bling;

import com.eduardo.ecomerce.domain.blingsync.BlingSyncRun;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunStatus;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncStep;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BlingSyncStatusOutput(
        UUID id,
        BlingSyncRunStatus status,
        BlingSyncStep step,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int categoriesSynced,
        int totalItems,
        int processedItems,
        int variantsSynced,
        int variantsSkipped,
        int standaloneSynced,
        int standaloneSkipped,
        int deactivated,
        String errorMessage,
        List<BlingSyncEventOutput> recentEvents
) {

    public static BlingSyncStatusOutput from(BlingSyncRun run, List<BlingSyncEventOutput> recentEvents) {
        return new BlingSyncStatusOutput(
                run.getId(),
                run.getStatus(),
                run.getStep(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCategoriesSynced(),
                run.getTotalItems(),
                run.getProcessedItems(),
                run.getVariantsSynced(),
                run.getVariantsSkipped(),
                run.getStandaloneSynced(),
                run.getStandaloneSkipped(),
                run.getDeactivated(),
                run.getErrorMessage(),
                List.copyOf(recentEvents)
        );
    }
}
