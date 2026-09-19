package com.crm.repository;

import com.crm.entity.LineAutoReplyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineAutoReplyRuleRepository extends JpaRepository<LineAutoReplyRule, Long> {

    List<LineAutoReplyRule> findByLineAccountIdOrderBySortOrderAsc(Long lineAccountId);

    List<LineAutoReplyRule> findByLineAccountIdAndTriggerTypeAndIsActiveTrueOrderBySortOrderAsc(
            Long lineAccountId, String triggerType);
}
