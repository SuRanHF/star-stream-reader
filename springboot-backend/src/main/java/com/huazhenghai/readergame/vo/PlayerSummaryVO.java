package com.huazhenghai.readergame.vo;

/**
 * 玩家摘要 VO (登录时使用).
 */
public class PlayerSummaryVO {

    private Long id;
    private String playerName;
    private String currentMainChapter;
    private Integer level;
    private String avatarRank;

    public PlayerSummaryVO() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getCurrentMainChapter() { return currentMainChapter; }
    public void setCurrentMainChapter(String currentMainChapter) { this.currentMainChapter = currentMainChapter; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getAvatarRank() { return avatarRank; }
    public void setAvatarRank(String avatarRank) { this.avatarRank = avatarRank; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String playerName;
        private String currentMainChapter;
        private Integer level;
        private String avatarRank;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder currentMainChapter(String currentMainChapter) { this.currentMainChapter = currentMainChapter; return this; }
        public Builder level(Integer level) { this.level = level; return this; }
        public Builder avatarRank(String avatarRank) { this.avatarRank = avatarRank; return this; }

        public PlayerSummaryVO build() {
            PlayerSummaryVO vo = new PlayerSummaryVO();
            vo.id = id;
            vo.playerName = playerName;
            vo.currentMainChapter = currentMainChapter;
            vo.level = level;
            vo.avatarRank = avatarRank;
            return vo;
        }
    }
}
