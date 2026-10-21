package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class RankingServiceImplTest {

    @Mock private RankingMapper rankingMapper;
    @Mock private PlayerMapper playerMapper;

    private RankingServiceImpl rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingServiceImpl(rankingMapper, playerMapper);
    }

    @Test
    void leaderboardIncludesRankedAndUnrankedPlayersWithoutPerPlayerQueries() {
        Map<String, Object> rankedRow = row(12L, "Ranked player", 1, 1180, 4, 2, 1250);
        Map<String, Object> unrankedRow = row(13L, "New player", 0, 1000, 0, 0, 1000);
        when(rankingMapper.selectEloLeaderboard(50)).thenReturn(List.of(rankedRow, unrankedRow));

        List<RankingVO> result = rankingService.getRankings(50);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getRank());
        assertTrue(result.get(0).isRanked());
        assertEquals(1180, result.get(0).getRating());
        assertEquals(0, result.get(1).getRank());
        assertFalse(result.get(1).isRanked());
        assertEquals(1000, result.get(1).getRating());
        verify(rankingMapper).selectEloLeaderboard(50);
        verify(playerMapper, never()).selectById(12L);
        verify(playerMapper, never()).selectById(13L);
    }

    @Test
    void storyDiscoveryLeaderboardUsesRollingSevenDayWindowAndCompetitionRanks() {
        when(rankingMapper.selectStoryDiscoveryLeaderboard(any(LocalDateTime.class),
                any(LocalDateTime.class), eq(100))).thenReturn(List.of(
                storyRow(12L, "First explorer", 3),
                storyRow(13L, "Second explorer", 3),
                storyRow(14L, "Third explorer", 1)));
        ArgumentCaptor<LocalDateTime> startAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endAt = ArgumentCaptor.forClass(LocalDateTime.class);

        var result = rankingService.getStoryDiscoveryRankings(1000);

        assertEquals(List.of(1, 1, 3), result.stream()
                .map(com.huazhenghai.readergame.vo.StoryDiscoveryRankingVO::getRank).toList());
        assertEquals(List.of(3, 3, 1), result.stream()
                .map(com.huazhenghai.readergame.vo.StoryDiscoveryRankingVO::getDiscoveryCount).toList());
        verify(rankingMapper).selectStoryDiscoveryLeaderboard(startAt.capture(), endAt.capture(), eq(100));
        assertEquals(7, Duration.between(startAt.getValue(), endAt.getValue()).toDays());
    }

    @Test
    void storyDiscoveryLeaderboardClampsTheRequestedLimit() {
        when(rankingMapper.selectStoryDiscoveryLeaderboard(any(LocalDateTime.class),
                any(LocalDateTime.class), eq(1))).thenReturn(List.of());

        assertTrue(rankingService.getStoryDiscoveryRankings(0).isEmpty());

        verify(rankingMapper).selectStoryDiscoveryLeaderboard(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(1));
    }

    @Test
    void unrankedPlayerGetsAnExplicitInitialRatingInsteadOfAnEmptyRank() {
        Player player = new Player();
        player.setId(13L);
        when(playerMapper.selectById(13L)).thenReturn(player);
        when(rankingMapper.selectByPlayerId(13L)).thenReturn(null);
        when(rankingMapper.countRankedPlayers()).thenReturn(7L);

        PlayerRankVO result = rankingService.getPlayerRank(13L);

        assertEquals(0, result.getRank());
        assertEquals(7, result.getTotalPlayers());
        assertFalse(result.isRanked());
        assertEquals(1000, result.getRating());
        assertEquals(0, result.getWins());
        assertEquals(0, result.getLosses());
        assertEquals(1000, result.getHighestRating());
        verify(rankingMapper, never()).countRankedPlayersAhead(1000, 0, 0, 13L);
    }

    @Test
    void rankedPlayerGetsItsPositionUsingTheLeaderboardTieBreakers() {
        Player player = new Player();
        player.setId(21L);
        Ranking ranking = new Ranking();
        ranking.setRating(1320);
        ranking.setWins(8);
        ranking.setLosses(3);
        ranking.setHighestRating(1370);
        when(playerMapper.selectById(21L)).thenReturn(player);
        when(rankingMapper.selectByPlayerId(21L)).thenReturn(ranking);
        when(rankingMapper.countRankedPlayersAhead(1320, 8, 3, 21L)).thenReturn(2L);
        when(rankingMapper.countRankedPlayers()).thenReturn(12L);

        PlayerRankVO result = rankingService.getPlayerRank(21L);

        assertTrue(result.isRanked());
        assertEquals(3, result.getRank());
        assertEquals(12, result.getTotalPlayers());
        assertEquals(1320, result.getRating());
        assertEquals(8, result.getWins());
        assertEquals(3, result.getLosses());
        assertEquals(1370, result.getHighestRating());
    }

    @Test
    void nearbyRankingsReturnThePlayerAndAdjacentCompetitorsInOfficialOrder() {
        Player player = new Player();
        player.setId(21L);
        Ranking ownRanking = new Ranking();
        ownRanking.setRating(1320);
        ownRanking.setWins(8);
        ownRanking.setLosses(3);
        ownRanking.setHighestRating(1370);
        when(playerMapper.selectById(21L)).thenReturn(player);
        when(rankingMapper.selectByPlayerId(21L)).thenReturn(ownRanking);
        when(rankingMapper.countRankedPlayersAhead(1320, 8, 3, 21L)).thenReturn(4L);
        when(rankingMapper.countRankedPlayers()).thenReturn(20L);
        when(rankingMapper.selectRankedLeaderboardWindow(5, 2)).thenReturn(List.of(
                leaderboardRow(18L, "Above two", 1400, 5, 2, 1420),
                leaderboardRow(19L, "Above one", 1360, 8, 3, 1380),
                leaderboardRow(21L, "Me", 1320, 8, 3, 1370),
                leaderboardRow(22L, "Below one", 1280, 6, 4, 1300),
                leaderboardRow(23L, "Below two", 1200, 3, 2, 1210)));

        List<RankingVO> result = rankingService.getNearbyRankings(21L, 2);

        assertEquals(List.of(3, 4, 5, 6, 7), result.stream().map(RankingVO::getRank).toList());
        assertEquals(List.of(18L, 19L, 21L, 22L, 23L),
                result.stream().map(RankingVO::getPlayerId).toList());
        assertTrue(result.stream().allMatch(RankingVO::isRanked));
        verify(rankingMapper).selectRankedLeaderboardWindow(5, 2);
    }

    @Test
    void nearbyRankingsReturnNoOfficialNeighborsUntilPlayerIsRanked() {
        Player player = new Player();
        player.setId(13L);
        when(playerMapper.selectById(13L)).thenReturn(player);
        when(rankingMapper.selectByPlayerId(13L)).thenReturn(null);
        when(rankingMapper.countRankedPlayers()).thenReturn(7L);

        assertTrue(rankingService.getNearbyRankings(13L, 2).isEmpty());

        verify(rankingMapper, never()).selectRankedLeaderboardWindow(5, 0);
    }

    @Test
    void nearbyWindowShrinksAtTheTopAndBottomOfTheLeaderboard() {
        Player topPlayer = new Player();
        topPlayer.setId(31L);
        Ranking topRanking = ranking(1000, 2, 0, 1000);
        when(playerMapper.selectById(31L)).thenReturn(topPlayer);
        when(rankingMapper.selectByPlayerId(31L)).thenReturn(topRanking);
        when(rankingMapper.countRankedPlayersAhead(1000, 2, 0, 31L)).thenReturn(0L);
        when(rankingMapper.countRankedPlayers()).thenReturn(4L);
        when(rankingMapper.selectRankedLeaderboardWindow(3, 0)).thenReturn(List.of(
                leaderboardRow(31L, "Top", 1000, 2, 0, 1000),
                leaderboardRow(32L, "Second", 990, 2, 1, 990),
                leaderboardRow(33L, "Third", 980, 1, 1, 980)));

        List<RankingVO> topWindow = rankingService.getNearbyRankings(31L, 2);

        assertEquals(List.of(1, 2, 3), topWindow.stream().map(RankingVO::getRank).toList());
        verify(rankingMapper).selectRankedLeaderboardWindow(3, 0);

        Player bottomPlayer = new Player();
        bottomPlayer.setId(34L);
        Ranking bottomRanking = ranking(800, 0, 3, 800);
        when(playerMapper.selectById(34L)).thenReturn(bottomPlayer);
        when(rankingMapper.selectByPlayerId(34L)).thenReturn(bottomRanking);
        when(rankingMapper.countRankedPlayersAhead(800, 0, 3, 34L)).thenReturn(3L);
        when(rankingMapper.countRankedPlayers()).thenReturn(4L);
        when(rankingMapper.selectRankedLeaderboardWindow(3, 1)).thenReturn(List.of(
                leaderboardRow(32L, "Second", 990, 2, 1, 990),
                leaderboardRow(33L, "Third", 980, 1, 1, 980),
                leaderboardRow(34L, "Bottom", 800, 0, 3, 800)));

        List<RankingVO> bottomWindow = rankingService.getNearbyRankings(34L, 2);

        assertEquals(List.of(2, 3, 4), bottomWindow.stream().map(RankingVO::getRank).toList());
        verify(rankingMapper).selectRankedLeaderboardWindow(3, 1);
    }

    @Test
    void unknownPlayerStillReturnsNoRank() {
        when(playerMapper.selectById(404L)).thenReturn(null);

        assertNull(rankingService.getPlayerRank(404L));

        verify(rankingMapper, never()).selectByPlayerId(404L);
    }

    @Test
    void leaderboardLimitIsClampedToOneThroughOneHundred() {
        when(rankingMapper.selectEloLeaderboard(1)).thenReturn(List.of());
        when(rankingMapper.selectEloLeaderboard(100)).thenReturn(List.of());

        rankingService.getRankings(0);
        rankingService.getRankings(500);

        verify(rankingMapper).selectEloLeaderboard(1);
        verify(rankingMapper).selectEloLeaderboard(100);
    }

    private static Map<String, Object> row(long playerId, String playerName, int ranked,
                                           int rating, int wins, int losses, int highestRating) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", playerId);
        row.put("playerName", playerName);
        row.put("level", 1);
        row.put("ranked", ranked);
        row.put("rating", rating);
        row.put("wins", wins);
        row.put("losses", losses);
        row.put("highestRating", highestRating);
        return row;
    }

    private static Map<String, Object> leaderboardRow(long playerId, String playerName, int rating,
                                                       int wins, int losses, int highestRating) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", playerId);
        row.put("playerName", playerName);
        row.put("level", 1);
        row.put("rating", rating);
        row.put("wins", wins);
        row.put("losses", losses);
        row.put("highestRating", highestRating);
        return row;
    }

    private static Map<String, Object> storyRow(long playerId, String playerName, int discoveryCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("playerId", playerId);
        row.put("playerName", playerName);
        row.put("discoveryCount", discoveryCount);
        return row;
    }

    private static Ranking ranking(int rating, int wins, int losses, int highestRating) {
        Ranking ranking = new Ranking();
        ranking.setRating(rating);
        ranking.setWins(wins);
        ranking.setLosses(losses);
        ranking.setHighestRating(highestRating);
        return ranking;
    }
}
