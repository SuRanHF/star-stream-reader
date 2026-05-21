package com.huazhenghai.readergame.vo;

public class FriendVO {

    private Long id;
    private Long playerId;
    private String playerName;
    private int level;
    private String avatarRank;
    private boolean isOnline;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getAvatarRank() { return avatarRank; }
    public void setAvatarRank(String avatarRank) { this.avatarRank = avatarRank; }
    public boolean getIsOnline() { return isOnline; }
    public void setIsOnline(boolean isOnline) { this.isOnline = isOnline; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
