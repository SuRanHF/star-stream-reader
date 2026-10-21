package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.WorldState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorldStateMapper extends BaseMapper<WorldState> {

    /** Serialize mutations to a shared world-state row within the caller's transaction. */
    @Select("SELECT * FROM world_states WHERE state_key = #{stateKey} FOR UPDATE")
    WorldState selectByStateKeyForUpdate(@Param("stateKey") String stateKey);
}
