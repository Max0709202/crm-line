package com.crm.service;

import com.crm.dto.PaymentForm;
import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.entity.TelecomOrder;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentPointRepository;
import com.crm.repository.TelecomOrderRepository;
import com.crm.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 決済関連設定 › テレコムクレジット: 決済データ受け取り. */
class TelecomCreditServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private TelecomOrderRepository orders;
    private PaymentService paymentService;
    private UserPointService points;
    private MailTemplateService mail;
    private TelecomCreditService svc;
    private TelecomOrder order;

    @BeforeEach
    void setUp() {
        CrmSettingRepository repo = mock(CrmSettingRepository.class);
        when(repo.findBySettingKey(anyString())).thenAnswer(inv -> {
            String v = store.get(inv.<String>getArgument(0));
            if (v == null) return Optional.empty();
            CrmSetting s = new CrmSetting();
            s.setSettingValue(v);
            return Optional.of(s);
        });
        store.put("payment.telecom.enabled", "true");
        store.put("payment.telecom.clientip", "12345");
        orders = mock(TelecomOrderRepository.class);
        order = new TelecomOrder();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 7L);
        order.setUserId(3L);
        order.setMethod("credit");
        order.setAmount(3000);
        order.setPoints(300);
        order.setStatus(TelecomOrder.STATUS_PENDING);
        when(orders.findForUpdate(7L)).thenReturn(Optional.of(order));
        when(orders.findBySettleUuid(anyString())).thenReturn(Optional.empty());
        CrmUserRepository users = mock(CrmUserRepository.class);
        CrmUser u = new CrmUser();
        u.setId(3L);
        when(users.findById(3L)).thenReturn(Optional.of(u));
        paymentService = mock(PaymentService.class);
        when(paymentService.create(any(PaymentForm.class))).thenAnswer(inv -> {
            Payment p = new Payment();
            p.setId(55L);
            p.setAmount(((PaymentForm) inv.getArgument(0)).getAmount());
            p.setStatus(Payment.STATUS_PAID);
            p.setPaidAt(LocalDateTime.now());
            return p;
        });
        PaymentSettingService paymentSettings = mock(PaymentSettingService.class);
        when(paymentSettings.getMethods(any())).thenReturn(Collections.emptyList());
        points = mock(UserPointService.class);
        mail = mock(MailTemplateService.class);
        svc = new TelecomCreditService(repo, orders, users, paymentService, mock(PaymentPointRepository.class),
                paymentSettings, points, mail, mock(AesEncryptionUtil.class));
    }

    private Map<String, String> notice(String rel, String money) {
        Map<String, String> p = new HashMap<>();
        p.put("clientip", "12345");
        p.put("sendid", "3");
        p.put("money", money);
        p.put("rel", rel);
        p.put("option", "7");
        p.put("settle_uuid", "uuid-1");
        p.put("telno", "09012345678");
        return p;
    }

    @Test
    void paidNotice_addsThePoints_recordsThePayment_andSendsTheMail() {
        svc.handleNotice(notice("yes", "3000"));
        verify(points).add(3L, 300);
        verify(paymentService).create(any(PaymentForm.class));
        verify(mail).sendPayment(any(CrmUser.class), eq(BigDecimal.valueOf(3000)), eq(300), anyString(), any(), eq(55L));
        assertThat(order.getStatus()).isEqualTo(TelecomOrder.STATUS_PAID);
        assertThat(order.getSettleUuid()).isEqualTo("uuid-1");
        assertThat(order.getResultParams()).contains("telno=***").doesNotContain("09012345678");
    }

    @Test
    void resentNotice_ofAPaidOrder_addsNothing() {
        order.setStatus(TelecomOrder.STATUS_PAID);
        svc.handleNotice(notice("yes", "3000"));
        verify(points, never()).add(anyLong(), anyInt());
        verify(paymentService, never()).create(any());
    }

    @Test
    void failedPayment_orWrongAmount_addsNothing() {
        svc.handleNotice(notice("no", "3000"));
        assertThat(order.getStatus()).isEqualTo(TelecomOrder.STATUS_FAILED);
        order.setStatus(TelecomOrder.STATUS_PENDING);
        svc.handleNotice(notice("yes", "1000"));
        assertThat(order.getStatus()).isEqualTo(TelecomOrder.STATUS_FAILED);
        verify(points, never()).add(anyLong(), anyInt());
    }

    @Test
    void anotherSitesNotice_isIgnored() {
        Map<String, String> p = notice("yes", "3000");
        p.put("clientip", "99999");
        svc.handleNotice(p);
        verify(points, never()).add(anyLong(), anyInt());
        assertThat(order.getStatus()).isEqualTo(TelecomOrder.STATUS_PENDING);
    }

    @Test
    void onlyTelecomServerIps_byDefault() {
        assertThat(svc.isServerIp("54.65.177.67")).isTrue();
        assertThat(svc.isServerIp("203.0.113.5")).isFalse();
    }
}
