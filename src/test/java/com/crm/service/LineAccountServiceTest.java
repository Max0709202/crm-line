package com.crm.service;

import com.crm.dto.LineAccountForm;
import com.crm.entity.LineAccount;
import com.crm.line.LineApiClient;
import com.crm.line.dto.LineBotInfoResponse;
import com.crm.repository.LineAccountRepository;
import com.crm.repository.LineUserRepository;
import com.crm.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineAccountServiceTest {

    private LineAccountRepository repo;
    private AesEncryptionUtil aes;
    private LineApiClient lineApiClient;
    private LineUserRepository lineUserRepository;
    private LineAccountService svc;

    @BeforeEach
    void setUp() {
        repo = mock(LineAccountRepository.class);
        aes = mock(AesEncryptionUtil.class);
        lineApiClient = mock(LineApiClient.class);
        lineUserRepository = mock(LineUserRepository.class);
        when(aes.encrypt(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        when(aes.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.startsWith("ENC(") ? s.substring(4, s.length() - 1) : s;
        });
        when(repo.save(any(LineAccount.class))).thenAnswer(inv -> {
            LineAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(99L);
            return a;
        });
        svc = new LineAccountService(repo, aes, lineApiClient, lineUserRepository);
    }

    private static LineAccountForm form(Long parentId, String channelId, String secret, String token) {
        LineAccountForm f = new LineAccountForm();
        f.setParentAccountId(parentId);
        f.setName("テストアカウント");
        f.setChannelId(channelId);
        f.setChannelSecret(secret);
        f.setAccessToken(token);
        return f;
    }

    private static LineAccount parent(Long id) {
        LineAccount a = new LineAccount();
        a.setId(id);
        a.setParentAccountId(null);
        a.setName("親");
        a.setChannelId("parent-channel-" + id);
        a.setChannelSecret("ENC(secret)");
        a.setAccessToken("ENC(token)");
        return a;
    }

    @Test
    void create_encryptsSecretAndToken() {
        when(repo.existsByChannelId(anyString())).thenReturn(false);
        when(repo.existsByWebhookToken(anyString())).thenReturn(false);

        LineAccount saved = svc.create(form(null, "ch1", "plain-secret", "plain-token"));

        assertThat(saved.getChannelSecret()).isEqualTo("ENC(plain-secret)");
        assertThat(saved.getAccessToken()).isEqualTo("ENC(plain-token)");
        assertThat(saved.getStatus()).isEqualTo(LineAccount.STATUS_UNUSED);
        assertThat(saved.getWebhookToken()).isNotBlank();
    }

    @Test
    void create_duplicateChannelId_throws() {
        when(repo.existsByChannelId("dup")).thenReturn(true);
        assertThatThrownBy(() -> svc.create(form(null, "dup", "s", "t")))
                .isInstanceOf(LineAccountService.DuplicateChannelIdException.class);
        verify(repo, never()).save(any(LineAccount.class));
    }

    @Test
    void create_missingSecret_throws() {
        assertThatThrownBy(() -> svc.create(form(null, "ch1", "", "t")))
                .isInstanceOf(LineAccountService.MissingCredentialException.class);
    }

    @Test
    void create_parentThatIsItselfAChild_rejected() {
        LineAccount grandchildAttempt = new LineAccount();
        grandchildAttempt.setId(5L);
        grandchildAttempt.setParentAccountId(1L); // this "parent" is itself a child
        when(repo.findById(5L)).thenReturn(Optional.of(grandchildAttempt));

        assertThatThrownBy(() -> svc.create(form(5L, "ch1", "s", "t")))
                .isInstanceOf(LineAccountService.InvalidParentException.class);
        verify(repo, never()).save(any(LineAccount.class));
    }

    @Test
    void create_nonExistentParent_rejected() {
        when(repo.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.create(form(999L, "ch1", "s", "t")))
                .isInstanceOf(LineAccountService.InvalidParentException.class);
    }

    @Test
    void update_blankSecretAndToken_keepsExisting() {
        LineAccount existing = parent(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.existsByChannelId(anyString())).thenReturn(false);

        LineAccountForm f = form(null, "parent-channel-1", "", "");
        LineAccount saved = svc.update(1L, f);

        assertThat(saved.getChannelSecret()).isEqualTo("ENC(secret)");
        assertThat(saved.getAccessToken()).isEqualTo("ENC(token)");
    }

    @Test
    void update_nonBlankSecret_reEncrypts() {
        LineAccount existing = parent(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.existsByChannelId(anyString())).thenReturn(false);

        LineAccountForm f = form(null, "parent-channel-1", "new-secret", "");
        LineAccount saved = svc.update(1L, f);

        assertThat(saved.getChannelSecret()).isEqualTo("ENC(new-secret)");
        assertThat(saved.getAccessToken()).isEqualTo("ENC(token)"); // token untouched
    }

    @Test
    void delete_withChildren_refuses() {
        when(repo.findById(1L)).thenReturn(Optional.of(parent(1L)));
        when(repo.countByParentAccountId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> svc.delete(1L))
                .isInstanceOf(LineAccountService.HasChildrenException.class);
        verify(repo, never()).delete(any(LineAccount.class));
    }

    @Test
    void delete_withNoChildren_succeeds() {
        LineAccount a = parent(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(repo.countByParentAccountId(1L)).thenReturn(0L);

        svc.delete(1L);

        verify(repo).delete(a);
    }

    @Test
    void checkConnection_success_flipsToActive() {
        LineAccount a = parent(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(lineApiClient.getBotInfo("token")).thenReturn(new LineBotInfoResponse());

        LineAccount result = svc.checkConnection(1L);

        assertThat(result.getStatus()).isEqualTo(LineAccount.STATUS_ACTIVE);
        assertThat(result.getLastConnectionCheckAt()).isNotNull();
    }

    @Test
    void checkConnection_failure_flipsToError() {
        LineAccount a = parent(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(a));
        when(lineApiClient.getBotInfo("token")).thenReturn(null);

        LineAccount result = svc.checkConnection(1L);

        assertThat(result.getStatus()).isEqualTo(LineAccount.STATUS_ERROR);
    }

    @Test
    void listParents_delegatesToRepository() {
        when(repo.findByParentAccountIdIsNullOrderByNameAsc()).thenReturn(Collections.singletonList(parent(1L)));
        assertThat(svc.listParents()).hasSize(1);
    }

    // ---- resolveDynamicLinkedAccountIds ----

    @Test
    void resolveDynamicLinkedAccountIds_picksLowestPriorityAccountWhenLinkedToMultiple() {
        com.crm.entity.LineUser toLowPriority = new com.crm.entity.LineUser();
        toLowPriority.setCrmUserId(1L);
        toLowPriority.setLineAccountId(10L);
        com.crm.entity.LineUser toHighPriority = new com.crm.entity.LineUser();
        toHighPriority.setCrmUserId(1L);
        toHighPriority.setLineAccountId(20L);
        // Ordered by lastMessageAt DESC as the real repository call would return — the
        // most-recently-active link (account 20) comes first, but it should still lose to
        // account 10's better (lower) priority.
        when(lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(Collections.singletonList(1L)))
                .thenReturn(java.util.Arrays.asList(toHighPriority, toLowPriority));

        LineAccount account10 = parent(10L);
        account10.setLinkagePriority(5);
        LineAccount account20 = parent(20L);
        account20.setLinkagePriority(50);
        when(repo.findAllById(any())).thenReturn(java.util.Arrays.asList(account10, account20));

        java.util.Map<Long, Long> result = svc.resolveDynamicLinkedAccountIds(Collections.singletonList(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(1L)).isEqualTo(10L);
    }

    @Test
    void resolveDynamicLinkedAccountIds_tieBreaksByMostRecentlyActiveWhenPrioritiesEqual() {
        com.crm.entity.LineUser mostRecent = new com.crm.entity.LineUser();
        mostRecent.setCrmUserId(1L);
        mostRecent.setLineAccountId(20L);
        com.crm.entity.LineUser older = new com.crm.entity.LineUser();
        older.setCrmUserId(1L);
        older.setLineAccountId(10L);
        when(lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(Collections.singletonList(1L)))
                .thenReturn(java.util.Arrays.asList(mostRecent, older));

        LineAccount account10 = parent(10L);
        account10.setLinkagePriority(50);
        LineAccount account20 = parent(20L);
        account20.setLinkagePriority(50);
        when(repo.findAllById(any())).thenReturn(java.util.Arrays.asList(account10, account20));

        java.util.Map<Long, Long> result = svc.resolveDynamicLinkedAccountIds(Collections.singletonList(1L));

        assertThat(result.get(1L)).isEqualTo(20L);
    }

    @Test
    void resolveDynamicLinkedAccountIds_omitsUsersWithNoLink() {
        when(lineUserRepository.findByCrmUserIdInOrderByLastMessageAtDesc(Collections.singletonList(1L)))
                .thenReturn(Collections.emptyList());

        assertThat(svc.resolveDynamicLinkedAccountIds(Collections.singletonList(1L))).isEmpty();
    }
}
