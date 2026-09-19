package com.crm.service;

import com.crm.dto.LineAutoReplyRuleForm;
import com.crm.entity.LineAutoReplyRule;
import com.crm.repository.LineAutoReplyRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD + trigger-matching for {@link LineAutoReplyRule}. Matching is deliberately simple —
 * exactly one FOLLOW rule and a first-match KEYWORD rule, both by {@code sortOrder} — since
 * this is a first cut of the feature; more elaborate matching (regex, multi-keyword AND/OR)
 * can be added later without changing the trigger points in {@link LineWebhookService}.
 */
@Service
public class LineAutoReplyService {

    private final LineAutoReplyRuleRepository repository;

    public LineAutoReplyService(LineAutoReplyRuleRepository repository) {
        this.repository = repository;
    }

    public List<LineAutoReplyRule> list(Long lineAccountId) {
        return repository.findByLineAccountIdOrderBySortOrderAsc(lineAccountId);
    }

    @Transactional
    public LineAutoReplyRule create(Long lineAccountId, LineAutoReplyRuleForm form) {
        if (!LineAutoReplyRule.TRIGGER_FOLLOW.equals(form.getTriggerType())
                && !LineAutoReplyRule.TRIGGER_KEYWORD.equals(form.getTriggerType())) {
            throw new InvalidRuleException("トリガー種別が不正です");
        }
        if (LineAutoReplyRule.TRIGGER_KEYWORD.equals(form.getTriggerType())
                && (form.getKeyword() == null || form.getKeyword().trim().isEmpty())) {
            throw new InvalidRuleException("キーワードトリガーにはキーワードが必須です");
        }
        LineAutoReplyRule r = new LineAutoReplyRule();
        r.setLineAccountId(lineAccountId);
        r.setTriggerType(form.getTriggerType());
        r.setKeyword(LineAutoReplyRule.TRIGGER_KEYWORD.equals(form.getTriggerType())
                ? form.getKeyword().trim() : null);
        r.setReplyBody(form.getReplyBody());
        r.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
        r.setIsActive(true);
        return repository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public LineAutoReplyRule toggleActive(Long id) {
        LineAutoReplyRule r = repository.findById(id).orElseThrow(() -> new NotFoundException(id));
        r.setIsActive(!Boolean.TRUE.equals(r.getIsActive()));
        return repository.save(r);
    }

    /** First active FOLLOW rule for this account, if any. */
    public Optional<LineAutoReplyRule> findFollowMatch(Long lineAccountId) {
        List<LineAutoReplyRule> rules = repository.findByLineAccountIdAndTriggerTypeAndIsActiveTrueOrderBySortOrderAsc(
                lineAccountId, LineAutoReplyRule.TRIGGER_FOLLOW);
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(0));
    }

    /** First active KEYWORD rule whose keyword is contained (case-insensitive) in {@code messageBody}. */
    public Optional<LineAutoReplyRule> findKeywordMatch(Long lineAccountId, String messageBody) {
        if (messageBody == null) return Optional.empty();
        String lower = messageBody.toLowerCase();
        for (LineAutoReplyRule r : repository.findByLineAccountIdAndTriggerTypeAndIsActiveTrueOrderBySortOrderAsc(
                lineAccountId, LineAutoReplyRule.TRIGGER_KEYWORD)) {
            if (r.getKeyword() != null && lower.contains(r.getKeyword().toLowerCase())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static class InvalidRuleException extends RuntimeException {
        public InvalidRuleException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("auto-reply rule not found: " + id); }
    }
}
