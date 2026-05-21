package com.huazhenghai.readergame.vo;

public class PartySummaryVO {

    private boolean inParty;
    private String partyNo;
    private String partyName;
    private String role;
    private int memberCount;
    private int maxMembers;
    private int onlineMemberCount;

    public boolean isInParty() { return inParty; }
    public void setInParty(boolean inParty) { this.inParty = inParty; }
    public String getPartyNo() { return partyNo; }
    public void setPartyNo(String partyNo) { this.partyNo = partyNo; }
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public int getOnlineMemberCount() { return onlineMemberCount; }
    public void setOnlineMemberCount(int onlineMemberCount) { this.onlineMemberCount = onlineMemberCount; }
}
