package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huazhenghai.readergame.entity.ScheduledTaskLog;
import com.huazhenghai.readergame.entity.WorldBoss;
import com.huazhenghai.readergame.mapper.BroadcastEventMapper;
import com.huazhenghai.readergame.mapper.FriendRequestMapper;
import com.huazhenghai.readergame.mapper.PkChallengeMapper;
import com.huazhenghai.readergame.mapper.ScheduledTaskLogMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import com.huazhenghai.readergame.service.AiDirectorService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceImplTest {

    @Mock private PkChallengeMapper pkChallengeMapper;
    @Mock private BroadcastEventMapper broadcastEventMapper;
    @Mock private FriendRequestMapper friendRequestMapper;
    @Mock private ScheduledTaskLogMapper taskLogMapper;
    @Mock private FactionService factionService;
    @Mock private WorldlineService worldlineService;
    @Mock private WorldBossService worldBossService;
    @Mock private WorldBossMapper worldBossMapper;
    @Mock private QuestService questService;
    @Mock private AiDirectorService aiDirectorService;
    @Mock private BroadcastService broadcastService;
    @Mock private WebSocketSessionManager sessionManager;

    private final List<ScheduledTaskLog> savedTaskLogs = new ArrayList<>();
    private SchedulerServiceImpl scheduler;

    @BeforeEach
    void setUp() {
        savedTaskLogs.clear();
        doAnswer(invocation -> {
            savedTaskLogs.add(invocation.getArgument(0));
            return 1;
        }).when(taskLogMapper).insert(any(ScheduledTaskLog.class));
        scheduler = new SchedulerServiceImpl(pkChallengeMapper, broadcastEventMapper,
                friendRequestMapper, taskLogMapper, factionService, worldlineService,
                worldBossService, worldBossMapper, questService, aiDirectorService,
                broadcastService, sessionManager);
    }

    @Test
    void concurrentAiBroadcastCreatesOnce() throws Exception {
        AtomicInteger created = new AtomicInteger();
        when(broadcastEventMapper.selectCount(any())).thenAnswer(invocation -> (long) created.get());
        when(aiDirectorService.generateBroadcastDraft()).thenReturn(Map.of("title", "测试放送"));
        when(broadcastService.createEvent(any())).thenAnswer(invocation -> {
            created.incrementAndGet();
            return ((Map<?, ?>) invocation.getArgument(0)).get("eventKey");
        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return scheduler.runSingleTask("generateAiBroadcast");
            }, pool);
            var second = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return scheduler.runSingleTask("generateAiBroadcast");
            }, pool);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int total = ((Number) first.get(5, TimeUnit.SECONDS).get("affectedCount")).intValue()
                    + ((Number) second.get(5, TimeUnit.SECONDS).get("affectedCount")).intValue();
            assertEquals(1, total);
            assertEquals(1, created.get());
            verify(broadcastService, times(1)).createEvent(any());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aiBroadcastDedupIgnoresNonAiKeys() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:ai_broadcast_prefix;MODE=MySQL", "sa", "");
             var create = connection.createStatement()) {
            create.execute("CREATE TABLE broadcast_events (event_key VARCHAR(100), status VARCHAR(20))");
            create.execute("INSERT INTO broadcast_events VALUES ('aix_event', 'active'), ('air_raid', 'active')");
            when(broadcastEventMapper.selectCount(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                QueryWrapper<com.huazhenghai.readergame.entity.BroadcastEvent> query = invocation.getArgument(0);
                var parameters = new ArrayList<Object>();
                var matcher = java.util.regex.Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)}")
                        .matcher(query.getSqlSegment());
                var sqlSegment = new StringBuffer();
                while (matcher.find()) {
                    parameters.add(query.getParamNameValuePairs().get(matcher.group(1)));
                    matcher.appendReplacement(sqlSegment, "?");
                }
                matcher.appendTail(sqlSegment);
                String sql = "SELECT COUNT(*) FROM broadcast_events WHERE " + sqlSegment;
                try (var statement = connection.prepareStatement(sql)) {
                    int position = 1;
                    for (Object value : parameters)
                        statement.setObject(position++, value);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        return rows.getLong(1);
                    }
                }
            });
            when(aiDirectorService.generateBroadcastDraft()).thenReturn(Map.of("title", "AI 放送"));
            when(broadcastService.createEvent(any())).thenReturn("ai_2026110104");
            assertEquals(1, scheduler.runSingleTask("generateAiBroadcast").get("affectedCount"));
            create.execute("INSERT INTO broadcast_events VALUES ('ai_2026110104', 'active')");
            assertEquals(0, scheduler.runSingleTask("generateAiBroadcast").get("affectedCount"));
            verify(broadcastService, times(1)).createEvent(any());
        }
    }

    @Test
    void expiredBossSettlementFailureIsLoggedAndRetriedOnNextTick() {
        WorldBoss retryBoss = boss("boss-expired-retry", "active");
        WorldBoss completedBoss = boss("boss-expired-completed", "active");
        when(worldBossMapper.selectList(any())).thenReturn(
                List.of(retryBoss, completedBoss), List.of(retryBoss));
        when(worldBossMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        doThrow(new IllegalStateException("temporary settlement failure"))
                .doNothing().when(worldBossService).settleBoss(retryBoss.getBossNo());

        Map<String, Object> first = scheduler.runSingleTask("expireWorldBosses");

        assertEquals("failed", first.get("status"));
        assertEquals(1, first.get("affectedCount"));
        assertTrue(String.valueOf(first.get("errorMessage")).contains("将重试"));
        assertEquals("failed", savedTaskLogs.get(0).getStatus());
        assertEquals(1, savedTaskLogs.get(0).getAffectedCount());
        verify(worldBossService).settleBoss(completedBoss.getBossNo());

        Map<String, Object> second = scheduler.runSingleTask("expireWorldBosses");

        assertEquals("success", second.get("status"));
        assertEquals(1, second.get("affectedCount"));
        assertEquals("success", savedTaskLogs.get(1).getStatus());
        verify(worldBossService, times(2)).settleBoss(retryBoss.getBossNo());
        verify(worldBossMapper, times(2)).selectList(any());

        ArgumentCaptor<QueryWrapper<WorldBoss>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(worldBossMapper, times(2)).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getAllValues().stream()
                .allMatch(query -> query.getSqlSegment().toUpperCase().contains(" OR ")));
    }

    @Test
    void killedBossSettlementFailureMakesSchedulerTaskFailAndCanRetry() {
        WorldBoss boss = boss("boss-killed-retry", "killed");
        when(worldBossMapper.selectList(any())).thenReturn(List.of(boss), List.of(boss));
        doThrow(new IllegalStateException("temporary settlement failure"))
                .doNothing().when(worldBossService).settleBoss(boss.getBossNo());

        Map<String, Object> first = scheduler.runSingleTask("settleKilledWorldBosses");
        Map<String, Object> second = scheduler.runSingleTask("settleKilledWorldBosses");

        assertEquals("failed", first.get("status"));
        assertEquals(0, first.get("affectedCount"));
        assertEquals("success", second.get("status"));
        assertEquals(1, second.get("affectedCount"));
        assertEquals("failed", savedTaskLogs.get(0).getStatus());
        assertEquals("success", savedTaskLogs.get(1).getStatus());
        verify(worldBossService, times(2)).settleBoss(boss.getBossNo());
    }

    private WorldBoss boss(String bossNo, String status) {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(bossNo);
        boss.setName("测试首领");
        boss.setStatus(status);
        boss.setEndAt(LocalDateTime.now().minusMinutes(1));
        return boss;
    }
}
