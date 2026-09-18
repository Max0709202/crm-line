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

    public LineUserLinkService(LineUserRepository lineUserRepository, CrmUserRepository crmUserRepository) {
        this.lineUserRepository = lineUserRepository;
        this.crmUserRepository = crmUserRepository;
    }

    public List<LineUser> listUnlinked() {
        return lineUserRepository.findByCrmUserIdIsNullOrderByLastMessageAtDesc();
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
     */
    @Transactional
    public LineUser autoRegisterAndLink(LineUser lineUser) {
        CrmUser u = new CrmUser();
        String name = lineUser.getLineDisplayName();
        u.setDisplayName((name != null && !name.trim().isEmpty()) ? name : "LINE友だち");
        CrmUser saved = crmUserRepository.save(u);
        lineUser.setCrmUserId(saved.getId());
        return lineUserRepository.save(lineUser);
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("LINE contact not found: " + id); }
    }

    public static class CrmUserNotFoundException extends RuntimeException {
        public CrmUserNotFoundException(Long id) { super("CRM user not found: " + id); }
    }
}
