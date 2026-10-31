package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.ExplorationEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/** 探索事件的纯选择与权重计算，不读取或写入玩家状态。 */
final class ExploreEventSelector {

    private ExploreEventSelector() {
    }

    static ExplorationEvent selectEvent(
            List<ExplorationEvent> events,
            int storyPity,
            Map<String, Object> eventRates,
            double storyEventBonus,
            Random random) {
        return selectEvent(events, storyPity, eventRates, storyEventBonus, random, event -> false, 1.0);
    }

    static ExplorationEvent selectEvent(
            List<ExplorationEvent> events,
            int storyPity,
            Map<String, Object> eventRates,
            double storyEventBonus,
            Random random,
            Predicate<ExplorationEvent> tracedStory,
            double traceMultiplier) {
        List<ExplorationEvent> selectionEvents = events;
        // 硬保底只排除非主线事件；主线之间仍按配置权重随机，避免依赖数据库返回顺序。
        if (storyPity >= 8) {
            List<ExplorationEvent> availableStories = events.stream()
                    .filter(event -> "story".equals(event.getEventType()))
                    .toList();
            if (!availableStories.isEmpty()) {
                selectionEvents = availableStories;
            }
        }

        // 按 event_rates_json 进行加权：base_weight × type_rate_bonus。
        double totalWeight = 0.0;
        Map<String, Double> adjustedWeights = new LinkedHashMap<>();
        for (ExplorationEvent event : selectionEvents) {
            int weight = event.getWeight();
            // event_rates 是百分比基准，100 = 1x。
            if (eventRates.containsKey(event.getEventType())) {
                double rateFactor = ((Number) eventRates.get(event.getEventType())).doubleValue() / 100.0;
                weight = Math.max(1, (int) Math.round(weight * rateFactor));
            }
            if ("story".equals(event.getEventType())) {
                weight = applyStoryPityToWeight(weight, storyPity);
            }

            double adjustedWeight = weight;
            if ("story".equals(event.getEventType())) {
                adjustedWeight = applyStoryEventBonusToWeight(weight, storyEventBonus);
            }
            if (tracedStory.test(event)) adjustedWeight *= traceMultiplier;
            adjustedWeights.put(event.getEventKey(), adjustedWeight);
            totalWeight += adjustedWeight;
        }

        if (totalWeight <= 0) {
            return selectionEvents.get(0);
        }

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (ExplorationEvent event : selectionEvents) {
            cumulative += adjustedWeights.get(event.getEventKey());
            if (roll < cumulative) {
                return event;
            }
        }
        return selectionEvents.get(selectionEvents.size() - 1);
    }

    static double applyStoryEventBonusToWeight(double weight, double bonus) {
        if (!Double.isFinite(weight) || weight <= 0.0) {
            return 0.0;
        }
        double safeBonus = Double.isFinite(bonus) ? Math.max(-0.99, bonus) : 0.0;
        return Math.max(0.01, weight * (1.0 + safeBonus));
    }

    static int applyStoryPityToWeight(int weight, int storyPity) {
        if (weight <= 0) {
            return 0;
        }
        long safePity = Math.max(0, storyPity);
        long bonusPerMiss = Math.max(1, weight / 2);
        return (int) Math.min(Integer.MAX_VALUE, weight + safePity * bonusPerMiss);
    }

    static double combineEffectBonus(
            Map<String, Object> skillEffects,
            Map<String, Object> titleEffects,
            Map<String, Object> equipmentEffects,
            String effectKey) {
        double combined = finiteNumericEffect(skillEffects, effectKey)
                + finiteNumericEffect(titleEffects, effectKey)
                + finiteNumericEffect(equipmentEffects, effectKey);
        return Double.isFinite(combined) ? combined : 0.0;
    }

    private static double finiteNumericEffect(Map<String, Object> effects, String effectKey) {
        if (effects == null || !(effects.get(effectKey) instanceof Number value)) {
            return 0.0;
        }
        double number = value.doubleValue();
        return Double.isFinite(number) ? number : 0.0;
    }
}
