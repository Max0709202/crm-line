package com.crm.service;

import com.crm.controller.MemberSiteController;

import com.crm.entity.Chara;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 会員ページ (MemberSiteController): each page renders in the スマホ and ガラケー designs with the member's data. */
class MemberSiteControllerTest {

    private MemberSiteService site;
    private MemberSiteController controller;
    private MockHttpSession session;
    private final CrmUser user = new CrmUser();
    private final Chara mai = new Chara();

    @BeforeEach
    void setUp() {
        user.setId(5L);
        user.setLoginId("10005");
        user.setDisplayName("なおと<b>");
        user.setEmail("naoto@example.jp");
        user.setStatus(CrmUser.STATUS_ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(mai, "id", 2L);
        mai.setName("まい");
        mai.setAge(25);
        mai.setPref("東京都");
        mai.setPhotoUrl("/img/9");

        site = mock(MemberSiteService.class);
        when(site.member(5L)).thenReturn(Optional.of(user));
        when(site.points(user)).thenReturn(1200);
        when(site.unreadCount(user)).thenReturn(1);
        when(site.cost(anyString(), eq(user))).thenReturn(20);
        when(site.offered(anyString(), eq(user))).thenReturn(true);
        when(site.chara(2L)).thenReturn(Optional.of(mai));
        when(site.friends(user)).thenReturn(Collections.singletonList(mai));
        Message out = new Message();
        out.setId(70L);
        out.setUserId(5L);
        out.setDirection(Message.DIR_OUT);
        out.setSubject("こんばんは");
        out.setBodyText("今日は寒いですね\nhttps://example.jp/reply/x");
        out.setSentAt(LocalDateTime.now());
        Message in = new Message();
        in.setId(71L);
        in.setUserId(5L);
        in.setDirection(Message.DIR_IN);
        in.setBodyText("本当ですね <script>");
        in.setCreatedAt(LocalDateTime.now());
        when(site.inbox(eq(user), anyString(), any())).thenReturn(Arrays.asList(
                new MemberSiteService.InboxItem(out, mai, true, false), new MemberSiteService.InboxItem(out, null, false, false)));
        when(site.inboxGroups(eq(user), anyString(), any())).thenReturn(Arrays.asList(
                new MemberSiteService.InboxGroup(mai, out, 1), new MemberSiteService.InboxGroup(null, out, 0)));
        Message read = new Message();
        read.setId(69L);
        read.setUserId(5L);
        read.setDirection(Message.DIR_OUT);
        read.setSubject("おはよう");
        read.setBodyText("昨日はありがとう");
        read.setSentAt(LocalDateTime.now().minusHours(3));
        when(site.charaInbox(user, 2L)).thenReturn(Arrays.asList(
                new MemberSiteService.InboxItem(out, mai, true, false), new MemberSiteService.InboxItem(read, mai, false, false)));
        when(site.previewLength()).thenReturn(15);
        Map<Long, String> senders = new LinkedHashMap<>();
        senders.put(2L, "まい");
        when(site.pastSenders(user)).thenReturn(senders);
        when(site.conversation(user, 2L)).thenReturn(Arrays.asList(
                new MemberSiteService.ConvItem(out, true, false, Collections.singletonList(40L)),
                new MemberSiteService.ConvItem(in, false, true, Collections.<Long>emptyList())));
        when(site.openImages(eq(user), any())).thenReturn(Collections.<Long>emptySet());
        when(site.isOpen(eq(user), anyString(), anyLong())).thenReturn(false);

        SiteDesignService design = mock(SiteDesignService.class);
        List<SiteDesignService.Slot> slots = new ArrayList<>();
        for (String code : SiteDesignService.MEMBER_PAGES.keySet()) {
            slots.add(new SiteDesignService.Slot(code, code, "", "", "", Collections.<String>emptyList(), Collections.<String>emptyList()));
        }
        when(design.getSlots()).thenReturn(slots);
        when(design.getConfiguredSiteName()).thenReturn("サイト");
        when(design.slotHtmlFor(anyString(), anyString(), any())).thenReturn("");
        PaymentSettingService pay = mock(PaymentSettingService.class);
        when(pay.getMethods(any())).thenReturn(Collections.<PaymentSettingService.Method>emptyList());
        PointSettingService pointSettings = mock(PointSettingService.class);
        when(pointSettings.listShown(any())).thenReturn(Collections.<PointSettingService.Row>emptyList());
        UserProfileService profiles = mock(UserProfileService.class);
        when(profiles.get(5L)).thenReturn(new com.crm.entity.UserProfile());
        controller = new MemberSiteController(site, new MemberPageService(design, pay), design, mock(PublicSiteService.class),
                pay, pointSettings, mock(TelecomCreditService.class), profiles, mock(MessageImageService.class),
                mock(HtmlImageService.class), mock(LoginThrottleService.class));
        session = new MockHttpSession();
        session.setAttribute("memberUserId", 5L);
    }

    private MockHttpServletRequest req(boolean fp) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("_csrf", "TOKEN");
        r.addHeader("User-Agent", fp ? "DoCoMo/2.0 P903i(c100;TB;W24H12)" : "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");
        return r;
    }

    @SuppressWarnings("unchecked")
    private static String body(Object res) {
        assertThat(res).isInstanceOf(ResponseEntity.class);
        return ((ResponseEntity<String>) res).getBody();
    }

    @Test
    void everyPage_rendersInBothDesigns_withEscapedData() {
        for (boolean fp : new boolean[]{false, true}) {
            // 受信BOX: one row per キャラ — 未読あり(n) / 未読なし in place of the age, 受信一覧
            String inbox = body(controller.inbox("all", null, 1, req(fp), session, new ExtendedModelMap()));
            assertThat(inbox).contains("まい", "未読あり(1)", "未読なし", "NEW", "/member/inbox/list?c=2", "/member/inbox/list?c=0",
                    "サポート窓口", "こんばんは", "今日は寒いですね", "なおと&lt;b&gt;")
                    .doesNotContain("なおと<b>", "25歳", "運営", "https://example.jp/reply/x", "本文閲覧/返信");
            if (!fp) assertThat(inbox).contains("プロフィール閲覧", ">受信一覧<").doesNotContain("プロフィール参照");
            // 送信済み: no age, 受信一覧 instead of やり取りを見る
            String sent = body(controller.inbox("sent", null, 1, req(fp), session, new ExtendedModelMap()));
            assertThat(sent).contains("To: ", "/member/inbox/list?c=2").doesNotContain("25歳", "やり取りを見る");
            // 受信一覧: received only, newest first, 未読 / 既読 with the envelope icons, each → 返信画面
            String list = body(controller.inboxList(2L, 1, req(fp), session));
            assertThat(list).contains("受信一覧", "未読あり(1)", "mail-unread.svg", "mail-read.svg", ">未読<", ">既読<",
                    "/member/reply?c=2&amp;m=70#m70", "/member/reply?c=2&amp;m=69#m69", "おはよう");
            assertThat(list.indexOf("m=70")).isLessThan(list.indexOf("m=69"));
            String reply = body(controller.reply(2L, null, req(fp), session, new ExtendedModelMap()));
            assertThat(reply).contains("本文閲覧（20pt）", "📎", "name=\"_csrf\" value=\"TOKEN\"", "action=\"/member/reply\"",
                    "本当ですね &lt;script&gt;").doesNotContain("今日は寒いですね", "<script>\"");
            assertThat(body(controller.charaProfile(2L, req(fp), session, new ExtendedModelMap()))).contains("プロフィールを見る（20pt）");
            assertThat(body(controller.charaPhoto(2L, req(fp), session, new ExtendedModelMap()))).contains("写真を見る（20pt）");
            assertThat(body(controller.friends(req(fp), session, new ExtendedModelMap()))).contains("まい", "東京都").doesNotContain("25歳");
            assertThat(body(controller.search(req(fp), session))).contains("name=\"pref\"", "東京都");
            assertThat(body(controller.support(req(fp), session, new ExtendedModelMap()))).contains("naoto@example.jp");
            assertThat(body(controller.profile(req(fp), session, new ExtendedModelMap()))).contains("name=\"nickname\"", "なおと&lt;b&gt;");
            assertThat(body(controller.points(null, req(fp), session, new ExtendedModelMap()))).contains("現在購入できるプランはありません");
            body(controller.pointTable(req(fp), session));
            body(controller.menu(req(fp), session));
        }
    }

    @Test
    void notLoggedIn_goesToTheLoginDialog() {
        assertThat(controller.inbox("all", null, 1, req(false), new MockHttpSession(), new ExtendedModelMap())).isEqualTo("redirect:/#login");
        assertThat(controller.inboxList(2L, 1, req(false), new MockHttpSession())).isEqualTo("redirect:/#login");
    }

    @Test
    void openingOneMail_marksOnlyThatMailRead() {
        controller.reply(2L, 70L, req(false), session, new ExtendedModelMap());
        org.mockito.Mockito.verify(site).markReadIfFree(eq(user), any(), eq(70L));
    }

    @Test
    void inboxList_pagesByTen() {
        List<MemberSiteService.InboxItem> many = new ArrayList<>();
        for (long i = 0; i < 11; i++) {
            Message m = new Message();
            m.setId(100L + i);
            m.setUserId(5L);
            m.setDirection(Message.DIR_OUT);
            m.setSubject("件名" + i);
            m.setBodyText("本文");
            m.setSentAt(LocalDateTime.now());
            many.add(new MemberSiteService.InboxItem(m, mai, false, false));
        }
        when(site.charaInbox(user, 2L)).thenReturn(many);
        String p1 = body(controller.inboxList(2L, 1, req(false), session));
        assertThat(p1).contains("m=109", "page=2").doesNotContain("m=110");
        // 1ページ目は「次へ」だけ、2ページ目以降は「戻る」と「次へ」(ページ番号なし)
        assertThat(p1).contains("次へ").doesNotContain("戻る", "前へ");
        String p2 = body(controller.inboxList(2L, 2, req(false), session));
        assertThat(p2).contains("m=110").doesNotContain("m=109");
        assertThat(p2).contains("戻る", "page=1").doesNotContain("前へ");
    }

    @Test
    void replyFromInboxList_showsOnlyTheClickedMail_withPlainAttachRows() {
        for (boolean fp : new boolean[]{false, true}) {
            String reply = body(controller.reply(2L, 70L, req(fp), session, new ExtendedModelMap()));
            assertThat(reply).contains("id=\"m70\"", "写真添付", "アドレス添付", "電話番号添付", "name=\"m\" value=\"70\"",
                    "font-size:inherit").doesNotContain("id=\"m71\"", "本当ですね", "アドレスを添付", "電話番号を添付", "20ポイント）</span>");
            assertThat(reply.indexOf("写真添付")).isLessThan(reply.indexOf("アドレス添付"));
            assertThat(reply.indexOf("アドレス添付")).isLessThan(reply.indexOf("電話番号添付"));
        }
    }

    @Test
    void afterSending_goesToSentWithTheNote() {
        org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap ra = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        Object res = controller.send(2L, 70L, "件名", "本文", false, false, null, req(false), session, ra);
        assertThat(res).isEqualTo("redirect:/member/inbox?tab=sent");
        assertThat(ra.getFlashAttributes().get("memberNote")).isEqualTo("メッセージを送信しました");
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("memberNote", "メッセージを送信しました");
        assertThat(body(controller.inbox("sent", null, 1, req(false), session, model))).contains("メッセージを送信しました");
    }

    @Test
    void supportMail_isOpenedWithoutPoints_withTheSupportFormBelow() {
        Message sup = new Message();
        sup.setId(80L);
        sup.setUserId(5L);
        sup.setDirection(Message.DIR_OUT);
        sup.setSubject("お知らせ");
        sup.setBodyText("サポートからのご案内");
        sup.setSentAt(LocalDateTime.now());
        List<MemberSiteService.ConvItem> conv = Collections.singletonList(new MemberSiteService.ConvItem(sup, true, true, Collections.<Long>emptyList()));
        when(site.conversation(user, 0L)).thenReturn(conv);
        for (boolean fp : new boolean[]{false, true}) {
            String page = body(controller.reply(0L, 80L, req(fp), session, new ExtendedModelMap()));
            assertThat(page).contains("サポートからのご案内", "action=\"/member/support\"", "name=\"back\" value=\"/member/reply?c=0&amp;m=80\"",
                    "お問い合わせ内容").doesNotContain("本文閲覧（", "action=\"/member/reply\"");
            assertThat(page.indexOf("サポートからのご案内")).isLessThan(page.indexOf("action=\"/member/support\""));
        }
        org.mockito.Mockito.verify(site, org.mockito.Mockito.atLeastOnce()).openSupportMails(eq(user), any(), eq(80L));
        org.mockito.Mockito.verify(site, org.mockito.Mockito.never()).markReadIfFree(eq(user), any(), eq(80L));
    }

    @Test
    void friends_listsWithoutFriendAddedLabel() {
        assertThat(body(controller.friends(req(false), session, new ExtendedModelMap()))).contains("まい", "メッセージ送信").doesNotContain("友達追加済");
    }

    @Test
    void featurePhonePages_allHaveTheSameViewport() {
        String vp = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">";
        assertThat(body(controller.menu(req(true), session))).contains(vp);
        assertThat(body(controller.inboxList(2L, 1, req(true), session))).contains(vp);
        assertThat(body(controller.friends(req(true), session, new ExtendedModelMap()))).contains(vp);
        assertThat(body(controller.reply(2L, 70L, req(true), session, new ExtendedModelMap()))).contains(vp);
    }
}
