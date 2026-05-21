package com.huazhenghai.readergame.service;

import java.util.Map;

public interface AiDirectorService {
    /**
     * Generate a broadcast event draft based on world state.
     * Uses LLM if configured, otherwise falls back to rule-based generator.
     */
    Map<String, Object> generateBroadcastDraft();

    /**
     * Generate using only fallback rules (no LLM).
     */
    Map<String, Object> fallbackBroadcastGenerator(Map<String, Object> worldState);
}
