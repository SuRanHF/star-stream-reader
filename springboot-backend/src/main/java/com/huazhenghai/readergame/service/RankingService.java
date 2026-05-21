package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;

import java.util.List;

public interface RankingService {

    List<RankingVO> getRankings(int limit);

    PlayerRankVO getPlayerRank(Long playerId);
}
