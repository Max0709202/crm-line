package com.crm.controller;

import com.crm.dto.MessageBoxItem;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 返信画面 (reply/page.html): 画像添付 shows a 📎 mark; 写真閲覧 opens each image. */
class ReplyPageImagesTemplateTest {

    private static String render(Map<String, Object> vars) {
        ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode(TemplateMode.HTML);
        r.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(r);
        engine.addDialect(new org.thymeleaf.extras.java8time.dialect.Java8TimeDialect());   // #temporals, as Spring Boot registers
        MockHttpServletRequest req = new MockHttpServletRequest();
        WebContext ctx = new WebContext(req, new MockHttpServletResponse(), new MockServletContext());
        ctx.setVariables(vars);
        return engine.process("reply/page", ctx);
    }

    private static Map<String, Object> base() {
        Map<String, Object> v = new HashMap<>();
        v.put("token", "tok");
        v.put("replyFormVisible", false);
        v.put("headerVisible", false);
        v.put("form", new ReplyPageController.ReplyForm());
        v.put("_csrf", "CSRF");
        v.put("pointShort", false);
        MessageBoxItem item = new MessageBoxItem(70L, LocalDateTime.now(), "件名", "本文", "Re: 件名", "EMAIL");
        v.put("messageBox", new PageImpl<>(Collections.singletonList(item)));
        v.put("inboundBox", new PageImpl<>(Collections.<MessageBoxItem>emptyList()));
        v.put("boxPage", 0);
        return v;
    }

    @Test
    void lockedImages_showTheMarkAndThePhotoViewButton() {
        Map<String, Object> v = base();
        Map<Long, List<Long>> imgs = new HashMap<>();
        imgs.put(70L, Arrays.asList(5L, 6L));
        v.put("boxImages", imgs);
        v.put("openImages", new HashSet<>(Collections.singletonList(6L)));
        v.put("photoViewCost", 20);
        v.put("memberPoints", 1200);
        String html = render(v);
        assertThat(html).contains("📎 添付画像 2枚")
                .contains("action=\"/reply/tok/image/5/open\"", "name=\"_csrf\" value=\"CSRF\"", "20pt")
                .contains("src=\"/reply/tok/image/6\"")
                .doesNotContain("src=\"/reply/tok/image/5\"")
                .contains("所持 1,200pt");
    }

    @Test
    void noImages_noMark() {
        assertThat(render(base())).doesNotContain("添付画像");
    }
}
