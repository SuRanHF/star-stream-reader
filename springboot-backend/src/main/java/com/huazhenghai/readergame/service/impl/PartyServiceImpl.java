package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Party;
import com.huazhenghai.readergame.entity.PartyMember;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.PartyService;
import com.huazhenghai.readergame.vo.PartyMemberVO;
import com.huazhenghai.readergame.vo.PartySummaryVO;
import com.huazhenghai.readergame.vo.PartyVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PartyServiceImpl implements PartyService {

    private static final DateTimeFormatter PT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int MAX_PARTY_SIZE = 3;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PartyMapper partyMapper;
    private final PartyMemberMapper partyMemberMapper;
    private final PlayerMapper playerMapper;
    private final PlayerLogMapper playerLogMapper;
    private final OnlinePlayerService onlinePlayerService;
    private final ChatService chatService;

    public PartyServiceImpl(PartyMapper partyMapper,
                            PartyMemberMapper partyMemberMapper,
                            PlayerMapper playerMapper,
                            PlayerLogMapper playerLogMapper,
                            OnlinePlayerService onlinePlayerService,
                            ChatService chatService) {
        this.partyMapper = partyMapper;
        this.partyMemberMapper = partyMemberMapper;
        this.playerMapper = playerMapper;
        this.playerLogMapper = playerLogMapper;
        this.onlinePlayerService = onlinePlayerService;
        this.chatService = chatService;
    }

    @Override
    @Transactional
    public PartyVO createParty(Long playerId, String name, String description, Long userId) {
        validatePlayerOwnership(playerId, userId);

        assertNotInActiveParty(playerId);

        if (name == null || name.isBlank() || name.length() < 2 || name.length() > 30)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "队伍名称长度2-30");

        String partyNo = generatePartyNo();

        Party party = new Party();
        party.setPartyNo(partyNo);
        party.setLeaderPlayerId(playerId);
        party.setName(name.trim());
        party.setDescription(description != null ? description.trim() : null);
        party.setStatus("active");
        party.setMaxMembers(MAX_PARTY_SIZE);
        party.setCreatedAt(LocalDateTime.now());
        party.setUpdatedAt(LocalDateTime.now());
        partyMapper.insert(party);

        PartyMember member = new PartyMember();
        member.setPartyNo(partyNo);
        member.setPlayerId(playerId);
        member.setRole("leader");
        member.setStatus("active");
        member.setJoinedAt(LocalDateTime.now());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        partyMemberMapper.insert(member);

        Player player = playerMapper.selectById(playerId);
        String playerName = player != null ? player.getPlayerName() : String.valueOf(playerId);
        writeLog(playerId, "party_create", "创建了队伍「" + name + "」");
        tryNotify("玩家 " + playerName + " 创建了队伍「" + name + "」");

        return buildPartyVO(partyNo);
    }

    @Override
    @Transactional
    public PartyVO joinParty(Long playerId, String partyNo, Long userId) {
        validatePlayerOwnership(playerId, userId);

        assertNotInActiveParty(playerId);

        Party party = getActivePartyByNo(partyNo);

        // Check not already in this party
        QueryWrapper<PartyMember> pmQw = new QueryWrapper<>();
        pmQw.eq("party_no", partyNo).eq("player_id", playerId);
        if (partyMemberMapper.selectOne(pmQw) != null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已经在这个队伍中");

        // Check capacity
        long memberCount = countActiveMembers(partyNo);
        if (memberCount >= party.getMaxMembers())
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "队伍已满（最多" + party.getMaxMembers() + "人）");

        PartyMember member = new PartyMember();
        member.setPartyNo(partyNo);
        member.setPlayerId(playerId);
        member.setRole("member");
        member.setStatus("active");
        member.setJoinedAt(LocalDateTime.now());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        partyMemberMapper.insert(member);

        party.setUpdatedAt(LocalDateTime.now());
        partyMapper.updateById(party);

        Player player = playerMapper.selectById(playerId);
        String playerName = player != null ? player.getPlayerName() : String.valueOf(playerId);
        writeLog(playerId, "party_join", "加入了队伍「" + party.getName() + "」");
        writeLog(party.getLeaderPlayerId(), "party_join", "玩家 " + playerName + " 加入了你的队伍");
        tryNotify("玩家 " + playerName + " 加入了队伍「" + party.getName() + "」");

        return buildPartyVO(partyNo);
    }

    @Override
    @Transactional
    public PartyVO leaveParty(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);

        PartyMember myMembership = getActiveMembership(playerId);
        String partyNo = myMembership.getPartyNo();
        Party party = getActivePartyByNo(partyNo);

        // Mark left
        myMembership.setStatus("left");
        myMembership.setLeftAt(LocalDateTime.now());
        myMembership.setUpdatedAt(LocalDateTime.now());
        partyMemberMapper.updateById(myMembership);

        if (party.getLeaderPlayerId().equals(playerId)) {
            // Leader leaves: transfer or disband
            QueryWrapper<PartyMember> activeQw = new QueryWrapper<>();
            activeQw.eq("party_no", partyNo).eq("status", "active")
                     .orderByAsc("joined_at");
            List<PartyMember> remaining = partyMemberMapper.selectList(activeQw);

            if (remaining.isEmpty()) {
                disbandParty(party);
            } else {
                PartyMember newLeader = remaining.get(0);
                updateBothRoles(partyNo, playerId, newLeader.getPlayerId());
                party.setLeaderPlayerId(newLeader.getPlayerId());
                party.setUpdatedAt(LocalDateTime.now());
                partyMapper.updateById(party);

                Player newLeaderPlayer = playerMapper.selectById(newLeader.getPlayerId());
                String newLeaderName = newLeaderPlayer != null ? newLeaderPlayer.getPlayerName() : String.valueOf(newLeader.getPlayerId());
                writeLog(newLeader.getPlayerId(), "party_leader", "你成为了队伍「" + party.getName() + "」的新队长");
                tryNotify("队长离开，「" + newLeaderName + "」成为队伍「" + party.getName() + "」的新队长");
            }
        } else {
            // Member leaves
            party.setUpdatedAt(LocalDateTime.now());
            partyMapper.updateById(party);
        }

        Player player = playerMapper.selectById(playerId);
        String playerName = player != null ? player.getPlayerName() : String.valueOf(playerId);
        writeLog(playerId, "party_leave", "离开了队伍「" + party.getName() + "」");
        tryNotify("玩家 " + playerName + " 离开了队伍「" + party.getName() + "」");

        // Check if still active after leave
        if ("disbanded".equals(party.getStatus())) {
            PartyVO vo = new PartyVO();
            vo.setPartyNo(partyNo);
            vo.setStatus("disbanded");
            return vo;
        }
        return buildPartyVO(partyNo);
    }

    @Override
    @Transactional
    public PartyVO kickMember(Long leaderPlayerId, Long targetPlayerId, Long userId) {
        validatePlayerOwnership(leaderPlayerId, userId);

        if (leaderPlayerId.equals(targetPlayerId))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "不能踢出自己");

        MemberRecord leaderRec = getActiveMember(leaderPlayerId);
        if (!"leader".equals(leaderRec.member.getRole()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是队长，无权踢人");

        String partyNo = leaderRec.member.getPartyNo();
        Party party = leaderRec.party;

        MemberRecord targetRec = getActiveMemberInParty(partyNo, targetPlayerId);
        if (targetRec == null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "目标不在队伍中或已离队");

        targetRec.member.setStatus("kicked");
        targetRec.member.setLeftAt(LocalDateTime.now());
        targetRec.member.setUpdatedAt(LocalDateTime.now());
        partyMemberMapper.updateById(targetRec.member);

        party.setUpdatedAt(LocalDateTime.now());
        partyMapper.updateById(party);

        writeLog(leaderPlayerId, "party_kick", "你踢出了 " + targetRec.playerName + " («" + party.getName() + "»)");
        writeLog(targetPlayerId, "party_kicked", "你被踢出了队伍「" + party.getName() + "」");
        tryNotify("玩家 " + targetRec.playerName + " 被踢出了队伍「" + party.getName() + "」");

        return buildPartyVO(partyNo);
    }

    @Override
    @Transactional
    public PartyVO transferLeader(Long leaderPlayerId, Long targetPlayerId, Long userId) {
        validatePlayerOwnership(leaderPlayerId, userId);

        if (leaderPlayerId.equals(targetPlayerId))
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已经是队长");

        MemberRecord leaderRec = getActiveMember(leaderPlayerId);
        if (!"leader".equals(leaderRec.member.getRole()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不是队长，无权转让");

        String partyNo = leaderRec.member.getPartyNo();
        Party party = leaderRec.party;

        MemberRecord targetRec = getActiveMemberInParty(partyNo, targetPlayerId);
        if (targetRec == null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "目标不在队伍中或已离队");

        updateBothRoles(partyNo, leaderPlayerId, targetPlayerId);
        party.setLeaderPlayerId(targetPlayerId);
        party.setUpdatedAt(LocalDateTime.now());
        partyMapper.updateById(party);

        writeLog(leaderPlayerId, "party_leader", "你将队长转让给了 " + targetRec.playerName + " («" + party.getName() + "»)");
        writeLog(targetPlayerId, "party_leader", "你成为了队伍「" + party.getName() + "」的新队长");
        tryNotify("「" + targetRec.playerName + "」成为了队伍「" + party.getName() + "」的新队长");

        return buildPartyVO(partyNo);
    }

    @Override
    @Transactional
    public void disbandParty(Long leaderPlayerId, Long userId) {
        validatePlayerOwnership(leaderPlayerId, userId);

        MemberRecord leaderRec = getActiveMember(leaderPlayerId);
        if (!"leader".equals(leaderRec.member.getRole()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有队长可以解散队伍");

        Party party = leaderRec.party;
        disbandParty(party);

        writeLog(leaderPlayerId, "party_disband", "你解散了队伍「" + party.getName() + "」");
        tryNotify("队伍「" + party.getName() + "」已被队长解散");
    }

    @Override
    public PartyVO getMyParty(Long playerId, Long userId) {
        validatePlayerOwnership(playerId, userId);
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        PartyMember member = partyMemberMapper.selectOne(qw);
        if (member == null) return null;
        return buildPartyVO(member.getPartyNo());
    }

    @Override
    public PartyVO getPartyByNo(String partyNo) {
        Party party = partyMapper.selectOne(new QueryWrapper<Party>().eq("party_no", partyNo));
        if (party == null) return null;
        return buildPartyVO(partyNo);
    }

    @Override
    public List<PartyVO> getActiveParties() {
        QueryWrapper<Party> qw = new QueryWrapper<>();
        qw.eq("status", "active");
        List<Party> activeParties = partyMapper.selectList(qw);
        List<PartyVO> result = new ArrayList<>();
        for (Party party : activeParties) {
            PartyVO vo = buildPartyVO(party.getPartyNo());
            if (vo != null) result.add(vo);
        }
        return result;
    }

    @Override
    public PartySummaryVO getPartySummary(Long playerId) {
        PartySummaryVO vo = new PartySummaryVO();
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        PartyMember member = partyMemberMapper.selectOne(qw);

        if (member == null) {
            vo.setInParty(false);
            return vo;
        }

        Party party = partyMapper.selectOne(new QueryWrapper<Party>().eq("party_no", member.getPartyNo()).eq("status", "active"));
        if (party == null) {
            vo.setInParty(false);
            return vo;
        }

        vo.setInParty(true);
        vo.setPartyNo(party.getPartyNo());
        vo.setPartyName(party.getName());
        vo.setRole(member.getRole());
        vo.setMemberCount((int) countActiveMembers(party.getPartyNo()));
        vo.setMaxMembers(party.getMaxMembers() != null ? party.getMaxMembers() : MAX_PARTY_SIZE);
        vo.setOnlineMemberCount(countOnlineMembers(party.getPartyNo()));
        return vo;
    }

    // ─── helpers ───

    private void validatePlayerOwnership(Long playerId, Long userId) {
        Player player = playerMapper.selectById(playerId);
        if (player == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        if (!player.getUserId().equals(userId))
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此玩家");
    }

    private void assertNotInActiveParty(Long playerId) {
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        if (partyMemberMapper.selectOne(qw) != null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你已经在一个队伍中，请先离开当前队伍");
    }

    private Party getActivePartyByNo(String partyNo) {
        QueryWrapper<Party> qw = new QueryWrapper<>();
        qw.eq("party_no", partyNo).eq("status", "active");
        Party party = partyMapper.selectOne(qw);
        if (party == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "队伍不存在或已解散");
        return party;
    }

    private PartyMember getActiveMembership(Long playerId) {
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("player_id", playerId).eq("status", "active");
        PartyMember member = partyMemberMapper.selectOne(qw);
        if (member == null)
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "你不在任何队伍中");
        return member;
    }

    private MemberRecord getActiveMember(Long playerId) {
        PartyMember member = getActiveMembership(playerId);
        Party party = partyMapper.selectOne(new QueryWrapper<Party>().eq("party_no", member.getPartyNo()).eq("status", "active"));
        if (party == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "队伍不存在或已解散");
        Player player = playerMapper.selectById(playerId);
        return new MemberRecord(member, party, player != null ? player.getPlayerName() : String.valueOf(playerId));
    }

    private MemberRecord getActiveMemberInParty(String partyNo, Long playerId) {
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("party_no", partyNo).eq("player_id", playerId).eq("status", "active");
        PartyMember member = partyMemberMapper.selectOne(qw);
        if (member == null) return null;
        Party party = partyMapper.selectOne(new QueryWrapper<Party>().eq("party_no", partyNo).eq("status", "active"));
        Player player = playerMapper.selectById(playerId);
        return new MemberRecord(member, party, player != null ? player.getPlayerName() : String.valueOf(playerId));
    }

    private long countActiveMembers(String partyNo) {
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("party_no", partyNo).eq("status", "active");
        return partyMemberMapper.selectCount(qw);
    }

    private int countOnlineMembers(String partyNo) {
        QueryWrapper<PartyMember> qw = new QueryWrapper<>();
        qw.eq("party_no", partyNo).eq("status", "active");
        List<PartyMember> members = partyMemberMapper.selectList(qw);
        int online = 0;
        for (PartyMember m : members) {
            if (onlinePlayerService.isOnline(m.getPlayerId())) online++;
        }
        return online;
    }

    private void updateBothRoles(String partyNo, Long fromPlayerId, Long toPlayerId) {
        // From: leader→member, To: member→leader
        QueryWrapper<PartyMember> fQw = new QueryWrapper<>();
        fQw.eq("party_no", partyNo).eq("player_id", fromPlayerId).eq("status", "active");
        PartyMember from = partyMemberMapper.selectOne(fQw);
        if (from != null) {
            from.setRole("member");
            from.setUpdatedAt(LocalDateTime.now());
            partyMemberMapper.updateById(from);
        }

        QueryWrapper<PartyMember> tQw = new QueryWrapper<>();
        tQw.eq("party_no", partyNo).eq("player_id", toPlayerId).eq("status", "active");
        PartyMember to = partyMemberMapper.selectOne(tQw);
        if (to != null) {
            to.setRole("leader");
            to.setUpdatedAt(LocalDateTime.now());
            partyMemberMapper.updateById(to);
        }
    }

    private void disbandParty(Party party) {
        party.setStatus("disbanded");
        party.setDisbandedAt(LocalDateTime.now());
        party.setUpdatedAt(LocalDateTime.now());
        partyMapper.updateById(party);

        // Mark remaining active members as left
        QueryWrapper<PartyMember> activeQw = new QueryWrapper<>();
        activeQw.eq("party_no", party.getPartyNo()).eq("status", "active");
        List<PartyMember> members = partyMemberMapper.selectList(activeQw);
        for (PartyMember m : members) {
            m.setStatus("left");
            m.setLeftAt(LocalDateTime.now());
            m.setUpdatedAt(LocalDateTime.now());
            partyMemberMapper.updateById(m);
        }
    }

    private PartyVO buildPartyVO(String partyNo) {
        Party party = partyMapper.selectOne(new QueryWrapper<Party>().eq("party_no", partyNo));
        if (party == null) return null;

        QueryWrapper<PartyMember> activeQw = new QueryWrapper<>();
        activeQw.eq("party_no", partyNo).eq("status", "active")
                 .orderByAsc("joined_at");
        List<PartyMember> activeMembers = partyMemberMapper.selectList(activeQw);

        PartyVO vo = new PartyVO();
        vo.setId(party.getId());
        vo.setPartyNo(party.getPartyNo());
        vo.setLeaderPlayerId(party.getLeaderPlayerId());
        vo.setName(party.getName());
        vo.setDescription(party.getDescription());
        vo.setStatus(party.getStatus());
        vo.setMaxMembers(party.getMaxMembers() != null ? party.getMaxMembers() : MAX_PARTY_SIZE);
        vo.setTargetType(party.getTargetType());
        vo.setTargetKey(party.getTargetKey());
        vo.setMemberCount(activeMembers.size());
        vo.setCreatedAt(fmt(party.getCreatedAt()));
        vo.setUpdatedAt(fmt(party.getUpdatedAt()));
        vo.setDisbandedAt(fmt(party.getDisbandedAt()));

        List<PartyMemberVO> memberVOs = new ArrayList<>();
        int onlineCount = 0;
        for (PartyMember m : activeMembers) {
            PartyMemberVO mvo = new PartyMemberVO();
            mvo.setPlayerId(m.getPlayerId());
            mvo.setRole(m.getRole());
            mvo.setStatus(m.getStatus());
            mvo.setJoinedAt(fmt(m.getJoinedAt()));
            boolean online = onlinePlayerService.isOnline(m.getPlayerId());
            mvo.setOnline(online);
            if (online) onlineCount++;

            Player player = playerMapper.selectById(m.getPlayerId());
            mvo.setPlayerName(player != null ? player.getPlayerName() : String.valueOf(m.getPlayerId()));
            if (player != null && player.getStatsJson() != null) {
                Map<String, Object> stats = parseJsonMap(player.getStatsJson());
                mvo.setLevel(toInt(stats.get("level"), 1));
            } else {
                mvo.setLevel(1);
            }
            mvo.setReady("ready".equals(m.getStatus()));

            memberVOs.add(mvo);
        }
        vo.setMembers(memberVOs);
        vo.setOnlineMemberCount(onlineCount);

        Player leader = playerMapper.selectById(party.getLeaderPlayerId());
        vo.setLeaderName(leader != null ? leader.getPlayerName() : "未知");

        return vo;
    }

    private String generatePartyNo() {
        String base = "PT" + LocalDateTime.now().format(PT_DATE_FMT);
        String suffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return base + suffix;
    }

    private void writeLog(Long playerId, String type, String message) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        playerLogMapper.insert(log);
    }

    private void tryNotify(String content) {
        try {
            chatService.saveSystemMessage(content, "party", null);
        } catch (Exception ignored) {
        }
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.toString() : null;
    }

    // ─── record class ───

    private static class MemberRecord {
        final PartyMember member;
        final Party party;
        final String playerName;

        MemberRecord(PartyMember member, Party party, String playerName) {
            this.member = member;
            this.party = party;
            this.playerName = playerName;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json))
            return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private int toInt(Object val, int defaultVal) {
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
