package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.entity.ReplyPageSetting;
import com.crm.repository.CrmSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LINE設定 — 最大文字数を超えた分は返信URL先へ／固定テンプレート. */
class LineTextServiceTest {

    private static final String URL = "https://example.jp/reply/abc";
    private final Map<String, String> store = new HashMap<>();
    private ReplyPageService replyPages;
    private LineTextService svc;
    private final CrmUser user = new CrmUser();

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
        DomainSettingService domain = mock(DomainSettingService.class);
        when(domain.getLineMaxBodyLength()).thenReturn(20);
        replyPages = mock(ReplyPageService.class);
        when(replyPages.createShortReplyPageFor(any())).thenReturn(URL);
        when(replyPages.createReplyPageFor(any())).thenReturn(URL);
        ReplyPageSettingService rps = mock(ReplyPageSettingService.class);
        when(rps.getOrCreate()).thenReturn(new ReplyPageSetting());
        PlaceholderService ph = mock(PlaceholderService.class);
        when(ph.substitute(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        svc = new LineTextService(repo, domain, replyPages, rps, ph);
    }

    private Message apply(String body) {
        Message m = new Message();
        svc.apply(m, user, body);
        return m;
    }

    @Test
    void shortBody_noTemplate_sentAsWritten() {
        Message m = apply("こんにちは");
        assertThat(m.getBodyText()).isEqualTo("こんにちは");
        assertThat(m.getSentBodyText()).isNull();
        verify(replyPages, never()).createShortReplyPageFor(any());
    }

    @Test
    void longBody_sendsFirst20_andTheReplyUrl() {
        String body = "12345678901234567890続きの文章です";
        Message m = apply(body);
        assertThat(m.getBodyText()).isEqualTo(body);   // 返信URL先 shows everything
        assertThat(m.getSentBodyText()).isEqualTo("12345678901234567890\n" + URL);
    }

    @Test
    void template_isAddedBelow_andItsReplyUrlIsNotDuplicated() {
        store.put(LineTextService.KEY_TEMPLATE_ENABLED, "true");
        store.put(LineTextService.KEY_TEMPLATE_TEXT, "続きはこちら\n%reply_url%");
        Message m = apply("12345678901234567890あいう");
        assertThat(m.getSentBodyText()).isEqualTo("12345678901234567890\n続きはこちら\n" + URL);
    }

    @Test
    void template_notCountedTowardTheLimit() {
        store.put(LineTextService.KEY_TEMPLATE_ENABLED, "true");
        store.put(LineTextService.KEY_TEMPLATE_TEXT, "※このメッセージは自動送信です");
        Message m = apply("短い本文");
        assertThat(m.getSentBodyText()).isEqualTo("短い本文\n※このメッセージは自動送信です");
        verify(replyPages, never()).createShortReplyPageFor(any());
    }

    @Test
    void replyUrlInsideTheFirst20_isKeptOnce() {
        // 21 visible characters: the URL (inside the first 20) stays, the 21st goes to the 返信URL page
        Message m = apply("詳細は%reply_url%をご覧くださいませませませませませま");
        assertThat(m.getSentBodyText()).isEqualTo("詳細は\n" + URL + "をご覧くださいませませませませませ");
    }

    @Test
    void emojiCountsAsOneCharacter() {
        assertThat(LineTextService.visibleLength("😀😀%reply_url%")).isEqualTo(2);
    }
}
