package com.huazhenghai.readergame.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PartyVO {

    private Long id;
    @JsonProperty("party_no")
    private String partyNo;
    @JsonProperty("leader_id")
    private Long leaderPlayerId;
    @JsonProperty("leader_name")
    private String leaderName;
    private String name;
    private String description;
    private String status;
    @JsonProperty("max_members")
    private int maxMembers;
    @JsonProperty("target_type")
    private String targetType;
    @JsonProperty("boss_key")
    private String targetKey;
    private int memberCount;
    @JsonProperty("online_count")
    private int onlineMemberCount;
    private List<PartyMemberVO> members;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("disbanded_at")
    private String disbandedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }
    public Long getLeaderPlayerId() { return leaderPlayerId; }
    public void setLeaderPlayerId(Long leaderPlayerId) { this.leaderPlayerId = leaderPlayerId; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public int getOnlineMemberCount() { return onlineMemberCount; }
    public void setOnlineMemberCount(int onlineMemberCount) { this.onlineMemberCount = onlineMemberCount; }
    public List<PartyMemberVO> getMembers() { return members; }
    public void setMembers(List<PartyMemberVO> members) { this.members = members; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getDisbandedAt() { return disbandedAt; }
    public void setDisbandedAt(String disbandedAt) { this.disbandedAt = disbandedAt; }
}
