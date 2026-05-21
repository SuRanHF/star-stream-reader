package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.GameBootstrapVO;

/**
 * 游戏启动引导服务接口.
 */
public interface GameBootstrapService {

    /**
     * 获取游戏启动所需的所有数据.
     *
     * @param userId 用户 ID
     * @return 启动引导视图 (用户信息 + 玩家角色 + 最近日志)
     */
    GameBootstrapVO bootstrap(Long userId);
}
