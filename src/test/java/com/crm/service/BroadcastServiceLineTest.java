package com.crm.service;

import com.crm.dto.BroadcastForm;
import com.crm.entity.Broadcast;
import com.crm.entity.CrmUser;
import com.crm.entity.LineUser;
import com.crm.entity.Message;
import com.crm.repository.BroadcastRepository;
import com.crm.repository.CarrierAddressPoolRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineUserRepository;
import com.crm.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LINE broadcast targeting is fundamentally different from email/SMS: there's no single
 * "identity" to send from — the operator picks one {@code LineAccount}, and only targets
 * already linked to THAT specific account (via a {@link LineUser} row) are deliverable,
 * since LINE only allows messaging contacts who have already followed the Official Account
 * being sent from.
 */
class BroadcastServiceLineTest {

    private BroadcastRepository broadcastRepo;
    private CrmUserRepository userRepo;
    private CarrierAddressPoolRepository poolRepo;
    private CarrierBindingService bindingService;
    private MessageRepository messageRepo;
    private PlaceholderService placeholderService;
    private ReplyPageService replyPageService;
    private DomainSettingService domainSettingService;
    private ReplyPageSettingService replyPageSettingService;
    private SmsSettingService smsSettingService;
    private LineUserRepository lineUserRepo;
    private BroadcastService svc;

    @BeforeEach
    void setUp() {
        broadcastRepo = mock(BroadcastRepository.class);
        userRepo = mock(CrmUserRepository.class);
        poolRepo = mock(CarrierAddressPoolRepository.class);
        bindingService = mock(CarrierBindingService.class);
        messageRepo = mock(MessageRepository.class);
        placeholderService = mock(PlaceholderService.class);
        replyPageService = mock(ReplyPageService.class);
        domainSettingService = mock(DomainSettingService.class);
        replyPageSettingService = mock(ReplyPageSettingService.class);
        smsSettingService = mock(SmsSettingService.class);
        lineUserRepo = mock(LineUserRepository.class);

        when(replyPageSettingService.getOrCreate()).thenReturn(new com.crm.entity.ReplyPageSetting());
        when(placeholderService.substitute(any(), any(CrmUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(messageRepo.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(broadcastRepo.save(any(Broadcast.class))).thenAnswer(inv -> {
            Broadcast b = inv.getArgument(0);
            if (b.getId() == null) b.setId(77L);
            return b;
        });

        svc = new BroadcastService(broadcastRepo, userRepo, poolRepo, bindingService,
                messageRepo, placeholderService, replyPageService, domainSettingService,
                replyPageSettingService, smsSettingService, lineUserRepo);
    }

    private static CrmUser user(Long id) {
        CrmUser u = new CrmUser();
        u.setId(id);
        return u;
    }

    private static BroadcastForm lineForm(Long lineAccountId, Long... targetIds) {
        BroadcastForm f = new BroadcastForm();
        f.setChannel("LINE");
        f.setLineAccountId(lineAccountId);
        f.setBody("お知らせです");
        f.setTargetUserIds(Arrays.asList(targetIds));
        return f;
    }

    @Test
    void createAndQueueLine_missingLineAccountId_throwsNoTargetsException() {
        BroadcastForm form = lineForm(null, 1L);
        assertThatThrownBy(() -> svc.createAndQueueLine(form, 1L))
                .isInstanceOf(BroadcastService.NoTargetsException.class);
    }

    @Test
    void createAndQueueLine_onlyLinkedUsersAreDeliverable() {
        when(userRepo.findAllById(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(user(1L), user(2L)));

        LineUser linked = new LineUser();
        linked.setCrmUserId(1L);
        linked.setLineUserId("Ulinked1");
        when(lineUserRepo.findByLineAccountIdAndCrmUserIdIn(5L, Arrays.asList(1L, 2L)))
                .thenReturn(Collections.singletonList(linked));

        Broadcast saved = svc.createAndQueueLine(lineForm(5L, 1L, 2L), 1L);

        assertThat(saved.getTotalCount()).isEqualTo(1);
        assertThat(saved.getUnsendableCount()).isEqualTo(1);
        assertThat(saved.getChannel()).isEqualTo("LINE");

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(messageRepo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        Message m = cap.getAllValues().get(0);
        assertThat(m.getUserId()).isEqualTo(1L);
        assertThat(m.getChannel()).isEqualTo(Message.CHANNEL_LINE);
        assertThat(m.getLineAccountId()).isEqualTo(5L);
        assertThat(m.getToAddress()).isEqualTo("Ulinked1");
    }

    @Test
    void createAndQueueLine_noLinkedUsers_throwsNoTargetsException() {
        when(userRepo.findAllById(Arrays.asList(1L))).thenReturn(Collections.singletonList(user(1L)));
        when(lineUserRepo.findByLineAccountIdAndCrmUserIdIn(5L, Arrays.asList(1L)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> svc.createAndQueueLine(lineForm(5L, 1L), 1L))
                .isInstanceOf(BroadcastService.NoTargetsException.class);
    }

    @Test
    void createAndQueue_dispatchesToLineWhenChannelIsLine() {
        when(userRepo.findAllById(Arrays.asList(1L))).thenReturn(Collections.singletonList(user(1L)));
        LineUser linked = new LineUser();
        linked.setCrmUserId(1L);
        linked.setLineUserId("Uabc");
        when(lineUserRepo.findByLineAccountIdAndCrmUserIdIn(anyLong(), any()))
                .thenReturn(Collections.singletonList(linked));

        Broadcast saved = svc.createAndQueue(lineForm(5L, 1L), 1L);

        assertThat(saved.getChannel()).isEqualTo("LINE");
    }
}
