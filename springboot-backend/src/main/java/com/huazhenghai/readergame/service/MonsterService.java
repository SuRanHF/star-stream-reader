package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.vo.MonsterVO;

import java.util.List;

public interface MonsterService {

    List<MonsterVO> getAllMonsters();

    List<MonsterVO> getMonstersByLocation(String locationKey);

    Monster getMonsterEntity(String monsterKey);
}
