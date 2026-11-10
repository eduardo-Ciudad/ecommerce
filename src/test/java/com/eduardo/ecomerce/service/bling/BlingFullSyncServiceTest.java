package com.eduardo.ecomerce.service.bling;

import com.eduardo.ecomerce.domain.blingsync.BlingSyncRun;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunRepository;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncRunStatus;
import com.eduardo.ecomerce.domain.blingsync.BlingSyncStep;
import com.eduardo.ecomerce.dto.output.bling.BlingSyncStatusOutput;
import com.eduardo.ecomerce.dto.output.bling.SyncProductsResult;
import com.eduardo.ecomerce.service.BlingService;
import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemKind;
import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlingFullSyncServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final SyncProductsResult EMPTY_RESULT = new SyncProductsResult(0, 0, 0, 0, 0);

    @Mock private BlingService blingService;
    @Mock private BlingSyncRunRepository runRepository;

    private final List<Runnable> pendingTasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(runRepository.save(any(BlingSyncRun.class))).thenAnswer(invocation -> {
            BlingSyncRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
    }

    private BlingFullSyncService serviceWith(Executor executor) {
        return new BlingFullSyncService(blingService, runRepository, executor, CLOCK);
    }

    private BlingFullSyncService directService() {
        return serviceWith(Runnable::run);
    }

    private BlingFullSyncService deferredService() {
        return serviceWith(pendingTasks::add);
    }

    private BlingSyncStatusOutput latest(BlingFullSyncService service) {
        return service.getLatestStatus().orElseThrow();
    }

    @Test
    void startRunsCategoriesBeforeProductsAndFinishesWithSuccessAndCounters() {
        BlingFullSyncService service = directService();
        when(blingService.syncCategories()).thenReturn(29);
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            listener.onTotal(2);
            listener.onItem(1L, "Short Preto 4", ItemKind.VARIANT, ItemOutcome.SYNCED);
            listener.onItem(2L, "Body Liso", ItemKind.STANDALONE, ItemOutcome.SKIPPED);
            listener.onReconciling();
            listener.onDeactivated(1);
        }));

        service.start();

        InOrder order = inOrder(blingService);
        order.verify(blingService).syncCategories();
        order.verify(blingService).syncProducts(any(SyncProgressListener.class));

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.SUCCESS);
        assertThat(status.step()).isEqualTo(BlingSyncStep.DONE);
        assertThat(status.startedAt()).isEqualTo(NOW);
        assertThat(status.finishedAt()).isEqualTo(NOW);
        assertThat(status.categoriesSynced()).isEqualTo(29);
        assertThat(status.totalItems()).isEqualTo(2);
        assertThat(status.processedItems()).isEqualTo(2);
        assertThat(status.variantsSynced()).isEqualTo(1);
        assertThat(status.variantsSkipped()).isZero();
        assertThat(status.standaloneSynced()).isZero();
        assertThat(status.standaloneSkipped()).isEqualTo(1);
        assertThat(status.deactivated()).isEqualTo(1);
        assertThat(status.errorMessage()).isNull();
        assertThat(status.recentEvents())
                .extracting(event -> event.blingId() + "|" + event.outcome())
                .containsExactly("2|SKIPPED", "1|SYNCED");
    }

    @Test
    void failedItemsCountAsSkippedLikeTheExistingSyncResult() {
        BlingFullSyncService service = directService();
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            listener.onTotal(2);
            listener.onItem(1L, "Variação", ItemKind.VARIANT, ItemOutcome.FAILED);
            listener.onItem(2L, "Simples", ItemKind.STANDALONE, ItemOutcome.FAILED);
        }));

        service.start();

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.variantsSkipped()).isEqualTo(1);
        assertThat(status.standaloneSkipped()).isEqualTo(1);
        assertThat(status.processedItems()).isEqualTo(2);
    }

    @Test
    void failureOnCategoriesSkipsProductsMarksFailedAndReleasesTheLock() {
        BlingFullSyncService service = directService();
        when(blingService.syncCategories()).thenThrow(new IllegalStateException("Bling fora do ar"));

        service.start();

        verify(blingService, never()).syncProducts(any(SyncProgressListener.class));
        BlingSyncStatusOutput status = latest(service);
        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.FAILED);
        assertThat(status.step()).isEqualTo(BlingSyncStep.CATEGORIES);
        assertThat(status.finishedAt()).isEqualTo(NOW);
        assertThat(status.errorMessage()).contains("Bling fora do ar");

        service.start();
        verify(blingService, times(2)).syncCategories();
    }

    @Test
    void failureOnProductsKeepsProductsStepAndCountersAlreadyProcessed() {
        BlingFullSyncService service = directService();
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(invocation -> {
            SyncProgressListener listener = invocation.getArgument(0);
            listener.onTotal(5);
            listener.onItem(1L, "Primeiro", ItemKind.VARIANT, ItemOutcome.SYNCED);
            throw new IllegalStateException("listagem quebrou");
        });

        service.start();

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.FAILED);
        assertThat(status.step()).isEqualTo(BlingSyncStep.PRODUCTS);
        assertThat(status.processedItems()).isEqualTo(1);
        assertThat(status.errorMessage()).contains("listagem quebrou");
    }

    @Test
    void successReleasesTheLockSoAnotherSyncCanStart() {
        BlingFullSyncService service = directService();
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenReturn(EMPTY_RESULT);

        service.start();
        service.start();

        verify(blingService, times(2)).syncCategories();
    }

    @Test
    void secondStartWhileRunningIsRejectedWithoutCreatingAnotherRun() {
        BlingFullSyncService service = deferredService();

        BlingSyncStatusOutput first = service.start();

        assertThat(first.status()).isEqualTo(BlingSyncRunStatus.RUNNING);
        assertThat(first.step()).isEqualTo(BlingSyncStep.CATEGORIES);
        assertThatThrownBy(service::start).isInstanceOf(BlingSyncAlreadyRunningException.class);
        verify(runRepository, times(1)).save(any(BlingSyncRun.class));
        assertThat(pendingTasks).hasSize(1);
    }

    @Test
    void startClearsEventsFromThePreviousRun() {
        BlingFullSyncService service = directService();
        when(blingService.syncProducts(any(SyncProgressListener.class)))
                .thenAnswer(products(listener -> listener.onItem(1L, "Antigo", ItemKind.VARIANT, ItemOutcome.SYNCED)))
                .thenReturn(EMPTY_RESULT);

        service.start();
        service.start();

        assertThat(latest(service).recentEvents()).isEmpty();
    }

    @Test
    void statusWhileRunningShowsLiveProgress() {
        BlingFullSyncService service = directService();
        List<BlingSyncStatusOutput> midway = new ArrayList<>();
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            listener.onTotal(3);
            listener.onItem(1L, "Primeiro", ItemKind.VARIANT, ItemOutcome.SYNCED);
            midway.add(latest(service));
        }));

        service.start();

        BlingSyncStatusOutput status = midway.get(0);
        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.RUNNING);
        assertThat(status.step()).isEqualTo(BlingSyncStep.PRODUCTS);
        assertThat(status.totalItems()).isEqualTo(3);
        assertThat(status.processedItems()).isEqualTo(1);
        assertThat(status.recentEvents()).hasSize(1);
    }

    @Test
    void reconcileStepIsVisibleWhileReconciling() {
        BlingFullSyncService service = directService();
        List<BlingSyncStep> steps = new ArrayList<>();
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            listener.onReconciling();
            steps.add(latest(service).step());
        }));

        service.start();

        assertThat(steps).containsExactly(BlingSyncStep.RECONCILE);
    }

    @Test
    void recentEventsAreCappedAndNewestComesFirst() {
        BlingFullSyncService service = directService();
        int items = BlingFullSyncService.MAX_RECENT_EVENTS + 10;
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            for (long i = 1; i <= items; i++) {
                listener.onItem(i, "Item " + i, ItemKind.VARIANT, ItemOutcome.SYNCED);
            }
        }));

        service.start();

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.recentEvents()).hasSize(BlingFullSyncService.MAX_RECENT_EVENTS);
        assertThat(status.recentEvents().get(0).blingId()).isEqualTo((long) items);
        assertThat(status.processedItems()).isEqualTo(items);
    }

    @Test
    void progressIsPersistedInBatchesInsteadOfOnEveryItem() {
        BlingFullSyncService service = directService();
        int items = BlingFullSyncService.PERSIST_EVERY_ITEMS * 2;
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenAnswer(products(listener -> {
            listener.onTotal(items);
            for (long i = 1; i <= items; i++) {
                listener.onItem(i, "Item " + i, ItemKind.VARIANT, ItemOutcome.SYNCED);
            }
        }));

        service.start();

        verify(runRepository, times(6)).save(any(BlingSyncRun.class));
    }

    @Test
    void failingToPersistProgressDoesNotAbortTheSync() {
        BlingFullSyncService service = directService();
        doAnswer(invocation -> {
            BlingSyncRun run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        })
                .doThrow(new IllegalStateException("banco indisponível"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(runRepository).save(any(BlingSyncRun.class));
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenReturn(EMPTY_RESULT);

        service.start();

        verify(blingService).syncProducts(any(SyncProgressListener.class));
        assertThat(latest(service).status()).isEqualTo(BlingSyncRunStatus.SUCCESS);
    }

    @Test
    void executorRejectionMarksRunFailedAndReleasesTheLock() {
        BlingFullSyncService service = serviceWith(task -> {
            throw new RejectedExecutionException("sem thread");
        });

        assertThatThrownBy(service::start).isInstanceOf(RejectedExecutionException.class);

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.FAILED);
        assertThat(status.errorMessage()).contains("sem thread");
        assertThatThrownBy(service::start).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void markInterruptedRunsFailsRunsLeftRunningByARestart() {
        BlingFullSyncService service = directService();
        BlingSyncRun orphan = BlingSyncRun.start(NOW.minusHours(1));
        orphan.setStep(BlingSyncStep.PRODUCTS);
        when(runRepository.findByStatus(BlingSyncRunStatus.RUNNING)).thenReturn(List.of(orphan));

        service.markInterruptedRuns();

        assertThat(orphan.getStatus()).isEqualTo(BlingSyncRunStatus.FAILED);
        assertThat(orphan.getStep()).isEqualTo(BlingSyncStep.PRODUCTS);
        assertThat(orphan.getFinishedAt()).isEqualTo(NOW);
        assertThat(orphan.getErrorMessage()).isEqualTo(BlingFullSyncService.INTERRUPTED_MESSAGE);
        verify(runRepository).save(orphan);
    }

    @Test
    void latestStatusFallsBackToDatabaseWhenNothingRanSinceStartup() {
        BlingFullSyncService service = directService();
        BlingSyncRun previous = BlingSyncRun.start(NOW.minusDays(1));
        previous.finishSuccess(NOW.minusDays(1).plusHours(2));
        when(runRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(previous));

        BlingSyncStatusOutput status = latest(service);

        assertThat(status.status()).isEqualTo(BlingSyncRunStatus.SUCCESS);
        assertThat(status.recentEvents()).isEmpty();
    }

    @Test
    void latestStatusIsEmptyWhenNoSyncEverRan() {
        BlingFullSyncService service = directService();
        when(runRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.empty());

        assertThat(service.getLatestStatus()).isEmpty();
    }

    @Test
    void datesAreRecordedInStoreTimeEvenWhenServerClockIsUtc() {
        Clock utcClock = Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), ZoneId.of("UTC"));
        BlingFullSyncService service = new BlingFullSyncService(blingService, runRepository, Runnable::run, utcClock);
        when(blingService.syncProducts(any(SyncProgressListener.class))).thenReturn(EMPTY_RESULT);

        service.start();

        BlingSyncStatusOutput status = latest(service);
        assertThat(status.startedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 14, 0));
        assertThat(status.finishedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 14, 0));
    }

    private static Answer<SyncProductsResult> products(java.util.function.Consumer<SyncProgressListener> script) {
        return invocation -> {
            script.accept(invocation.getArgument(0));
            return EMPTY_RESULT;
        };
    }
}
