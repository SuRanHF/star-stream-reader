package com.huazhenghai.readergame.service;

public interface PlayerActionReceiptService {

    <T> T findReplay(Long playerId, String actionType, String idempotencyKey,
                     Object requestPayload, Class<T> responseType);

    void record(Long playerId, String actionType, String idempotencyKey,
                Object requestPayload, Object responsePayload);
}
