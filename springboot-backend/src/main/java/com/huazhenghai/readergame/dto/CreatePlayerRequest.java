package com.huazhenghai.readergame.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建玩家请求 DTO.
 */
public class CreatePlayerRequest {

    private String playerName;

    public CreatePlayerRequest() {
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

}
