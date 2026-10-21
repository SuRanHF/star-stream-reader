package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.BattleLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BattleLogMapper extends BaseMapper<BattleLog> {

    @Select("SELECT * FROM battle_logs WHERE player_id = #{playerId} AND request_key = #{requestKey} LIMIT 1")
    BattleLog selectByPlayerAndRequestKey(@Param("playerId") Long playerId,
                                          @Param("requestKey") String requestKey);
}
