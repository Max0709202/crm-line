package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** セキュリティ設定 › 管理画面IP許可設定. */
class AdminIpServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private AdminIpService svc;

    @BeforeEach
    void setUp() {
        CrmSettingRepository repo = mock(CrmSettingRepository.class);
        when(repo.findBySettingKey(anyString())).thenAnswer(inv -> {
            String v = store.get(inv.<String>getArgument(0));
            if (v == null) return Optional.empty();
            CrmSetting s = new CrmSetting();
            s.setSettingKey(inv.getArgument(0));
            s.setSettingValue(v);
            return Optional.of(s);
        });
        when(repo.save(any(CrmSetting.class))).thenAnswer(inv -> {
            CrmSetting s = inv.getArgument(0);
            store.put(s.getSettingKey(), s.getSettingValue());
            return s;
        });
        svc = new AdminIpService(repo);
    }

    @Test
    void disabled_allowsEveryone() {
        assertThat(svc.isAllowed("198.51.100.7")).isTrue();
    }

    @Test
    void enabled_allowsOnlyListedIpsAndRanges() {
        svc.save(true, Arrays.asList("203.0.113.10", "198.51.100.0/24"), Arrays.asList("本社", "支店"));
        assertThat(svc.isAllowed("203.0.113.10")).isTrue();
        assertThat(svc.isAllowed("198.51.100.200")).isTrue();
        assertThat(svc.isAllowed("203.0.113.11")).isFalse();
        assertThat(svc.isAllowed("198.51.101.1")).isFalse();
        assertThat(svc.isAllowed(null)).isFalse();
        assertThat(svc.list()).extracting(AdminIpService.Entry::getMemo).containsExactly("本社", "支店");
    }

    @Test
    void enabledWithEmptyList_isRefused() {
        assertThatThrownBy(() -> svc.save(true, Collections.<String>emptyList(), Collections.<String>emptyList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void badEntries_areRefused_andNoHostNameIsLookedUp() {
        assertThatThrownBy(() -> svc.save(false, Collections.singletonList("example.com"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.save(false, Collections.singletonList("203.0.113.300"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.save(false, Collections.singletonList("203.0.113.0/33"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ipv6_andIpv4MappedAddresses() {
        assertThat(AdminIpService.covers("2001:db8::/32", "2001:db8:1::5")).isTrue();
        assertThat(AdminIpService.covers("2001:db8::/32", "2001:db9::1")).isFalse();
        assertThat(AdminIpService.covers("203.0.113.0/24", "::ffff:203.0.113.9")).isTrue();
        assertThat(AdminIpService.normalize("203.0.113.10/32")).isEqualTo("203.0.113.10");
    }
}
