package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.PlayerQuest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlayerQuestMapper extends BaseMapper<PlayerQuest> {

    @Select("SELECT * FROM player_quests WHERE id = #{playerQuestId} FOR UPDATE")
    PlayerQuest selectByIdForUpdate(@Param("playerQuestId") Long playerQuestId);

    /** Lock active progress rows so concurrent exploration/combat events cannot overwrite each other. */
    @Select("SELECT * FROM player_quests WHERE player_id = #{playerId} AND status = 'active' FOR UPDATE")
    List<PlayerQuest> selectActiveByPlayerForUpdate(@Param("playerId") Long playerId);
}
