package com.crm.service;

import com.crm.entity.LineAccount;
import com.crm.entity.LineUser;
import com.crm.entity.Message;
import com.crm.line.LineApiClient;
import com.crm.line.dto.LineProfileResponse;
import com.crm.line.dto.LineWebhookPayload;
import com.crm.repository.LineAccountRepository;
import com.crm.repository.LineUserRepository;
import com.crm.repository.MessageRepository;
import com.crm.util.AesEncryptionUtil;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Processes LINE webhook events (already signature-verified by the time this is called —
 * see {@link com.crm.controller.LineWebhookController}).
 *
 * <p>Only {@code message} (text) and {@code follow}/{@code unfollow} events are handled;
 * anything else is skipped explicitly (not an error) so new LINE event/message types don't
 * need a code change to avoid breaking processing of the ones already supported.
 *
 * <p>The first time a given LINE user is seen for an account (on {@code follow} or on their
 * first message, whichever fires first), {@link #findOrCreateLineUser} auto-registers a bare
 * {@link com.crm.entity.CrmUser} and links it via {@link LineUserLinkService#autoRegisterAndLink}
 * — a LINE friend becomes a normal customer immediately, since {@code MESSAGE.USER_ID} is
 * {@code NOT NULL} with a real FK and there'd otherwise be nothing to attach a reply to.
 * {@link LineUserLinkService#link} remains for the separate, manual case of merging a contact
 * into an already-existing customer record instead of a fresh one.
 */
@Service
public class LineWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookService.class);

    private final LineAccountRepository lineAccountRepository;
    private final LineUserRepository lineUserRepository;
    private final MessageRepository messageRepository;
    private final AesEncryptionUtil aes;
    private final LineApiClient lineApiClient;
    private final LineUserLinkService lineUserLinkService;
    private final LineAutoReplyService lineAutoReplyService;
    private final MessageService messageService;

    public LineWebhookService(LineAccountRepository lineAccountRepository,
                               LineUserRepository lineUserRepository,
                               MessageRepository messageRepository,
                               AesEncryptionUtil aes,
                               LineApiClient lineApiClient,
                               LineUserLinkService lineUserLinkService,
                               LineAutoReplyService lineAutoReplyService,
                               MessageService messageService) {
        this.lineAccountRepository = lineAccountRepository;
        this.lineUserRepository = lineUserRepository;
        this.messageRepository = messageRepository;
        this.aes = aes;
        this.lineApiClient = lineApiClient;
        this.lineUserLinkService = lineUserLinkService;
        this.lineAutoReplyService = lineAutoReplyService;
        this.messageService = messageService;
    }

    public static class ProcessResult {
        public final boolean accepted;
        public final String reason;
        private ProcessResult(boolean accepted, String reason) { this.accepted = accepted; this.reason = reason; }
        static ProcessResult ok() { return new ProcessResult(true, null); }
        static ProcessResult skip(String reason) { return new ProcessResult(false, reason); }
    }

    public Optional<LineAccount> resolveAccount(String webhookToken) {
        return lineAccountRepository.findByWebhookToken(webhookToken);
    }

    @Transactional
    public List<ProcessResult> process(LineAccount account, LineWebhookPayload payload) {
        List<ProcessResult> results = new ArrayList<>();
        if (payload == null || payload.getEvents() == null || payload.getEvents().isEmpty()) {
            // LINE's console "Verify" button sends an empty events array on a healthy 200 —
            // this is a normal, successful case, not an error.
            results.add(ProcessResult.skip("empty_events"));
            return results;
        }
        for (LineWebhookPayload.LineEvent event : payload.getEvents()) {
            results.add(processOne(account, event));
        }
        return results;
    }

    private ProcessResult processOne(LineAccount account, LineWebhookPayload.LineEvent event) {
        if (event == null || event.getSource() == null || event.getSource().getUserId() == null) {
            return ProcessResult.skip("missing_source_user_id");
        }
        String lineUserId = event.getSource().getUserId();
        String type = event.getType();

        if ("follow".equals(type)) {
            boolean isNewContact = !lineUserRepository.findByLineAccountIdAndLineUserId(account.getId(), lineUserId).isPresent();
            LineUser lineUser = findOrCreateLineUser(account, lineUserId);
            log.info("[LINE] follow: account={} lineUserId={}", account.getId(), LogSafe.of(lineUserId));
            if (isNewContact) {
                sendAutoReplyIfMatched(account, lineUser, com.crm.entity.LineAutoReplyRule.TRIGGER_FOLLOW, null);
            }
            return ProcessResult.ok();
        }
        if ("unfollow".equals(type)) {
            log.info("[LINE] unfollow: account={} lineUserId={}", account.getId(), LogSafe.of(lineUserId));
            return ProcessResult.ok();
        }
        if (!"message".equals(type) || event.getMessage() == null) {
            return ProcessResult.skip("unsupported_event_type:" + type);
        }
        if (!"text".equals(event.getMessage().getType())) {
            return ProcessResult.skip("unsupported_message_type:" + event.getMessage().getType());
        }

        String dedupKey = event.getWebhookEventId() == null ? null : "line-mo:" + event.getWebhookEventId();
        if (dedupKey != null && messageRepository.existsByMessageIdHeader(dedupKey)) {
            log.info("[LINE] inbound skipped as duplicate: webhookEventId={}", LogSafe.of(event.getWebhookEventId()));
            return ProcessResult.skip("duplicate");
        }

        String body = event.getMessage().getText() == null ? "" : event.getMessage().getText();
        LineUser lineUser = findOrCreateLineUser(account, lineUserId);
        lineUser.setLastMessagePreview(body);
        lineUser.setLastMessageAt(LocalDateTime.now());
        lineUserRepository.save(lineUser);

        if (!lineUser.isLinked()) {
            log.info("[LINE] inbound from unlinked contact: account={} lineUserId={}",
                    account.getId(), LogSafe.of(lineUserId));
            return ProcessResult.skip("unlinked_contact");
        }

        Message m = new Message();
        m.setUserId(lineUser.getCrmUserId());
        m.setDirection(Message.DIR_IN);
        m.setChannel(Message.CHANNEL_LINE);
        m.setLineAccountId(account.getId());
        m.setFromAddress(lineUserId);
        m.setToAddress(account.getOfficialAccountId());
        m.setBodyText(body);
        m.setStatus(Message.STATUS_SENT);
        if (dedupKey != null) m.setMessageIdHeader(dedupKey);
        messageRepository.save(m);

        log.info("[LINE] inbound matched: account={} lineUserId={} crmUserId={}",
                account.getId(), LogSafe.of(lineUserId), lineUser.getCrmUserId());
        sendAutoReplyIfMatched(account, lineUser, com.crm.entity.LineAutoReplyRule.TRIGGER_KEYWORD, body);
        return ProcessResult.ok();
    }

    /** Best-effort — an auto-reply failure (e.g. a since-revoked access token) must not fail
     *  webhook processing of the real inbound event itself. */
    private void sendAutoReplyIfMatched(LineAccount account, LineUser lineUser, String triggerType, String messageBody) {
        if (lineUser.getCrmUserId() == null) return;
        Optional<com.crm.entity.LineAutoReplyRule> match =
                com.crm.entity.LineAutoReplyRule.TRIGGER_FOLLOW.equals(triggerType)
                        ? lineAutoReplyService.findFollowMatch(account.getId())
                        : lineAutoReplyService.findKeywordMatch(account.getId(), messageBody);
        if (!match.isPresent()) return;
        try {
            com.crm.dto.LineComposeForm form = new com.crm.dto.LineComposeForm();
            form.setBody(match.get().getReplyBody());
            messageService.composeLine(lineUser.getCrmUserId(), null, form);
            log.info("[LINE] auto-reply sent: account={} ruleId={} trigger={}",
                    account.getId(), match.get().getId(), triggerType);
        } catch (Exception e) {
            log.warn("[LINE] auto-reply failed: account={} ruleId={} error={}",
                    account.getId(), match.get().getId(), LogSafe.of(e.toString()));
        }
    }

    private LineUser findOrCreateLineUser(LineAccount account, String lineUserId) {
        Optional<LineUser> existing = lineUserRepository.findByLineAccountIdAndLineUserId(account.getId(), lineUserId);
        if (existing.isPresent()) return existing.get();

        LineUser u = new LineUser();
        u.setLineAccountId(account.getId());
        u.setLineUserId(lineUserId);
        LineProfileResponse profile = lineApiClient.getProfile(aes.decrypt(account.getAccessToken()), lineUserId);
        if (profile != null) {
            u.setLineDisplayName(profile.getDisplayName());
            u.setLinePictureUrl(profile.getPictureUrl());
        }
        return lineUserLinkService.autoRegisterAndLink(u);
    }
}
