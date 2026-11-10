package com.eduardo.ecomerce.dto.output.bling;

import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemKind;
import com.eduardo.ecomerce.service.bling.SyncProgressListener.ItemOutcome;

import java.time.LocalDateTime;

public record BlingSyncEventOutput(
        Long blingId,
        String name,
        ItemKind kind,
        ItemOutcome outcome,
        LocalDateTime at
) {
}
