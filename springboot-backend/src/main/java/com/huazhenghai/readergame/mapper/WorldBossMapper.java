package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.WorldBoss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorldBossMapper extends BaseMapper<WorldBoss> {

    @Select("SELECT * FROM world_bosses WHERE boss_no = #{bossNo} AND status = 'active' FOR UPDATE")
    WorldBoss selectActiveByBossNoForUpdate(@Param("bossNo") String bossNo);

    @Select("SELECT * FROM world_bosses WHERE boss_no = #{bossNo} FOR UPDATE")
    WorldBoss selectByBossNoForUpdate(@Param("bossNo") String bossNo);
}
