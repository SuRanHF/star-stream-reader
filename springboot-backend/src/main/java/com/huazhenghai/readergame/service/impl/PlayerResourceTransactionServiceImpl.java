package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.PlayerResourceTransaction;
import com.huazhenghai.readergame.mapper.PlayerResourceTransactionMapper;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PlayerResourceTransactionServiceImpl implements PlayerResourceTransactionService {

    private final PlayerResourceTransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;

    public PlayerResourceTransactionServiceImpl(PlayerResourceTransactionMapper transactionMapper,
                                                ObjectMapper objectMapper) {
        this.transactionMapper = transactionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public boolean record(Change change) {
        validate(change);

        QueryWrapper<PlayerResourceTransaction> query = new QueryWrapper<>();
        query.eq("player_id", change.playerId())
                .eq("idempotency_key", change.idempotencyKey());
        PlayerResourceTransaction existing = transactionMapper.selectOne(query);
        if (existing != null) {
            if (matches(existing, change)) return false;
            throw new IllegalStateException("资源流水幂等键已用于不同变更: " + change.idempotencyKey());
        }

        PlayerResourceTransaction transaction = new PlayerResourceTransaction();
        transaction.setPlayerId(change.playerId());
        transaction.setResourceType(change.resourceType().trim());
        transaction.setResourceKey(change.resourceKey().trim());
        transaction.setAmount(change.amount());
        transaction.setBalanceBefore(change.balanceBefore());
        transaction.setBalanceAfter(change.balanceAfter());
        transaction.setSourceType(change.sourceType().trim());
        transaction.setSourceId(blankToNull(change.sourceId()));
        transaction.setTransactionGroupId(blankToNull(change.transactionGroupId()));
        transaction.setIdempotencyKey(change.idempotencyKey().trim());
        transaction.setMetadataJson(serializeMetadata(change));
        if (transactionMapper.insert(transaction) <= 0) {
            throw new IllegalStateException("资源流水写入失败");
        }
        return true;
    }

    private void validate(Change change) {
        if (change == null || change.playerId() == null || change.playerId() <= 0) {
            throw new IllegalArgumentException("资源流水必须指定有效玩家");
        }
        requireText(change.resourceType(), 32, "资源类型");
        requireText(change.resourceKey(), 100, "资源键");
        requireText(change.sourceType(), 64, "来源动作");
        requireText(change.idempotencyKey(), 160, "幂等键");
        requireOptionalText(change.sourceId(), 128, "来源记录键");
        requireOptionalText(change.transactionGroupId(), 64, "流水组键");
        if (change.amount() == 0) throw new IllegalArgumentException("资源流水变化量不能为 0");
        long actualDelta;
        try {
            actualDelta = Math.subtractExact(change.balanceAfter(), change.balanceBefore());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("资源余额差超出可记录范围", exception);
        }
        if (actualDelta != change.amount()) {
            throw new IllegalArgumentException("资源变化量与前后余额不一致");
        }
    }

    private boolean matches(PlayerResourceTransaction existing, Change change) {
        return Objects.equals(existing.getResourceType(), change.resourceType().trim())
                && Objects.equals(existing.getResourceKey(), change.resourceKey().trim())
                && Objects.equals(existing.getAmount(), change.amount())
                && Objects.equals(existing.getBalanceBefore(), change.balanceBefore())
                && Objects.equals(existing.getBalanceAfter(), change.balanceAfter())
                && Objects.equals(existing.getSourceType(), change.sourceType().trim())
                && Objects.equals(existing.getSourceId(), blankToNull(change.sourceId()))
                && Objects.equals(existing.getTransactionGroupId(), blankToNull(change.transactionGroupId()));
    }

    private String serializeMetadata(Change change) {
        if (change.metadata() == null || change.metadata().isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(change.metadata());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("资源流水附加信息无法序列化", exception);
        }
    }

    private void requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(label + "不能为空且不能超过 " + maxLength + " 个字符");
        }
    }

    private void requireOptionalText(String value, int maxLength, String label) {
        if (value != null && value.trim().length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
