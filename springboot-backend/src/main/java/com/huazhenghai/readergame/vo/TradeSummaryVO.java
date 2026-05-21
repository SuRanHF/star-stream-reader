package com.huazhenghai.readergame.vo;

public class TradeSummaryVO {

    private int activeListingCount;
    private int soldCount;
    private int boughtCount;
    private int recentTradeCount;

    public int getActiveListingCount() { return activeListingCount; }
    public void setActiveListingCount(int activeListingCount) { this.activeListingCount = activeListingCount; }
    public int getSoldCount() { return soldCount; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }
    public int getBoughtCount() { return boughtCount; }
    public void setBoughtCount(int boughtCount) { this.boughtCount = boughtCount; }
    public int getRecentTradeCount() { return recentTradeCount; }
    public void setRecentTradeCount(int recentTradeCount) { this.recentTradeCount = recentTradeCount; }
}
