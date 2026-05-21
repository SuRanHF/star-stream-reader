package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.service.RankingService;
import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankingServiceImpl implements RankingService {

    private final RankingMapper rankingMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public RankingServiceImpl(RankingMapper rankingMapper,
                               PlayerMapper playerMapper,
                               ObjectMapper objectMapper) {
        this.rankingMapper = rankingMapper;
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<RankingVO> getRankings(int limit) {
        QueryWrapper<Ranking> qw = new QueryWrapper<>();
        qw.orderByDesc("rating").last("LIMIT " + Math.min(limit, 100));
        List<Ranking> rankings = rankingMapper.selectList(qw);

        List<RankingVO> result = new ArrayList<>();
        int rank = 1;
        for (Ranking r : rankings) {
            Player p = playerMapper.selectById(r.getPlayerId());
            if (p == null) continue;

            Map<String, Object> stats = parseJsonMap(p.getStatsJson());

            RankingVO vo = new RankingVO();
            vo.setRank(rank++);
            vo.setPlayerId(r.getPlayerId());
            vo.setPlayerName(p.getPlayerName());
            vo.setLevel(toInt(stats.get("level"), 1));
            vo.setRating(r.getRating());
            vo.setWins(r.getWins());
            vo.setLosses(r.getLosses());
            vo.setHighestRating(r.getHighestRating());
            result.add(vo);
        }
        return result;
    }

    @Override
    public PlayerRankVO getPlayerRank(Long playerId) {
        // 获取所有排行按评分降序
        QueryWrapper<Ranking> qw = new QueryWrapper<>();
        qw.orderByDesc("rating");
        List<Ranking> allRankings = rankingMapper.selectList(qw);

        int rank = 0;
        Ranking found = null;
        for (int i = 0; i < allRankings.size(); i++) {
            if (allRankings.get(i).getPlayerId().equals(playerId)) {
                rank = i + 1;
                found = allRankings.get(i);
                break;
            }
        }

        if (found == null) return null;

        PlayerRankVO vo = new PlayerRankVO();
        vo.setRank(rank);
        vo.setTotalPlayers(allRankings.size());
        vo.setRating(found.getRating());
        vo.setWins(found.getWins());
        vo.setLosses(found.getLosses());
        vo.setHighestRating(found.getHighestRating());
        return vo;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
