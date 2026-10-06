package com.crm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Member site (ログイン前のトップ・仮登録 / 本登録・ログイン後の会員ページ): the page can't be zoomed.
 * Applied when the page is rendered, so it also covers HTML saved on 番組デザイン設定.
 * <ul>
 *   <li>viewport: {@code maximum-scale=1, user-scalable=no} (Android / iOS form-focus zoom);</li>
 *   <li>iOS Safari ignores user-scalable, so pinch (gesture events, 2-finger touchmove) is cancelled
 *       and double-tap zoom is off via {@code touch-action: manipulation};</li>
 *   <li>PC: Ctrl / ⌘ + wheel (and trackpad pinch) and Ctrl / ⌘ + [+ − ; =] are cancelled.</li>
 * </ul>
 */
public final class NoZoom {

    private static final Pattern VIEWPORT = Pattern.compile("(?is)<meta\\b[^>]*name\\s*=\\s*[\"']viewport[\"'][^>]*>");
    private static final Pattern CONTENT = Pattern.compile("(?is)(content\\s*=\\s*)([\"'])(.*?)\\2");
    private static final Pattern SCALE_KEYS = Pattern.compile("(?i)\\s*,?\\s*(maximum-scale|minimum-scale|user-scalable)\\s*=\\s*[^,]*");
    private static final Pattern HEAD = Pattern.compile("(?i)<head\\b[^>]*>");
    private static final Pattern HEAD_END = Pattern.compile("(?i)</head\\s*>");
    private static final String LOCK = ", maximum-scale=1, user-scalable=no";

    /** The {@code <head>} style + script (member/page.html, a Thymeleaf page, has the same lines). */
    private static final String SNIPPET = "<style>html{touch-action:manipulation;-webkit-text-size-adjust:100%;text-size-adjust:100%}</style>\n"
            + "<script>(function(){var o={passive:false};function no(e){e.preventDefault();}"
            + "document.addEventListener('gesturestart',no,o);document.addEventListener('gesturechange',no,o);"
            + "document.addEventListener('touchmove',function(e){if(e.touches&&e.touches.length>1)e.preventDefault();},o);"
            + "window.addEventListener('wheel',function(e){if(e.ctrlKey||e.metaKey)e.preventDefault();},o);"
            + "document.addEventListener('keydown',function(e){if((e.ctrlKey||e.metaKey)&&['+','-','=',';','_'].indexOf(e.key)>=0)e.preventDefault();});"
            + "})();</script>\n";

    private NoZoom() {}

    public static String apply(String html) {
        if (html == null) return null;
        Matcher vp = VIEWPORT.matcher(html);
        if (vp.find()) {
            String tag = vp.group();
            Matcher c = CONTENT.matcher(tag);
            String rest = c.find() ? SCALE_KEYS.matcher(c.group(3)).replaceAll("").trim().replaceAll("^,\\s*", "") : "";
            if (rest.isEmpty()) rest = "width=device-width, initial-scale=1";
            String fixed = c.find(0)
                    ? tag.substring(0, c.start()) + c.group(1) + c.group(2) + rest + LOCK + c.group(2) + tag.substring(c.end())
                    : "<meta name=\"viewport\" content=\"" + rest + LOCK + "\">";
            html = html.substring(0, vp.start()) + fixed + html.substring(vp.end());
        } else {
            Matcher h = HEAD.matcher(html);
            String meta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1" + LOCK + "\">";
            html = h.find() ? html.substring(0, h.end()) + "\n" + meta + html.substring(h.end()) : html;
        }
        Matcher end = HEAD_END.matcher(html);
        return end.find() ? html.substring(0, end.start()) + SNIPPET + html.substring(end.start()) : html;
    }
}
