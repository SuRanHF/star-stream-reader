package com.huazhenghai.readergame.service;

import java.util.Map;

public interface PlayerResourceTransactionService {

    /**
     * Append one resource mutation. Returns false when the exact same logical mutation
     * was already recorded under this player's idempotency key.
     */
    boolean record(Change change);

    record Change(
            Long playerId,
            String resourceType,
            String resourceKey,
            long amount,
            long balanceBefore,
            long balanceAfter,
            String sourceType,
            String sourceId,
            String transactionGroupId,
            String idempotencyKey,
            Map<String, Object> metadata) {
    }
}
