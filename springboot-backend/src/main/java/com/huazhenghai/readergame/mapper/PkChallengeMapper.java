package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.PkChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PkChallengeMapper extends BaseMapper<PkChallenge> {

    @Select("SELECT * FROM pk_challenges WHERE id = #{challengeId} FOR UPDATE")
    PkChallenge selectByIdForUpdate(@Param("challengeId") Long challengeId);
}
