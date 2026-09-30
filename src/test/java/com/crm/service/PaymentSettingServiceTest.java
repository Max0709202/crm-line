package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 決済関連設定: defaults from the ガラケー ポイント購入 design, row validation, and 共通 / folder scopes. */
class PaymentSettingServiceTest {

    private Map<String, CrmSetting> store;
    private PaymentSettingService svc;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        CrmSettingRepository repo = mock(CrmSettingRepository.class);
        when(repo.findBySettingKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(repo.save(any(CrmSetting.class))).thenAnswer(inv -> {
            CrmSetting s = inv.getArgument(0);
            store.put(s.getSettingKey(), s);
            return s;
        });
        svc = new PaymentSettingService(repo);
    }

    @Test
    void defaultsMatchThePointPurchaseDesign() {
        List<PaymentSettingService.Method> methods = svc.getMethods(null);
        assertThat(methods).extracting(PaymentSettingService.Method::getLabel)
                .containsExactly("クレジットカード", "銀行振込", "コンビニ決済", "電子マネー");
        PaymentSettingService.Method credit = methods.get(0);
        assertThat(credit.isShown()).isTrue();
        assertThat(credit.getPlans()).hasSize(PaymentSettingService.PLAN_ROWS);
        assertThat(credit.getOfferedPlans()).extracting(PaymentSettingService.Plan::getAmount)
                .containsExactly(1000, 3000, 5000, 10000, 30000);
        assertThat(credit.getOfferedPlans()).extracting(PaymentSettingService.Plan::getPoints)
                .containsExactly(1000, 3000, 5000, 10000, 30000);
    }

    @Test
    void savesShowFlagsAndPlansAndReportsBadRows() {
        Map<String, PaymentSettingService.MethodInput> in = new HashMap<>();
        List<PaymentSettingService.PlanInput> plans = new ArrayList<>();
        plans.add(new PaymentSettingService.PlanInput(true, "2,000", "2200"));  // comma is accepted
        plans.add(new PaymentSettingService.PlanInput(false, "3000", "3000"));  // hidden row
        plans.add(new PaymentSettingService.PlanInput(true, "5000", ""));       // half-filled → rejected, kept
        plans.add(new PaymentSettingService.PlanInput(true, "", ""));           // emptied
        plans.add(new PaymentSettingService.PlanInput(true, "0", "10"));        // 0円 → rejected, kept
        in.put("bank", new PaymentSettingService.MethodInput(true, plans));
        in.put("emoney", new PaymentSettingService.MethodInput(false, new ArrayList<>()));

        List<String> rejected = svc.saveCommon(in);
        assertThat(rejected).containsExactly("銀行振込 3行目", "銀行振込 5行目");

        List<PaymentSettingService.Method> methods = svc.getMethods(null);
        PaymentSettingService.Method bank = methods.get(1);
        assertThat(bank.getPlans().get(0).getAmount()).isEqualTo(2000);
        assertThat(bank.getPlans().get(0).getPoints()).isEqualTo(2200);
        assertThat(bank.getPlans().get(2).getAmount()).isEqualTo(5000);   // kept
        assertThat(bank.getPlans().get(3).isEmpty()).isTrue();
        assertThat(bank.getPlans().get(4).getAmount()).isEqualTo(30000); // kept
        assertThat(bank.getOfferedPlans()).extracting(PaymentSettingService.Plan::getAmount)
                .containsExactly(2000, 5000, 30000);
        // hidden method offers nothing; a method not submitted keeps its values
        assertThat(methods.get(3).isShown()).isFalse();
        assertThat(methods.get(3).getOfferedPlans()).isEmpty();
        assertThat(methods.get(0).getOfferedPlans()).hasSize(5);
    }

    @Test
    void folderUsesCommonUntilItHasItsOwnSettings() {
        Map<String, PaymentSettingService.MethodInput> hideCredit = new HashMap<>();
        hideCredit.put("credit", new PaymentSettingService.MethodInput(false, new ArrayList<>()));
        svc.saveCommon(hideCredit);
        assertThat(svc.hasFolderSettings("A")).isFalse();
        assertThat(svc.getMethods("A").get(0).isShown()).isFalse();

        Map<String, PaymentSettingService.MethodInput> showCredit = new HashMap<>();
        showCredit.put("credit", new PaymentSettingService.MethodInput(true, new ArrayList<>()));
        svc.saveFolder("A", true, showCredit);
        assertThat(svc.hasFolderSettings("A")).isTrue();
        assertThat(svc.getMethods("A").get(0).isShown()).isTrue();
        assertThat(svc.getMethods("B").get(0).isShown()).isFalse();
        assertThat(svc.getMethods(null).get(0).isShown()).isFalse();

        svc.saveFolder("A", false, null);
        assertThat(svc.hasFolderSettings("A")).isFalse();
        assertThat(svc.getMethods("A").get(0).isShown()).isFalse();
    }

    @Test
    void overlongFolderNameIsRejected() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i <= PaymentSettingService.MAX_FOLDER_NAME; i++) name.append('x');
        assertThatThrownBy(() -> svc.saveFolder(name.toString(), true, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(svc.getMethods(name.toString()).get(0).getOfferedPlans()).hasSize(5);
    }
}
