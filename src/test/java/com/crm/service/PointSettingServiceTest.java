package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ポイント設定: defaults from the client's ポイント表, input validation, and the
 *  アドレス添付 / 番号添付 show-hide flags that drive what members see. */
class PointSettingServiceTest {

    private Map<String, CrmSetting> store;
    private PointSettingService svc;

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
        svc = new PointSettingService(repo);
    }

    @Test
    void defaultsMatchTheClientPointTable() {
        assertThat(svc.getCost("mail_send")).isEqualTo(55);
        assertThat(svc.getCost(PointSettingService.CODE_TEL_ATTACH)).isEqualTo(600);
        assertThat(svc.getCost(PointSettingService.CODE_ADDRESS_ATTACH)).isEqualTo(600);
        assertThat(svc.getCost("profile_view")).isEqualTo(10);
        assertThat(svc.getCost("photo_view")).isEqualTo(20);
        assertThat(svc.getCost("search")).isEqualTo(0);
        assertThat(svc.listAll()).hasSize(10);
        // Both attachment options are shown until the operator unticks them.
        assertThat(svc.isShown(PointSettingService.CODE_TEL_ATTACH)).isTrue();
        assertThat(svc.isShown(PointSettingService.CODE_ADDRESS_ATTACH)).isTrue();
        assertThat(svc.listShown()).hasSize(10);
    }

    @Test
    void savedCostsAreReadBack() {
        Map<String, String> costs = new HashMap<>();
        costs.put("mail_send", "70");
        costs.put("photo_view", "1,000");
        List<String> rejected = svc.saveAll(costs, allToggleable());

        assertThat(rejected).isEmpty();
        assertThat(svc.getCost("mail_send")).isEqualTo(70);
        assertThat(svc.getCost("photo_view")).isEqualTo(1000);
        assertThat(svc.getCost("profile_view")).isEqualTo(10); // not submitted → unchanged
    }

    @Test
    void invalidValuesAreRejectedAndKeepTheCurrentCost() {
        Map<String, String> costs = new HashMap<>();
        costs.put("mail_send", "abc");
        costs.put("photo_view", "-5");
        costs.put("profile_view", "");
        List<String> rejected = svc.saveAll(costs, allToggleable());

        assertThat(rejected).containsExactlyInAnyOrder("メール送信", "写真閲覧");
        assertThat(svc.getCost("mail_send")).isEqualTo(55);
        assertThat(svc.getCost("photo_view")).isEqualTo(20);
        assertThat(svc.getCost("profile_view")).isEqualTo(10); // blank is ignored, not an error
    }

    @Test
    void untickedAttachmentIsHiddenFromTheMemberPointTable() {
        svc.saveAll(Collections.emptyMap(),
                Collections.singletonList(PointSettingService.CODE_ADDRESS_ATTACH));

        assertThat(svc.isShown(PointSettingService.CODE_ADDRESS_ATTACH)).isTrue();
        assertThat(svc.isShown(PointSettingService.CODE_TEL_ATTACH)).isFalse();
        List<String> shownLabels = svc.listShown().stream()
                .map(PointSettingService.Row::getLabel).collect(Collectors.toList());
        assertThat(shownLabels).hasSize(9).doesNotContain("メール送信電話番号添付");
        // Items that can't be hidden stay visible even though they weren't in the ticked list.
        assertThat(svc.isShown("mail_send")).isTrue();
    }

    private static List<String> allToggleable() {
        List<String> codes = new ArrayList<>();
        codes.add(PointSettingService.CODE_ADDRESS_ATTACH);
        codes.add(PointSettingService.CODE_TEL_ATTACH);
        return codes;
    }

    @Test
    void initialPointsDefaultToZeroAndRejectInvalidInput() {
        assertThat(svc.getInitialPoints()).isEqualTo(0);
        assertThat(svc.saveInitialPoints("300")).isTrue();
        assertThat(svc.getInitialPoints()).isEqualTo(300);
        assertThat(svc.saveInitialPoints("abc")).isFalse();
        assertThat(svc.saveInitialPoints("")).isTrue(); // blank = unchanged, not an error
        assertThat(svc.getInitialPoints()).isEqualTo(300);
    }

    @Test
    void folderUsesCommonUntilItHasItsOwnSettings() {
        Map<String, String> common = new HashMap<>();
        common.put("mail_send", "70");
        svc.saveAll(common, Collections.singletonList(PointSettingService.CODE_TEL_ATTACH));
        assertThat(svc.hasFolderSettings("A")).isFalse();
        assertThat(svc.getCost("mail_send", "A")).isEqualTo(70);
        assertThat(svc.isShown(PointSettingService.CODE_ADDRESS_ATTACH, "A")).isFalse();

        // own settings start from 共通: blank / invalid values keep the 共通 value
        Map<String, String> costs = new HashMap<>();
        costs.put("mail_send", "30");
        costs.put("photo_view", "abc");
        costs.put("search", "");
        List<String> rejected = svc.saveFolder("A", true, costs,
                Collections.singletonList(PointSettingService.CODE_ADDRESS_ATTACH));
        assertThat(rejected).containsExactly("写真閲覧");
        assertThat(svc.hasFolderSettings("A")).isTrue();
        assertThat(svc.getCost("mail_send", "A")).isEqualTo(30);
        assertThat(svc.getCost("photo_view", "A")).isEqualTo(20);
        assertThat(svc.isShown(PointSettingService.CODE_ADDRESS_ATTACH, "A")).isTrue();
        assertThat(svc.isShown(PointSettingService.CODE_TEL_ATTACH, "A")).isFalse();

        // other folders and 共通 are untouched
        assertThat(svc.getCost("mail_send")).isEqualTo(70);
        assertThat(svc.getCost("mail_send", "B")).isEqualTo(70);
        assertThat(svc.getCost("mail_send", null)).isEqualTo(70);

        // unticking 専用 goes back to 共通
        svc.saveFolder("A", false, new HashMap<>(), null);
        assertThat(svc.hasFolderSettings("A")).isFalse();
        assertThat(svc.getCost("mail_send", "A")).isEqualTo(70);
    }

    @Test
    void unreadableFolderRowFallsBackToCommon() {
        CrmSetting bad = new CrmSetting();
        bad.setSettingKey("point.folder.A");
        bad.setSettingValue("{not json");
        store.put(bad.getSettingKey(), bad);
        assertThat(svc.getCost("mail_send", "A")).isEqualTo(55);
    }
}
