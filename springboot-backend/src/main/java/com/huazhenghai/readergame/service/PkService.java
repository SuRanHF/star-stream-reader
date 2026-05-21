package com.huazhenghai.readergame.service;

import com.huazhenghai.readergame.vo.PkChallengeVO;
import com.huazhenghai.readergame.vo.PkRecordVO;
import com.huazhenghai.readergame.vo.PkResultVO;

import java.util.List;
import java.util.Map;

public interface PkService {

    List<Map<String, Object>> getOpponents(Long playerId, Long userId);

    Map<String, Object> createChallenge(Long attackerId, Long defenderId, String mode, Long userId);

    Map<String, Object> resolveChallenge(Long challengeId, boolean accept, Long playerId, Long userId);

    List<PkRecordVO> getPKRecords(Long playerId, Long userId);
}
