package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.MainChapter;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Title;
import com.huazhenghai.readergame.mapper.MainChapterMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.vo.ChapterProgressVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterServiceImplTest {

    private static final Long PLAYER_ID = 58L;
    private static final Long USER_ID = 15L;
    private static final String CHAPTER_ONE = "main_ch01_paid_service";
    private static final String CHAPTER_TWO = "main_ch02_station";

    @Mock private PlayerMapper playerMapper;
    @Mock private MainChapterMapper chapterMapper;
    @Mock private PlayerTitleMapper playerTitleMapper;
    @Mock private TitleMapper titleMapper;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerLogService playerLogService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private ChapterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChapterServiceImpl(playerMapper, chapterMapper, playerTitleMapper,
                titleMapper, new ObjectMapper(), playerEffectService, playerLogService, resourceTransactionService);
    }

    @Test
    void advanceLocksPlayerChecksExpectedChapterAndGrantsRewardOnce() throws Exception {
        Player player = playerAt(CHAPTER_ONE);
        player.setCoins(5);
        player.setStoryFragments(2);
        player.setStatsJson("{\"channelHeat\":4}");
        MainChapter chapterOne = chapter(CHAPTER_ONE, CHAPTER_TWO,
                "{\"coins\":10,\"storyFragments\":3,\"channelHeat\":6}");
        MainChapter chapterTwo = chapter(CHAPTER_TWO, null, "{}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapterOne, chapterTwo, chapterTwo);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(playerEffectService.applyChannelHeatGainRate(PLAYER_ID, 6)).thenReturn(6);
        when(resourceTransactionService.record(any())).thenReturn(true);

        ChapterProgressVO result = service.claimChapterRewardAndAdvance(PLAYER_ID, CHAPTER_ONE, USER_ID);

        assertEquals(CHAPTER_TWO, player.getCurrentMainChapter());
        assertEquals(15, player.getCoins());
        assertEquals(5, player.getStoryFragments());
        assertEquals(10, new ObjectMapper().readTree(player.getStatsJson()).path("channelHeat").asInt());
        assertEquals(CHAPTER_TWO, result.getCurrentChapterKey());
        verify(playerMapper).updateById(player);
        verify(playerLogService).addLog(any(), any(), any(), any());
        ArgumentCaptor<PlayerResourceTransactionService.Change> ledger =
                ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, times(3)).record(ledger.capture());
        var changes = ledger.getAllValues().stream().collect(java.util.stream.Collectors.toMap(
                change -> change.resourceType() + ":" + change.resourceKey(), change -> change));
        assertEquals(10L, changes.get("currency:coins").amount());
        assertEquals(5L, changes.get("currency:story_fragments").balanceAfter());
        assertEquals(6L, changes.get("progress:channel_heat").amount());
        assertEquals(4L, changes.get("progress:channel_heat").balanceBefore());

        InOrder lockOrder = inOrder(playerMapper, chapterMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        lockOrder.verify(chapterMapper, times(3)).selectOne(any());
    }

    @Test
    void replayedAdvanceForPreviousChapterIsRejectedWithoutGrantingOrAdvancingAgain() {
        Player player = playerAt(CHAPTER_TWO);
        player.setCoins(15);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);

        assertThrows(BusinessException.class,
                () -> service.claimChapterRewardAndAdvance(PLAYER_ID, CHAPTER_ONE, USER_ID));

        assertEquals(CHAPTER_TWO, player.getCurrentMainChapter());
        assertEquals(15, player.getCoins());
        verify(chapterMapper, never()).selectOne(any());
        verify(playerMapper, never()).updateById(any(Player.class));
    }

    @Test
    void chapterFourShowsLocationAndEpicTitleRequirementsInsteadOfSkippingThem() {
        Player player = playerAt("main_ch04_abyss_awakening");
        player.setStatsJson("{\"avatarRank\":\"B\",\"worldLineShift\":20}");
        player.setStageProgressJson("{\"explorationsByLocation\":{\"demon_gate\":2}}");
        MainChapter chapter = chapter("main_ch04_abyss_awakening", "main_ch05_starstream_convergence",
                "{\"locationExploreCount\":{\"demon_gate\":3},\"epicTitleRequired\":true}", "{}");
        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapter);
        when(playerTitleMapper.selectList(any())).thenReturn(java.util.List.of());

        ChapterProgressVO progress = service.getCurrentChapter(PLAYER_ID);

        assertFalse(progress.isCompleted());
        assertFalse(progress.isCanAdvance());
        assertEquals(2, progress.getRequirements().get(0).getCurrent());
        assertEquals(3, progress.getRequirements().get(0).getRequired());
        assertEquals("史诗及以上称号", progress.getRequirements().get(1).getLabel());
        assertFalse(progress.getRequirements().get(1).isCompleted());
    }

    @Test
    void chapterFiveCountsDistinctHiddenEventsAndChecksLegendaryTitles() {
        Player player = playerAt("main_ch05_starstream_convergence");
        player.setStatsJson("{\"avatarRank\":\"A\",\"channelHeat\":500}");
        player.setStageProgressJson("{\"hiddenEventsTriggered\":[\"hidden_1\",\"hidden_2\",\"hidden_2\"]}");
        MainChapter chapter = chapter("main_ch05_starstream_convergence", "main_ch06_final_script",
                "{\"legendaryTitleRequired\":true,\"hiddenEventsTriggeredMin\":3}", "{}");
        PlayerTitle ownedTitle = new PlayerTitle();
        ownedTitle.setTitleKey("legendary_reader");
        Title title = new Title();
        title.setTitleKey("legendary_reader");
        title.setRarity("legendary");
        title.setEnabled(1);
        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapter);
        when(playerTitleMapper.selectList(any())).thenReturn(java.util.List.of(ownedTitle));
        when(titleMapper.selectList(any())).thenReturn(java.util.List.of(title));

        ChapterProgressVO progress = service.getCurrentChapter(PLAYER_ID);

        assertFalse(progress.isCompleted());
        assertTrue(progress.getRequirements().get(0).isCompleted());
        assertEquals(2, progress.getRequirements().get(1).getCurrent());
        assertEquals(3, progress.getRequirements().get(1).getRequired());
    }

    @Test
    void unknownChapterCompletionConditionsFailClosedAndRemainVisible() {
        Player player = playerAt("unknown_condition_chapter");
        MainChapter chapter = chapter("unknown_condition_chapter", "next_chapter",
                "{\"misspelledCondition\":1}", "{}");
        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapter);

        ChapterProgressVO progress = service.getCurrentChapter(PLAYER_ID);

        assertFalse(progress.isCompleted());
        assertFalse(progress.isCanAdvance());
        assertEquals("misspelledCondition", progress.getRequirements().get(0).getType());
        assertEquals("未支持的章节条件（需管理员修正配置）", progress.getRequirements().get(0).getLabel());
    }

    @Test
    void worldLineAndRankRequirementsAreValidatedBeforeRewardOrAdvance() {
        Player player = playerAt("main_ch07_omniscient");
        player.setStatsJson("{\"avatarRank\":\"SS\",\"worldLineShift\":50}");
        MainChapter chapter = chapter("main_ch07_omniscient", "final_chapter",
                "{\"avatarRankMin\":\"SSS\",\"worldLineShiftMin\":50}", "{\"coins\":1000}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapter);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimChapterRewardAndAdvance(PLAYER_ID, "main_ch07_omniscient", USER_ID));

        assertEquals("当前阶段尚未完成", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void unknownNextChapterUnlockConditionCannotBeBypassed() {
        Player player = playerAt(CHAPTER_ONE);
        MainChapter chapterOne = chapter(CHAPTER_ONE, CHAPTER_TWO, "{}", "{\"coins\":10}");
        MainChapter chapterTwo = chapter(CHAPTER_TWO, null, "{}", "{}");
        chapterTwo.setUnlockConditionsJson("{\"unknownGate\":true}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapterOne, chapterTwo);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimChapterRewardAndAdvance(PLAYER_ID, CHAPTER_ONE, USER_ID));

        assertEquals("不满足下一阶段解锁条件", error.getMessage());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void chapterRewardIsNotReportedWhenPlayerStateCannotBeSaved() {
        Player player = playerAt(CHAPTER_ONE);
        player.setCoins(5);
        MainChapter chapterOne = chapter(CHAPTER_ONE, CHAPTER_TWO, "{}", "{\"coins\":10}");
        MainChapter chapterTwo = chapter(CHAPTER_TWO, null, "{}", "{}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapterOne, chapterTwo);
        when(playerMapper.updateById(player)).thenReturn(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.claimChapterRewardAndAdvance(PLAYER_ID, CHAPTER_ONE, USER_ID));

        assertEquals("主线阶段奖励与进度保存失败", error.getMessage());
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    @Test
    void chapterRewardRejectsCurrencyOverflowBeforeWritingPlayerOrLedger() {
        Player player = playerAt(CHAPTER_ONE);
        player.setCoins(Integer.MAX_VALUE);
        MainChapter chapterOne = chapter(CHAPTER_ONE, CHAPTER_TWO, "{}", "{\"coins\":1}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(chapterMapper.selectOne(any())).thenReturn(chapterOne);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimChapterRewardAndAdvance(PLAYER_ID, CHAPTER_ONE, USER_ID));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(playerLogService, never()).addLog(any(), any(), any(), any());
    }

    private Player playerAt(String chapterKey) {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setCurrentMainChapter(chapterKey);
        player.setStatsJson("{}");
        player.setStageProgressJson("{}");
        return player;
    }

    private MainChapter chapter(String key, String nextKey, String rewardsJson) {
        return chapter(key, nextKey, "{}", rewardsJson);
    }

    private MainChapter chapter(String key, String nextKey, String completionJson, String rewardsJson) {
        MainChapter chapter = new MainChapter();
        chapter.setChapterKey(key);
        chapter.setName(key);
        chapter.setEnabled(1);
        chapter.setNextChapterKey(nextKey);
        chapter.setCompletionConditionsJson(completionJson);
        chapter.setUnlockConditionsJson("{}");
        chapter.setRewardsJson(rewardsJson);
        return chapter;
    }
}
