package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.MessageSentSubject;
import com.crm.entity.ReplyPageSetting;
import com.crm.repository.MessageSentSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** メールテンプレート設定 › メール通知 (%body%) as the form of a キャラ mail. */
class MailMessageTemplateServiceTest {

    private static final String URL = "https://example.jp/reply/abc";
    private MailTemplateService templates;
    private MessageSentSubjectRepository subjects;
    private MailMessageTemplateService svc;
    private final CrmUser user = new CrmUser();

    @BeforeEach
    void setUp() {
        templates = mock(MailTemplateService.class);
        DomainSettingService domain = mock(DomainSettingService.class);
        when(domain.getEmailReplyUrlClipLength()).thenReturn(10);
        ReplyPageService replyPages = mock(ReplyPageService.class);
        when(replyPages.createReplyPageFor(any())).thenReturn(URL);
        ReplyPageSettingService rps = mock(ReplyPageSettingService.class);
        when(rps.getOrCreate()).thenReturn(new ReplyPageSetting());
        subjects = mock(MessageSentSubjectRepository.class);
        when(subjects.findById(anyLong())).thenReturn(Optional.empty());
        // fill: replace the given tags literally (as MailTemplateService.fill does)
        when(templates.renderMessageTemplate(anyString(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked") Map<String, String> extra = inv.getArgument(2);
            String[] src = templates.messageTemplateSource(inv.<String>getArgument(0));
            String s = src[0], b = src[1];
            for (Map.Entry<String, String> e : extra.entrySet()) { s = s.replace(e.getKey(), e.getValue()); b = b.replace(e.getKey(), e.getValue()); }
            return new String[]{s, b};
        });
        svc = new MailMessageTemplateService(templates, domain, replyPages, rps, subjects);
    }

    private Message apply(String subject, String body) {
        Message m = new Message();
        m.setId(9L);
        svc.apply(m, user, subject, body, "まい");
        return m;
    }

    @Test
    void onlyTheBodyIsClipped_theTemplateTextIsNotCounted() {
        when(templates.messageTemplateSource(MailTemplateService.NOTICE)).thenReturn(new String[]{"【新着】%message_title%",
                "%staff_name%さんからメッセージが届きました。\n%body%\n続きはこちら\n%reply_url%\n※このメールは送信専用です"});
        Message m = apply("こんばんは", "1234567890あいうえお続きの文章\n%reply_url%");
        assertThat(m.getSentBodyText()).isEqualTo("まいさんからメッセージが届きました。\n1234567890\n続きはこちら\n" + URL + "\n※このメールは送信専用です");
        assertThat(m.getBodyText()).isEqualTo("1234567890あいうえお続きの文章\n" + URL);   // 返信画面 shows it all
        ArgumentCaptor<MessageSentSubject> cap = ArgumentCaptor.forClass(MessageSentSubject.class);
        verify(subjects).save(cap.capture());
        assertThat(cap.getValue().getSubject()).isEqualTo("【新着】こんばんは");
    }

    @Test
    void replyUrlOnlyInTheMessage_goesRightAfterTheBody() {
        when(templates.messageTemplateSource(MailTemplateService.NOTICE)).thenReturn(new String[]{"お知らせ", "%body%\n----\n運営"});
        Message m = apply("件名", "短い本文\n%reply_url%");
        assertThat(m.getSentBodyText()).isEqualTo("短い本文\n" + URL + "\n----\n運営");
    }

    @Test
    void supportSend_usesTheSupportTemplate_withItsSenderName() {
        when(templates.messageTemplateSource(MailTemplateService.SUPPORT)).thenReturn(new String[]{"【%staff_name%】%message_title%",
                "%staff_name%からのお知らせです。\n%body%"});
        Message m = new Message();
        m.setId(9L);
        svc.apply(m, user, "メンテナンス", "本日メンテナンスを行います", "サポートデスク", MailTemplateService.SUPPORT);
        assertThat(m.getSentBodyText()).isEqualTo("サポートデスクからのお知らせです。\n本日メンテナンスを行います");
        ArgumentCaptor<MessageSentSubject> cap = ArgumentCaptor.forClass(MessageSentSubject.class);
        verify(subjects).save(cap.capture());
        assertThat(cap.getValue().getSubject()).isEqualTo("【サポートデスク】メンテナンス");
    }

    @Test
    void imagesWithoutAReplyUrl_getOne() {
        List<Long> imgs = Arrays.asList(1L, 2L);
        assertThat(MessageService.withReplyUrlForImages("本文", imgs)).isEqualTo("本文\n%reply_url%");
        assertThat(MessageService.withReplyUrlForImages("本文\n%reply_url%", imgs)).isEqualTo("本文\n%reply_url%");
        assertThat(MessageService.withReplyUrlForImages("本文", Collections.<Long>emptyList())).isEqualTo("本文");
    }
}
