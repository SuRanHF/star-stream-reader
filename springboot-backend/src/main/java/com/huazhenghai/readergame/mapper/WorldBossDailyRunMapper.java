package com.huazhenghai.readergame.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface WorldBossDailyRunMapper {

    /** The run_date primary key atomically allows only one claim for each game date. */
    @Insert("INSERT IGNORE INTO world_boss_daily_runs (run_date) VALUES (#{runDate})")
    int claimDailyRun(@Param("runDate") LocalDate runDate);
}
