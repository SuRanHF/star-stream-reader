package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.*;

import java.util.List;
import java.util.Map;

public interface TradeService {

    TradeListingVO createItemListing(Long sellerPlayerId, String itemKey, int quantity, int unitPrice);

    TradeListingVO createEquipmentListing(Long sellerPlayerId, String equipmentKey, int unitPrice);

    Map<String, Object> getMarketListings(String type, String keyword, int page, int size);

    List<TradeListingVO> getMyListings(Long playerId, String status);

    TradeBuyResultVO buyListing(Long buyerPlayerId, String listingNo);

    TradeListingVO cancelListing(Long sellerPlayerId, String listingNo);

    List<TradeRecordVO> getTradeRecords(Long playerId, int page, int size);

    TradeSummaryVO getTradeSummary(Long playerId);
}
