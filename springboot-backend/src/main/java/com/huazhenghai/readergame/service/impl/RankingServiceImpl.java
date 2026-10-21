package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.service.RankingService;
import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryPositionVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryRankingVO;
import com.huazhenghai.readergame.vo.PkPerformancePositionVO;
import com.huazhenghai.readergame.vo.PkPerformanceRankingVO;
import com.huazhenghai.readergame.vo.ExploreActionPositionVO;
import com.huazhenghai.readergame.vo.ExploreActionRankingVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class RankingServiceImpl implements RankingService {

    private final RankingMapper rankingMapper;
    private final PlayerMapper playerMapper;

    public RankingServiceImpl(RankingMapper rankingMapper,
                              PlayerMapper playerMapper) {
        this.rankingMapper = rankingMapper;
        this.playerMapper = playerMapper;
    }

    @Override
    public List<RankingVO> getRankings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> rows = rankingMapper.selectEloLeaderboard(safeLimit);
        List<RankingVO> result = new java.util.ArrayList<>(rows.size());
        int rank = 0;
        for (Map<String, Object> row : rows) {
            RankingVO vo = new RankingVO();
            vo.setPlayerId(toLong(row.get("playerId")));
            vo.setPlayerName(toText(row.get("playerName")));
            vo.setLevel(toInt(row.get("level"), 1));
            vo.setRanked(toBoolean(row.get("ranked")));
            if (vo.isRanked()) rank++;
            vo.setRank(vo.isRanked() ? rank : 0);
            vo.setRating(toInt(row.get("rating"), 1000));
            vo.setWins(toInt(row.get("wins"), 0));
            vo.setLosses(toInt(row.get("losses"), 0));
            vo.setHighestRating(toInt(row.get("highestRating"), 1000));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryDiscoveryRankingVO> getStoryDiscoveryRankings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LocalDateTime endAt = LocalDateTime.now();
        LocalDateTime startAt = endAt.minusDays(7);
        List<Map<String, Object>> rows = rankingMapper.selectStoryDiscoveryLeaderboard(
                startAt, endAt, safeLimit);
        List<StoryDiscoveryRankingVO> result = new java.util.ArrayList<>(rows.size());
        int previousCount = Integer.MIN_VALUE;
        int currentRank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int discoveryCount = toInt(rowValue(row, "discoveryCount"), 0);
            if (discoveryCount != previousCount) currentRank = i + 1;
            StoryDiscoveryRankingVO vo = new StoryDiscoveryRankingVO();
            vo.setRank(currentRank);
            vo.setPlayerId(toLong(rowValue(row, "playerId")));
            vo.setPlayerName(toText(rowValue(row, "playerName")));
            vo.setDiscoveryCount(discoveryCount);
            result.add(vo);
            previousCount = discoveryCount;
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StoryDiscoveryPositionVO getStoryDiscoveryPosition(Long playerId) {
        LocalDateTime endAt = LocalDateTime.now();
        Map<String, Object> row = rankingMapper.selectStoryDiscoveryPosition(
                playerId, endAt.minusDays(7), endAt);
        int rank = toInt(rowValue(row, "playerRank"), 0);
        int discoveryCount = toInt(rowValue(row, "discoveryCount"), 0);
        Integer nextRankDiscoveryCount = toNullableInt(rowValue(row, "nextRankDiscoveryCount"));
        StoryDiscoveryPositionVO result = new StoryDiscoveryPositionVO();
        result.setRank(rank);
        result.setParticipantCount(toInt(rowValue(row, "participantCount"), 0));
        result.setDiscoveryCount(discoveryCount);
        result.setNextRankDiscoveryCount(nextRankDiscoveryCount);
        result.setDiscoveriesToNextRank(nextRankDiscoveryCount == null
                ? 0 : Math.max(0, nextRankDiscoveryCount - discoveryCount));
        result.setRanked(rank > 0);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PkPerformanceRankingVO> getPkPerformanceRankings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LocalDateTime endAt = LocalDateTime.now();
        List<Map<String, Object>> rows = rankingMapper.selectPkPerformanceLeaderboard(
                endAt.minusDays(7), endAt, safeLimit);
        List<PkPerformanceRankingVO> result = new java.util.ArrayList<>(rows.size());
        int previousDelta = Integer.MIN_VALUE;
        int currentRank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int ratingDelta = toInt(rowValue(row, "ratingDelta"), 0);
            if (ratingDelta != previousDelta) currentRank = i + 1;
            PkPerformanceRankingVO vo = new PkPerformanceRankingVO();
            vo.setRank(currentRank);
            vo.setPlayerId(toLong(rowValue(row, "playerId")));
            vo.setPlayerName(toText(rowValue(row, "playerName")));
            vo.setRatingDelta(ratingDelta);
            vo.setMatchCount(toInt(rowValue(row, "matchCount"), 0));
            vo.setWinCount(toInt(rowValue(row, "winCount"), 0));
            result.add(vo);
            previousDelta = ratingDelta;
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PkPerformancePositionVO getPkPerformancePosition(Long playerId) {
        LocalDateTime endAt = LocalDateTime.now();
        Map<String, Object> row = rankingMapper.selectPkPerformancePosition(
                playerId, endAt.minusDays(7), endAt);
        int rank = toInt(rowValue(row, "playerRank"), 0);
        int ratingDelta = toInt(rowValue(row, "ratingDelta"), 0);
        Integer nextRankRatingDelta = toNullableInt(rowValue(row, "nextRankRatingDelta"));
        PkPerformancePositionVO result = new PkPerformancePositionVO();
        result.setParticipated(rank > 0);
        result.setRank(rank);
        result.setParticipantCount(toInt(rowValue(row, "participantCount"), 0));
        result.setRatingDelta(ratingDelta);
        result.setMatchCount(toInt(rowValue(row, "matchCount"), 0));
        result.setWinCount(toInt(rowValue(row, "winCount"), 0));
        result.setNextRankRatingDelta(nextRankRatingDelta);
        result.setPointsToNextRank(nextRankRatingDelta == null
                ? 0 : Math.max(0, nextRankRatingDelta - ratingDelta));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExploreActionRankingVO> getExploreActionRankings(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LocalDateTime endAt = LocalDateTime.now();
        List<Map<String, Object>> rows = rankingMapper.selectExploreActionLeaderboard(
                endAt.minusDays(7), endAt, safeLimit);
        List<ExploreActionRankingVO> result = new java.util.ArrayList<>(rows.size());
        int previousCount = Integer.MIN_VALUE;
        int currentRank = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            int actionCount = toInt(rowValue(row, "actionCount"), 0);
            if (actionCount != previousCount) currentRank = i + 1;
            ExploreActionRankingVO vo = new ExploreActionRankingVO();
            vo.setRank(currentRank);
            vo.setPlayerId(toLong(rowValue(row, "playerId")));
            vo.setPlayerName(toText(rowValue(row, "playerName")));
            vo.setActionCount(actionCount);
            result.add(vo);
            previousCount = actionCount;
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ExploreActionPositionVO getExploreActionPosition(Long playerId) {
        LocalDateTime endAt = LocalDateTime.now();
        Map<String, Object> row = rankingMapper.selectExploreActionPosition(
                playerId, endAt.minusDays(7), endAt);
        int rank = toInt(rowValue(row, "playerRank"), 0);
        int actionCount = toInt(rowValue(row, "actionCount"), 0);
        Integer nextRankActionCount = toNullableInt(rowValue(row, "nextRankActionCount"));
        ExploreActionPositionVO result = new ExploreActionPositionVO();
        result.setParticipated(rank > 0);
        result.setRank(rank);
        result.setParticipantCount(toInt(rowValue(row, "participantCount"), 0));
        result.setActionCount(actionCount);
        result.setNextRankActionCount(nextRankActionCount);
        result.setActionsToNextRank(nextRankActionCount == null
                ? 0 : Math.max(0, nextRankActionCount - actionCount));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RankingVO> getNearbyRankings(Long playerId, int radius) {
        PlayerRankVO ownRank = getPlayerRank(playerId);
        if (ownRank == null || !ownRank.isRanked()) return List.of();

        int safeRadius = Math.max(1, Math.min(radius, 5));
        int firstRank = Math.max(1, ownRank.getRank() - safeRadius);
        int lastRank = Math.min(ownRank.getTotalPlayers(), ownRank.getRank() + safeRadius);
        int offset = firstRank - 1;
        int limit = lastRank - firstRank + 1;
        if (limit <= 0) return List.of();
        List<Map<String, Object>> rows = rankingMapper.selectRankedLeaderboardWindow(limit, offset);
        List<RankingVO> result = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            RankingVO vo = new RankingVO();
            vo.setRank(offset + i + 1);
            vo.setPlayerId(toLong(row.get("playerId")));
            vo.setPlayerName(toText(row.get("playerName")));
            vo.setLevel(toInt(row.get("level"), 1));
            vo.setRanked(true);
            vo.setRating(toInt(row.get("rating"), 1000));
            vo.setWins(toInt(row.get("wins"), 0));
            vo.setLosses(toInt(row.get("losses"), 0));
            vo.setHighestRating(toInt(row.get("highestRating"), 1000));
            result.add(vo);
        }
        return result;
    }

    @Override
    public PlayerRankVO getPlayerRank(Long playerId) {
        if (playerId == null || playerMapper.selectById(playerId) == null) return null;

        Ranking ranking = rankingMapper.selectByPlayerId(playerId);
        boolean ranked = ranking != null;
        int rating = ranked ? toInt(ranking.getRating(), 1000) : 1000;
        int wins = ranked ? toInt(ranking.getWins(), 0) : 0;
        int losses = ranked ? toInt(ranking.getLosses(), 0) : 0;
        int highestRating = ranked ? toInt(ranking.getHighestRating(), 1000) : 1000;
        int rank = ranked
                ? Math.toIntExact(rankingMapper.countRankedPlayersAhead(rating, wins, losses, playerId) + 1)
                : 0;

        PlayerRankVO vo = new PlayerRankVO();
        vo.setRank(rank);
        vo.setTotalPlayers(Math.toIntExact(rankingMapper.countRankedPlayers()));
        vo.setRanked(ranked);
        vo.setRating(rating);
        vo.setWins(wins);
        vo.setLosses(losses);
        vo.setHighestRating(highestRating);
        return vo;
    }

    private int toInt(Integer value, int defaultVal) {
        return value == null ? defaultVal : value;
    }

    private int toInt(Object value, int defaultVal) {
        return value instanceof Number number ? number.intValue() : defaultVal;
    }

    private Integer toNullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object rowValue(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) return row.get(columnName);
        return row.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(columnName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }
}
