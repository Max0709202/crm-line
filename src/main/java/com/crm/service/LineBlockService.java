package com.crm.service;

import com.crm.entity.LineUser;
import com.crm.entity.LineUserBlock;
import com.crm.repository.LineUserBlockRepository;
import com.crm.repository.LineUserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** LINE ブロック — which キャラ (LINE accounts) a user has blocked (from the LINE unfollow event). */
@Service
public class LineBlockService {

    private final LineUserRepository lineUserRepository;
    private final LineUserBlockRepository blockRepository;

    public LineBlockService(LineUserRepository lineUserRepository, LineUserBlockRepository blockRepository) {
        this.lineUserRepository = lineUserRepository;
        this.blockRepository = blockRepository;
    }

    /** LINE account ids the user has blocked. */
    public Set<Long> blockedAccountIds(Long crmUserId) {
        Set<Long> out = new HashSet<>();
        if (crmUserId == null) return out;
        Map<Long, Set<Long>> m = blockedAccountsByUser(java.util.Collections.singletonList(crmUserId));
        if (m.containsKey(crmUserId)) out.addAll(m.get(crmUserId));
        return out;
    }

    /** crmUserId → LINE account ids that user has blocked (users with no block are absent). */
    public Map<Long, Set<Long>> blockedAccountsByUser(Collection<Long> crmUserIds) {
        Map<Long, Set<Long>> out = new HashMap<>();
        if (crmUserIds == null || crmUserIds.isEmpty()) return out;
        List<LineUser> rows = lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(new ArrayList<>(crmUserIds));
        if (rows.isEmpty()) return out;
        Map<Long, LineUser> byRowId = new HashMap<>();
        for (LineUser lu : rows) byRowId.put(lu.getId(), lu);
        for (LineUserBlock b : blockRepository.findAllById(byRowId.keySet())) {
            LineUser lu = byRowId.get(b.getLineUserRowId());
            if (lu != null) out.computeIfAbsent(lu.getCrmUserId(), k -> new HashSet<>()).add(lu.getLineAccountId());
        }
        return out;
    }
}
