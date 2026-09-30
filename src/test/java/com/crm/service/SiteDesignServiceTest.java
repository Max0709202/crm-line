package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 番組デザイン設定 storage: the pre-login HTML is larger than one CRM_SETTING TEXT row. */
class SiteDesignServiceTest {

    private Map<String, CrmSetting> store;
    private SiteDesignService svc;

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
        svc = new SiteDesignService(repo, mock(DomainSettingService.class));
    }

    @Test
    void longHtmlRoundTripsAcrossRowsWithEveryRowUnderTheTextLimit() throws Exception {
        StringBuilder b = new StringBuilder();
        while (b.length() < 14999) b.append("日本語のHTML<p>");
        b.setLength(14999);
        b.append("😀"); // surrogate pair straddling the 15,000-char boundary
        while (b.length() < 52000) b.append("<div>あいうえお</div>");
        String html = b.toString();

        svc.saveTopHtml(html);
        assertThat(svc.getTopHtml()).isEqualTo(html);
        for (Map.Entry<String, CrmSetting> e : store.entrySet()) {
            assertThat(e.getValue().getSettingValue().getBytes("UTF-8").length)
                    .as(e.getKey()).isLessThanOrEqualTo(65535);
        }
        assertThat(store.get("site.top_html.1").getSettingValue()).startsWith("😀");

        // shorter value: leftover rows are emptied, not read back
        svc.saveTopHtml("<html>short</html>");
        assertThat(svc.getTopHtml()).isEqualTo("<html>short</html>");
        assertThat(store.get("site.top_html.2").getSettingValue()).isEmpty();

        // reset → default design
        svc.saveTopHtml(null);
        assertThat(svc.getTopHtml()).isNull();
    }

    @Test
    void oversizedHtmlIsRejected() {
        char[] big = new char[SiteDesignService.MAX_TOP_HTML_CHARS + 1];
        java.util.Arrays.fill(big, 'a');
        assertThatThrownBy(() -> svc.saveTopHtml(new String(big))).isInstanceOf(IllegalArgumentException.class);
        assertThat(svc.getTopHtml()).isNull();
    }

    @Test
    void footerNoteDefaultsAndNormalisesLineEndings() {
        assertThat(svc.getFooterNote()).isEqualTo(SiteDesignService.DEFAULT_FOOTER_NOTE);
        svc.saveFooterNote(" 1行目\r\n2行目 ");
        assertThat(svc.getFooterNote()).isEqualTo("1行目\n2行目");
    }

    @Test
    void memberPageHtmlAreasDefaultAndSave() {
        java.util.List<SiteDesignService.Slot> slots = svc.getSlots();
        assertThat(slots).extracting(SiteDesignService.Slot::getTitle)
                .containsExactly("MENU", "受信BOX", "返信・送信画面", "友達追加リスト", "条件検索",
                        "サポート窓口", "プロフ編集", "ポイント購入", "ポイント表");
        assertThat(slots.get(1).getTopHtml()).isEmpty();
        assertThat(slots.get(1).getBottomHtml()).isEmpty();
        assertThat(slots.get(1).getCss()).isEmpty();

        svc.saveSlot("inbox", "<p>キャンペーン中</p>\r\n", "<p>下</p>", ".c{color:red}\r\n");
        svc.saveSlot("points", null, "<b>x</b>", null);      // null = leave that field as it is
        svc.saveSlot("bogus", "<p>ignored</p>", null, null);  // unknown page → ignored
        SiteDesignService.Slot inbox = svc.getSlots().get(1);
        assertThat(inbox.getTopHtml()).isEqualTo("<p>キャンペーン中</p>\n");
        assertThat(inbox.getBottomHtml()).isEqualTo("<p>下</p>");
        assertThat(inbox.getCss()).isEqualTo(".c{color:red}\n");
        SiteDesignService.Slot points = svc.getSlots().get(7);
        assertThat(points.getTopHtml()).isEmpty();
        assertThat(points.getBottomHtml()).isEqualTo("<b>x</b>");
        assertThat(store.keySet()).noneMatch(k -> k.contains("bogus"));
    }

    @Test
    void memberPageAreaSavedBeforeTheTopBottomSplitIsReadIntoItsPosition() {
        store.put("site.slot.profile.position", setting("site.slot.profile.position", "top"));
        store.put("site.slot.profile.html.count", setting("site.slot.profile.html.count", "1"));
        store.put("site.slot.profile.html.0", setting("site.slot.profile.html.0", "<p>旧</p>"));
        SiteDesignService.Slot profile = svc.getSlots().get(6);
        assertThat(profile.getTopHtml()).isEqualTo("<p>旧</p>");
        assertThat(profile.getBottomHtml()).isEmpty();
    }

    @Test
    void memberPageAreasOverTheLimitAreRejectedWithoutSavingAnything() {
        String html = repeat('あ', SiteDesignService.MAX_SLOT_HTML_CHARS);
        svc.saveSlot("search", html, null, null);                  // exactly at the limit is fine
        assertThat(svc.getSlots().get(4).getTopHtml()).hasSize(SiteDesignService.MAX_SLOT_HTML_CHARS);

        // CRLF counts as one character, as in the browser's maxlength
        String withBreaks = repeat('a', SiteDesignService.MAX_SLOT_CSS_CHARS - 1) + "\r\n";
        svc.saveSlot("search", null, null, withBreaks);
        assertThat(svc.getSlots().get(4).getCss()).hasSize(SiteDesignService.MAX_SLOT_CSS_CHARS);

        assertThatThrownBy(() -> svc.saveSlot("search", "<p>new</p>", html + "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("下部HTML");
        assertThat(svc.getSlots().get(4).getTopHtml()).isEqualTo(html);   // top not saved either
    }

    private static String repeat(char c, int n) {
        char[] a = new char[n];
        java.util.Arrays.fill(a, c);
        return new String(a);
    }

    private static CrmSetting setting(String key, String value) {
        CrmSetting s = new CrmSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        return s;
    }

    @Test
    void slotHtmlIsShownToEveryoneOrOnlyToTheChosenFolders() {
        svc.saveSlot("inbox", "<p>上</p>", "<p>下</p>", null);
        svc.saveSlotFolders("inbox", java.util.Arrays.asList("VIP", "LINE", "VIP", " "), java.util.Collections.emptyList());

        SiteDesignService.Slot inbox = svc.getSlots().get(1);
        assertThat(inbox.getTopFolders()).containsExactly("VIP", "LINE");
        assertThat(inbox.getBottomFolders()).isEmpty();

        assertThat(svc.slotHtmlFor("inbox", "top", "VIP")).isEqualTo("<p>上</p>");
        assertThat(svc.slotHtmlFor("inbox", "top", "一般")).isEmpty();
        assertThat(svc.slotHtmlFor("inbox", "top", null)).isEmpty();
        assertThat(svc.slotHtmlFor("inbox", "bottom", "一般")).isEqualTo("<p>下</p>");   // 全表示

        svc.saveSlotFolders("inbox", null, null);                                        // null = keep
        assertThat(svc.getSlots().get(1).getTopFolders()).containsExactly("VIP", "LINE");
        svc.saveSlotFolders("inbox", java.util.Collections.emptyList(), null);            // back to 全表示
        assertThat(svc.slotHtmlFor("inbox", "top", "一般")).isEqualTo("<p>上</p>");
    }
}
