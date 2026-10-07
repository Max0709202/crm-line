package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.repository.CrmSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a LINE message actually sends (LINE設定):
 * <ul>
 *   <li>LINE本文の最大文字数 — a longer body is sent up to that many characters and the rest is read
 *       on the 返信URL page (the URL is added automatically when the body / template has none);</li>
 *   <li>LINE送信の固定テンプレート — when 使用, a fixed text added under every LINE message, with
 *       %reply_url%, the default 置き換えタグ and the user's 置き換えタグ replaced; 短縮URL chooses the
 *       short (10-character) or the long reply-page token.</li>
 * </ul>
 * {@link Message#getBodyText() BODY_TEXT} keeps the full body (メッセージボックス / 返信URL page /
 * 履歴); {@link Message#getSentBodyText() SENT_BODY_TEXT} is what LINE receives.
 */
@Service
public class LineTextService {

    public static final String KEY_TEMPLATE_ENABLED = "line.fixed_template.enabled";
    public static final String KEY_TEMPLATE_TEXT = "line.fixed_template.text";
    public static final String KEY_SHORT_URL = "line.fixed_template.short_url";
    public static final int MAX_TEMPLATE = 1000;
    /** LINE's own limit for one text message. */
    static final int LINE_MAX = 5000;

    private static final String REPLY = MessageService.REPLY_URL_PLACEHOLDER;
    private static final String EXTERNAL = MessageService.EXTERNAL_URL_PLACEHOLDER;

    private final CrmSettingRepository settings;
    private final DomainSettingService domainSettingService;
    private final ReplyPageService replyPageService;
    private final ReplyPageSettingService replyPageSettingService;
    private final PlaceholderService placeholderService;

    public LineTextService(CrmSettingRepository settings, DomainSettingService domainSettingService,
                           ReplyPageService replyPageService, ReplyPageSettingService replyPageSettingService,
                           PlaceholderService placeholderService) {
        this.settings = settings;
        this.domainSettingService = domainSettingService;
        this.replyPageService = replyPageService;
        this.replyPageSettingService = replyPageSettingService;
        this.placeholderService = placeholderService;
    }

    /* ===================== 固定テンプレート設定 ===================== */

    public boolean isTemplateEnabled() { return "true".equals(get(KEY_TEMPLATE_ENABLED)); }

    public String getTemplate() { String v = get(KEY_TEMPLATE_TEXT); return v == null ? "" : v; }

    /** 短縮URL — default on (the individual LINE reply already used the short token). */
    public boolean isShortUrl() { String v = get(KEY_SHORT_URL); return v == null || "true".equals(v); }

    @Transactional
    public void saveTemplate(boolean enabled, String text, boolean shortUrl) {
        String t = text == null ? "" : text.replace("\r\n", "\n");
        if (t.length() > MAX_TEMPLATE) throw new IllegalArgumentException("固定テンプレートは" + MAX_TEMPLATE + "文字までです");
        if (enabled && t.trim().isEmpty()) throw new IllegalArgumentException("固定テンプレートを使用する場合は内容を入力してください");
        put(KEY_TEMPLATE_ENABLED, String.valueOf(enabled));
        put(KEY_TEMPLATE_TEXT, t);
        put(KEY_SHORT_URL, String.valueOf(shortUrl));
    }

    /* ===================== 送信テキスト ===================== */

    /**
     * Sets BODY_TEXT / SENT_BODY_TEXT of a LINE OUT message that is already saved (a reply page,
     * when needed, points at its id). {@code renderedBody} has the 置き換えタグ replaced but still
     * holds %reply_url% / %external_url%. The caller saves {@code msg} afterwards.
     */
    @Transactional
    public void apply(Message msg, CrmUser user, String renderedBody) {
        String body = renderedBody == null ? "" : renderedBody;
        String template = isTemplateEnabled() ? placeholderService.substitute(getTemplate(), user) : "";
        boolean hasTemplate = !template.trim().isEmpty();
        int max = domainSettingService.getLineMaxBodyLength();
        boolean clip = visibleLength(body) > max;
        boolean templateHasReply = hasTemplate && template.contains(REPLY);
        boolean needPage = clip || body.contains(REPLY) || body.contains(EXTERNAL)
                || templateHasReply || (hasTemplate && template.contains(EXTERNAL));

        msg.setBodyText(body);
        msg.setSentBodyText(null);
        if (!needPage && !hasTemplate) return;   // plain short message: sent as written

        String replyUrl = null, externalUrl = null;
        if (needPage) {
            replyUrl = isShortUrl() ? replyPageService.createShortReplyPageFor(msg) : replyPageService.createReplyPageFor(msg);
            externalUrl = domainSettingService.buildExternalUrl(msg.getReplyPageToken());
            msg.setExcludedFromBox(domainSettingService.isActiveLinkDomainExternalLanding());
        }
        String fullBody = resolve(body, replyUrl, externalUrl);
        msg.setBodyText(fullBody);

        StringBuilder sent = new StringBuilder();
        if (clip) {
            boolean[] replyIncluded = {false};
            sent.append(clipVisible(body, max, replyUrl, externalUrl, replyIncluded));
            // the rest of the text is on the 返信URL page — make sure the URL is in the message
            if (!replyIncluded[0] && !templateHasReply) {
                if (sent.length() > 0 && sent.charAt(sent.length() - 1) != '\n') sent.append('\n');
                sent.append(replyUrl);
            }
        } else {
            sent.append(fullBody);
        }
        if (hasTemplate) {
            if (sent.length() > 0 && sent.charAt(sent.length() - 1) != '\n') sent.append('\n');
            sent.append(resolve(template, replyUrl, externalUrl));
        }
        String s = sent.length() > LINE_MAX ? sent.substring(0, LINE_MAX) : sent.toString();
        msg.setSentBodyText(s.equals(fullBody) ? null : s);
    }

    /** Characters a reader sees (tags not counted; a surrogate pair counts once). */
    static int visibleLength(String body) {
        String t = body.replace(REPLY, "").replace(EXTERNAL, "");
        return t.codePointCount(0, t.length());
    }

    /** The first {@code max} visible characters; tags met on the way are kept (as their URLs). */
    static String clipVisible(String body, int max, String replyUrl, String externalUrl, boolean[] replyIncluded) {
        StringBuilder out = new StringBuilder();
        int count = 0, i = 0;
        while (i < body.length() && count < max) {
            if (body.startsWith(REPLY, i)) {
                out.append(replyUrl);
                replyIncluded[0] = true;
                i += REPLY.length();
                continue;
            }
            if (body.startsWith(EXTERNAL, i)) {
                out.append(externalUrl == null ? "" : externalUrl);
                i += EXTERNAL.length();
                continue;
            }
            int cp = body.codePointAt(i);
            out.appendCodePoint(cp);
            i += Character.charCount(cp);
            count++;
        }
        return out.toString();
    }

    /** 本文の通りに送信: each tag becomes the bare URL right where it was typed (no line break / URL前文言 added). */
    private static String resolve(String text, String replyUrl, String externalUrl) {
        String out = text;
        if (out.contains(REPLY)) out = out.replace(REPLY, replyUrl == null ? "" : replyUrl);
        if (out.contains(EXTERNAL)) out = out.replace(EXTERNAL, externalUrl == null ? "" : externalUrl);
        return out;
    }

    private String get(String key) {
        return settings.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void put(String key, String value) {
        CrmSetting s = settings.findBySettingKey(key).orElseGet(() -> {
            CrmSetting n = new CrmSetting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        settings.save(s);
    }
}
