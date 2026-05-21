package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.AiDirectorService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.SchedulerService;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.ScheduledTaskLogVO;
import com.huazhenghai.readergame.vo.SchedulerSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchedulerServiceImpl implements SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerServiceImpl.class);

    private final PkChallengeMapper pkChallengeMapper;
    private final BroadcastEventMapper broadcastEventMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final ScheduledTaskLogMapper taskLogMapper;
    private final WorldlineService worldlineService;
    private final WorldBossService worldBossService;
    private final WorldBossMapper worldBossMapper;
    private final QuestService questService;
    private final AiDirectorService aiDirectorService;
    private final BroadcastService broadcastService;

    @Value("${app.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public SchedulerServiceImpl(PkChallengeMapper pkChallengeMapper,
                                BroadcastEventMapper broadcastEventMapper,
                                FriendRequestMapper friendRequestMapper,
                                ScheduledTaskLogMapper taskLogMapper,
                                WorldlineService worldlineService,
                                WorldBossService worldBossService,
                                WorldBossMapper worldBossMapper,
                                QuestService questService,
                                AiDirectorService aiDirectorService,
                                BroadcastService broadcastService) {
        this.pkChallengeMapper = pkChallengeMapper;
        this.broadcastEventMapper = broadcastEventMapper;
        this.friendRequestMapper = friendRequestMapper;
        this.taskLogMapper = taskLogMapper;
        this.worldlineService = worldlineService;
        this.worldBossService = worldBossService;
        this.worldBossMapper = worldBossMapper;
        this.questService = questService;
        this.aiDirectorService = aiDirectorService;
        this.broadcastService = broadcastService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.tick-interval-ms:60000}")
    public void scheduledTick() {
        if (!schedulerEnabled) return;
        try {
            runGlobalTick();
        } catch (Exception e) {
            log.error("Scheduler global tick failed", e);
        }
    }

    @Override
    public Map<String, Object> runGlobalTick() {
        String startedAt = LocalDateTime.now().toString();
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (String taskName : ALL_TASKS) {
            Map<String, Object> taskResult = runSingleTask(taskName);
            results.add(taskResult);
            String status = (String) taskResult.get("status");
            if ("success".equals(status)) successCount++;
            else if ("failed".equals(status)) failedCount++;
            else skippedCount++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startedAt", startedAt);
        summary.put("finishedAt", LocalDateTime.now().toString());
        summary.put("totalTasks", ALL_TASKS.size());
        summary.put("successCount", successCount);
        summary.put("failedCount", failedCount);
        summary.put("skippedCount", skippedCount);
        summary.put("taskResults", results);
        return summary;
    }

    @Override
    public Map<String, Object> runSingleTask(String taskName) {
        LocalDateTime startedAt = LocalDateTime.now();
        ScheduledTaskLog taskLog = new ScheduledTaskLog();
        taskLog.setTaskName(taskName);
        taskLog.setStartedAt(startedAt);
        taskLog.setCreatedAt(startedAt);

        try {
            int affected;
            switch (taskName) {
                case "expirePkChallenges" -> {
                    affected = expirePkChallenges();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "expireBroadcastEvents" -> {
                    affected = expireBroadcastEvents();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "applyWorldlineDecay" -> {
                    affected = applyWorldlineDecay();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "expireFriendRequests" -> {
                    affected = expireFriendRequests();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "cleanupOldTaskLogs" -> {
                    affected = cleanupOldTaskLogs();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "openWorldBossIfNeeded" -> {
                    affected = openWorldBossIfNeeded();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "expireWorldBosses" -> {
                    affected = expireWorldBosses();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "expireDailyQuests" -> {
                    affected = questService.refreshDailyQuests();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "expireWeeklyQuests" -> {
                    affected = questService.refreshWeeklyQuests();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "settleKilledWorldBosses" -> {
                    affected = settleKilledWorldBosses();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                case "generateAiBroadcast" -> {
                    affected = generateAiBroadcast();
                    taskLog.setStatus("success");
                    taskLog.setMessage(taskName + " 执行成功");
                }
                default -> {
                    affected = 0;
                    taskLog.setStatus("skipped");
                    taskLog.setMessage("未知任务名称: " + taskName);
                }
            }
            taskLog.setAffectedCount(affected);
        } catch (Exception e) {
            taskLog.setStatus("failed");
            taskLog.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            taskLog.setMessage(taskName + " 执行失败");
            log.error("Scheduler task {} failed", taskName, e);
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        taskLog.setFinishedAt(finishedAt);
        taskLog.setDurationMs(java.time.Duration.between(startedAt, finishedAt).toMillis());
        taskLogMapper.insert(taskLog);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskName", taskName);
        result.put("status", taskLog.getStatus());
        result.put("affectedCount", taskLog.getAffectedCount());
        result.put("durationMs", taskLog.getDurationMs());
        result.put("message", taskLog.getMessage());
        if (taskLog.getErrorMessage() != null) {
            result.put("errorMessage", taskLog.getErrorMessage());
        }
        return result;
    }

    @Override
    public List<ScheduledTaskLogVO> getTaskLogs(int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        QueryWrapper<ScheduledTaskLog> qw = new QueryWrapper<>();
        qw.orderByDesc("created_at")
          .last("LIMIT " + offset + ", " + pageSize);
        List<ScheduledTaskLog> logs = taskLogMapper.selectList(qw);
        return logs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public SchedulerSummaryVO getSummary() {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);

        QueryWrapper<ScheduledTaskLog> failQ = new QueryWrapper<>();
        failQ.eq("status", "failed")
            .ge("created_at", since24h);
        long failedCount = taskLogMapper.selectCount(failQ);

        QueryWrapper<ScheduledTaskLog> successQ = new QueryWrapper<>();
        successQ.eq("status", "success")
               .ge("created_at", since24h);
        long successCount = taskLogMapper.selectCount(successQ);

        QueryWrapper<ScheduledTaskLog> skippedQ = new QueryWrapper<>();
        skippedQ.eq("status", "skipped")
                .ge("created_at", since24h);
        long skippedCount = taskLogMapper.selectCount(skippedQ);

        QueryWrapper<ScheduledTaskLog> lastQ = new QueryWrapper<>();
        lastQ.orderByDesc("created_at")
             .last("LIMIT 1");
        ScheduledTaskLog lastLog = taskLogMapper.selectOne(lastQ);

        SchedulerSummaryVO vo = new SchedulerSummaryVO();
        vo.setSchedulerEnabled(schedulerEnabled);
        vo.setFailedTaskCount24h((int) failedCount);
        vo.setSuccessTaskCount24h((int) successCount);
        vo.setSkippedTaskCount24h((int) skippedCount);

        if (lastLog != null) {
            vo.setLastGlobalTickAt(lastLog.getCreatedAt() != null ? lastLog.getCreatedAt().toString() : null);
            vo.setLastGlobalTickStatus(lastLog.getStatus());
        }
        return vo;
    }

    // ─── sub-tasks ───

    private int expirePkChallenges() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        UpdateWrapper<PkChallenge> uw = new UpdateWrapper<>();
        uw.eq("status", "pending")
          .lt("created_at", cutoff)
          .set("status", "expired")
          .set("resolved_at", LocalDateTime.now());
        return pkChallengeMapper.update(null, uw);
    }

    private int expireBroadcastEvents() {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<BroadcastEvent> uw = new UpdateWrapper<>();
        uw.eq("status", "active")
          .lt("end_at", now)
          .set("status", "expired")
          .set("updated_at", now);
        return broadcastEventMapper.update(null, uw);
    }

    private int applyWorldlineDecay() {
        return worldlineService.applyDecay(0.01);
    }

    private int expireFriendRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        UpdateWrapper<FriendRequest> uw = new UpdateWrapper<>();
        uw.eq("status", "pending")
          .lt("created_at", cutoff)
          .set("status", "expired")
          .set("updated_at", LocalDateTime.now());
        return friendRequestMapper.update(null, uw);
    }

    private int cleanupOldTaskLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        QueryWrapper<ScheduledTaskLog> qw = new QueryWrapper<>();
        qw.lt("created_at", cutoff);
        return taskLogMapper.delete(qw);
    }

    private int openWorldBossIfNeeded() {
        try {
            worldBossService.openScheduledBoss();
            return 1;
        } catch (Exception e) {
            log.warn("openWorldBossIfNeeded failed: {}", e.getMessage());
            return 0;
        }
    }

    private int expireWorldBosses() {
        try {
            QueryWrapper<WorldBoss> qw = new QueryWrapper<>();
            qw.eq("status", "active").lt("end_at", LocalDateTime.now());
            List<WorldBoss> expired = worldBossMapper.selectList(qw);
            for (WorldBoss boss : expired) {
                boss.setStatus("expired");
                boss.setUpdatedAt(LocalDateTime.now());
                worldBossMapper.updateById(boss);
                try { worldBossService.settleBoss(boss.getBossNo()); } catch (Exception ignored) {}
            }
            return expired.size();
        } catch (Exception e) {
            log.warn("expireWorldBosses failed: {}", e.getMessage());
            return 0;
        }
    }

    private int settleKilledWorldBosses() {
        try {
            QueryWrapper<WorldBoss> qw = new QueryWrapper<>();
            qw.eq("status", "killed");
            List<WorldBoss> killed = worldBossMapper.selectList(qw);
            for (WorldBoss boss : killed) {
                try { worldBossService.settleBoss(boss.getBossNo()); } catch (Exception ignored) {}
            }
            return killed.size();
        } catch (Exception e) {
            log.warn("settleKilledWorldBosses failed: {}", e.getMessage());
            return 0;
        }
    }

    // ─── internal ───

    private int generateAiBroadcast() {
        try {
            // Limit: keep at most 2 active AI-generated broadcasts
            QueryWrapper<BroadcastEvent> eq = new QueryWrapper<>();
            eq.eq("status", "active")
              .like("event_key", "ai_");
            long activeAiCount = broadcastEventMapper.selectCount(eq);
            if (activeAiCount >= 2) return 0;

            Map<String, Object> draft = aiDirectorService.generateBroadcastDraft();
            String eventKey = broadcastService.createEvent(draft);
            if (eventKey != null) {
                log.info("AI Director generated broadcast: {} ({})", draft.get("title"), eventKey);
                return 1;
            }
            return 0;
        } catch (Exception e) {
            log.warn("generateAiBroadcast failed: {}", e.getMessage());
            return 0;
        }
    }

    private static final List<String> ALL_TASKS = List.of(
            "expirePkChallenges",
            "expireBroadcastEvents",
            "applyWorldlineDecay",
            "expireFriendRequests",
            "cleanupOldTaskLogs",
            "openWorldBossIfNeeded",
            "expireWorldBosses",
            "settleKilledWorldBosses",
            "expireDailyQuests",
            "expireWeeklyQuests",
            "generateAiBroadcast"
    );

    private ScheduledTaskLogVO toVO(ScheduledTaskLog e) {
        ScheduledTaskLogVO vo = new ScheduledTaskLogVO();
        vo.setId(e.getId());
        vo.setTaskName(e.getTaskName());
        vo.setStatus(e.getStatus());
        vo.setStartedAt(e.getStartedAt() != null ? e.getStartedAt().toString() : null);
        vo.setFinishedAt(e.getFinishedAt() != null ? e.getFinishedAt().toString() : null);
        vo.setDurationMs(e.getDurationMs());
        vo.setAffectedCount(e.getAffectedCount());
        vo.setMessage(e.getMessage());
        vo.setErrorMessage(e.getErrorMessage());
        vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return vo;
    }
}
