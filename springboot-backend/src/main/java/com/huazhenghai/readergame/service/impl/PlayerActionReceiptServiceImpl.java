package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.PlayerActionReceipt;
import com.huazhenghai.readergame.mapper.PlayerActionReceiptMapper;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PlayerActionReceiptServiceImpl implements PlayerActionReceiptService {

    private final PlayerActionReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;

    public PlayerActionReceiptServiceImpl(PlayerActionReceiptMapper receiptMapper,
                                          ObjectMapper objectMapper) {
        this.receiptMapper = receiptMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T findReplay(Long playerId, String actionType, String idempotencyKey,
                            Object requestPayload, Class<T> responseType) {
        validate(playerId, actionType, idempotencyKey, responseType);
        QueryWrapper<PlayerActionReceipt> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
                .eq("action_type", actionType.trim())
                .eq("idempotency_key", idempotencyKey.trim());
        PlayerActionReceipt receipt = receiptMapper.selectOne(query);
        if (receipt == null) return null;

        String requestedHash = fingerprint(requestPayload);
        if (!MessageDigest.isEqual(receipt.getRequestHash().getBytes(StandardCharsets.US_ASCII),
                requestedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE,
                    "该请求键已用于不同的操作内容，请刷新页面后重试");
        }
        try {
            return objectMapper.readValue(receipt.getResponseJson(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已保存的操作回执无法读取", exception);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long playerId, String actionType, String idempotencyKey,
                       Object requestPayload, Object responsePayload) {
        validate(playerId, actionType, idempotencyKey, responsePayload == null ? Object.class : responsePayload.getClass());
        if (responsePayload == null) throw new IllegalArgumentException("操作回执不能为空");

        PlayerActionReceipt receipt = new PlayerActionReceipt();
        receipt.setPlayerId(playerId);
        receipt.setActionType(actionType.trim());
        receipt.setIdempotencyKey(idempotencyKey.trim());
        receipt.setRequestHash(fingerprint(requestPayload));
        try {
            receipt.setResponseJson(objectMapper.writeValueAsString(responsePayload));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("操作回执无法序列化", exception);
        }
        if (receiptMapper.insert(receipt) <= 0) {
            throw new IllegalStateException("操作回执写入失败");
        }
    }

    private void validate(Long playerId, String actionType, String idempotencyKey,
                          Class<?> responseType) {
        if (playerId == null || playerId <= 0) throw new IllegalArgumentException("操作回执玩家无效");
        if (actionType == null || actionType.isBlank() || actionType.trim().length() > 40)
            throw new IllegalArgumentException("操作类型不能为空且不能超过 40 个字符");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 64)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestKey 必须为 1 到 64 个字符");
        if (responseType == null) throw new IllegalArgumentException("回执类型不能为空");
    }

    private String fingerprint(Object requestPayload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(requestPayload));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("请求内容无法生成指纹", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
