package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.LineUser;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.LineUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineUserLinkServiceTest {

    private LineUserRepository lineUserRepository;
    private CrmUserRepository crmUserRepository;
    private DomainSettingService domainSettingService;
    private DiffScheduleService diffScheduleService;
    private LineUserLinkService svc;

    @BeforeEach
    void setUp() {
        lineUserRepository = mock(LineUserRepository.class);
        crmUserRepository = mock(CrmUserRepository.class);
        domainSettingService = mock(DomainSettingService.class);
        diffScheduleService = mock(DiffScheduleService.class);
        svc = new LineUserLinkService(lineUserRepository, crmUserRepository, domainSettingService, diffScheduleService);
        when(crmUserRepository.save(any(CrmUser.class))).thenAnswer(inv -> {
            CrmUser u = inv.getArgument(0);
            u.setId(500L);
            return u;
        });
        when(lineUserRepository.save(any(LineUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void autoRegisterAndLink_createsCrmUserWithLineDisplayNameAndLinksIt() {
        LineUser contact = new LineUser();
        contact.setLineAccountId(1L);
        contact.setLineUserId("Unew");
        contact.setLineDisplayName("山田太郎");

        LineUser linked = svc.autoRegisterAndLink(contact);

        ArgumentCaptor<CrmUser> cap = ArgumentCaptor.forClass(CrmUser.class);
        verify(crmUserRepository).save(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("山田太郎");
        assertThat(linked.getCrmUserId()).isEqualTo(500L);
        assertThat(linked.isLinked()).isTrue();
        verify(diffScheduleService).applyRegistrationSteps(cap.getValue(), null);   // 登録後(分後) fires for a new customer
    }

    @Test
    void autoRegisterAndLink_sameLineUserOnAnotherCharacter_joinsExistingCustomerInsteadOfDuplicating() {
        LineUser onFirstChar = new LineUser();
        onFirstChar.setLineAccountId(1L);
        onFirstChar.setLineUserId("Uyutaka");
        onFirstChar.setCrmUserId(50L);
        when(lineUserRepository.findByLineUserIdAndCrmUserIdIsNotNullOrderByIdAsc("Uyutaka"))
                .thenReturn(java.util.Collections.singletonList(onFirstChar));
        when(crmUserRepository.existsById(50L)).thenReturn(true);
        LineUser onSecondChar = new LineUser();
        onSecondChar.setLineAccountId(2L);
        onSecondChar.setLineUserId("Uyutaka");
        onSecondChar.setLineDisplayName("ゆたか");

        LineUser linked = svc.autoRegisterAndLink(onSecondChar);

        assertThat(linked.getCrmUserId()).isEqualTo(50L);
        verify(crmUserRepository, org.mockito.Mockito.never()).save(any(CrmUser.class));
        verify(diffScheduleService, org.mockito.Mockito.never()).applyRegistrationSteps(any(), any());  // not a new registration
    }

    @Test
    void autoRegisterAndLink_appliesConfiguredAutoRegisterFolder() {
        when(domainSettingService.getLineAutoRegisterFolder()).thenReturn("LINE");
        LineUser contact = new LineUser();
        contact.setLineAccountId(1L);
        contact.setLineUserId("Ufolder");

        svc.autoRegisterAndLink(contact);

        ArgumentCaptor<CrmUser> cap = ArgumentCaptor.forClass(CrmUser.class);
        verify(crmUserRepository).save(cap.capture());
        assertThat(cap.getValue().getFolder()).isEqualTo("LINE");
    }

    @Test
    void autoRegisterAndLink_fallsBackToDefaultNameWhenLineHasNone() {
        LineUser contact = new LineUser();
        contact.setLineAccountId(1L);
        contact.setLineUserId("Unoname");

        svc.autoRegisterAndLink(contact);

        ArgumentCaptor<CrmUser> cap = ArgumentCaptor.forClass(CrmUser.class);
        verify(crmUserRepository).save(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("LINE友だち");
    }

    @Test
    void link_attachesExistingCrmUserToLineUser() {
        LineUser existing = new LineUser();
        existing.setId(10L);
        when(lineUserRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(crmUserRepository.existsById(42L)).thenReturn(true);

        LineUser result = svc.link(10L, 42L);

        assertThat(result.getCrmUserId()).isEqualTo(42L);
    }

    @Test
    void link_rejectsUnknownCrmUserId() {
        LineUser existing = new LineUser();
        existing.setId(10L);
        when(lineUserRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(crmUserRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> svc.link(10L, 999L))
                .isInstanceOf(LineUserLinkService.CrmUserNotFoundException.class);
    }

    @Test
    void suggestMatches_proposesExactlyOneCandidateOnUniqueNameMatch() {
        LineUser unlinked = new LineUser();
        unlinked.setId(20L);
        unlinked.setLineDisplayName("鈴木一郎");
        when(lineUserRepository.findByCrmUserIdIsNullOrderByLastMessageAtDesc())
                .thenReturn(java.util.Collections.singletonList(unlinked));
        CrmUser match = new CrmUser();
        match.setId(77L);
        match.setDisplayName("鈴木一郎");
        when(crmUserRepository.findByDisplayNameIgnoreCase("鈴木一郎"))
                .thenReturn(java.util.Collections.singletonList(match));

        java.util.List<LineUserLinkService.MatchCandidate> result = svc.suggestMatches();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLineUser().getId()).isEqualTo(20L);
        assertThat(result.get(0).getCrmUser().getId()).isEqualTo(77L);
    }

    @Test
    void suggestMatches_skipsWhenMultipleOrNoCandidates() {
        LineUser ambiguous = new LineUser();
        ambiguous.setId(21L);
        ambiguous.setLineDisplayName("田中");
        LineUser noMatch = new LineUser();
        noMatch.setId(22L);
        noMatch.setLineDisplayName("誰でもない");
        when(lineUserRepository.findByCrmUserIdIsNullOrderByLastMessageAtDesc())
                .thenReturn(java.util.Arrays.asList(ambiguous, noMatch));
        CrmUser a = new CrmUser(); a.setId(1L); a.setDisplayName("田中");
        CrmUser b = new CrmUser(); b.setId(2L); b.setDisplayName("田中");
        when(crmUserRepository.findByDisplayNameIgnoreCase("田中")).thenReturn(java.util.Arrays.asList(a, b));
        when(crmUserRepository.findByDisplayNameIgnoreCase("誰でもない")).thenReturn(java.util.Collections.emptyList());

        assertThat(svc.suggestMatches()).isEmpty();
    }

    @Test
    void linkAll_linksEachPairAndToleratesOneBadEntry() {
        LineUser u1 = new LineUser(); u1.setId(30L);
        LineUser u2 = new LineUser(); u2.setId(31L);
        when(lineUserRepository.findById(30L)).thenReturn(Optional.of(u1));
        when(lineUserRepository.findById(31L)).thenReturn(Optional.of(u2));
        when(crmUserRepository.existsById(100L)).thenReturn(true);
        when(crmUserRepository.existsById(999L)).thenReturn(false);

        java.util.Map<Long, Long> pairs = new java.util.LinkedHashMap<>();
        pairs.put(30L, 100L);
        pairs.put(31L, 999L);

        int n = svc.linkAll(pairs);

        assertThat(n).isEqualTo(1);
        assertThat(u1.getCrmUserId()).isEqualTo(100L);
        assertThat(u2.getCrmUserId()).isNull();
    }

    @Test
    void deleteUnlinkedByIds_deletesOnlyUnlinkedRows() {
        LineUser unlinked = new LineUser(); unlinked.setId(40L);
        LineUser linked = new LineUser(); linked.setId(41L); linked.setCrmUserId(9L);
        when(lineUserRepository.findById(40L)).thenReturn(Optional.of(unlinked));
        when(lineUserRepository.findById(41L)).thenReturn(Optional.of(linked));
        when(lineUserRepository.findById(42L)).thenReturn(Optional.empty());

        int n = svc.deleteUnlinkedByIds(java.util.Arrays.asList(40L, 41L, 42L, null));

        assertThat(n).isEqualTo(1);
        verify(lineUserRepository).delete(unlinked);
        org.mockito.Mockito.verify(lineUserRepository, org.mockito.Mockito.never()).delete(linked);
    }
}
