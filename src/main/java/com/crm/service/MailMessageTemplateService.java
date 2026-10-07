package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.MessageSentSubject;
import com.crm.repository.MessageSentSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * メールテンプレート設定 › メール通知 as the form of a キャラ mail (個別返信 / 一斉送信 / 差分), while it is
 * 有効 with {@code %body%} in its body ({@link MailTemplateService#isMessageTemplateActive()}):
 * <ul>
 *   <li>{@code %body%} — the message text; with a 返信URL (in the template or the message) it is
 *       clipped to メール送信設定's 本文の文字数設定, which counts only this part (as LINE's
 *       固定テンプレート: the template's own text is not counted);</li>
 *   <li>{@code %message_title%} — the message's 件名, {@code %staff_name%} — the キャラ name,
 *       {@code %reply_url%} / {@code %external_url%} — the message's 返信URL / 外部リンクURL;</li>
 *   <li>a 返信URL written in the message but not in the template goes right after {@code %body%}.</li>
 * </ul>
 * {@link Message#getBodyText() BODY_TEXT} keeps the full message (返信画面 / 履歴),
 * {@link Message#getSentBodyText() SENT_BODY_TEXT} the templated mail, and the templated subject is
 * kept in MESSAGE_SENT_SUBJECT (MESSAGE.SUBJECT stays the message's own title).
 */
@Service
public class MailMessageTemplateService {

    private static final String REPLY = MessageService.REPLY_URL_PLACEHOLDER;
    private static final String EXTERNAL = MessageService.EXTERNAL_URL_PLACEHOLDER;

    private final MailTemplateService mailTemplateService;
    private final DomainSettingService domainSettingService;
    private final ReplyPageService replyPageService;
    private final ReplyPageSettingService replyPageSettingService;
    private final MessageSentSubjectRepository sentSubjectRepository;

    public MailMessageTemplateService(MailTemplateService mailTemplateService, DomainSettingService domainSettingService,
                                      ReplyPageService replyPageService, ReplyPageSettingService replyPageSettingService,
                                      MessageSentSubjectRepository sentSubjectRepository) {
        this.mailTemplateService = mailTemplateService;
        this.domainSettingService = domainSettingService;
        this.replyPageService = replyPageService;
        this.replyPageSettingService = replyPageSettingService;
        this.sentSubjectRepository = sentSubjectRepository;
    }

    public boolean isActive() {
        return isActive(MailTemplateService.NOTICE);
    }

    /** Template {@code key} (メール通知 / サポート窓口) is 有効 with {@code %body%}. */
    public boolean isActive(String key) {
        return mailTemplateService.isMessageTemplateActive(key);
    }

    /**
     * Templates an already-saved キャラ mail (a reply page, when needed, points at its id).
     * {@code renderedBody} has the 置き換えタグ replaced but still holds %reply_url% / %external_url%.
     * The caller saves {@code msg} afterwards.
     */
    @Transactional
    public void apply(Message msg, CrmUser user, String renderedSubject, String renderedBody, String charaName) {
        apply(msg, user, renderedSubject, renderedBody, charaName, MailTemplateService.NOTICE);
    }

    /** The same with template {@code key} — サポート窓口 for a キャラ指定なし mail ({@code charaName} = its 送信者名). */
    @Transactional
    public void apply(Message msg, CrmUser user, String renderedSubject, String renderedBody, String charaName, String key) {
        String body = renderedBody == null ? "" : renderedBody;
        String[] tpl = mailTemplateService.messageTemplateSource(key);
        String tplAll = tpl[0] + "\n" + tpl[1];
        boolean tplReply = tplAll.contains(REPLY), tplExternal = tplAll.contains(EXTERNAL);
        boolean bodyReply = body.contains(REPLY), bodyExternal = body.contains(EXTERNAL);

        String replyUrl = null, externalUrl = null;
        if (tplReply || tplExternal || bodyReply || bodyExternal) {
            replyUrl = replyPageService.createReplyPageFor(msg);
            externalUrl = domainSettingService.buildExternalUrl(msg.getReplyPageToken());
            msg.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
        }

        // 返信画面 / 履歴: the whole message with its URLs
        msg.setBodyText(resolve(body, replyUrl, externalUrl));

        // %body%: the text only — clipped to 本文の文字数設定 when a 返信URL goes with it (the rest is
        // read on the 返信画面), as a mail without the template is
        String text = stripTrailing(body.replace(REPLY, "").replace(EXTERNAL, ""));
        if (replyUrl != null) text = clip(text, domainSettingService.getEmailReplyUrlClipLength());
        // (the tags were taken out of the text above, so their URLs follow it on a line of their own)
        if (bodyReply && !tplReply) text += onNewLine(text, replyUrl);
        if (bodyExternal && !tplExternal && externalUrl != null) text += onNewLine(text, externalUrl);

        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("%body%", text);
        extra.put("%message_title%", renderedSubject == null ? "" : renderedSubject);
        extra.put("%staff_name%", charaName == null ? "" : charaName);
        if (replyUrl != null) extra.put(REPLY, replyUrl);
        extra.put(EXTERNAL, externalUrl == null ? "" : externalUrl);
        String[] filled = mailTemplateService.renderMessageTemplate(key, user, extra);
        msg.setSentBodyText(filled[1]);

        MessageSentSubject s = sentSubjectRepository.findById(msg.getId()).orElseGet(MessageSentSubject::new);
        s.setMessageId(msg.getId());
        s.setSubject(filled[0].isEmpty() ? (renderedSubject == null ? "" : renderedSubject) : filled[0]);
        s.setCreatedAt(LocalDateTime.now());
        sentSubjectRepository.save(s);
    }

    /** Subject to send {@code msg} with: the templated one when it was templated, else its own. */
    public String sentSubjectOf(Message msg) {
        String own = msg.getSubject() == null ? "" : msg.getSubject();
        if (msg.getId() == null) return own;
        return sentSubjectRepository.findById(msg.getId()).map(MessageSentSubject::getSubject).orElse(own);
    }

    /** The first {@code max} characters (a surrogate pair counts once, as on the LINE side). */
    static String clip(String text, int max) {
        if (text.codePointCount(0, text.length()) <= max) return text;
        return text.substring(0, text.offsetByCodePoints(0, max));
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    /** 本文の通りに送信: each tag becomes the bare URL right where it was typed (no line break / URL前文言 added). */
    private static String resolve(String text, String replyUrl, String externalUrl) {
        String out = text;
        if (replyUrl != null && out.contains(REPLY)) out = out.replace(REPLY, replyUrl);
        if (out.contains(EXTERNAL)) out = out.replace(EXTERNAL, externalUrl == null ? "" : externalUrl);
        return out;
    }

    /** {@code url} on a line of its own after {@code text}. */
    private static String onNewLine(String text, String url) {
        return text.endsWith("\n") ? url : "\n" + url;
    }
}
