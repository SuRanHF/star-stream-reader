package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.FriendRequestVO;
import com.huazhenghai.readergame.vo.FriendSummaryVO;
import com.huazhenghai.readergame.vo.FriendVO;

import java.util.List;
import java.util.Map;

public interface FriendService {

    List<Map<String, Object>> searchPlayers(String keyword, Long currentPlayerId);

    Map<String, Object> sendRequest(Long fromPlayerId, Long toPlayerId);

    Map<String, Object> acceptRequest(Long playerId, Long requestId);

    Map<String, Object> rejectRequest(Long playerId, Long requestId);

    Map<String, Object> cancelRequest(Long playerId, Long requestId);

    List<FriendVO> getFriends(Long playerId);

    Map<String, Object> removeFriend(Long playerId, Long friendPlayerId);

    List<FriendRequestVO> getPendingRequests(Long playerId);

    FriendSummaryVO getFriendSummary(Long playerId);
}
