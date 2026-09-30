package com.crm.service;

import com.crm.dto.LineAccountForm;
import com.crm.entity.LineAccount;
import com.crm.line.LineApiClient;
import com.crm.line.dto.LineBotInfoResponse;
import com.crm.entity.LineUser;
import com.crm.repository.LineAccountRepository;
import com.crm.repository.LineUserRepository;
import com.crm.util.AesEncryptionUtil;
import com.crm.util.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD + connection-check for {@link LineAccount}. Channel Secret/Access Token are
 * AES-encrypted before persisting (see {@link AesEncryptionUtil}) and never decrypted
 * back out to the UI — only to call LINE's own API (connection check here; send/webhook
 * verification in later phases). On edit, a blank submitted secret/token means "keep the
 * existing encrypted value", the same convention {@link CarrierPoolService#update} uses
 * for {@code smtpPassword}.
 */
@Service
public class LineAccountService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LineAccountService.class);

    private final LineAccountRepository repository;
    private final AesEncryptionUtil aes;
    private final LineApiClient lineApiClient;
    private final LineUserRepository lineUserRepository;

    public LineAccountService(LineAccountRepository repository, AesEncryptionUtil aes, LineApiClient lineApiClient,
                               LineUserRepository lineUserRepository) {
        this.repository = repository;
        this.aes = aes;
        this.lineApiClient = lineApiClient;
        this.lineUserRepository = lineUserRepository;
    }

    /**
     * For each of the given CrmUser ids, resolves which single LINE account they should be
     * treated as linked to for a 「紐づきアカ」(dynamic account) broadcast/diff-step send, when
     * they're friended with more than one child account. The account with the lowest
     * {@link LineAccount#getLinkagePriority()} wins; ties are broken by whichever link they
     * most recently messaged on. A recipient with no link at all is simply absent from the
     * result (client request 2026-09-27 — previously "most recently active" was the only
     * tie-break, with no admin-controllable ordering, and there was no guarantee against a
     * recipient ending up in more than one resolved group).
     */
    public Map<Long, Long> resolveDynamicLinkedAccountIds(Collection<Long> crmUserIds) {
        if (crmUserIds == null || crmUserIds.isEmpty()) return java.util.Collections.emptyMap();
        List<LineUser> links = lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(crmUserIds);
        if (links.isEmpty()) return java.util.Collections.emptyMap();

        java.util.Set<Long> accountIds = new java.util.HashSet<>();
        for (LineUser lu : links) accountIds.add(lu.getLineAccountId());
        Map<Long, Integer> priorityByAccountId = new HashMap<>();
        for (LineAccount a : repository.findAllById(accountIds)) {
            priorityByAccountId.put(a.getId(), a.getLinkagePriority() == null ? 100 : a.getLinkagePriority());
        }

        // links is already ordered by lastMessageAt DESC, so within equal priority the first
        // one encountered per crmUserId is the most-recently-active — exactly the desired
        // secondary tie-break.
        Map<Long, LineUser> bestByCrmUserId = new HashMap<>();
        for (LineUser lu : links) {
            LineUser current = bestByCrmUserId.get(lu.getCrmUserId());
            if (current == null) {
                bestByCrmUserId.put(lu.getCrmUserId(), lu);
                continue;
            }
            int currentPriority = priorityByAccountId.getOrDefault(current.getLineAccountId(), 100);
            int candidatePriority = priorityByAccountId.getOrDefault(lu.getLineAccountId(), 100);
            if (candidatePriority < currentPriority) {
                bestByCrmUserId.put(lu.getCrmUserId(), lu);
            }
        }
        Map<Long, Long> out = new HashMap<>();
        for (Map.Entry<Long, LineUser> e : bestByCrmUserId.entrySet()) {
            out.put(e.getKey(), e.getValue().getLineAccountId());
        }
        return out;
    }

    public List<LineAccount> listParents() {
        return repository.findByParentAccountIdIsNullOrderByNameAsc();
    }

    public List<LineAccount> listChildren(Long parentAccountId) {
        return repository.findByParentAccountIdOrderByNameAsc(parentAccountId);
    }

    public long countChildren(Long parentAccountId) {
        return repository.countByParentAccountId(parentAccountId);
    }

    public Optional<LineAccount> findById(Long id) {
        return repository.findById(id);
    }

    /** Decrypted only for callers that need to actually call LINE's API — never render this. */
    public String decryptAccessToken(LineAccount a) {
        return aes.decrypt(a.getAccessToken());
    }

    public String decryptChannelSecret(LineAccount a) {
        return aes.decrypt(a.getChannelSecret());
    }

    @Transactional
    public LineAccount create(LineAccountForm form) {
        String channelId = form.getChannelId() == null ? null : form.getChannelId().trim();
        if (channelId != null && repository.existsByChannelId(channelId)) {
            throw new DuplicateChannelIdException(channelId);
        }
        if (form.getChannelSecret() == null || form.getChannelSecret().trim().isEmpty()) {
            throw new MissingCredentialException("channelSecret");
        }
        if (form.getAccessToken() == null || form.getAccessToken().trim().isEmpty()) {
            throw new MissingCredentialException("accessToken");
        }
        validateParent(form.getParentAccountId(), null);

        LineAccount a = new LineAccount();
        a.setParentAccountId(form.getParentAccountId());
        a.setName(form.getName().trim());
        a.setOfficialAccountId(trimOrNull(form.getOfficialAccountId()));
        a.setChannelId(channelId);
        a.setChannelSecret(aes.encrypt(form.getChannelSecret()));
        a.setAccessToken(aes.encrypt(form.getAccessToken()));
        a.setStatus(LineAccount.STATUS_UNUSED);
        a.setWebhookToken(generateUniqueWebhookToken());
        a.setLinkagePriority(clampPriority(form.getLinkagePriority()));
        return repository.save(a);
    }

    @Transactional
    public LineAccount update(Long id, LineAccountForm form) {
        LineAccount a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
        String newChannelId = form.getChannelId() == null ? null : form.getChannelId().trim();
        if (newChannelId != null && !newChannelId.equals(a.getChannelId()) && repository.existsByChannelId(newChannelId)) {
            throw new DuplicateChannelIdException(newChannelId);
        }
        validateParent(form.getParentAccountId(), id);

        a.setParentAccountId(form.getParentAccountId());
        a.setName(form.getName().trim());
        a.setOfficialAccountId(trimOrNull(form.getOfficialAccountId()));
        a.setChannelId(newChannelId);
        // Blank input means "keep existing" — never force a re-entry of a working secret.
        if (form.getChannelSecret() != null && !form.getChannelSecret().trim().isEmpty()) {
            a.setChannelSecret(aes.encrypt(form.getChannelSecret()));
        }
        if (form.getAccessToken() != null && !form.getAccessToken().trim().isEmpty()) {
            a.setAccessToken(aes.encrypt(form.getAccessToken()));
        }
        a.setLinkagePriority(clampPriority(form.getLinkagePriority()));
        return repository.save(a);
    }

    /** 1〜999 にクランプ。未入力はデフォルト(100)扱い。 */
    private static Integer clampPriority(Integer priority) {
        if (priority == null) return 100;
        if (priority < 1) return 1;
        if (priority > 999) return 999;
        return priority;
    }

    /**
     * Refuses to delete a parent that still has children — the self-referencing FK is
     * {@code ON DELETE RESTRICT} (not the CASCADE every other FK in this schema uses)
     * specifically so a parent can't silently take up to ~100 children with it; this
     * check turns that into a clear message instead of a raw SQL constraint exception.
     */
    @Transactional
    public void delete(Long id) {
        LineAccount a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
        long children = repository.countByParentAccountId(id);
        if (children > 0) {
            throw new HasChildrenException(id, children);
        }
        repository.delete(a);
    }

    /** Bulk 選択削除 — one bad id (not found, or a parent with children) doesn't abort the
     *  rest of the batch, mirroring {@link CarrierPoolService#deleteByIds}. */
    @Transactional
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = 0;
        for (Long id : ids) {
            if (id == null) continue;
            try {
                delete(id);
                n++;
            } catch (Exception ignored) {}
        }
        return n;
    }

    /**
     * Calls LINE's {@code GET /v2/bot/info} with the decrypted access token and flips
     * status to ACTIVE/ERROR based on the result. Never throws on a failed connection —
     * that's a normal, expected outcome (wrong/expired token), not a system error.
     */
    @Transactional
    public LineAccount checkConnection(Long id) {
        LineAccount a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
        LineBotInfoResponse info = lineApiClient.getBotInfo(aes.decrypt(a.getAccessToken()));
        a.setStatus(info != null ? LineAccount.STATUS_ACTIVE : LineAccount.STATUS_ERROR);
        a.setLastConnectionCheckAt(LocalDateTime.now());
        return repository.save(a);
    }

    /** Separate lane so a slow LINE API (up to connect+read timeout per account) never holds
     *  the shared @Scheduled thread that message dispatch also runs on. */
    private final java.util.concurrent.ExecutorService connectionCheckLane =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "line-connection-check");
                t.setDaemon(true);
                return t;
            });
    private final java.util.concurrent.atomic.AtomicBoolean connectionCheckRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Every 10 minutes — re-runs {@link #checkConnection} for every account in use (ACTIVE)
     * or already failing (ERROR, so a recovered one clears by itself), so an account that
     * stops connecting is flagged on the 管理トップ without anyone pressing 接続確認
     * (2026-09-29 client request). UNUSED accounts are left alone — never checked yet.
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${app.line.connection-check-ms:600000}",
            initialDelayString = "${app.line.connection-check-initial-delay-ms:120000}")
    public void scheduleConnectionChecks() {
        if (!connectionCheckRunning.compareAndSet(false, true)) return;
        try {
            connectionCheckLane.submit(() -> {
                try {
                    checkAllConnections();
                } finally {
                    connectionCheckRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            connectionCheckRunning.set(false);
            throw e;
        }
    }

    void checkAllConnections() {
        List<LineAccount> targets = repository.findByStatusIn(
                java.util.Arrays.asList(LineAccount.STATUS_ACTIVE, LineAccount.STATUS_ERROR));
        for (LineAccount a : targets) {
            try {
                String before = a.getStatus();
                LineAccount after = checkConnection(a.getId());
                if (!before.equals(after.getStatus())) {
                    log.warn("[LINE] connection status changed: account={} name={} {} -> {}",
                            a.getId(), com.crm.util.LogSafe.of(a.getName()), before, after.getStatus());
                }
            } catch (Exception e) {
                log.warn("[LINE] auto connection check failed: account={} error={}", a.getId(), com.crm.util.LogSafe.of(e.toString()));
            }
        }
    }

    /** Accounts currently failing their connection check — the dashboard warning list. */
    public List<LineAccount> listConnectionErrors() {
        return repository.findByStatusOrderByNameAsc(LineAccount.STATUS_ERROR);
    }

    @Transactional
    public LineAccount setGroupChatMode(Long id, boolean enabled) {
        LineAccount a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
        a.setIsGroupChatMode(enabled);
        return repository.save(a);
    }

    /** Strict 2-level tree: a chosen parent must itself be a parent (no grandparents). */
    private void validateParent(Long parentAccountId, Long selfId) {
        if (parentAccountId == null) return;
        if (parentAccountId.equals(selfId)) {
            throw new InvalidParentException("account cannot be its own parent");
        }
        LineAccount parent = repository.findById(parentAccountId)
                .orElseThrow(() -> new InvalidParentException("parent account not found: " + parentAccountId));
        if (!parent.isParent()) {
            throw new InvalidParentException("chosen parent is itself a child account — only a 2-level hierarchy is supported");
        }
    }

    private String generateUniqueWebhookToken() {
        String token;
        do {
            token = TokenGenerator.generateReplyToken();
        } while (repository.existsByWebhookToken(token));
        return token;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static class DuplicateChannelIdException extends RuntimeException {
        public DuplicateChannelIdException(String channelId) { super("duplicate Channel ID: " + channelId); }
    }

    public static class MissingCredentialException extends RuntimeException {
        public MissingCredentialException(String field) { super("missing required credential: " + field); }
    }

    public static class InvalidParentException extends RuntimeException {
        public InvalidParentException(String message) { super(message); }
    }

    public static class HasChildrenException extends RuntimeException {
        public HasChildrenException(Long id, long childCount) {
            super("account " + id + " still has " + childCount + " child account(s)");
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("LINE account not found: " + id); }
    }
}
