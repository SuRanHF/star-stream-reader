package com.huazhenghai.readergame.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps a permanent tombstone so later unlock evaluation cannot restore a sacrificed title. */
public final class TitleSacrificeHistory {
    private static final String KEY = "sacrificedTitleKeys";

    private TitleSacrificeHistory() {}

    public static boolean contains(Player player, String titleKey, ObjectMapper mapper) {
        Object value = progress(player, mapper).get(KEY);
        return value instanceof List<?> list && list.contains(titleKey);
    }

    public static void add(Player player, String titleKey, ObjectMapper mapper) {
        Map<String, Object> progress = progress(player, mapper);
        List<String> keys = new ArrayList<>();
        Object previous = progress.get(KEY);
        if (previous instanceof List<?> list) {
            for (Object value : list) keys.add(String.valueOf(value));
        }
        if (!keys.contains(titleKey)) keys.add(titleKey);
        progress.put(KEY, keys);
        try {
            player.setTitleProgressJson(mapper.writeValueAsString(progress));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "保存称号献祭记录失败");
        }
    }

    private static Map<String, Object> progress(Player player, ObjectMapper mapper) {
        String json = player.getTitleProgressJson();
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "读取称号进度失败");
        }
    }
}
