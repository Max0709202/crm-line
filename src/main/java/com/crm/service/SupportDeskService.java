package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.SupportInquiry;
import com.crm.entity.SupportReply;
import com.crm.entity.SupportTemplate;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.SupportInquiryRepository;
import com.crm.repository.SupportReplyRepository;
import com.crm.repository.SupportTemplateRepository;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * サポート窓口 (client request 2026-10-02): inquiries mailed to the support address (EC宛メール)
 * or sent from the site's WEB form, answered by mail from the same address.
 *
 * The support address is {@code support@<main domain>}, the main domain being the host of
 * ドメイン設定's reply base URL — so it follows the domain when the real one is set there. Mail
 * to the site's お問い合わせ address (番組デザイン設定) is accepted too.
 */
@Service
public class SupportDeskService {

    private static final Logger log = LoggerFactory.getLogger(SupportDeskService.class);

    /** The first N templates always exist; only ones added after them can be deleted. */
    public static final int DEFAULT_TEMPLATES = 10;
    public static final int TEMPLATE_TITLE_MAX = 40;
    public static final int SUBJECT_MAX = 500;
    public static final int BODY_MAX = 60_000;
    public static final int SENDER_NAME_MAX = 100;
    /** 送信者名: the From display name of replies (blank = the address only). */
    private static final String KEY_SENDER_NAME = "support.sender_name";

    private final SupportInquiryRepository inquiryRepository;
    private final SupportReplyRepository replyRepository;
    private final SupportTemplateRepository templateRepository;
    private final CrmUserRepository userRepository;
    private final DomainSettingService domainSettingService;
    private final SiteDesignService siteDesignService;
    private final LocalPostfixOutboundMailService mailService;
    private final CrmSettingRepository settingRepository;

    public SupportDeskService(SupportInquiryRepository inquiryRepository,
                              SupportReplyRepository replyRepository,
                              SupportTemplateRepository templateRepository,
                              CrmUserRepository userRepository,
                              DomainSettingService domainSettingService,
                              SiteDesignService siteDesignService,
                              LocalPostfixOutboundMailService mailService,
                              CrmSettingRepository settingRepository) {
        this.inquiryRepository = inquiryRepository;
        this.replyRepository = replyRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.domainSettingService = domainSettingService;
        this.siteDesignService = siteDesignService;
        this.mailService = mailService;
        this.settingRepository = settingRepository;
    }

    /* ===================== address ===================== */

    /** {@code support@<main domain>}, or null while ドメイン設定 has no reply base URL. */
    public String supportAddress() {
        String base = domainSettingService.getReplyBaseUrl();
        if (base == null || base.trim().isEmpty()) return null;
        try {
            String host = URI.create(base.trim()).getHost();
            return (host == null || host.isEmpty()) ? null : "support@" + host.toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 送信者名 saved on サポート窓口 ("" when unset). */
    public String senderName() {
        return settingRepository.findBySettingKey(KEY_SENDER_NAME)
                .map(CrmSetting::getSettingValue).map(String::trim).orElse("");
    }

    public void saveSenderName(String name) {
        String v = name == null ? "" : name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (v.length() > SENDER_NAME_MAX) v = v.substring(0, SENDER_NAME_MAX);
        CrmSetting s = settingRepository.findBySettingKey(KEY_SENDER_NAME).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(KEY_SENDER_NAME);
            return ns;
        });
        s.setSettingValue(v);
        settingRepository.save(s);
    }

    /** The From of a reply: {@code "送信者名" <address>} (MIME-encoded), or the address alone. */
    private String fromWithName(String address) {
        String name = senderName();
        if (name.isEmpty()) return address;
        try {
            return MimeUtility.encodeWord(name, "UTF-8", "B") + " <" + address + ">";
        } catch (UnsupportedEncodingException e) {
            return address;
        }
    }

    /** True when mail sent to {@code toAddr} belongs to the サポート窓口. */
    public boolean isSupportAddress(String toAddr) {
        if (toAddr == null || toAddr.trim().isEmpty()) return false;
        String to = toAddr.trim();
        String support = supportAddress();
        if (support != null && support.equalsIgnoreCase(to)) return true;
        String contact = siteDesignService.getContactEmail();
        return contact != null && contact.trim().equalsIgnoreCase(to);
    }

    /* ===================== inbound ===================== */

    /**
     * Stores one mail to the support address. Returns false when it was already stored
     * (same dedup key as the rest of the inbound pipeline).
     */
    @Transactional
    public boolean receiveMail(String fromAddr, String fromHeaderName, String toAddr,
                               String subject, String body, String dedupKey) {
        if (dedupKey != null && inquiryRepository.existsByMessageIdHeader(dedupKey)) return false;
        SupportInquiry q = new SupportInquiry();
        q.setChannel(SupportInquiry.CHANNEL_MAIL);
        q.setEmail(truncate(fromAddr, 255));
        q.setToAddress(truncate(toAddr, 255));
        q.setSubject(truncate(blankToDefault(subject, "(件名なし)"), SUBJECT_MAX));
        q.setBody(body == null ? "" : body);
        q.setMessageIdHeader(truncate(dedupKey, 512));
        CrmUser member = findMember(fromAddr);
        if (member != null) q.setMemberId(member.getId());
        String name = fromHeaderName;
        if (isBlank(name) && member != null) name = member.getDisplayName();
        if (isBlank(name)) name = localPart(fromAddr);
        q.setName(truncate(name, 255));
        inquiryRepository.save(q);
        log.info("Support inquiry stored: id={} from={} to={}", q.getId(), LogSafe.of(fromAddr), LogSafe.of(toAddr));
        return true;
    }

    private CrmUser findMember(String email) {
        if (isBlank(email)) return null;
        try {
            return userRepository.findByEmail(email.trim()).orElse(null);
        } catch (RuntimeException e) {
            // Several users share the address: leave the inquiry unlinked rather than guess.
            return null;
        }
    }

    /* ===================== log ===================== */

    public List<SupportInquiry> listInquiries() {
        return inquiryRepository.findAllByOrderByReceivedAtDesc();
    }

    /** Sent replies per inquiry id, oldest first (failed sends are not shown in the thread). */
    public Map<Long, List<SupportReply>> repliesByInquiry(Collection<Long> inquiryIds) {
        Map<Long, List<SupportReply>> out = new LinkedHashMap<>();
        if (inquiryIds == null || inquiryIds.isEmpty()) return out;
        for (SupportReply r : replyRepository.findByInquiryIdInOrderByCreatedAtAsc(inquiryIds)) {
            if (!SupportReply.STATUS_SENT.equals(r.getStatus())) continue;
            out.computeIfAbsent(r.getInquiryId(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    @Transactional
    public boolean setStatus(Long id, String status) {
        if (!SupportInquiry.STATUS_OPEN.equals(status) && !SupportInquiry.STATUS_DONE.equals(status)) return false;
        SupportInquiry q = inquiryRepository.findById(id).orElse(null);
        if (q == null) return false;
        q.setStatus(status);
        inquiryRepository.save(q);
        return true;
    }

    @Transactional
    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<SupportInquiry> found = inquiryRepository.findAllById(ids);
        if (found.isEmpty()) return 0;
        List<Long> foundIds = new ArrayList<>();
        for (SupportInquiry q : found) foundIds.add(q.getId());
        replyRepository.deleteByInquiryIdIn(foundIds);
        inquiryRepository.deleteAll(found);
        return found.size();
    }

    @Transactional
    public int saveTags(List<Long> ids, String amount, String product, String fullAddress, String dateJp) {
        if (ids == null || ids.isEmpty()) return 0;
        List<SupportInquiry> found = inquiryRepository.findAllById(ids);
        for (SupportInquiry q : found) {
            q.setTagAmount(truncate(trimToNull(amount), 255));
            q.setTagProduct(truncate(trimToNull(product), 255));
            q.setTagFullAddress(truncate(trimToNull(fullAddress), 500));
            q.setTagDateJp(truncate(trimToNull(dateJp), 100));
        }
        inquiryRepository.saveAll(found);
        return found.size();
    }

    /* ===================== reply ===================== */

    public static final class ReplyOutcome {
        public final List<Long> sentIds = new ArrayList<>();
        public final Map<Long, String> failed = new LinkedHashMap<>();
    }

    /**
     * Mails the reply to every inquiry in {@code ids} from the support address, filling the
     * placeholders per inquiry. Each send is recorded (SENT / FAILED); successful ones are
     * marked 対応済 when {@code markDone}.
     */
    @Transactional
    public ReplyOutcome reply(List<Long> ids, String subject, String body, boolean markDone, String staffName) {
        ReplyOutcome out = new ReplyOutcome();
        if (ids == null || ids.isEmpty()) return out;
        String from = supportAddress();
        for (SupportInquiry q : inquiryRepository.findAllById(ids)) {
            String subj = isBlank(subject) ? "Re: " + blankToDefault(q.getSubject(), "") : subject.trim();
            String filledSubject = truncate(fill(subj, q), SUBJECT_MAX);
            String filledBody = fill(body, q);
            SupportReply r = new SupportReply();
            r.setInquiryId(q.getId());
            r.setStaffName(truncate(staffName, 255));
            r.setSubject(filledSubject);
            r.setBody(filledBody);
            String error = null;
            if (from == null) {
                error = "受付アドレスが未設定です（ドメイン設定の返信URLを設定してください）";
            } else {
                OutboundMailService.SendResult res = mailService.send(new OutboundMailService.OutboundRequest(
                        fromWithName(from), q.getEmail(), filledSubject, filledBody, "127.0.0.1", 25, null, null));
                if (!res.success) error = res.errorMessage == null ? "送信に失敗しました" : res.errorMessage;
            }
            if (error == null) {
                r.setStatus(SupportReply.STATUS_SENT);
                out.sentIds.add(q.getId());
                if (markDone && !SupportInquiry.STATUS_DONE.equals(q.getStatus())) {
                    q.setStatus(SupportInquiry.STATUS_DONE);
                    inquiryRepository.save(q);
                }
            } else {
                r.setStatus(SupportReply.STATUS_FAILED);
                r.setErrorMessage(truncate(error, 1000));
                out.failed.put(q.getId(), error);
            }
            replyRepository.save(r);
        }
        return out;
    }

    /** %name% / %email% / %date_jp% plus the inquiry's saved %amount% / %product% / %full_address%. */
    public String fill(String text, SupportInquiry q) {
        if (text == null) return "";
        String dateJp = isBlank(q.getTagDateJp()) ? todayJp() : q.getTagDateJp();
        return text.replace("%name%", nz(q.getName()))
                .replace("%email%", nz(q.getEmail()))
                .replace("%amount%", isBlank(q.getTagAmount()) ? "%amount%" : q.getTagAmount())
                .replace("%product%", isBlank(q.getTagProduct()) ? "%product%" : q.getTagProduct())
                .replace("%full_address%", isBlank(q.getTagFullAddress()) ? "%full_address%" : q.getTagFullAddress())
                .replace("%date_jp%", dateJp);
    }

    static String todayJp() {
        LocalDate d = LocalDate.now();
        return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    /* ===================== templates ===================== */

    /** Templates in display order; the first visit creates the {@link #DEFAULT_TEMPLATES} empty ones. */
    @Transactional
    public List<SupportTemplate> templates() {
        List<SupportTemplate> list = templateRepository.findAllByOrderBySortOrderAscIdAsc();
        if (!list.isEmpty()) return list;
        List<SupportTemplate> created = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_TEMPLATES; i++) {
            SupportTemplate t = new SupportTemplate();
            t.setSortOrder(i);
            t.setTitle("");
            t.setBody("");
            created.add(t);
        }
        return templateRepository.saveAll(created);
    }

    /** Creates (id null) or updates a template; returns it, or null when the id is unknown. */
    @Transactional
    public SupportTemplate saveTemplate(Long id, String title, String body) {
        SupportTemplate t;
        if (id == null) {
            int maxOrder = 0;
            for (SupportTemplate x : templateRepository.findAll()) maxOrder = Math.max(maxOrder, x.getSortOrder());
            t = new SupportTemplate();
            t.setSortOrder(maxOrder + 1);
        } else {
            t = templateRepository.findById(id).orElse(null);
            if (t == null) return null;
        }
        t.setTitle(truncate(title == null ? "" : title.trim(), TEMPLATE_TITLE_MAX));
        t.setBody(truncate(body == null ? "" : body, BODY_MAX));
        return templateRepository.save(t);
    }

    /** Deletes an added template; the first {@link #DEFAULT_TEMPLATES} cannot be deleted. */
    @Transactional
    public boolean deleteTemplate(Long id) {
        List<SupportTemplate> list = templateRepository.findAllByOrderBySortOrderAscIdAsc();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                if (i < DEFAULT_TEMPLATES) return false;
                templateRepository.delete(list.get(i));
                return true;
            }
        }
        return false;
    }

    /* ===================== helpers ===================== */

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String trimToNull(String s) { return isBlank(s) ? null : s.trim(); }
    private static String blankToDefault(String s, String def) { return isBlank(s) ? def : s; }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String localPart(String addr) {
        if (addr == null) return "";
        int at = addr.indexOf('@');
        return at > 0 ? addr.substring(0, at) : addr;
    }

    public static List<Long> parseIds(Collection<String> raw) {
        if (raw == null) return Collections.emptyList();
        List<Long> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
        }
        return out;
    }
}
