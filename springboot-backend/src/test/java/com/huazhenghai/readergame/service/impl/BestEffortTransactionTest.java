package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.BroadcastContribution;
import com.huazhenghai.readergame.entity.BroadcastEvent;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import com.huazhenghai.readergame.websocket.WebSocketSessionManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BestEffortTransactionTest {

    @Test
    void successfulBroadcastContributionRollsBackWithThePrimaryAction() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastHarness broadcast = fixture.broadcastService(false, false);

        fixture.runPrimaryActionThenFail(() -> broadcast.service().contribute(
                "event-test", 1L, 1, "combat"));

        assertEquals(Set.of(), new HashSet<>(fixture.effects()));
    }

    @Test
    void broadcastContributionFailureRollsBackOnlyItsSavepoint() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastHarness broadcast = fixture.broadcastService(false, true);

        fixture.runPrimaryAction(() -> assertThrows(IllegalStateException.class,
                () -> broadcast.service().contribute("event-test", 1L, 1, "combat")));

        assertEquals(Set.of("primary-before", "primary-after"), new HashSet<>(fixture.effects()));
    }

    @Test
    void broadcastCompletionNotificationsWaitForCommit() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastHarness broadcast = fixture.broadcastService(true, false);

        fixture.runPrimaryAction(() -> {
            broadcast.service().contribute("event-test", 1L, 1, "combat");
            verify(broadcast.chatService(), never()).saveSystemMessage(anyString(), anyString(), any());
            verify(broadcast.sessionManager(), never()).broadcastToAll(any());
        });

        verify(broadcast.chatService()).saveSystemMessage(anyString(), anyString(), any());
        verify(broadcast.sessionManager()).broadcastToAll(any());
        assertEquals(Set.of("primary-before", "primary-after", "broadcast-contribution"),
                new HashSet<>(fixture.effects()));
    }

    @Test
    void rolledBackBroadcastCompletionDoesNotNotifyClients() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastHarness broadcast = fixture.broadcastService(true, false);

        fixture.runPrimaryActionThenFail(() -> broadcast.service().contribute(
                "event-test", 1L, 1, "combat"));

        verify(broadcast.chatService(), never()).saveSystemMessage(anyString(), anyString(), any());
        verify(broadcast.sessionManager(), never()).broadcastToAll(any());
    }

    @Test
    void failedBroadcastRewardResourceWriteRollsBackAndLeavesRewardUnclaimed() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastService service = fixture.claimRewardService(true, false);

        assertThrows(IllegalStateException.class, () -> service.claimReward("event-test", 1L));

        assertEquals(23, fixture.rewardValue("coins"));
        assertEquals(10, fixture.rewardValue("fragments"));
        assertEquals(0, fixture.rewardValue("claimed"));
    }

    @Test
    void failedBroadcastClaimMarkerRollsBackPreviouslyGrantedResources() {
        TransactionFixture fixture = new TransactionFixture();
        BroadcastService service = fixture.claimRewardService(false, true);

        assertThrows(IllegalStateException.class, () -> service.claimReward("event-test", 1L));

        assertEquals(23, fixture.rewardValue("coins"));
        assertEquals(10, fixture.rewardValue("fragments"));
        assertEquals(0, fixture.rewardValue("claimed"));
    }

    @Test
    void questProgressFailureRollsBackOnlyItsSavepoint() {
        TransactionFixture fixture = new TransactionFixture();
        PlayerQuestMapper playerQuestMapper = mock(PlayerQuestMapper.class);
        doAnswer(invocation -> {
            fixture.jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "quest-partial");
            throw new IllegalStateException("simulated quest progress failure");
        }).when(playerQuestMapper).selectActiveByPlayerForUpdate(1L);

        QuestServiceImpl target = new QuestServiceImpl(
                mock(QuestMapper.class), playerQuestMapper, mock(QuestProgressLogMapper.class),
                mock(PlayerMapper.class), mock(PlayerLogMapper.class), mock(InventoryService.class),
                mock(TitleService.class), mock(AvatarRankService.class), new ObjectMapper(),
                mock(PlayerEffectService.class), mock(PlayerActionReceiptService.class),
                mock(PlayerResourceTransactionService.class));
        QuestService questService = fixture.proxy(target, QuestService.class);

        fixture.runPrimaryAction(() -> assertThrows(IllegalStateException.class,
                () -> questService.addProgress(1L, "combat_win_count", 1, "combat", "station_rat")));

        assertEquals(Set.of("primary-before", "primary-after"), new HashSet<>(fixture.effects()));
    }

    @Test
    void successfulQuestProgressStillRollsBackWithThePrimaryAction() {
        TransactionFixture fixture = new TransactionFixture();
        PlayerQuestMapper playerQuestMapper = mock(PlayerQuestMapper.class);
        doAnswer(invocation -> {
            fixture.jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "quest-success");
            return List.of();
        }).when(playerQuestMapper).selectActiveByPlayerForUpdate(1L);

        QuestServiceImpl target = new QuestServiceImpl(
                mock(QuestMapper.class), playerQuestMapper, mock(QuestProgressLogMapper.class),
                mock(PlayerMapper.class), mock(PlayerLogMapper.class), mock(InventoryService.class),
                mock(TitleService.class), mock(AvatarRankService.class), new ObjectMapper(),
                mock(PlayerEffectService.class), mock(PlayerActionReceiptService.class),
                mock(PlayerResourceTransactionService.class));
        QuestService questService = fixture.proxy(target, QuestService.class);

        fixture.runPrimaryActionThenFail(() -> questService.addProgress(
                1L, "combat_win_count", 1, "combat", "station_rat"));

        assertEquals(Set.of(), new HashSet<>(fixture.effects()));
    }

    @Test
    void factionContributionFailureRollsBackOnlyItsSavepoint() {
        TransactionFixture fixture = new TransactionFixture();
        PlayerFactionMapper playerFactionMapper = mock(PlayerFactionMapper.class);
        doAnswer(invocation -> {
            fixture.jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "faction-partial");
            throw new IllegalStateException("simulated faction contribution failure");
        }).when(playerFactionMapper).selectActiveByPlayerIdForUpdate(1L);

        FactionServiceImpl target = new FactionServiceImpl(
                mock(ConstellationFactionMapper.class), playerFactionMapper,
                mock(FactionContributionMapper.class), mock(FactionWarMapper.class),
                mock(FactionSkillMapper.class), mock(PlayerFactionSkillMapper.class),
                mock(PlayerMapper.class), mock(PlayerLogMapper.class), new ObjectMapper(),
                mock(QuestService.class), mock(PlayerActionReceiptService.class),
                mock(PlayerResourceTransactionService.class));
        FactionService factionService = fixture.proxy(target, FactionService.class);

        fixture.runPrimaryAction(() -> assertThrows(IllegalStateException.class,
                () -> factionService.contribute(1L, null, "combat", 2L,
                        "combat", "station_rat", null)));

        assertEquals(Set.of("primary-before", "primary-after"), new HashSet<>(fixture.effects()));
    }

    @Test
    void successfulFactionContributionStillRollsBackWithThePrimaryAction() {
        TransactionFixture fixture = new TransactionFixture();
        PlayerFactionMapper playerFactionMapper = mock(PlayerFactionMapper.class);
        doAnswer(invocation -> {
            fixture.jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "faction-success");
            return null;
        }).when(playerFactionMapper).selectActiveByPlayerIdForUpdate(1L);

        FactionServiceImpl target = new FactionServiceImpl(
                mock(ConstellationFactionMapper.class), playerFactionMapper,
                mock(FactionContributionMapper.class), mock(FactionWarMapper.class),
                mock(FactionSkillMapper.class), mock(PlayerFactionSkillMapper.class),
                mock(PlayerMapper.class), mock(PlayerLogMapper.class), new ObjectMapper(),
                mock(QuestService.class), mock(PlayerActionReceiptService.class),
                mock(PlayerResourceTransactionService.class));
        FactionService factionService = fixture.proxy(target, FactionService.class);

        fixture.runPrimaryActionThenFail(() -> factionService.contribute(
                1L, null, "combat", 2L, "combat", "station_rat", null));

        assertEquals(Set.of(), new HashSet<>(fixture.effects()));
    }

    private static final class TransactionFixture {
        private final JdbcTemplate jdbc;
        private final JdbcTransactionManager transactionManager;

        private TransactionFixture() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("org.h2.Driver");
            source.setUrl("jdbc:h2:mem:savepoint_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
            source.setUsername("sa");
            source.setPassword("");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE action_effects (effect VARCHAR(64) NOT NULL)");
            jdbc.execute("CREATE TABLE reward_state (player_id BIGINT PRIMARY KEY, coins INT NOT NULL, "
                    + "fragments INT NOT NULL, claimed INT NOT NULL)");
            jdbc.update("INSERT INTO reward_state(player_id, coins, fragments, claimed) VALUES (?, ?, ?, ?)",
                    1L, 23, 10, 0);
            // Matches Spring Boot's JDBC transaction manager defaults; verify NESTED without test-only overrides.
            transactionManager = new JdbcTransactionManager(source);
        }

        private BroadcastService claimRewardService(boolean failPlayerWrite, boolean failClaimWrite) {
            PlayerMapper playerMapper = mock(PlayerMapper.class);
            BroadcastEventMapper eventMapper = mock(BroadcastEventMapper.class);
            BroadcastContributionMapper contributionMapper = mock(BroadcastContributionMapper.class);

            when(playerMapper.selectByIdForUpdate(1L)).thenAnswer(invocation -> {
                Player player = new Player();
                player.setId(1L);
                player.setCoins(rewardValue("coins"));
                player.setStoryFragments(rewardValue("fragments"));
                return player;
            });

            BroadcastEvent event = new BroadcastEvent();
            event.setPersonalRewardsJson("{\"minContribution\":5,\"coins\":17,\"storyFragments\":3}");
            when(eventMapper.selectOne(any())).thenReturn(event);

            when(contributionMapper.selectOne(any())).thenAnswer(invocation -> {
                BroadcastContribution contribution = new BroadcastContribution();
                contribution.setContributionValue(5);
                contribution.setRewardClaimed(rewardValue("claimed"));
                return contribution;
            });
            doAnswer(invocation -> {
                Player player = invocation.getArgument(0);
                jdbc.update("UPDATE reward_state SET coins = ?, fragments = ? WHERE player_id = ?",
                        player.getCoins(), player.getStoryFragments(), player.getId());
                return failPlayerWrite ? 0 : 1;
            }).when(playerMapper).updateById(any(com.huazhenghai.readergame.entity.Player.class));
            doAnswer(invocation -> {
                BroadcastContribution contribution = invocation.getArgument(0);
                jdbc.update("UPDATE reward_state SET claimed = ? WHERE player_id = ?",
                        contribution.getRewardClaimed(), 1L);
                return failClaimWrite ? 0 : 1;
            }).when(contributionMapper).updateById(any(BroadcastContribution.class));

            BroadcastServiceImpl target = new BroadcastServiceImpl(
                    eventMapper, contributionMapper, mock(WorldlineService.class), mock(ChatService.class),
                    playerMapper, mock(InventoryService.class), new ObjectMapper(),
                    mock(WebSocketSessionManager.class), mock(PlayerEffectService.class),
                    mock(com.huazhenghai.readergame.service.PlayerResourceTransactionService.class));
            return proxy(target, BroadcastService.class);
        }

        private int rewardValue(String column) {
            return jdbc.queryForObject("SELECT " + column + " FROM reward_state WHERE player_id = 1", Integer.class);
        }

        private BroadcastHarness broadcastService(boolean completeEvent, boolean failContributionAfterWrite) {
            BroadcastEventMapper eventMapper = mock(BroadcastEventMapper.class);
            BroadcastContributionMapper contributionMapper = mock(BroadcastContributionMapper.class);
            ChatService chatService = mock(ChatService.class);
            WebSocketSessionManager sessionManager = mock(WebSocketSessionManager.class);

            BroadcastEvent event = new BroadcastEvent();
            event.setEventKey("event-test");
            event.setStatus("active");
            event.setCurrentValue(completeEvent ? 1 : 0);
            event.setTargetValue(completeEvent ? 1 : 100);
            when(eventMapper.selectOne(any())).thenReturn(event);
            when(eventMapper.selectList(any())).thenReturn(List.of());
            when(eventMapper.selectCount(any())).thenReturn(0L);
            doAnswer(invocation -> 1).when(eventMapper).update(isNull(), any(Wrapper.class));
            when(contributionMapper.selectCount(any())).thenReturn(0L);
            when(contributionMapper.selectOne(any())).thenReturn(null);
            doAnswer(invocation -> {
                jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "broadcast-contribution");
                if (failContributionAfterWrite) {
                    throw new IllegalStateException("simulated broadcast contribution failure");
                }
                return 1;
            }).when(contributionMapper).insert(any(BroadcastContribution.class));
            when(eventMapper.updateById(any(BroadcastEvent.class))).thenReturn(1);

            BroadcastServiceImpl target = new BroadcastServiceImpl(
                    eventMapper, contributionMapper, mock(WorldlineService.class), chatService,
                    mock(PlayerMapper.class), mock(InventoryService.class), new ObjectMapper(),
                    sessionManager, mock(PlayerEffectService.class),
                    mock(com.huazhenghai.readergame.service.PlayerResourceTransactionService.class));
            return new BroadcastHarness(proxy(target, BroadcastService.class), chatService, sessionManager);
        }

        private <T> T proxy(T target, Class<T> serviceInterface) {
            ProxyFactory factory = new ProxyFactory(target);
            factory.setInterfaces(serviceInterface);
            factory.addAdvice(new TransactionInterceptor(
                    transactionManager, new AnnotationTransactionAttributeSource()));
            return serviceInterface.cast(factory.getProxy());
        }

        private void runPrimaryAction(Runnable optionalAction) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "primary-before");
                optionalAction.run();
                jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "primary-after");
            });
        }

        private void runPrimaryActionThenFail(Runnable optionalAction) {
            assertThrows(IllegalStateException.class, () ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "primary-before");
                        optionalAction.run();
                        jdbc.update("INSERT INTO action_effects(effect) VALUES (?)", "primary-after");
                        throw new IllegalStateException("simulated primary action failure");
                    }));
        }

        private List<String> effects() {
            return jdbc.queryForList("SELECT effect FROM action_effects ORDER BY effect", String.class);
        }
    }

    private record BroadcastHarness(BroadcastService service, ChatService chatService,
                                    WebSocketSessionManager sessionManager) {}
}
