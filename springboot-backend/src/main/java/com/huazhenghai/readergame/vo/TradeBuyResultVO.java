package com.huazhenghai.readergame.vo;

import java.util.Map;

public class TradeBuyResultVO {

    private String listingNo;
    private String listingType;
    private Long buyerPlayerId;
    private Long sellerPlayerId;
    private int totalPrice;
    private Map<String, Object> transferredItem;
    private int newBuyerCoins;

    public String getListingNo() { return listingNo; }
    public void setListingNo(String listingNo) { this.listingNo = listingNo; }
    public String getListingType() { return listingType; }
    public void setListingType(String listingType) { this.listingType = listingType; }
    public Long getBuyerPlayerId() { return buyerPlayerId; }
    public void setBuyerPlayerId(Long buyerPlayerId) { this.buyerPlayerId = buyerPlayerId; }
    public Long getSellerPlayerId() { return sellerPlayerId; }
    public void setSellerPlayerId(Long sellerPlayerId) { this.sellerPlayerId = sellerPlayerId; }
    public int getTotalPrice() { return totalPrice; }
    public void setTotalPrice(int totalPrice) { this.totalPrice = totalPrice; }
    public Map<String, Object> getTransferredItem() { return transferredItem; }
    public void setTransferredItem(Map<String, Object> transferredItem) { this.transferredItem = transferredItem; }
    public int getNewBuyerCoins() { return newBuyerCoins; }
    public void setNewBuyerCoins(int newBuyerCoins) { this.newBuyerCoins = newBuyerCoins; }
}
