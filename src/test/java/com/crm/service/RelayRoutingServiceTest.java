package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.entity.RelayRoute;
import com.crm.entity.RelayServer;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentRepository;
import com.crm.repository.RelayRouteRepository;
import com.crm.repository.RelayServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** リレーサーバー設定 — which relay a recipient's mail goes through. */
class RelayRoutingServiceTest {

    private RelayServerRepository relayRepo;
    private RelayRouteRepository routeRepo;
    private CrmUserRepository userRepo;
    private PaymentRepository paymentRepo;
    private RelayRoutingService svc;
    private final List<RelayServer> relays = new ArrayList<>();
    private final List<RelayRoute> routes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        relayRepo = mock(RelayServerRepository.class);
        routeRepo = mock(RelayRouteRepository.class);
        userRepo = mock(CrmUserRepository.class);
        paymentRepo = mock(PaymentRepository.class);
        when(relayRepo.findAll()).thenReturn(relays);
        when(routeRepo.findAll()).thenReturn(routes);
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        svc = new RelayRoutingService(relayRepo, routeRepo, userRepo, paymentRepo,
                mock(DomainSettingService.class), "http://133.88.116.190/api/send");
    }

    private RelayServer relay(long id, String name, boolean active, int priority, String folders, Integer min, Integer max) {
        RelayServer r = new RelayServer();
        r.setId(id);
        r.setName(name);
        r.setIpAddress("10.0.0." + id);
        r.setPort(25);
        r.setIsActive(active);
        relays.add(r);
        RelayRoute rt = new RelayRoute();
        rt.setRelayId(id);
        rt.setPriority(priority);
        rt.setFolders(folders);
        rt.setPayMin(min);
        rt.setPayMax(max);
        routes.add(rt);
        return r;
    }

    private void user(String email, long id, String folder, long paid) {
        CrmUser u = new CrmUser();
        u.setId(id);
        u.setEmail(email);
        u.setFolder(folder);
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(u));
        when(paymentRepo.countByUserIdAndStatus(id, Payment.STATUS_PAID)).thenReturn(paid);
    }

    @Test
    void noActiveRelay_localPostfix() {
        relay(1, "A", false, 0, null, null, null);
        assertThat(svc.pickFor("x@example.com")).isNull();
    }

    @Test
    void firstMatchingRelayInPriorityOrderWins() {
        RelayServer all = relay(1, "全対象", true, 2, null, null, null);
        RelayServer vip = relay(2, "VIP", true, 0, "[\"VIP\"]", null, null);
        RelayServer first = relay(3, "初回", true, 1, null, 1, 1);
        user("vip@example.com", 10, "VIP", 5);
        user("new@example.com", 11, "一般", 1);
        user("other@example.com", 12, "一般", 3);

        assertThat(svc.pickFor("vip@example.com")).isSameAs(vip);
        assertThat(svc.pickFor("new@example.com")).isSameAs(first);
        assertThat(svc.pickFor("other@example.com")).isSameAs(all);
    }

    @Test
    void inactiveRelayIsSkipped() {
        relay(1, "VIP", false, 0, "[\"VIP\"]", null, null);
        RelayServer all = relay(2, "全対象", true, 1, null, null, null);
        user("vip@example.com", 10, "VIP", 0);
        assertThat(svc.pickFor("vip@example.com")).isSameAs(all);
    }

    @Test
    void nothingMatches_localPostfix() {
        relay(1, "VIP", true, 0, "[\"VIP\"]", null, null);
        relay(2, "入金5回以上", true, 1, null, 5, null);
        user("u@example.com", 10, "一般", 2);
        assertThat(svc.pickFor("u@example.com")).isNull();
    }

    @Test
    void unknownRecipient_countsAsNoFolderAndZeroPayments() {
        relay(1, "VIP", true, 0, "[\"VIP\"]", null, null);
        RelayServer unpaid = relay(2, "未入金", true, 1, null, 0, 0);
        assertThat(svc.pickFor("someone@example.com")).isSameAs(unpaid);
    }

    @Test
    void save_rejectsBadInput() {
        RelayRoutingService.Input in = new RelayRoutingService.Input();
        in.name = "";
        in.ip = "1.2.3.4";
        assertThatThrownBy(() -> svc.save(null, in)).hasMessageContaining("名前");
        in.name = "A";
        in.ip = "999.1.1.1";
        assertThatThrownBy(() -> svc.save(null, in)).hasMessageContaining("IPアドレス");
        in.ip = "1.2.3.4";
        in.payMin = "5";
        in.payMax = "2";
        assertThatThrownBy(() -> svc.save(null, in)).hasMessageContaining("入金回数");
        in.payMin = null;
        in.payMax = null;
        in.port = "70000";
        assertThatThrownBy(() -> svc.save(null, in)).hasMessageContaining("ポート");
    }

    @Test
    void bridgeHost_isTheRelayUrlHost() {
        assertThat(svc.bridgeHost()).isEqualTo("133.88.116.190");
        assertThat(Arrays.asList(svc.bridgeHost())).doesNotContainNull();
    }
}
