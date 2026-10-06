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
            String inbox = body(controller.inbox("all", null, req(fp), session));
            assertThat(inbox).contains("まい", "NEW", "/member/reply?c=2", "運営", "なおと&lt;b&gt;").doesNotContain("なおと<b>");
            String reply = body(controller.reply(2L, req(fp), session, new ExtendedModelMap()));
            assertThat(reply).contains("本文閲覧（20pt）", "📎", "name=\"_csrf\" value=\"TOKEN\"", "action=\"/member/reply\"",
                    "本当ですね &lt;script&gt;").doesNotContain("今日は寒いですね", "<script>\"");
            assertThat(body(controller.charaProfile(2L, req(fp), session, new ExtendedModelMap()))).contains("プロフィールを見る（20pt）");
            assertThat(body(controller.charaPhoto(2L, req(fp), session, new ExtendedModelMap()))).contains("写真を見る（20pt）");
            assertThat(body(controller.friends(req(fp), session, new ExtendedModelMap()))).contains("まい");
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
        assertThat(controller.inbox("all", null, req(false), new MockHttpSession())).isEqualTo("redirect:/#login");
    }
}
