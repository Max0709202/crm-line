package com.crm.service;

import com.crm.entity.LineUser;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manual linking of an unmatched {@link LineUser} (a real LINE contact with no
 * corresponding {@link com.crm.entity.CrmUser} yet) to an existing customer. LINE's webhook
 * carries no email/phone, so there's no automatic match the way inbound email/SMS has —
 * see {@link LineWebhookService}'s javadoc for why.
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

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("LINE contact not found: " + id); }
    }

    public static class CrmUserNotFoundException extends RuntimeException {
        public CrmUserNotFoundException(Long id) { super("CRM user not found: " + id); }
    }
}
