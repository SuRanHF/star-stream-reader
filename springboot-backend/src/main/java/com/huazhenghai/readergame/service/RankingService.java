package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PlayerRankVO;
import com.huazhenghai.readergame.vo.RankingVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryPositionVO;
import com.huazhenghai.readergame.vo.StoryDiscoveryRankingVO;
import com.huazhenghai.readergame.vo.PkPerformancePositionVO;
import com.huazhenghai.readergame.vo.PkPerformanceRankingVO;
import com.huazhenghai.readergame.vo.ExploreActionPositionVO;
import com.huazhenghai.readergame.vo.ExploreActionRankingVO;

import java.util.List;

public interface RankingService {

    List<RankingVO> getRankings(int limit);

    List<StoryDiscoveryRankingVO> getStoryDiscoveryRankings(int limit);

    StoryDiscoveryPositionVO getStoryDiscoveryPosition(Long playerId);

    List<PkPerformanceRankingVO> getPkPerformanceRankings(int limit);

    PkPerformancePositionVO getPkPerformancePosition(Long playerId);

    List<ExploreActionRankingVO> getExploreActionRankings(int limit);

    ExploreActionPositionVO getExploreActionPosition(Long playerId);

    List<RankingVO> getNearbyRankings(Long playerId, int radius);

    PlayerRankVO getPlayerRank(Long playerId);
}
