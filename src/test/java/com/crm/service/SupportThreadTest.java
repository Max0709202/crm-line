package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.SupportInquiry;
import com.crm.entity.SupportReply;
import com.crm.repository.CharaRepository;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.MessageRepository;
import com.crm.repository.SupportInquiryRepository;
import com.crm.repository.SupportReplyRepository;
import com.crm.repository.SupportTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** サポート窓口 のやり取り in the 会員ページ (2026-10-18 client request). */
class SupportThreadTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 18, 12, 0);

    private SupportInquiryRepository inquiries;
    private SupportReplyRepository replies;
    private MemberUnlockService unlocks;
    private MemberSiteService site;
    private CrmUser u;

    @BeforeEach
    void setUp() {
        inquiries = mock(SupportInquiryRepository.class);
        replies = mock(SupportReplyRepository.class);
        unlocks = mock(MemberUnlockService.class);
        site = new MemberSiteService(mock(CrmUserRepository.class), mock(MessageRepository.class), mock(CharaRepository.class),
                mock(CharaLinkService.class), mock(PointSettingService.class), mock(UserPointService.class), unlocks,
                mock(MessageImageService.class), mock(UserProfileService.class), inquiries, mock(ReplyAttachmentService.class),
                mock(UserActivityService.class), mock(PasswordEncoder.class));
        site.setSupportReplyRepository(replies);
        u = new CrmUser();
        u.setId(5L);
        SupportInquiry q = inquiry(3L, SupportInquiry.CHANNEL_WEB, 5L, "");
        q.setSubject("会員ページ サポート窓口（LINE）");
        q.setBody("ポイントが反映されません");
        q.setReceivedAt(T);
        when(inquiries.findByMemberIdAndChannelOrderByReceivedAtAsc(5L, SupportInquiry.CHANNEL_WEB))
                .thenReturn(Collections.singletonList(q));
        when(replies.findByInquiryIdInOrderByCreatedAtAsc(anyCollection())).thenReturn(Arrays.asList(
                reply(7L, 3L, SupportReply.STATUS_SENT, "Re: お問い合わせ", "確認しました https://example.jp/pay", T.plusHours(1)),
                reply(8L, 3L, SupportReply.STATUS_FAILED, "x", "failed send is not shown", T.plusHours(2))));
    }

    @Test
    void inquiryAndSentReplyAreTheSupportThreadOfTheInbox() {
        List<MemberSiteService.InboxGroup> groups = site.inboxGroups(u, "all", null);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).chara).isNull();                      // サポート窓口 (no キャラ)
        assertThat(groups.get(0).unread).isEqualTo(1);
        assertThat(groups.get(0).latest.getBodyText()).isEqualTo("確認しました https://example.jp/pay");
        assertThat(site.unreadCount(u)).isEqualTo(1);
        assertThat(site.pastSenders(u)).containsEntry(0L, MemberSiteService.SUPPORT_NAME);

        List<MemberSiteService.ConvItem> conv = site.conversation(u, 0L);
        assertThat(conv).extracting(c -> c.out).containsExactly(false, true);   // 送信 then 受信
        assertThat(conv.get(0).message.getBodyText()).isEqualTo("ポイントが反映されません");
        assertThat(conv).allMatch(c -> c.message.getId() < 0);
        // a reply's URL stays (not a 返信URL)
        assertThat(MemberSiteService.displayBody(conv.get(1).message)).contains("https://example.jp/pay");

        // 送信済み: the member's お問い合わせ
        assertThat(site.inbox(u, "sent", null)).extracting(it -> it.message.getBodyText()).containsExactly("ポイントが反映されません");
        // a キャラ's やり取り gets nothing of it
        assertThat(site.conversation(u, 12L)).isEmpty();
    }

    @Test
    void unreadRepliesUntilSeen() {
        assertThat(site.unreadSupportReplies(u)).containsExactly(-14L);
        when(unlocks.unlocked(eq(5L), eq(MemberUnlockService.BODY), anyCollection())).thenReturn(Collections.singleton(-14L));
        assertThat(site.unreadSupportReplies(u)).isEmpty();
        assertThat(site.inboxGroups(u, "all", null).get(0).unread).isZero();
        site.markSupportRepliesRead(u, Collections.singleton(-14L));
        verify(unlocks).unlock(5L, MemberUnlockService.BODY, -14L, 0);
    }

    @Test
    void mailInquiriesAreNotShown() {
        when(inquiries.findByMemberIdAndChannelOrderByReceivedAtAsc(5L, SupportInquiry.CHANNEL_WEB)).thenReturn(Collections.<SupportInquiry>emptyList());
        assertThat(site.inboxGroups(u, "all", null)).isEmpty();
        assertThat(site.supportHistory(u)).isEmpty();
    }

    /* ---------- 管理画面 サポート窓口 → 返信 ---------- */

    private SupportDeskService desk(LocalPostfixOutboundMailService mail, SupportInquiry q) {
        SupportInquiryRepository repo = mock(SupportInquiryRepository.class);
        when(repo.findAllById(any())).thenReturn(Collections.singletonList(q));
        DomainSettingService domains = mock(DomainSettingService.class);
        when(domains.getReplyBaseUrl()).thenReturn("https://b32kd.com/");
        CrmSettingRepository settings = mock(CrmSettingRepository.class);
        when(settings.findBySettingKey(anyString())).thenReturn(java.util.Optional.empty());
        return new SupportDeskService(repo, mock(SupportReplyRepository.class), mock(SupportTemplateRepository.class),
                mock(CrmUserRepository.class), domains, mock(SiteDesignService.class), mail, settings);
    }

    @Test
    void webInquiryOfAMemberWithoutAddressIsRepliedToTheMemberPage() {
        LocalPostfixOutboundMailService mail = mock(LocalPostfixOutboundMailService.class);
        SupportDeskService.ReplyOutcome out = desk(mail, inquiry(3L, SupportInquiry.CHANNEL_WEB, 5L, ""))
                .reply(Collections.singletonList(3L), null, "確認しました", true, "staff");
        assertThat(out.sentIds).containsExactly(3L);
        assertThat(out.failed).isEmpty();
        verify(mail, never()).send(any());
    }

    @Test
    void webInquiryOfAMemberStaysSentWhenTheMailFails() {
        LocalPostfixOutboundMailService mail = mock(LocalPostfixOutboundMailService.class);
        when(mail.send(any())).thenReturn(OutboundMailService.SendResult.fail("refused"));
        SupportDeskService.ReplyOutcome out = desk(mail, inquiry(3L, SupportInquiry.CHANNEL_WEB, 5L, "a@docomo.ne.jp"))
                .reply(Collections.singletonList(3L), null, "確認しました", false, "staff");
        assertThat(out.sentIds).containsExactly(3L);
        verify(mail).send(any());
    }

    @Test
    void mailInquiryStillFailsWhenTheMailFails() {
        LocalPostfixOutboundMailService mail = mock(LocalPostfixOutboundMailService.class);
        when(mail.send(any())).thenReturn(OutboundMailService.SendResult.fail("refused"));
        SupportDeskService.ReplyOutcome out = desk(mail, inquiry(3L, SupportInquiry.CHANNEL_MAIL, 5L, "a@docomo.ne.jp"))
                .reply(Collections.singletonList(3L), null, "確認しました", false, "staff");
        assertThat(out.sentIds).isEmpty();
        assertThat(out.failed).containsKey(3L);
    }

    /* ---------- 件名なしで返信 = 件名なし (2026-10-18) ---------- */

    private String savedSubject(SupportInquiry q, String subject) {
        SupportInquiryRepository repo = mock(SupportInquiryRepository.class);
        when(repo.findAllById(any())).thenReturn(Collections.singletonList(q));
        SupportReplyRepository saved = mock(SupportReplyRepository.class);
        DomainSettingService domains = mock(DomainSettingService.class);
        when(domains.getReplyBaseUrl()).thenReturn("https://b32kd.com/");
        CrmSettingRepository settings = mock(CrmSettingRepository.class);
        when(settings.findBySettingKey(anyString())).thenReturn(java.util.Optional.empty());
        LocalPostfixOutboundMailService mail = mock(LocalPostfixOutboundMailService.class);
        when(mail.send(any())).thenReturn(OutboundMailService.SendResult.ok());
        new SupportDeskService(repo, saved, mock(SupportTemplateRepository.class), mock(CrmUserRepository.class),
                domains, mock(SiteDesignService.class), mail, settings).reply(Collections.singletonList(q.getId()), subject, "確認しました", false, "staff");
        org.mockito.ArgumentCaptor<SupportReply> c = org.mockito.ArgumentCaptor.forClass(SupportReply.class);
        verify(saved).save(c.capture());
        return c.getValue().getSubject();
    }

    @Test
    void replyToTheMemberPageWithoutSubjectHasNoSubject() {
        assertThat(savedSubject(inquiry(3L, SupportInquiry.CHANNEL_WEB, 5L, ""), "  ")).isEmpty();
        assertThat(savedSubject(inquiry(3L, SupportInquiry.CHANNEL_WEB, 5L, ""), "ご確認")).isEqualTo("ご確認");
        // メールのお問い合わせ: as before
        assertThat(savedSubject(inquiry(3L, SupportInquiry.CHANNEL_MAIL, null, "a@docomo.ne.jp"), null)).isEqualTo("Re: 件名");
    }

    @Test
    void earlierAutoSubjectIsNotShownAndTheAdminThreadGetsTheExchange() {
        when(replies.findByInquiryIdInOrderByCreatedAtAsc(anyCollection())).thenReturn(Arrays.asList(
                reply(7L, 3L, SupportReply.STATUS_SENT, "Re: 会員ページ サポート窓口（LINE）", "確認しました", T.plusHours(1)),
                reply(9L, 3L, SupportReply.STATUS_SENT, "ポイントについて", "反映しました", T.plusHours(2))));
        List<Message> thread = site.supportThreadMessages(u);
        assertThat(thread).extracting(Message::getDirection).containsExactly(Message.DIR_IN, Message.DIR_OUT, Message.DIR_OUT);
        assertThat(thread.get(1).getSubject()).isNull();
        assertThat(thread.get(2).getSubject()).isEqualTo("ポイントについて");
        assertThat(thread).allMatch(MemberSiteService::isSupportThreadMessage);
    }

    /* ---------- NEW on the サポート窓口 links ---------- */

    @Test
    void supportNewMarksEachSupportLink() {
        assertThat(MemberPageService.supportNew("<a class=\"tile\" href=\"/member/support\"><span class=\"i\">🎧</span><b>サポート窓口</b></a>"))
                .contains("<span class=\"badge\">NEW</span><span class=\"i\">");
        assertThat(MemberPageService.supportNew("<a href=\"/member/support\">4. サポート窓口</a>")).contains("サポート窓口 <span").contains(">NEW</span></a>");
        assertThat(MemberPageService.supportNew("<a href=\"/member/support\"><span class=\"bi\" style=\"line-height:1.1\">🎧</span>サポート窓口</a>"))
                .contains("🎧<i class=\"fb\"").contains(">NEW</i></span>サポート窓口");
    }

    private static SupportInquiry inquiry(long id, String channel, Long memberId, String email) {
        SupportInquiry q = new SupportInquiry();
        ReflectionTestUtils.setField(q, "id", id);
        q.setChannel(channel);
        q.setMemberId(memberId);
        q.setEmail(email);
        q.setName("会員");
        q.setSubject("件名");
        q.setBody("本文");
        q.setReceivedAt(T);
        return q;
    }

    private static SupportReply reply(long id, long inquiryId, String status, String subject, String body, LocalDateTime at) {
        SupportReply r = new SupportReply();
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "createdAt", at);
        r.setInquiryId(inquiryId);
        r.setStatus(status);
        r.setSubject(subject);
        r.setBody(body);
        return r;
    }
}
