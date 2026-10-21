package com.huazhenghai.readergame.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingMapperMySqlCastTest {

    @Test
    void avatarRankQueriesUseCastTypesAcceptedByMySql() throws NoSuchMethodException {
        List<String> sql = List.of(
                selectSql("selectAvatarRankLeaderboard", int.class),
                selectSql("countAvatarRankPlayersAhead", int.class, int.class, int.class, Long.class));

        assertTrue(sql.stream().allMatch(query -> query.contains("AS SIGNED")));
        assertFalse(sql.stream().anyMatch(query -> query.contains("AS BIGINT")));
    }

    private String selectSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = RankingMapper.class.getMethod(methodName, parameterTypes);
        return String.join(" ", Arrays.stream(method.getAnnotation(Select.class).value()).toList());
    }
}
