package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.Monster;
import com.huazhenghai.readergame.mapper.MonsterMapper;
import com.huazhenghai.readergame.service.MonsterService;
import com.huazhenghai.readergame.vo.MonsterVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonsterServiceImpl implements MonsterService {

    private final MonsterMapper monsterMapper;
    private final ObjectMapper objectMapper;

    public MonsterServiceImpl(MonsterMapper monsterMapper, ObjectMapper objectMapper) {
        this.monsterMapper = monsterMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MonsterVO> getAllMonsters() {
        QueryWrapper<Monster> qw = new QueryWrapper<>();
        qw.eq("enabled", 1);
        List<Monster> monsters = monsterMapper.selectList(qw);
        List<MonsterVO> result = new ArrayList<>();
        for (Monster m : monsters) {
            result.add(toVO(m));
        }
        return result;
    }

    @Override
    public List<MonsterVO> getMonstersByLocation(String locationKey) {
        QueryWrapper<Monster> qw = new QueryWrapper<>();
        qw.eq("enabled", 1).eq("location_key", locationKey);
        List<Monster> monsters = monsterMapper.selectList(qw);
        List<MonsterVO> result = new ArrayList<>();
        for (Monster m : monsters) {
            result.add(toVO(m));
        }
        return result;
    }

    @Override
    public Monster getMonsterEntity(String monsterKey) {
        QueryWrapper<Monster> qw = new QueryWrapper<>();
        qw.eq("monster_key", monsterKey);
        return monsterMapper.selectOne(qw);
    }

    private MonsterVO toVO(Monster m) {
        MonsterVO vo = new MonsterVO();
        vo.setMonsterKey(m.getMonsterKey());
        vo.setName(m.getName());
        vo.setType(m.getType());
        vo.setRarity(m.getRarity());
        vo.setLocationKey(m.getLocationKey());
        vo.setDescription(m.getDescription());
        vo.setStats(parseJson(m.getStatsJson()));
        vo.setSkills(parseJson(m.getSkillsJson()));
        vo.setRewards(parseJson(m.getRewardsJson()));
        vo.setDrops(parseJson(m.getDropsJson()));
        vo.setNarrativeTags(parseStringList(m.getNarrativeTagsJson()));
        vo.setEnabled(m.getEnabled() != null && m.getEnabled() == 1);
        return vo;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
