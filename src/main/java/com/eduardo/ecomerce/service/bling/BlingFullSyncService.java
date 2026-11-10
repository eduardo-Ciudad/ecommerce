package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.blingsync.BlingSyncRun;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunRepository;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunStatus;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncStep;
import com.eduardo.ecomerce.dto.output.bling.BlingSyncEventOutput;
import com.eduardo.ecomerce.dto.output.bling.BlingSyncStatusOutput;
import com.eduardo.ecomerce.service.BlingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sync completo com o Bling disparado pelo admin: categorias, depois produtos (com reconcile).
 * Roda em background numa thread própria; o progresso fica em memória (ao vivo) e é gravado
 * em bling_sync_runs em lotes, para sobreviver à consulta depois que terminar.
 */
@Slf4j
@Service
public class BlingFullSyncService {

    static final int MAX_RECENT_EVENTS = 50;
    static final int PERSIST_EVERY_ITEMS = 20;
    static final int MAX_ERROR_LENGTH = 1000;
    static final String INTERRUPTED_MESSAGE = "Interrompido por reinício do servidor";
    static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final BlingService blingService;
    private final BlingSyncRunRepository runRepository;
    private final Executor executor;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();
    private final Deque<BlingSyncEventOutput> recentEvents = new ArrayDeque<>();
    private BlingSyncRun currentRun;

    @Autowired
    public BlingFullSyncService(BlingService blingService, BlingSyncRunRepository runRepository, Clock clock) {
        this(blingService, runRepository, newSingleThreadExecutor(), clock);
    }

    BlingFullSyncService(BlingService blingService, BlingSyncRunRepository runRepository, Executor executor, Clock clock) {
        this.blingService = blingService;
        this.runRepository = runRepository;
        this.executor = executor;
        this.clock = clock;
    }

    /** Executor próprio (não é bean) para não interferir no executor padrão do @Async. */
    private static ExecutorService newSingleThreadExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bling-full-sync");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    public BlingSyncStatusOutput start() {
        if (!running.compareAndSet(false, true)) {
            throw new BlingSyncAlreadyRunningException();
        }

        BlingSyncRun run;
        try {
            run = runRepository.save(BlingSyncRun.start(now()));
            synchronized (lock) {
                recentEvents.clear();
                currentRun = run;
            }
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        BlingSyncStatusOutput accepted = snapshot(run);
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException e) {
            log.error("Não foi possível iniciar o sync completo do Bling", e);
            synchronized (lock) {
                run.finishFailure(now(), describe(e));
            }
            persist(run);
            running.set(false);
            throw e;
        }
        return accepted;
    }

    void execute(BlingSyncRun run) {
        log.info("Sync completo do Bling iniciado (run={})", run.getId());
        try {
            int categories = blingService.syncCategories();
            synchronized (lock) {
                run.setCategoriesSynced(categories);
                run.setStep(BlingSyncStep.PRODUCTS);
            }
            persist(run);

            blingService.syncProducts(new RunProgressListener(run));

            synchronized (lock) {
                run.finishSuccess(now());
            }
            log.info("Sync completo do Bling concluído (run={}, itens={})", run.getId(), run.getProcessedItems());
        } catch (Exception e) {
            log.error("Sync completo do Bling falhou na etapa {} (run={})", run.getStep(), run.getId(), e);
            synchronized (lock) {
                run.finishFailure(now(), describe(e));
            }
        } finally {
            persist(run);
            running.set(false);
        }
    }

    public Optional<BlingSyncStatusOutput> getLatestStatus() {
        synchronized (lock) {
            if (currentRun != null) {
                return Optional.of(snapshot(currentRun));
            }
        }
        return runRepository.findTopByOrderByStartedAtDesc()
                .map(run -> BlingSyncStatusOutput.from(run, List.of()));
    }

    /** Um run que ficou RUNNING no banco só pode ter sido cortado por reinício: o botão não pode travar. */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRuns() {
        for (BlingSyncRun run : runRepository.findByStatus(BlingSyncRunStatus.RUNNING)) {
            synchronized (lock) {
                if (currentRun != null && currentRun.getId() != null && currentRun.getId().equals(run.getId())) {
                    continue;
                }
            }
            run.finishFailure(now(), INTERRUPTED_MESSAGE);
            runRepository.save(run);
            log.warn("Sync do Bling (run={}) marcado como FAILED: {}", run.getId(), INTERRUPTED_MESSAGE);
        }
    }

    private BlingSyncStatusOutput snapshot(BlingSyncRun run) {
        synchronized (lock) {
            return BlingSyncStatusOutput.from(run, recentEvents.stream().toList());
        }
    }

    private void persist(BlingSyncRun run) {
        try {
            synchronized (lock) {
                runRepository.save(run);
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao gravar o progresso do sync do Bling (run={}) — o sync continua", run.getId(), e);
        }
    }

    /** O container roda em UTC; as datas do sync são exibidas no admin no horário da loja. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(STORE_ZONE));
    }

    private static String describe(Exception e) {
        String message = e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private class RunProgressListener implements SyncProgressListener {

        private final BlingSyncRun run;

        RunProgressListener(BlingSyncRun run) {
            this.run = run;
        }

        @Override
        public void onTotal(int totalItems) {
            synchronized (lock) {
                run.setTotalItems(totalItems);
            }
            persist(run);
        }

        @Override
        public void onItem(Long blingId, String name, ItemKind kind, ItemOutcome outcome) {
            int processed;
            synchronized (lock) {
                processed = run.getProcessedItems() + 1;
                run.setProcessedItems(processed);
                boolean synced = outcome == ItemOutcome.SYNCED;
                if (kind == ItemKind.VARIANT) {
                    if (synced) {
                        run.setVariantsSynced(run.getVariantsSynced() + 1);
                    } else {
                        run.setVariantsSkipped(run.getVariantsSkipped() + 1);
                    }
                } else {
                    if (synced) {
                        run.setStandaloneSynced(run.getStandaloneSynced() + 1);
                    } else {
                        run.setStandaloneSkipped(run.getStandaloneSkipped() + 1);
                    }
                }
                recentEvents.addFirst(new BlingSyncEventOutput(blingId, name, kind, outcome, now()));
                while (recentEvents.size() > MAX_RECENT_EVENTS) {
                    recentEvents.removeLast();
                }
            }
            if (processed % PERSIST_EVERY_ITEMS == 0) {
                persist(run);
            }
        }

        @Override
        public void onReconciling() {
            synchronized (lock) {
                run.setStep(BlingSyncStep.RECONCILE);
            }
            persist(run);
        }

        @Override
        public void onDeactivated(int count) {
            synchronized (lock) {
                run.setDeactivated(count);
            }
        }
    }
}
