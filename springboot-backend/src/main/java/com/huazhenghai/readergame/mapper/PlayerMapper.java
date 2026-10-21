package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.Player;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
