package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.Party;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PartyMapper extends BaseMapper<Party> {

    /**
     * Serialize concurrent joins for the same party so the capacity check and insert are atomic.
     */
    @Select("SELECT * FROM parties WHERE party_no = #{partyNo} AND status = 'active' FOR UPDATE")
    Party selectActiveForUpdate(@Param("partyNo") String partyNo);
}
