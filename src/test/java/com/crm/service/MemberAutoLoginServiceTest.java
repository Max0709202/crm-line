package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.MemberAutoLogin;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.MemberAutoLoginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 自動ログインURL — which tokens log a member in. */
class MemberAutoLoginServiceTest {

    private static final String TOKEN = "a123456789012345678901234567890123456789012345678901234567890bcd";

    private MemberAutoLoginRepository repo;
    private CrmUserRepository userRepo;
    private MemberAutoLoginService svc;

    @BeforeEach
    void setUp() {
        repo = mock(MemberAutoLoginRepository.class);
        userRepo = mock(CrmUserRepository.class);
        DomainSettingService domain = mock(DomainSettingService.class);
        when(domain.getReplyBaseUrl()).thenReturn("https://example.jp/");
        svc = new MemberAutoLoginService(repo, userRepo, domain);
        when(repo.findByToken(any())).thenReturn(Optional.empty());
    }

    private CrmUser user(long id, String status) {
        CrmUser u = new CrmUser();
        u.setId(id);
        u.setStatus(status);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));
        MemberAutoLogin a = new MemberAutoLogin();
        a.setUserId(id);
        a.setToken(TOKEN);
        when(repo.findByToken(TOKEN)).thenReturn(Optional.of(a));
        when(repo.findById(id)).thenReturn(Optional.of(a));
        return u;
    }

    @Test
    void activeMember_isLoggedIn() {
        CrmUser u = user(5, CrmUser.STATUS_ACTIVE);
        assertThat(svc.resolve(TOKEN)).contains(u);
    }

    @Test
    void pendingMember_isNot() {
        user(5, CrmUser.STATUS_PENDING);
        assertThat(svc.resolve(TOKEN)).isEmpty();
    }

    @Test
    void unknownOrMalformedToken_isRejected() {
        assertThat(svc.resolve(null)).isEmpty();
        assertThat(svc.resolve("short")).isEmpty();
        assertThat(svc.resolve(TOKEN)).isEmpty();
    }

    @Test
    void url_usesTheStoredToken() {
        CrmUser u = user(5, CrmUser.STATUS_ACTIVE);
        assertThat(svc.urlFor(u)).isEqualTo("https://example.jp/member/auto-login?t=" + TOKEN);
    }
}
