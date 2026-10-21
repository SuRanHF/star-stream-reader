package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.PlayerFaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlayerFactionMapper extends BaseMapper<PlayerFaction> {

    @Select("SELECT * FROM player_factions WHERE player_id = #{playerId} AND status = 'active' FOR UPDATE")
    PlayerFaction selectActiveByPlayerIdForUpdate(@Param("playerId") Long playerId);
}
