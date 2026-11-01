package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.Player;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlayerMapper extends BaseMapper<Player> {

    /**
     * 在当前事务内锁定玩家记录，避免并发结算基于同一份旧进度重复发放奖励。
     */
    @Select("SELECT * FROM players WHERE id = #{playerId} FOR UPDATE")
    Player selectByIdForUpdate(@Param("playerId") Long playerId);

    /** Read only the stats column so public aggregation never loads player story flags or choices. */
    @Select("SELECT stats_json FROM players WHERE stats_json IS NOT NULL")
    List<String> selectStatsJsonForWorldAggregation();

    /** The same role=player population as the flowchart, with minimal route progress columns. */
    @Select("SELECT p.decision_history_json, p.stage_progress_json FROM players p "
            + "JOIN users u ON u.id = p.user_id WHERE u.role = 'player'")
    @Results({@Result(column = "decision_history_json", property = "decisionHistoryJson"),
            @Result(column = "stage_progress_json", property = "stageProgressJson")})
    List<PlayerRouteSnapshot> selectPlayerRoutesForStoryTree();

    class PlayerRouteSnapshot {
        private String decisionHistoryJson;
        private String stageProgressJson;

        public String getDecisionHistoryJson() { return decisionHistoryJson; }
        public void setDecisionHistoryJson(String value) { decisionHistoryJson = value; }
        public String getStageProgressJson() { return stageProgressJson; }
        public void setStageProgressJson(String value) { stageProgressJson = value; }
    }
}
