package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.LineUser;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Linking a {@link LineUser} to a {@link com.crm.entity.CrmUser}, either automatically at
 * friend-add time ({@link #autoRegisterAndLink}, the normal path — see
 * {@link LineWebhookService}) or manually to merge a legacy unmatched contact into an
 * already-existing customer record ({@link #link}).
 */
@Service
public class LineUserLinkService {

    private final LineUserRepository lineUserRepository;
    private final CrmUserRepository crmUserRepository;
    private final DomainSettingService domainSettingService;
    private final DiffScheduleService diffScheduleService;
    private final MemberLoginIdService loginIdService;

    public LineUserLinkService(LineUserRepository lineUserRepository, CrmUserRepository crmUserRepository,
                                DomainSettingService domainSettingService,
                                @org.springframework.context.annotation.Lazy DiffScheduleService diffScheduleService,
                                MemberLoginIdService loginIdService) {
        this.lineUserRepository = lineUserRepository;
        this.crmUserRepository = crmUserRepository;
        this.domainSettingService = domainSettingService;
        this.diffScheduleService = diffScheduleService;
        this.loginIdService = loginIdService;
    }

    public List<LineUser> listUnlinked() {
        return lineUserRepository.findByCrmUserIdIsNullOrderByLastMessageAtDesc();
    }

    /**
     * For every legacy unmatched contact, proposes exactly one candidate {@link CrmUser} when
     * its display name matches (case-insensitive) — never guesses when there's zero or more
     * than one match, leaving those for the existing manual {@link #link} form instead.
     */
    public java.util.List<MatchCandidate> suggestMatches() {
        java.util.List<MatchCandidate> out = new java.util.ArrayList<>();
        for (LineUser u : listUnlinked()) {
            String name = u.getLineDisplayName();
            if (name == null || name.trim().isEmpty()) continue;
            List<CrmUser> candidates = crmUserRepository.findByDisplayNameIgnoreCase(name.trim());
            if (candidates.size() == 1) {
                out.add(new MatchCandidate(u, candidates.get(0)));
            }
        }
        return out;
    }

    /** Bulk-approve a set of suggested pairs. One bad pair doesn't abort the rest, same
     *  tolerant-batch shape used by LineAccountService#deleteByIds. */
    @Transactional
    public int linkAll(java.util.Map<Long, Long> lineUserIdToCrmUserId) {
        if (lineUserIdToCrmUserId == null || lineUserIdToCrmUserId.isEmpty()) return 0;
        int n = 0;
        for (java.util.Map.Entry<Long, Long> e : lineUserIdToCrmUserId.entrySet()) {
            try {
                link(e.getKey(), e.getValue());
                n++;
            } catch (Exception ignored) {}
        }
        return n;
    }

    /**
     * Bulk-delete unmatched (unlinked) contacts so they can be re-registered by a fresh
     * friend-add. Only rows with no linked CrmUser are ever deleted here — a contact that's
     * already a customer is never removed by this screen, even if its id is submitted.
     */
    @Transactional
    public int deleteUnlinkedByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (Long id : ids) {
            if (id == null) continue;
            LineUser u = lineUserRepository.findById(id).orElse(null);
            if (u == null || u.isLinked()) continue;
            lineUserRepository.delete(u);
            n++;
        }
        return n;
    }

    public static class MatchCandidate {
        private final LineUser lineUser;
        private final CrmUser crmUser;
        public MatchCandidate(LineUser lineUser, CrmUser crmUser) { this.lineUser = lineUser; this.crmUser = crmUser; }
        public LineUser getLineUser() { return lineUser; }
        public CrmUser getCrmUser() { return crmUser; }
    }

    @Transactional
    public LineUser link(Long lineUserRowId, Long crmUserId) {
        LineUser u = lineUserRepository.findById(lineUserRowId)
                .orElseThrow(() -> new NotFoundException(lineUserRowId));
        if (!crmUserRepository.existsById(crmUserId)) {
            throw new CrmUserNotFoundException(crmUserId);
        }
        u.setCrmUserId(crmUserId);
        return lineUserRepository.save(u);
    }

    /**
     * Creates a bare {@link CrmUser} (display name only — LINE gives no email/phone) for a
     * brand-new LINE contact and links it in the same step, so the contact is immediately a
     * normal customer: searchable/filterable on the user list and eligible for 一斉送信 /
     * 差分予約 like any other user. Replaces the old "friend-add leaves an unmatched contact
     * that must be manually linked" default (2026-09-18 client request) — manual {@link #link}
     * still exists for merging into an already-existing customer record when that's wanted.
     *
     * <p>A person who friends several of our accounts (キャラ) arrives with the same LINE
     * {@code userId} on each one (userIds are per-provider, and our accounts share one), so
     * that userId already being linked means they're an existing customer — the new friend
     * row joins that same CrmUser instead of creating a duplicate customer per account
     * (2026-09-29 client request: one ゆたか with 3 linked characters, not 3 ゆたか).
     */
    @Transactional
    public LineUser autoRegisterAndLink(LineUser lineUser) {
        for (LineUser other : lineUserRepository.findByLineUserIdAndCrmUserIdIsNotNullOrderByIdAsc(lineUser.getLineUserId())) {
            if (crmUserRepository.existsById(other.getCrmUserId())) {
                lineUser.setCrmUserId(other.getCrmUserId());
                return lineUserRepository.save(lineUser);
            }
        }
        CrmUser u = new CrmUser();
        String name = lineUser.getLineDisplayName();
        u.setDisplayName((name != null && !name.trim().isEmpty()) ? name : "LINE友だち");
        u.setFolder(domainSettingService.getLineAutoRegisterFolder());
        // same 10000〜 numbering as member registration / admin 新規登録 / CSV import
        u.setLoginId(loginIdService.next());
        CrmUser saved = crmUserRepository.save(u);
        lineUser.setCrmUserId(saved.getId());
        LineUser linked = lineUserRepository.save(lineUser);
        // 登録後(分後) diff steps — a brand-new customer only (not a friend-add that joined an
        // existing customer above).
        try {
            diffScheduleService.applyRegistrationSteps(saved, null);
        } catch (RuntimeException e) {
            // never fail the friend-add over this; details are logged by the service
        }
        return linked;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("LINE contact not found: " + id); }
    }

    public static class CrmUserNotFoundException extends RuntimeException {
        public CrmUserNotFoundException(Long id) { super("CRM user not found: " + id); }
    }
}
