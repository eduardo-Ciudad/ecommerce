package com.eduardo.ecomerce.service.bling;

/** Recebe o progresso do sync de produtos do Bling, item a item. */
public interface SyncProgressListener {

    SyncProgressListener NOOP = new SyncProgressListener() {
    };

    enum ItemKind {
        VARIANT,
        STANDALONE
    }

    enum ItemOutcome {
        SYNCED,
        SKIPPED,
        FAILED
    }

    default void onTotal(int totalItems) {
    }

    default void onItem(Long blingId, String name, ItemKind kind, ItemOutcome outcome) {
    }

    default void onReconciling() {
    }

    default void onDeactivated(int count) {
    }
}
