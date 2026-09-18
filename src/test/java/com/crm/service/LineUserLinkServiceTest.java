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
    private LineUserLinkService svc;

    @BeforeEach
    void setUp() {
        lineUserRepository = mock(LineUserRepository.class);
        crmUserRepository = mock(CrmUserRepository.class);
        svc = new LineUserLinkService(lineUserRepository, crmUserRepository);
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
}
