package com.huazhenghai.readergame.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hard gate: player-bound gameplay APIs require a selected constellation.
 */
@Aspect
@Component
@Order(20)
public class ConstellationRequiredAspect {

    private static final List<String> PLAYER_ID_KEYS = List.of(
            "playerId",
            "reviverId",
            "attackerId",
            "buyerPlayerId",
            "sellerPlayerId",
            "leaderPlayerId"
    );

    private static final List<String> PLAYER_ID_GETTERS = List.of(
            "getPlayerId",
            "getReviverId",
            "getAttackerId",
            "getBuyerPlayerId",
            "getSellerPlayerId",
            "getLeaderPlayerId"
    );

    private static final Set<String> VALID_CONSTELLATIONS = Set.of(
            "demon_judge_of_fire", "master_of_steel",
            "prisoner_of_golden_headband", "abyssal_black_flame_dragon",
            "queen_of_darkest_spring", "father_of_rich_night",
            "scribe_of_heaven", "morning_star"
    );

    private final PlayerMapper playerMapper;
    private final ObjectMapper objectMapper;

    public ConstellationRequiredAspect(PlayerMapper playerMapper, ObjectMapper objectMapper) {
        this.playerMapper = playerMapper;
        this.objectMapper = objectMapper;
    }

    @Around("within(com.huazhenghai.readergame.controller..*)")
    public Object requireConstellation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> controllerClass = method.getDeclaringClass();

        if (isWhitelisted(controllerClass.getSimpleName(), method.getName())) {
            return joinPoint.proceed();
        }

        Long playerId = extractPlayerId(method, joinPoint.getArgs());
        if (playerId == null) {
            return joinPoint.proceed();
        }

        LoginUser loginUser = LoginUserContext.get();
        Player player = playerMapper.selectById(playerId);
        if (player == null || loginUser == null || !loginUser.getUserId().equals(player.getUserId())) {
            return joinPoint.proceed();
        }

        Map<String, Object> stats = parseJsonMap(player.getStatsJson());
        Object constellation = stats.get("constellation");
        if (constellation == null || !VALID_CONSTELLATIONS.contains(String.valueOf(constellation))) {
            throw new BusinessException(ErrorCode.CONSTELLATION_REQUIRED, "必须先选择背后星，才能继续使用该功能");
        }

        return joinPoint.proceed();
    }

    private boolean isWhitelisted(String controllerName, String methodName) {
        if ("AuthController".equals(controllerName)
                || "AdminController".equals(controllerName)
                || "SystemController".equals(controllerName)
                || "WorldlineController".equals(controllerName)
                || "OnlineController".equals(controllerName)
                || "FeedbackController".equals(controllerName)
                || "SchedulerController".equals(controllerName)) {
            return true;
        }
        if (!"PlayerController".equals(controllerName)) return false;
        return "createPlayer".equals(methodName)
                || "getMyPlayer".equals(methodName)
                || "getPlayerById".equals(methodName)
                || "getConstellations".equals(methodName)
                || "selectConstellation".equals(methodName)
                || "changeConstellation".equals(methodName);
    }

    private Long extractPlayerId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Long fromAnnotatedParam = extractFromAnnotatedParam(parameters, i, arg);
            if (fromAnnotatedParam != null) return fromAnnotatedParam;

            Long fromArg = extractFromArg(arg);
            if (fromArg != null) return fromArg;
        }
        return null;
    }

    private Long extractFromAnnotatedParam(Parameter[] parameters, int index, Object arg) {
        if (arg == null || index >= parameters.length) return null;
        if (!(arg instanceof Number)) return null;

        Parameter parameter = parameters[index];
        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        String name = null;
        if (pathVariable != null) name = annotationName(pathVariable.name(), pathVariable.value(), parameter.getName());
        if (requestParam != null) name = annotationName(requestParam.name(), requestParam.value(), parameter.getName());
        if (name == null) name = parameter.getName();

        return PLAYER_ID_KEYS.contains(name) ? ((Number) arg).longValue() : null;
    }

    private String annotationName(String name, String value, String fallback) {
        if (name != null && !name.isBlank()) return name;
        if (value != null && !value.isBlank()) return value;
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Long extractFromArg(Object arg) {
        if (arg == null) return null;
        if (arg instanceof Map<?, ?> map) {
            for (String key : PLAYER_ID_KEYS) {
                Object value = ((Map<String, Object>) map).get(key);
                Long id = toLong(value);
                if (id != null) return id;
            }
            return null;
        }
        for (String getter : PLAYER_ID_GETTERS) {
            try {
                Method method = arg.getClass().getMethod(getter);
                Long id = toLong(method.invoke(arg));
                if (id != null) return id;
            } catch (NoSuchMethodException ignored) {
                // Try the next common player-id getter.
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
