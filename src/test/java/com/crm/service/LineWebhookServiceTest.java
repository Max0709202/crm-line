package com.crm.service;

import com.crm.entity.LineAccount;
import com.crm.entity.LineUser;
import com.crm.entity.Message;
import com.crm.line.LineApiClient;
import com.crm.line.dto.LineWebhookPayload;
import com.crm.repository.LineAccountRepository;
import com.crm.repository.LineUserRepository;
import com.crm.repository.MessageRepository;
import com.crm.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineWebhookServiceTest {

    private LineAccountRepository lineAccountRepository;
    private LineUserRepository lineUserRepository;
    private MessageRepository messageRepository;
    private AesEncryptionUtil aes;
    private LineApiClient lineApiClient;
    private LineUserLinkService lineUserLinkService;
    private LineAutoReplyService lineAutoReplyService;
    private MessageService messageService;
    private UserActivityService userActivityService;
    private LineWebhookService svc;

    @BeforeEach
    void setUp() {
        lineAccountRepository = mock(LineAccountRepository.class);
        lineUserRepository = mock(LineUserRepository.class);
        messageRepository = mock(MessageRepository.class);
        aes = mock(AesEncryptionUtil.class);
        lineApiClient = mock(LineApiClient.class);
        lineUserLinkService = mock(LineUserLinkService.class);
        lineAutoReplyService = mock(LineAutoReplyService.class);
        messageService = mock(MessageService.class);
        userActivityService = mock(UserActivityService.class);
        when(aes.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(lineUserRepository.save(any(LineUser.class))).thenAnswer(inv -> {
            LineUser u = inv.getArgument(0);
            if (u.getId() == null) u.setId(50L);
            return u;
        });
        // Mirrors LineUserLinkService.autoRegisterAndLink's real effect (a brand-new contact
        // is linked to a fresh CrmUser immediately) without pulling in CrmUserRepository here.
        when(lineUserLinkService.autoRegisterAndLink(any(LineUser.class))).thenAnswer(inv -> {
            LineUser u = inv.getArgument(0);
            u.setCrmUserId(999L);
            return lineUserRepository.save(u);
        });
        svc = new LineWebhookService(lineAccountRepository, lineUserRepository, messageRepository, aes, lineApiClient,
                lineUserLinkService, lineAutoReplyService, messageService, userActivityService);
    }

    private static LineAccount account() {
        LineAccount a = new LineAccount();
        a.setId(1L);
        a.setOfficialAccountId("@test");
        a.setAccessToken("token");
        return a;
    }

    private static LineWebhookPayload.LineEvent textEvent(String eventId, String lineUserId, String text) {
        LineWebhookPayload.LineEvent e = new LineWebhookPayload.LineEvent();
        e.setType("message");
        e.setWebhookEventId(eventId);
        LineWebhookPayload.LineSource src = new LineWebhookPayload.LineSource();
        src.setType("user");
        src.setUserId(lineUserId);
        e.setSource(src);
        LineWebhookPayload.LineMessageContent msg = new LineWebhookPayload.LineMessageContent();
        msg.setType("text");
        msg.setText(text);
        e.setMessage(msg);
        return e;
    }

    private static LineWebhookPayload payloadOf(LineWebhookPayload.LineEvent... events) {
        LineWebhookPayload p = new LineWebhookPayload();
        p.setEvents(Arrays.asList(events));
        return p;
    }

    @Test
    void textFromBrandNewContact_autoRegistersAndCreatesLineMessage() {
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "U123")).thenReturn(Optional.empty());

        svc.process(account(), payloadOf(textEvent("evt1", "U123", "こんにちは")));

        verify(lineUserLinkService).autoRegisterAndLink(any(LineUser.class));
        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(cap.capture());
        Message m = cap.getValue();
        assertThat(m.getUserId()).isEqualTo(999L);
        assertThat(m.getBodyText()).isEqualTo("こんにちは");
    }

    @Test
    void textFromLinkedContact_createsLineMessage() {
        LineUser linked = new LineUser();
        linked.setId(50L);
        linked.setLineAccountId(1L);
        linked.setLineUserId("U456");
        linked.setCrmUserId(999L);
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "U456")).thenReturn(Optional.of(linked));

        svc.process(account(), payloadOf(textEvent("evt2", "U456", "予約したいです")));

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(cap.capture());
        Message m = cap.getValue();
        assertThat(m.getUserId()).isEqualTo(999L);
        assertThat(m.getChannel()).isEqualTo(Message.CHANNEL_LINE);
        assertThat(m.getDirection()).isEqualTo(Message.DIR_IN);
        assertThat(m.getBodyText()).isEqualTo("予約したいです");
        assertThat(m.getLineAccountId()).isEqualTo(1L);
        assertThat(m.getMessageIdHeader()).isEqualTo("line-mo:evt2");
        // LINE activity moves the user up the ログイン順 user list (最終ログイン).
        verify(userActivityService).touchLastLogin(999L);
    }

    @Test
    void duplicateWebhookEventId_isSkipped() {
        LineUser linked = new LineUser();
        linked.setId(50L);
        linked.setCrmUserId(999L);
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "U789")).thenReturn(Optional.of(linked));
        when(messageRepository.existsByMessageIdHeader("line-mo:evt3")).thenReturn(true);

        svc.process(account(), payloadOf(textEvent("evt3", "U789", "重複")));

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void followEvent_autoRegistersLineUser_noMessage() {
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "Ufollow")).thenReturn(Optional.empty());
        LineWebhookPayload.LineEvent follow = new LineWebhookPayload.LineEvent();
        follow.setType("follow");
        LineWebhookPayload.LineSource src = new LineWebhookPayload.LineSource();
        src.setUserId("Ufollow");
        follow.setSource(src);

        svc.process(account(), payloadOf(follow));

        verify(lineUserLinkService).autoRegisterAndLink(any(LineUser.class));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void emptyEvents_skippedGracefully() {
        LineWebhookPayload p = new LineWebhookPayload();
        p.setEvents(Collections.emptyList());
        assertThat(svc.process(account(), p)).hasSize(1);
        assertThat(svc.process(account(), p).get(0).accepted).isFalse();
    }

    @Test
    void nullPayload_skippedGracefully() {
        assertThat(svc.process(account(), null)).hasSize(1);
    }

    @Test
    void unsupportedMessageType_skipped() {
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "Uimg")).thenReturn(Optional.empty());
        LineWebhookPayload.LineEvent e = new LineWebhookPayload.LineEvent();
        e.setType("message");
        LineWebhookPayload.LineSource src = new LineWebhookPayload.LineSource();
        src.setUserId("Uimg");
        e.setSource(src);
        LineWebhookPayload.LineMessageContent msg = new LineWebhookPayload.LineMessageContent();
        msg.setType("image");
        e.setMessage(msg);

        svc.process(account(), payloadOf(e));

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void newFollow_withMatchingFollowRule_sendsAutoReply() {
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "Uwelcome")).thenReturn(Optional.empty());
        com.crm.entity.LineAutoReplyRule rule = new com.crm.entity.LineAutoReplyRule();
        rule.setId(5L);
        rule.setReplyBody("友だち追加ありがとうございます！");
        when(lineAutoReplyService.findFollowMatch(1L)).thenReturn(Optional.of(rule));
        LineWebhookPayload.LineEvent follow = new LineWebhookPayload.LineEvent();
        follow.setType("follow");
        LineWebhookPayload.LineSource src = new LineWebhookPayload.LineSource();
        src.setUserId("Uwelcome");
        follow.setSource(src);

        svc.process(account(), payloadOf(follow));

        ArgumentCaptor<com.crm.dto.LineComposeForm> cap = ArgumentCaptor.forClass(com.crm.dto.LineComposeForm.class);
        verify(messageService).composeLine(org.mockito.ArgumentMatchers.eq(999L), org.mockito.ArgumentMatchers.isNull(), cap.capture(),
                org.mockito.ArgumentMatchers.eq(false));   // 自動応答: sent as written
        assertThat(cap.getValue().getBody()).isEqualTo("友だち追加ありがとうございます！");
        // replies from the character that was friended, not whichever link comes first
        assertThat(cap.getValue().getLineAccountId()).isEqualTo(1L);
    }

    @Test
    void inboundKeywordMatch_sendsAutoReply() {
        LineUser linked = new LineUser();
        linked.setId(50L);
        linked.setLineAccountId(1L);
        linked.setLineUserId("Ukeyword");
        linked.setCrmUserId(777L);
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "Ukeyword")).thenReturn(Optional.of(linked));
        com.crm.entity.LineAutoReplyRule rule = new com.crm.entity.LineAutoReplyRule();
        rule.setId(6L);
        rule.setReplyBody("営業時間は9時〜18時です");
        when(lineAutoReplyService.findKeywordMatch(1L, "営業時間を教えて")).thenReturn(Optional.of(rule));

        svc.process(account(), payloadOf(textEvent("evtkw", "Ukeyword", "営業時間を教えて")));

        ArgumentCaptor<com.crm.dto.LineComposeForm> cap = ArgumentCaptor.forClass(com.crm.dto.LineComposeForm.class);
        verify(messageService).composeLine(org.mockito.ArgumentMatchers.eq(777L), org.mockito.ArgumentMatchers.isNull(), cap.capture(),
                org.mockito.ArgumentMatchers.eq(false));   // 自動応答: sent as written
        assertThat(cap.getValue().getBody()).isEqualTo("営業時間は9時〜18時です");
    }

    @Test
    void inboundNoKeywordMatch_doesNotSendAutoReply() {
        LineUser linked = new LineUser();
        linked.setId(50L);
        linked.setLineAccountId(1L);
        linked.setLineUserId("Uno");
        linked.setCrmUserId(778L);
        when(lineUserRepository.findByLineAccountIdAndLineUserId(1L, "Uno")).thenReturn(Optional.of(linked));

        svc.process(account(), payloadOf(textEvent("evtnokw", "Uno", "こんにちは")));

        verify(messageService, never()).composeLine(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
