package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {

    @Select("SELECT * FROM friend_requests WHERE id = #{requestId} FOR UPDATE")
    FriendRequest selectByIdForUpdate(@Param("requestId") Long requestId);
}
