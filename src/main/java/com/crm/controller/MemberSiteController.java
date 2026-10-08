package com.crm.controller;

import com.crm.entity.Chara;
import com.crm.entity.CrmUser;
import com.crm.entity.Message;
import com.crm.service.CharaService;
import com.crm.service.HtmlImageService;
import com.crm.service.LoginThrottleService;
import com.crm.service.MemberPageService;
import com.crm.service.MemberSiteService;
import com.crm.service.MemberUnlockService;
import com.crm.service.MessageImageService;
import com.crm.service.PaymentSettingService;
import com.crm.service.PointSettingService;
import com.crm.service.PublicSiteService;
import com.crm.service.SiteDesignService;
import com.crm.service.TelecomCreditService;
import com.crm.service.UserProfileService;
import com.crm.util.ClientIpResolver;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 会員ページ — 会員ログイン and every page after it, in the 番組デザイン (スマホ/PC design, or the ガラケー
 * design for feature phones): MENU / 受信BOX / 返信 (やり取り・送信) / 友達追加リスト / 条件検索 / サポート窓口 /
 * プロフ編集 / ポイント購入 (テレコムクレジット) / ポイント表, plus プロフィール閲覧 and 写真閲覧 of a キャラ.
 * The design's frame and 番組デザイン設定's 上部 / 下部HTML come from {@link MemberPageService}; the page
 * content is built here from the member's data ({@link MemberSiteService}).
 */
@Controller
public class MemberSiteController {

    private static final Pattern FEATURE_PHONE = Pattern.compile(
            "(?i)(DoCoMo/|KDDI-|UP\\.Browser|SoftBank/|Vodafone/|J-PHONE/|WILLCOM|DDIPOCKET|MOT-|emobile/)");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("M/d H:mm");

    /** Page code → the member URL (links inside the design). */
    private static final Map<String, String> URLS = new HashMap<>();
    static {
        URLS.put("menu", "/member/menu");
        URLS.put("inbox", "/member/inbox");
        URLS.put("reply", "/member/inbox");
        URLS.put("friends", "/member/friends");
        URLS.put("search", "/member/search");
        URLS.put("support", "/member/support");
        URLS.put("profile", PublicSiteService.PROFILE_URL);
        URLS.put("points", "/member/points");
        URLS.put("point_table", "/member/point-table");
    }

    private final MemberSiteService site;
    private final MemberPageService pages;
    private final SiteDesignService siteDesignService;
    private final PublicSiteService publicSiteService;
    private final PaymentSettingService paymentSettingService;
    private final PointSettingService pointSettingService;
    private final TelecomCreditService telecomCreditService;
    private final UserProfileService userProfileService;
    private final MessageImageService messageImageService;
    private final HtmlImageService htmlImageService;
    private final LoginThrottleService throttle;

    public MemberSiteController(MemberSiteService site, MemberPageService pages, SiteDesignService siteDesignService,
                                PublicSiteService publicSiteService, PaymentSettingService paymentSettingService,
                                PointSettingService pointSettingService, TelecomCreditService telecomCreditService,
                                UserProfileService userProfileService, MessageImageService messageImageService,
                                HtmlImageService htmlImageService, LoginThrottleService throttle) {
        this.site = site;
        this.pages = pages;
        this.siteDesignService = siteDesignService;
        this.publicSiteService = publicSiteService;
        this.paymentSettingService = paymentSettingService;
        this.pointSettingService = pointSettingService;
        this.telecomCreditService = telecomCreditService;
        this.userProfileService = userProfileService;
        this.messageImageService = messageImageService;
        this.htmlImageService = htmlImageService;
        this.throttle = throttle;
    }

    /* ===================== ログイン / ログアウト ===================== */

    @PostMapping("/member/login")
    public Object login(@RequestParam(name = "login_id", required = false) String loginId,
                        @RequestParam(name = "login_password", required = false) String password,
                        HttpServletRequest request, Model model) {
        String key = "member:" + ClientIpResolver.resolve(request);
        if (throttle.isBlocked(key)) {
            return publicMessage(request, model, "ログインできません", "<p>ログインの試行回数が多すぎます。しばらく時間をおいてからもう一度お試しください。</p>"
                    + "<p><a href=\"/#login\">ログイン画面に戻る</a></p>");
        }
        Optional<CrmUser> user = site.authenticate(loginId, password);
        if (!user.isPresent()) {
            throttle.recordFailure(key);
            return publicMessage(request, model, "ログインできません", "<p>メールアドレス（ログインID）またはパスワードが正しくありません。"
                    + "本登録がお済みでない場合は、届いたメールのURLから本登録を完了してください。</p>"
                    + "<p><a href=\"/#login\">ログイン画面に戻る</a></p>");
        }
        throttle.recordSuccess(key);
        // New session ID (no session fixation), same session (an operator's 管理画面 login stays)
        HttpSession session = request.getSession(true);
        if (!session.isNew()) request.changeSessionId();
        session.setAttribute(PublicSiteController.SESSION_MEMBER_ID, user.get().getId());
        site.touch(user.get());
        return "redirect:/member/menu";
    }

    @GetMapping("/member/login")
    public String loginPage() {
        return "redirect:/#login";
    }

    @GetMapping("/member/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(PublicSiteController.SESSION_MEMBER_ID);
        session.removeAttribute(SESSION_PREVIEW_ID);
        return "redirect:/";
    }

    /* ===================== MENU ===================== */

    @GetMapping({"/member", "/member/menu"})
    public Object menu(HttpServletRequest request, HttpSession session) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        return render("menu", request, u, null, null);
    }

    /* ===================== 受信BOX ===================== */

    @GetMapping("/member/inbox")
    public Object inbox(@RequestParam(name = "tab", defaultValue = "all") String tab,
                        @RequestParam(name = "c", required = false) Long charaFilter,
                        @RequestParam(name = "page", defaultValue = "1") int page,
                        HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        boolean fp = fp(request);
        String note = flash(model);
        String t = "unread".equals(tab) || "fav".equals(tab) || "sent".equals(tab) ? tab : "all";
        boolean sentTab = "sent".equals(t);
        // 送信済み: the member's own mails (10 per page); the other tabs: one row per キャラ
        List<MemberSiteService.InboxItem> sentAll = sentTab ? site.inbox(u, t, charaFilter) : java.util.Collections.<MemberSiteService.InboxItem>emptyList();
        int pages = pageCount(sentAll.size());
        int p = Math.max(1, Math.min(page, pages));
        List<MemberSiteService.InboxItem> items = sentAll.subList((p - 1) * MemberSiteService.PAGE_SIZE,
                Math.min(sentAll.size(), p * MemberSiteService.PAGE_SIZE));
        List<MemberSiteService.InboxGroup> groups = sentTab ? java.util.Collections.<MemberSiteService.InboxGroup>emptyList()
                : site.inboxGroups(u, t, charaFilter);
        String pageBase = "/member/inbox?tab=" + t + (charaFilter == null ? "" : "&c=" + charaFilter);
        Set<Long> friendIds = new LinkedHashSet<>();
        for (Chara c : site.friends(u)) friendIds.add(c.getId());
        StringBuilder b = new StringBuilder();
        String[][] tabs = {{"all", "すべて"}, {"unread", "未読"}, {"fav", "お気に入り"}, {"sent", "送信済み"}};
        Map<Long, String> senders = site.pastSenders(u);
        if (fp) {
            if (note != null) b.append("<div class=\"row\">").append(e(note)).append("</div>");
            b.append("<div class=\"m\"><a href=\"/member/menu\">0. MENUへ戻る</a></div><div class=\"row\">");
            for (String[] x : tabs) {
                b.append(x[0].equals(t) ? "<b>" + x[1] + "</b>" : "<a href=\"/member/inbox?tab=" + x[0] + "\">" + x[1] + "</a>").append(" ");
            }
            b.append("</div>");
            if (items.isEmpty() && groups.isEmpty()) b.append("<div class=\"mail\">メッセージはありません</div>");
            for (MemberSiteService.InboxGroup g : groups) {
                long cid = g.chara == null ? 0L : g.chara.getId();
                b.append("<div class=\"mail\"><a href=\"/member/inbox/list?c=").append(cid).append("\"><b>").append(e(name(g.chara))).append("</b></a> ")
                        .append(unreadBadge(g.unread)).append(g.unread > 0 ? " <span class=new>NEW</span>" : "").append("<br>")
                        .append(titleAndBody(g.latest, "<br>")).append("<br><small>").append(when(g.latest)).append("</small></div>");
            }
            for (MemberSiteService.InboxItem it : items) {
                long cid = it.chara == null ? 0L : it.chara.getId();
                b.append("<div class=\"mail\">To: <a href=\"/member/inbox/list?c=").append(cid).append("\"><b>").append(e(name(it.chara))).append("</b>")
                        .append("</a><br>").append(e(preview(u, it))).append("<br><small>")
                        .append(when(it.message)).append("</small></div>");
            }
            if (sentTab) b.append(pager(pageBase, p, pages, true));
        } else {
            b.append("<div class=\"tabs\">");
            for (String[] x : tabs) {
                b.append("<a class=\"tab").append(x[0].equals(t) ? " active" : "").append("\" style=\"display:block;text-decoration:none;color:inherit\" href=\"/member/inbox?tab=")
                        .append(x[0]).append("\">").append(x[1]).append("</a>");
            }
            b.append("</div><div class=\"tools\"><select onchange=\"location.href='/member/inbox?tab=").append(t)
                    .append("'+(this.value?'&amp;c='+this.value:'')\"><option value=\"\">過去の受信者から選択</option>");
            for (Map.Entry<Long, String> s : senders.entrySet()) {
                b.append("<option value=\"").append(s.getKey()).append("\"").append(s.getKey().equals(charaFilter) ? " selected" : "")
                        .append(">").append(e(s.getValue())).append("</option>");
            }
            b.append("</select></div>");
            b.append("<section id=\"ml\"><div class=\"boxline\"></div>");
            if (note != null) b.append("<div class=\"note\">").append(e(note)).append("</div>");
            if (items.isEmpty() && groups.isEmpty()) b.append("<div class=\"note\">メッセージはありません</div>");
            // すべて / 未読 / お気に入り: one row per キャラ — 未読あり(n) / 未読なし in place of the age, the newest
            // mail's タイトル and 本文, and 受信一覧 (that キャラ's mails)
            for (MemberSiteService.InboxGroup g : groups) {
                long cid = g.chara == null ? 0L : g.chara.getId();
                b.append("<div class=\"msg\" id=\"c").append(cid).append("\"><div class=\"avatar\">").append(avatar(u, g.chara)).append("</div><div><b>")
                        .append(e(name(g.chara))).append("</b>　").append(unreadBadge(g.unread)).append(g.unread > 0 ? " <span class=\"new\">NEW</span>" : "")
                        .append("<div class=\"preview\">").append(titleAndBody(g.latest, "<br>")).append("</div></div><div class=\"date\">")
                        .append(when(g.latest)).append("</div><div class=\"msgactions\">");
                if (g.chara != null) b.append(charaButtons(request, u, g.chara, friendIds.contains(g.chara.getId()), "/member/inbox?tab=" + t, false));
                b.append("<a class=\"smallbtn reply\" href=\"/member/inbox/list?c=").append(cid).append("\">受信一覧</a></div></div>");
            }
            // 送信済み: as before (no age), 受信一覧 instead of やり取りを見る
            for (MemberSiteService.InboxItem it : items) {
                long cid = it.chara == null ? 0L : it.chara.getId();
                b.append("<div class=\"msg\" id=\"m").append(it.message.getId()).append("\">");
                b.append("<div class=\"avatar\">").append(avatar(u, it.chara)).append("</div><div><b>To: ").append(e(name(it.chara)))
                        .append("</b><div class=\"preview\">").append(e(preview(u, it)))
                        .append("</div></div><div class=\"date\">").append(when(it.message)).append("</div><div class=\"msgactions\">");
                if (it.chara != null) b.append(charaButtons(request, u, it.chara, friendIds.contains(it.chara.getId()), pageBase + "&page=" + p, false));
                b.append("<a class=\"smallbtn reply\" href=\"/member/inbox/list?c=").append(cid).append("\">受信一覧</a></div></div>");
            }
            b.append("<div class=\"boxline\"></div></section>");
            if (sentTab) b.append(pager(pageBase, p, pages, false));
        }
        return render("inbox", request, u, b.toString(), null);
    }

    /* ===================== 受信一覧 (one キャラ's mails) ===================== */

    @GetMapping("/member/inbox/list")
    public Object inboxList(@RequestParam(name = "c", defaultValue = "0") long charaId,
                            @RequestParam(name = "page", defaultValue = "1") int page,
                            HttpServletRequest request, HttpSession session) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        Chara chara = null;
        if (charaId > 0) {
            chara = site.chara(charaId).orElse(null);
            if (chara == null) return "redirect:/member/inbox";
        }
        boolean fp = fp(request);
        // received only, newest first, 10 per page
        List<MemberSiteService.InboxItem> all = site.charaInbox(u, charaId);
        int unread = 0;
        for (MemberSiteService.InboxItem it : all) if (it.unread) unread++;
        int pages = pageCount(all.size());
        int p = Math.max(1, Math.min(page, pages));
        List<MemberSiteService.InboxItem> items = all.subList((p - 1) * MemberSiteService.PAGE_SIZE,
                Math.min(all.size(), p * MemberSiteService.PAGE_SIZE));
        String base = "/member/inbox/list?c=" + charaId;
        StringBuilder b = new StringBuilder();
        if (fp) {
            b.append("<div class=\"m\"><a href=\"/member/inbox\">0. 受信BOXへ戻る</a></div>");
            b.append("<div class=\"row\"><b>").append(e(name(chara))).append("</b> ").append(unreadBadge(unread))
                    .append(unread > 0 ? " <span class=new>NEW</span>" : "");
            if (chara != null) {
                b.append("<br><a href=\"/member/photo?c=").append(chara.getId()).append("\">写真閲覧</a>／<a href=\"/member/chara?c=")
                        .append(chara.getId()).append("\">プロフ閲覧</a>");
            }
            b.append("</div>");
            if (items.isEmpty()) b.append("<div class=\"mail\">メッセージはありません</div>");
            for (MemberSiteService.InboxItem it : items) {
                b.append("<div class=\"mail\"><a href=\"").append(replyHref(charaId, it.message)).append("\">").append(readMark(it.unread))
                        .append(" <small>").append(when(it.message)).append("</small><br>").append(titleAndBody(it.message, "<br>")).append("</a></div>");
            }
            b.append(prevNextPager(base, p, pages, true));
        } else {
            b.append("<section class=\"box\"><div class=\"boxline\"></div><div class=\"person\"><div class=\"avatar\">").append(avatar(u, chara))
                    .append("</div><div class=\"grow\"><b>").append(e(name(chara))).append("</b>　").append(unreadBadge(unread))
                    .append(unread > 0 ? " <span class=\"new\">NEW</span>" : "");
            if (chara != null) {
                b.append("<div class=\"pbtns\">").append(charaButtons(request, u, chara, site.isFriend(u, chara.getId()), base + "&page=" + p, true)).append("</div>");
            }
            b.append("</div></div>");
            // 管理者プレビュー「受信ボックス確認」: 選択削除 — the same soft delete as the 管理画面's
            // メッセージボックス, so the message leaves this 受信BOX (and the 返信画面's box) for the member.
            boolean boxDelete = preview(session) && !items.isEmpty();
            if (boxDelete) {
                b.append("<form id=\"pvDel\" method=\"post\" action=\"/manager/users/").append(u.getId()).append("/message-box/delete\"")
                        .append(" style=\"display:flex;gap:12px;align-items:center;margin:8px 0;padding:8px 10px;border:1px dashed #b91c1c;border-radius:6px\">")
                        .append(csrf(request)).append("<input type=\"hidden\" name=\"returnTo\" value=\"member-inbox\">")
                        .append("<label><input type=\"checkbox\" onclick=\"var on=this.checked;document.querySelectorAll('input[form=pvDel]').forEach(function(c){c.checked=on;})\"> 全選択</label>")
                        .append("<button type=\"submit\" class=\"smallbtn\" style=\"background:#dc2626;color:#fff;border:0\" onclick=\"return confirm('選択したメッセージを削除しますか？（ユーザーの受信BOXから消えます）');\">選択削除</button></form>");
            }
            if (items.isEmpty()) b.append("<div class=\"note\">メッセージはありません</div>");
            // each mail → 返信画面 (opening it makes only that mail 既読)
            for (MemberSiteService.InboxItem it : items) {
                b.append("<div id=\"m").append(it.message.getId()).append("\" style=\"position:relative;border-bottom:1px solid #dfe6ef;background:#fff")
                        .append(boxDelete ? ";padding-left:38px" : "").append("\">");
                if (boxDelete) b.append("<input type=\"checkbox\" name=\"ids\" value=\"").append(it.message.getId())
                        .append("\" form=\"pvDel\" style=\"position:absolute;left:12px;top:50%;transform:translateY(-50%);width:18px;height:18px\">");
                b.append("<a href=\"").append(replyHref(charaId, it.message)).append("\" style=\"display:block;padding:12px;color:inherit;text-decoration:none\">")
                        .append("<div style=\"display:flex;align-items:center;gap:8px\">").append(readMark(it.unread))
                        .append("<span class=\"date\" style=\"margin-left:auto\">").append(when(it.message)).append("</span></div>")
                        .append("<div class=\"preview\" style=\"font-size:13px;color:#18324b\">").append(titleAndBody(it.message, "<br>")).append("</div></a></div>");
            }
            b.append(prevNextPager(base, p, pages, false)).append("<div class=\"boxline\"></div></section>");
        }
        return render("inbox", request, u, b.toString(), "受信一覧");
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + MemberSiteService.PAGE_SIZE - 1) / MemberSiteService.PAGE_SIZE);
    }

    /** ‹ 前へ 1 2 … 次へ › (only when there is more than one page). */
    private static String pager(String base, int page, int pages, boolean fp) {
        if (pages <= 1) return "";
        StringBuilder b = new StringBuilder(fp ? "<div class=\"row\">" : "<div style=\"display:flex;flex-wrap:wrap;justify-content:center;gap:6px;padding:14px 8px\">");
        if (page > 1) b.append("<a class=\"smallbtn\" href=\"").append(base).append("&amp;page=").append(page - 1).append("\">‹ 前へ</a> ");
        for (int i = 1; i <= pages; i++) {
            if (i == page) b.append(fp ? "<b>" + i + "</b> " : "<span class=\"smallbtn active\">" + i + "</span>");
            else b.append("<a class=\"smallbtn\" href=\"").append(base).append("&amp;page=").append(i).append("\">").append(i).append("</a> ");
        }
        if (page < pages) b.append("<a class=\"smallbtn\" href=\"").append(base).append("&amp;page=").append(page + 1).append("\">次へ ›</a>");
        return b.append("</div>").toString();
    }

    /** 受信一覧's page move: page 1 → 次へ only; page 2 and after → 戻る and 次へ (次へ while there is a next page). */
    private static String prevNextPager(String base, int page, int pages, boolean fp) {
        if (pages <= 1) return "";
        StringBuilder b = new StringBuilder(fp ? "<div class=\"row\">" : "<div style=\"display:flex;justify-content:center;gap:12px;padding:14px 8px\">");
        if (page > 1) b.append("<a class=\"smallbtn\" href=\"").append(base).append("&amp;page=").append(page - 1).append("\">‹ 戻る</a> ");
        if (page < pages) b.append("<a class=\"smallbtn\" href=\"").append(base).append("&amp;page=").append(page + 1).append("\">次へ ›</a>");
        return b.append("</div>").toString();
    }

    /** 未読あり(n) — red, white text / 未読なし — grey, white text. */
    private static String unreadBadge(int unread) {
        return unread > 0
                ? "<span style=\"display:inline-block;padding:2px 8px;border-radius:4px;background:#e0313f;color:#fff;font-size:11px;font-weight:800\">未読あり(" + unread + ")</span>"
                : "<span style=\"display:inline-block;padding:2px 8px;border-radius:4px;background:#9aa3ad;color:#fff;font-size:11px;font-weight:800\">未読なし</span>";
    }

    /** 受信一覧's mark of one mail: the envelope icon, then 未読 (red, white text) / 既読 (grey, white text). */
    private static String readMark(boolean unread) {
        String icon = "<img src=\"/member/images/" + (unread ? "mail-unread.svg" : "mail-read.svg") + "\" alt=\"\" width=\"22\" height=\"" + (unread ? 18 : 20)
                + "\" style=\"vertical-align:middle\">";
        String label = "<span style=\"display:inline-block;padding:2px 8px;border-radius:4px;background:" + (unread ? "#e0313f" : "#9aa3ad")
                + ";color:#fff;font-size:11px;font-weight:800;vertical-align:middle\">" + (unread ? "未読" : "既読") + "</span>";
        return icon + label;
    }

    /** A mail's タイトル, then (on the next line) its 本文 up to 表示文字数. */
    private String titleAndBody(Message m, String br) {
        String s = m.getSubject() == null ? "" : m.getSubject().trim();
        String body = MemberSiteService.displayBody(m).replaceAll("\\s+", " ").trim();
        int max = site.previewLength();
        if (body.codePointCount(0, body.length()) > max) body = body.substring(0, body.offsetByCodePoints(0, max)) + "…";
        StringBuilder b = new StringBuilder();
        if (!s.isEmpty()) b.append("<b>").append(e(s)).append("</b>");
        if (!body.isEmpty()) b.append(b.length() > 0 ? br : "").append(e(body));
        return b.length() == 0 ? "メッセージが届いています" : b.toString();
    }

    private static String replyHref(long charaId, Message m) {
        return "/member/reply?c=" + charaId + "&amp;m=" + m.getId() + "#m" + m.getId();
    }

    /* ===================== 返信 (やり取り・送信) ===================== */

    @GetMapping("/member/reply")
    public Object reply(@RequestParam(name = "c", defaultValue = "0") long charaId,
                        @RequestParam(name = "m", required = false) Long openedId,
                        HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        Chara chara = null;
        if (charaId > 0) {
            chara = site.chara(charaId).orElse(null);
            if (chara == null) return "redirect:/member/inbox";
        }
        boolean fp = fp(request);
        boolean support = charaId == 0;
        List<MemberSiteService.ConvItem> conv = site.conversation(u, charaId);
        // 受信一覧 で1通クリック → その1通だけ表示
        if (openedId != null) {
            List<MemberSiteService.ConvItem> one = new java.util.ArrayList<>();
            for (MemberSiteService.ConvItem it : conv) if (openedId.equals(it.message.getId())) one.add(it);
            conv = one;
        }
        // 1通ずつ既読: only the mail clicked in 受信一覧 becomes 既読 (サポート窓口: no 閲覧pt)
        if (!preview(session)) {
            if (support) site.openSupportMails(u, conv, openedId);
            else site.markReadIfFree(u, conv, openedId);
        }
        Set<Long> allImages = new LinkedHashSet<>();
        for (MemberSiteService.ConvItem it : conv) allImages.addAll(it.images);
        Set<Long> openImgs = support ? allImages : site.openImages(u, allImages);
        int bodyCost = site.cost(MemberSiteService.COST_BODY, u);
        int photoCost = site.cost(MemberSiteService.COST_PHOTO, u);
        String back = "/member/reply?c=" + charaId + (openedId == null ? "" : "&m=" + openedId);
        StringBuilder b = new StringBuilder();
        String note = flash(model);
        if (fp) {
            b.append(note == null ? "" : "<div class=\"row\">" + e(note) + "</div>");
            b.append("<div class=\"row\"><b>").append(e(name(chara))).append("</b>").append(age(chara));
            if (chara != null) {
                b.append("<br><a href=\"/member/photo?c=").append(chara.getId()).append("\">写真閲覧</a>／<a href=\"/member/chara?c=")
                        .append(chara.getId()).append("\">プロフ閲覧</a>");
            }
            b.append("</div>");
        } else {
            b.append("<section class=\"box\"><div class=\"boxline\"></div>").append(note == null ? "" : "<div class=\"note\">" + e(note) + "</div>")
                    .append("<div class=\"person\"><div class=\"avatar\">").append(avatar(u, chara)).append("</div><div class=\"grow\"><b>")
                    .append(e(name(chara))).append("</b>　<small>").append(ageText(chara)).append("</small>");
            if (chara != null) {
                b.append("<div class=\"pbtns\">").append(charaButtons(request, u, chara, site.isFriend(u, chara.getId()), back, true)).append("</div>");
            }
            b.append("</div></div>");
        }

        // やり取り (oldest first, the latest 30) — from 受信一覧: the one mail clicked
        int from = Math.max(0, conv.size() - 30);
        for (int i = from; i < conv.size(); i++) {
            MemberSiteService.ConvItem it = conv.get(i);
            Message m = it.message;
            String head = when(m) + (it.out ? "　受信" : "　送信");
            StringBuilder body = new StringBuilder();
            if (m.getSubject() != null && !m.getSubject().trim().isEmpty()) body.append("<b>").append(e(m.getSubject())).append("</b><br>");
            if (it.open) {
                body.append("<div style=\"white-space:pre-wrap;word-break:break-word\">").append(e(MemberSiteService.displayBody(m))).append("</div>");
            } else {
                body.append(openForm(request, MemberUnlockService.BODY, m.getId(), back + "#m" + m.getId(),
                        "本文閲覧（" + bodyCost + "pt）", fp));
            }
            if (!it.images.isEmpty()) {
                body.append(fp ? "<br>" : "<div style=\"margin-top:8px\">").append("📎 添付画像 ").append(it.images.size()).append("枚")
                        .append(fp ? "<br>" : "<div style=\"display:flex;flex-wrap:wrap;gap:8px;margin-top:6px\">");
                for (Long img : it.images) {
                    if (openImgs.contains(img)) {
                        body.append("<a href=\"/member/image/").append(img).append("\" target=\"_blank\">")
                                .append(fp ? "画像を見る</a><br>" : "<img src=\"/member/image/" + img + "\" alt=\"添付画像\" style=\"width:96px;height:96px;object-fit:cover;border:1px solid #cbd5e1;border-radius:6px\"></a>");
                    } else {
                        body.append(openForm(request, MemberUnlockService.IMAGE, img, back + "#m" + m.getId(), "📎写真閲覧（" + photoCost + "pt）", fp));
                    }
                }
                body.append(fp ? "" : "</div></div>");
            }
            if (fp) {
                b.append("<div class=\"row\" id=\"m").append(m.getId()).append("\"><small>").append(head).append("</small><br>").append(body).append("</div>");
            } else {
                b.append("<div class=\"row\" id=\"m").append(m.getId()).append("\"><div class=\"key\">").append(head)
                        .append("</div><div class=\"field\">").append(body).append("</div></div>");
            }
        }
        if (conv.isEmpty()) b.append(fp ? "<div class=\"row\">まだやり取りはありません</div>" : "<div class=\"note\">まだやり取りはありません</div>");

        if (support) {
            // サポート窓口: 本文の下に サポート窓口 の送信画面 (送信後はこの画面に戻る)
            b.append(supportForm(request, u, fp, back));
            if (!fp) b.append("<div class=\"boxline\"></div></section>");
            return render("reply", request, u, b.toString(), null);
        }

        // 送信フォーム — タイトル・本文の入力欄は受信メールと同じ文字サイズ; 写真添付 / アドレス添付・電話番号添付 (ポイント表示なし)
        boolean address = site.offered(MemberSiteService.COST_ADDRESS, u);
        boolean tel = site.offered(MemberSiteService.COST_TEL, u);
        int mailCost = site.cost(MemberSiteService.COST_MAIL, u);
        String sameSize = " style=\"font-size:inherit;font-family:inherit\"";
        b.append("<form method=\"post\" action=\"/member/reply\" enctype=\"multipart/form-data\">").append(csrf(request))
                .append("<input type=\"hidden\" name=\"c\" value=\"").append(charaId).append("\">")
                .append(openedId == null ? "" : "<input type=\"hidden\" name=\"m\" value=\"" + openedId + "\">")
                .append(field(fp, "タイトル", "<input type=\"text\" name=\"subject\" maxlength=\"" + MemberSiteService.SUBJECT_MAX + "\"" + sameSize + ">"))
                .append(field(fp, "本文", "<textarea name=\"body\" maxlength=\"" + MemberSiteService.BODY_MAX + "\" required" + sameSize + "></textarea>"));
        StringBuilder attach = new StringBuilder();
        attach.append("<div><label style=\"display:inline;font-weight:800\">写真添付</label> <input type=\"file\" name=\"photo\" accept=\"image/*\"></div>");
        if (address || tel) {
            attach.append("<div style=\"margin-top:10px;display:flex;flex-wrap:wrap;gap:8px 28px\">");
            if (address) attach.append("<label style=\"display:inline;font-weight:800\"><input type=\"checkbox\" name=\"address\" value=\"true\"> アドレス添付</label>");
            if (tel) attach.append("<label style=\"display:inline;font-weight:800\"><input type=\"checkbox\" name=\"tel\" value=\"true\"> 電話番号添付</label>");
            attach.append("</div>");
        }
        b.append(fp ? "<div class=\"row\">" + attach + "</div>" : "<div class=\"row\" style=\"display:block;padding:12px 15px\">" + attach + "</div>");
        b.append(fp ? "<div class=\"row\"><button class=\"btn\">送信</button>（" + mailCost + "ポイント）</div>"
                : "<div class=\"actions\"><button class=\"btn\">送信</button><div class=\"note\" style=\"padding:6px 0 0\">メール送信 " + mailCost + "ポイント</div></div>");
        b.append("</form>");
        if (!fp) b.append("<div class=\"boxline\"></div></section>");
        return render("reply", request, u, b.toString(), null);
    }

    @PostMapping("/member/reply")
    public Object send(@RequestParam(name = "c", defaultValue = "0") long charaId,
                       @RequestParam(name = "m", required = false) Long openedId,
                       @RequestParam(name = "subject", required = false) String subject,
                       @RequestParam(name = "body", required = false) String body,
                       @RequestParam(name = "address", defaultValue = "false") boolean address,
                       @RequestParam(name = "tel", defaultValue = "false") boolean tel,
                       @RequestParam(name = "photo", required = false) MultipartFile photo,
                       HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        MemberSiteService.SendInput in = new MemberSiteService.SendInput();
        in.subject = subject;
        in.body = body;
        in.address = address;
        in.tel = tel;
        in.photo = photo;
        try {
            site.send(u, charaId, in, ClientIpResolver.resolve(request), request.getHeader("User-Agent"));
            // 送信後: 「メッセージを送信しました」で 送信済み へ
            ra.addFlashAttribute("memberNote", "メッセージを送信しました");
            return "redirect:/member/inbox?tab=sent";
        } catch (MemberSiteService.MemberException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
        }
        return "redirect:/member/reply?c=" + charaId + (openedId == null ? "" : "&m=" + openedId);
    }

    /** 本文閲覧 / プロフィール閲覧 / 写真閲覧 (uses the points once). */
    @PostMapping("/member/open")
    public Object open(@RequestParam("kind") String kind, @RequestParam("ref") Long ref,
                       @RequestParam(name = "back", required = false) String back,
                       HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        try {
            if (site.open(u, kind, ref) == MemberUnlockService.Result.NOT_ENOUGH_POINTS) {
                ra.addFlashAttribute("memberNote", "ポイントが足りません。ポイントを購入してからもう一度お試しください");
            }
        } catch (MemberSiteService.MemberException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    /** An image attached to a キャラ message — only to that member, and once 写真閲覧 is free / used. */
    @GetMapping("/member/image/{imageId}")
    public ResponseEntity<Resource> image(@PathVariable Long imageId, HttpSession session) {
        CrmUser u = member(session);
        if (u == null || !messageImageService.isImageOfUser(u.getId(), imageId)) return ResponseEntity.notFound().build();
        if (!site.isOpen(u, MemberUnlockService.IMAGE, imageId)) return ResponseEntity.status(403).build();
        Optional<com.crm.entity.HtmlImage> img = htmlImageService.findById(imageId);
        File f = img.isPresent() ? htmlImageService.fileFor(img.get()) : null;
        if (f == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.get().getContentType()))
                .header("Cache-Control", "private, max-age=3600").body(new FileSystemResource(f));
    }

    /* ===================== プロフィール閲覧 / 写真閲覧 ===================== */

    @GetMapping("/member/chara")
    public Object charaProfile(@RequestParam("c") Long charaId, HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        Chara c = site.chara(charaId).orElse(null);
        if (c == null) return "redirect:/member/inbox";
        boolean fp = fp(request);
        if (!preview(session) && site.cost(MemberSiteService.COST_PROFILE, u) <= 0) site.open(u, MemberUnlockService.PROFILE, c.getId());
        boolean open = site.isOpen(u, MemberUnlockService.PROFILE, c.getId());
        String back = "/member/chara?c=" + c.getId();
        StringBuilder b = new StringBuilder();
        String note = flash(model);
        b.append(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div>");
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        if (fp) {
            b.append("<div class=\"row\"><b>").append(e(c.getName())).append("</b>").append(age(c)).append("</div>");
        } else {
            b.append("<div class=\"person\"><div class=\"avatar\">").append(avatar(u, c)).append("</div><div class=\"grow\"><b>").append(e(c.getName()))
                    .append("</b>　<small>").append(ageText(c)).append("</small><div class=\"pbtns\">")
                    .append(charaButtons(request, u, c, site.isFriend(u, c.getId()), back, true)).append("</div></div></div>");
        }
        if (!open) {
            b.append(fp ? "<div class=\"row\">" : "<div class=\"note\">").append("プロフィールを見るには ").append(site.cost(MemberSiteService.COST_PROFILE, u))
                    .append("ポイント使います（所持 ").append(String.format("%,d", site.points(u))).append("ポイント）。一度見たプロフィールは何度でも見られます。</div>")
                    .append(openForm(request, MemberUnlockService.PROFILE, c.getId(), back, "プロフィールを見る（" + site.cost(MemberSiteService.COST_PROFILE, u) + "pt）", fp));
        } else {
            b.append(field(fp, "年齢", ageText(c).isEmpty() ? "—" : ageText(c)))
                    .append(field(fp, "地域", e(nz(c.getPref(), "—"))))
                    .append(field(fp, "血液型", e(nz(c.getBlood(), "—"))))
                    .append(field(fp, "星座", e(nz(c.getSign(), "—"))))
                    .append(field(fp, "プロフィール", "<div style=\"white-space:pre-wrap;word-break:break-word\">" + e(nz(c.getProfile(), "—")) + "</div>"));
            if (c.getPhotoUrl() != null) {
                b.append(field(fp, "写真", site.isOpen(u, MemberUnlockService.PHOTO, c.getId())
                        ? "<a href=\"/member/photo?c=" + c.getId() + "\">" + (fp ? "写真を見る" : "<img src=\"" + e(c.getPhotoUrl()) + "\" alt=\"\" style=\"max-width:180px;border-radius:8px\">") + "</a>"
                        : openForm(request, MemberUnlockService.PHOTO, c.getId(), back, "写真閲覧（" + site.cost(MemberSiteService.COST_PHOTO, u) + "pt）", fp)));
            }
        }
        b.append(fp ? "<div class=\"row\"><a href=\"/member/reply?c=" + c.getId() + "\">メッセージを送る</a></div>"
                : "<div class=\"actions\"><a class=\"btn\" style=\"display:inline-block;text-decoration:none\" href=\"/member/reply?c=" + c.getId() + "\">メッセージを送る</a></div><div class=\"boxline\"></div></section>");
        return render("reply", request, u, b.toString(), "プロフィール");
    }

    @GetMapping("/member/photo")
    public Object charaPhoto(@RequestParam("c") Long charaId, HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        Chara c = site.chara(charaId).orElse(null);
        if (c == null) return "redirect:/member/inbox";
        boolean fp = fp(request);
        StringBuilder b = new StringBuilder();
        String note = flash(model);
        b.append(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div>");
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        b.append(fp ? "<div class=\"row\"><b>" + e(c.getName()) + "</b>" + age(c) + "</div>"
                : "<div class=\"person\"><div class=\"grow\"><b>" + e(c.getName()) + "</b>　<small>" + ageText(c) + "</small></div></div>");
        if (c.getPhotoUrl() == null) {
            b.append(fp ? "<div class=\"row\">写真は登録されていません</div>" : "<div class=\"note\">写真は登録されていません</div>");
        } else {
            if (!preview(session) && site.cost(MemberSiteService.COST_PHOTO, u) <= 0) site.open(u, MemberUnlockService.PHOTO, c.getId());
            if (site.isOpen(u, MemberUnlockService.PHOTO, c.getId())) {
                b.append("<div style=\"padding:12px;text-align:center\"><img src=\"").append(e(c.getPhotoUrl()))
                        .append("\" alt=\"\" style=\"max-width:100%;").append(fp ? "width:240px" : "max-height:70vh").append(";border-radius:8px\"></div>");
            } else {
                b.append(fp ? "<div class=\"row\">" : "<div class=\"note\">").append("写真を見るには ").append(site.cost(MemberSiteService.COST_PHOTO, u))
                        .append("ポイント使います（所持 ").append(String.format("%,d", site.points(u))).append("ポイント）。一度見た写真は何度でも見られます。</div>")
                        .append(openForm(request, MemberUnlockService.PHOTO, c.getId(), "/member/photo?c=" + c.getId(),
                                "写真を見る（" + site.cost(MemberSiteService.COST_PHOTO, u) + "pt）", fp));
            }
        }
        b.append(fp ? "<div class=\"row\"><a href=\"/member/reply?c=" + c.getId() + "\">メッセージを送る</a></div>"
                : "<div class=\"actions\"><a class=\"btn\" style=\"display:inline-block;text-decoration:none\" href=\"/member/reply?c=" + c.getId() + "\">メッセージを送る</a></div><div class=\"boxline\"></div></section>");
        return render("reply", request, u, b.toString(), "写真閲覧");
    }

    /* ===================== 友達追加リスト ===================== */

    @GetMapping("/member/friends")
    public Object friends(HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        boolean fp = fp(request);
        List<Chara> list = site.friends(u);
        StringBuilder b = new StringBuilder();
        String note = flash(model);
        if (fp) {
            if (note != null) b.append("<div class=\"row\">").append(e(note)).append("</div>");
            b.append("<div class=\"m\">");
            for (Chara c : list) {
                b.append("<a href=\"/member/reply?c=").append(c.getId()).append("\">").append(e(c.getName())).append(" / ")
                        .append(e(nz(c.getPref(), "—"))).append("</a>");
            }
            b.append("</div>");
            if (list.isEmpty()) b.append("<div class=\"row\">友達はまだいません</div>");
        } else {
            b.append("<section class=\"box\"><div class=\"boxline\"></div>");
            if (note != null) b.append("<div class=\"note\">").append(e(note)).append("</div>");
            for (Chara c : list) b.append(person(request, u, c, true, "/member/friends", false, false));
            if (list.isEmpty()) b.append("<div class=\"note\">友達はまだいません。受信BOX・条件検索から「友達追加」できます。</div>");
            b.append("<div class=\"boxline\"></div></section>");
        }
        return render("friends", request, u, b.toString(), null);
    }

    @PostMapping("/member/friends/add")
    public Object addFriend(@RequestParam("c") Long charaId, @RequestParam(name = "back", required = false) String back,
                            HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        try {
            site.addFriend(u, charaId);
            ra.addFlashAttribute("memberNote", "友達に追加しました");
        } catch (MemberSiteService.MemberException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    /* ===================== 条件検索 ===================== */

    @GetMapping("/member/search")
    public Object search(HttpServletRequest request, HttpSession session) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        return render("search", request, u, searchHtml(request, u, new MemberSiteService.SearchInput(), null, null), null);
    }

    @PostMapping("/member/search")
    public Object doSearch(@RequestParam(name = "pref", required = false) String pref,
                           @RequestParam(name = "photo", required = false) String photo,
                           @RequestParam(name = "age", required = false) String age,
                           @RequestParam(name = "sign", required = false) String sign,
                           @RequestParam(name = "blood", required = false) String blood,
                           HttpServletRequest request, HttpSession session) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        MemberSiteService.SearchInput in = new MemberSiteService.SearchInput();
        in.pref = pref;
        in.photo = photo;
        in.age = age;
        in.sign = sign;
        in.blood = blood;
        List<Chara> result = null;
        String note = null;
        try {
            result = site.search(u, in);
        } catch (MemberSiteService.MemberException e) {
            note = e.getMessage();
        }
        return render("search", request, u, searchHtml(request, u, in, result, note), null);
    }

    private String searchHtml(HttpServletRequest request, CrmUser u, MemberSiteService.SearchInput in, List<Chara> result, String note) {
        boolean fp = fp(request);
        StringBuilder b = new StringBuilder(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div>");
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        b.append("<form method=\"post\" action=\"/member/search\">").append(csrf(request))
                .append(field(fp, "地域", select("pref", CharaService.PREFS, in.pref)))
                .append(field(fp, "写真", "<select name=\"photo\"><option value=\"\">指定しない</option><option value=\"yes\"" + sel("yes", in.photo)
                        + ">写真あり</option><option value=\"no\"" + sel("no", in.photo) + ">写真なし</option></select>"))
                .append(field(fp, "年齢", "<select name=\"age\"><option value=\"\">指定しない</option>"
                        + ageOpt("18-24", "18～24", in.age) + ageOpt("25-29", "25～29", in.age) + ageOpt("30-34", "30～34", in.age)
                        + ageOpt("35-40", "35～40", in.age) + ageOpt("41-", "41～", in.age) + "</select>"))
                .append(field(fp, "星座", select("sign", CharaService.SIGNS, in.sign)))
                .append(field(fp, "血液型", select("blood", CharaService.BLOODS, in.blood)));
        int cost = site.cost(MemberSiteService.COST_SEARCH, u);
        b.append(fp ? "<div class=\"row\"><button class=\"btn\">検索する</button>" + (cost > 0 ? "（" + cost + "ポイント）" : "") + "</div>"
                : "<div class=\"actions\"><button class=\"btn\">検索する</button>" + (cost > 0 ? "<div class=\"note\" style=\"padding:6px 0 0\">検索 " + cost + "ポイント</div>" : "") + "</div>");
        b.append("</form>");
        if (result != null) {
            b.append(fp ? "<div class=\"ttl\">検索結果 " + result.size() + "件</div><div class=\"m\">" : "<h3 class=\"methodtitle\">検索結果 " + result.size() + "件</h3>");
            Set<Long> friendIds = new LinkedHashSet<>();
            for (Chara c : site.friends(u)) friendIds.add(c.getId());
            for (Chara c : result) {
                if (fp) {
                    b.append("<a href=\"/member/chara?c=").append(c.getId()).append("\">").append(e(c.getName())).append(" / ")
                            .append(e(nz(c.getPref(), "—"))).append(" / ").append(ageText(c).isEmpty() ? "—" : ageText(c)).append("</a>");
                } else {
                    b.append(person(request, u, c, friendIds.contains(c.getId()), "/member/search", true, true));
                }
            }
            if (result.isEmpty()) b.append(fp ? "<a href=\"/member/search\">条件に合うお相手はいませんでした</a>" : "<div class=\"note\">条件に合うお相手はいませんでした</div>");
            if (fp) b.append("</div>");
        }
        if (!fp) b.append("<div class=\"boxline\"></div></section>");
        return b.toString();
    }

    /* ===================== サポート窓口 ===================== */

    @GetMapping("/member/support")
    public Object support(HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        return render("support", request, u, supportHtml(request, u, flash(model)), null);
    }

    @PostMapping("/member/support")
    public Object sendSupport(@RequestParam(name = "name", required = false) String name,
                              @RequestParam(name = "email", required = false) String email,
                              @RequestParam(name = "body", required = false) String body,
                              @RequestParam(name = "back", required = false) String back,
                              HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        try {
            site.support(u, name, email, body);
            ra.addFlashAttribute("memberNote", "お問い合わせを送信しました。担当者からのご連絡をお待ちください。");
        } catch (MemberSiteService.MemberException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
        }
        // サポート窓口からのメールの画面 (受信一覧 → 1通) から送ったときは、その画面に戻る
        return "redirect:" + (back == null ? "/member/support" : safeBack(back));
    }

    private String supportHtml(HttpServletRequest request, CrmUser u, String note) {
        boolean fp = fp(request);
        StringBuilder b = new StringBuilder(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div>");
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        b.append(supportForm(request, u, fp, null));
        if (!fp) b.append("<div class=\"boxline\"></div></section>");
        return b.toString();
    }

    /** サポート窓口's 送信画面 (お名前・メールアドレス・お問い合わせ内容); {@code back} = the page to return to after sending (null = サポート窓口). */
    private static String supportForm(HttpServletRequest request, CrmUser u, boolean fp, String back) {
        return "<form method=\"post\" action=\"/member/support\">" + csrf(request)
                + (back == null ? "" : "<input type=\"hidden\" name=\"back\" value=\"" + e(back) + "\">")
                + field(fp, "お名前", "<input type=\"text\" name=\"name\" maxlength=\"100\" value=\"" + e(nz(u.getDisplayName(), "")) + "\">")
                + field(fp, "メールアドレス", "<input type=\"email\" name=\"email\" maxlength=\"255\" value=\"" + e(nz(u.getEmail(), "")) + "\">")
                + field(fp, "お問い合わせ内容", "<textarea name=\"body\" required></textarea>")
                + (fp ? "<div class=\"row\"><button class=\"btn\">送信する</button></div>" : "<div class=\"actions\"><button class=\"btn\">送信する</button></div>")
                + "</form>";
    }

    /* ===================== プロフ編集 ===================== */

    @GetMapping(PublicSiteService.PROFILE_URL)
    public Object profile(HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        touch(session, u);
        boolean fp = fp(request);
        com.crm.entity.UserProfile p = userProfileService.get(u.getId());
        StringBuilder b = new StringBuilder(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div>");
        String note = flash(model);
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        int editCost = site.cost(MemberSiteService.COST_PROFILE_EDIT, u);
        int photoCost = site.cost(MemberSiteService.COST_PROFILE_PHOTO, u);
        b.append("<form method=\"post\" action=\"").append(PublicSiteService.PROFILE_URL).append("\" enctype=\"multipart/form-data\">").append(csrf(request))
                .append(field(fp, "メールアドレス", "<input type=\"email\" value=\"" + e(nz(u.getEmail(), "")) + "\" readonly style=\"background:#f1f5f9;color:#94a3b8;cursor:default\">"))
                .append(field(fp, "ニックネーム", "<input type=\"text\" name=\"nickname\" maxlength=\"20\" required value=\"" + e(nz(u.getDisplayName(), "")) + "\">"))
                .append(field(fp, "携帯番号", "<input type=\"tel\" name=\"phone\" maxlength=\"13\" value=\"" + e(nz(u.getPhoneNumber(), "")) + "\">"))
                .append(field(fp, "都道府県", select("pref", CharaService.PREFS, p.getPref())))
                .append(field(fp, "血液型", select("blood", CharaService.BLOODS, p.getBlood())))
                .append(field(fp, "星座", select("sign", CharaService.SIGNS, p.getSign())))
                .append(field(fp, "年齢", "<input type=\"number\" name=\"age\" min=\"" + CharaService.MIN_AGE + "\" max=\"" + CharaService.MAX_AGE
                        + "\" value=\"" + (p.getAge() == null ? "" : p.getAge()) + "\"> 歳"))
                .append(field(fp, "PR", "<textarea name=\"pr\" maxlength=\"500\">" + e(nz(p.getProfile(), "")) + "</textarea>"))
                .append(field(fp, "写真", (p.getPhotoUrl() == null ? "" : (fp ? "登録済み<br>" : "<img src=\"" + e(p.getPhotoUrl())
                        + "\" alt=\"\" style=\"display:block;max-width:120px;border-radius:8px;margin-bottom:6px\">"))
                        + "<input type=\"file\" name=\"photo\" accept=\"image/*\">" + (photoCost > 0 ? " <span class=\"cost\">" + photoCost + "ポイント</span>" : "")));
        b.append(fp ? "<div class=\"row\"><button class=\"btn\">保存</button>" + (editCost > 0 ? "（" + editCost + "ポイント）" : "") + "</div>"
                : "<div class=\"actions\"><button class=\"btn\">保存</button>" + (editCost > 0 ? "<div class=\"note\" style=\"padding:6px 0 0\">プロフィール変更 " + editCost + "ポイント</div>" : "") + "</div>");
        b.append("</form>");
        if (!fp) b.append("<div class=\"boxline\"></div></section>");
        return render("profile", request, u, b.toString(), null);
    }

    @PostMapping(PublicSiteService.PROFILE_URL)
    public Object saveProfile(@RequestParam(name = "nickname", required = false) String nickname,
                              @RequestParam(name = "phone", required = false) String phone,
                              @RequestParam(name = "pref", required = false) String pref,
                              @RequestParam(name = "blood", required = false) String blood,
                              @RequestParam(name = "sign", required = false) String sign,
                              @RequestParam(name = "age", required = false) String age,
                              @RequestParam(name = "pr", required = false) String pr,
                              @RequestParam(name = "photo", required = false) MultipartFile photo,
                              HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        MemberSiteService.ProfileInput in = new MemberSiteService.ProfileInput();
        in.nickname = nickname;
        in.phone = phone;
        in.pref = pref;
        in.blood = blood;
        in.sign = sign;
        in.age = age;
        in.pr = pr;
        in.photo = photo;
        try {
            site.saveProfile(u, in, "member:" + u.getId());
            ra.addFlashAttribute("memberNote", "プロフィールを保存しました");
        } catch (MemberSiteService.MemberException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
        }
        return "redirect:" + PublicSiteService.PROFILE_URL;
    }

    /* ===================== ポイント購入 / ポイント表 ===================== */

    @GetMapping("/member/points")
    public Object points(@RequestParam(name = "done", required = false) String done,
                         HttpServletRequest request, HttpSession session, Model model) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        boolean fp = fp(request);
        StringBuilder b = new StringBuilder(fp ? "" : "<section class=\"box plans\"><div class=\"boxline\"></div>");
        String note = flash(model);
        if (done != null) note = "決済が完了しました。ポイントの反映まで少しお待ちください（反映されない場合はページを再読み込みしてください）。";
        if (note != null) b.append(fp ? "<div class=\"row\">" + e(note) + "</div>" : "<div class=\"note\">" + e(note) + "</div>");
        boolean any = false;
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(u.getFolder())) {
            List<PaymentSettingService.Plan> plans = m.getOfferedPlans();
            if (plans.isEmpty()) continue;
            any = true;
            boolean payable = telecomCreditService.handles(m.getCode());
            b.append(fp ? "<div class=\"ttl\">" + e(m.getLabel()) + "</div><div class=\"m\">" : "<h3 class=\"methodtitle\">" + e(m.getLabel()) + "</h3>");
            for (int i = 0; i < m.getPlans().size(); i++) {
                PaymentSettingService.Plan p = m.getPlans().get(i);
                if (!p.isOffered()) continue;
                String pts = String.format("%,d", p.getPoints()), yen = String.format("%,d", p.getAmount());
                String form = "<form method=\"post\" action=\"/member/points/buy\" style=\"margin:0\">" + csrf(request)
                        + "<input type=\"hidden\" name=\"method\" value=\"" + e(m.getCode()) + "\"><input type=\"hidden\" name=\"plan\" value=\"" + i + "\">";
                if (fp) {
                    b.append("<div class=\"row\">").append(pts).append("ポイント　¥").append(yen)
                            .append(payable ? form + "<button class=\"btn\">購入する</button></form>" : "（準備中）").append("</div>");
                } else {
                    b.append("<div class=\"person\"><div class=\"grow\"><b>").append(pts).append("ポイント</b><span>").append(yen).append("円(税込)</span></div>")
                            .append(payable ? form + "<button class=\"btn\">購入する</button></form>" : "<button class=\"btn\" disabled style=\"opacity:.45\">準備中</button>")
                            .append("</div>");
                }
            }
            if (fp) b.append("</div>");
        }
        if (!any) b.append(fp ? "<div class=\"row\">現在購入できるプランはありません。</div>" : "<div class=\"note\">現在購入できるプランはありません。</div>");
        if (!fp) b.append("<div class=\"boxline\"></div></section>");
        return render("points", request, u, b.toString(), null);
    }

    /** 購入する → テレコムクレジットの決済画面 (the browser posts the order there). */
    @PostMapping("/member/points/buy")
    public Object buy(@RequestParam("method") String method, @RequestParam("plan") int plan,
                      HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        if (preview(session)) return previewBlocked();
        TelecomCreditService.Checkout co;
        try {
            String back = ServletUriComponentsBuilder.fromCurrentContextPath().path("/member/points").queryParam("done", "1").toUriString();
            co = telecomCreditService.startOrder(u, method, plan, back);
        } catch (TelecomCreditService.CheckoutException e) {
            ra.addFlashAttribute("memberNote", e.getMessage());
            return "redirect:/member/points";
        }
        StringBuilder b = new StringBuilder("<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\"><meta name=\"robots\" content=\"noindex\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>決済画面へ移動しています</title></head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding:40px 16px;color:#334155\">"
                + "<p>決済画面へ移動しています…</p><form id=\"co\" method=\"post\" action=\"" + e(co.action) + "\">");
        for (Map.Entry<String, String> f : co.fields.entrySet()) {
            b.append("<input type=\"hidden\" name=\"").append(e(f.getKey())).append("\" value=\"").append(e(f.getValue())).append("\">");
        }
        b.append("<button type=\"submit\">決済画面へ進む</button></form>"
                + "<script>document.getElementById('co').submit();</script></body></html>");
        return html(b.toString());
    }

    @GetMapping("/member/point-table")
    public Object pointTable(HttpServletRequest request, HttpSession session) {
        CrmUser u = member(session);
        if (u == null) return toLogin();
        boolean fp = fp(request);
        StringBuilder b = new StringBuilder(fp ? "" : "<section class=\"box\"><div class=\"boxline\"></div><table class=\"ptable\">");
        for (PointSettingService.Row r : pointSettingService.listShown(u.getFolder())) {
            if (fp) b.append("<div class=\"row\">").append(e(r.getLabel())).append("：").append(String.format("%,d", r.getCost())).append("ポイント</div>");
            else b.append("<tr><td>").append(e(r.getLabel())).append("</td><td class=\"cost\">").append(String.format("%,d", r.getCost())).append("ポイント</td></tr>");
        }
        if (!fp) b.append("</table><div class=\"boxline\"></div></section>");
        return render("point_table", request, u, b.toString(), null);
    }

    /* ===================== frame / helpers ===================== */

    private CrmUser member(HttpSession session) {
        Object previewId = session.getAttribute(SESSION_PREVIEW_ID);
        if (previewId != null) return site.member(previewId).orElse(null);
        return site.member(session.getAttribute(PublicSiteController.SESSION_MEMBER_ID)).orElse(null);
    }

    /* ===================== 管理者プレビュー ===================== */

    /**
     * 管理画面 › やり取り「表示画面を確認 / 受信ボックス確認」: the operator sees the member's pages exactly as
     * the member does (their data, folder's HTML areas), read-only — no 最終ログイン update, no free
     * 既読 / 閲覧, and every member POST (送信・閲覧・検索・購入 …) is refused. Set by
     * {@link ReplyPageController#userView}, cleared by its プレビュー終了 or /member/logout.
     */
    public static final String SESSION_PREVIEW_ID = "memberPreviewUserId";

    static boolean preview(HttpSession session) {
        return session != null && session.getAttribute(SESSION_PREVIEW_ID) != null;
    }

    /** 最終ログイン — not while an operator is previewing the member's pages. */
    private void touch(HttpSession session, CrmUser u) {
        if (!preview(session)) site.touch(u);
    }

    /** A member POST during a preview: nothing happens; back to the page it came from with a note. */
    private static String previewBlocked() {
        HttpServletRequest req = ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
        org.springframework.web.servlet.FlashMap flash = org.springframework.web.servlet.support.RequestContextUtils.getOutputFlashMap(req);
        if (flash != null) flash.put("memberNote", "管理者プレビュー中のため、この操作はできません（閲覧のみ）");
        String ref = req.getHeader("Referer");
        int at = ref == null ? -1 : ref.indexOf("/member/");
        return "redirect:" + (at < 0 ? "/member/menu" : ref.substring(at));
    }

    /** Red bar on top of every previewed page: whose pages these are, and プレビュー終了. */
    private static String previewBanner(String html, CrmUser u) {
        String bar = "<div style=\"position:sticky;top:0;z-index:2147483647;display:flex;flex-wrap:wrap;gap:6px 12px;align-items:center;"
                + "padding:8px 12px;background:#b91c1c;color:#fff;font:bold 13px/1.5 sans-serif\">"
                + "管理者プレビュー中：" + e(nz(u.getDisplayName(), "")) + "（ID " + e(nz(u.getLoginId(), String.valueOf(u.getId())))
                + "）— 閲覧のみ（送信・閲覧・購入などの操作はできません）"
                + "<a href=\"/manager/users/preview/end\" style=\"margin-left:auto;color:#fff;text-decoration:underline\">プレビュー終了</a></div>";
        java.util.regex.Matcher body = java.util.regex.Pattern.compile("(?i)<body\\b[^>]*>").matcher(html);
        return body.find() ? html.substring(0, body.end()) + bar + html.substring(body.end()) : bar + html;
    }

    private static String toLogin() {
        return "redirect:/#login";
    }

    /** ガラケー (feature phone) by User-Agent — also used for the top page and the 仮登録 / 本登録 pages. */
    static boolean fp(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && FEATURE_PHONE.matcher(ua).find();
    }

    private ResponseEntity<String> render(String code, HttpServletRequest request, CrmUser u, String mainHtml, String title) {
        Map<String, String> values = new HashMap<>();
        values.put("id", nz(u.getLoginId(), String.valueOf(u.getId())));
        values.put("name", nz(u.getDisplayName(), ""));
        values.put("point", String.format("%,d", site.points(u)));
        values.put("email", nz(u.getEmail(), ""));
        String html = pages.renderMember(code, fp(request) ? "fp" : "sp", values, u.getFolder(), mainHtml, title,
                site.unreadCount(u), c -> URLS.getOrDefault(c, "/member/menu"));
        if (preview(request.getSession(false))) html = previewBanner(html, u);
        return html(html);
    }

    private static ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8")
                .header("Cache-Control", "no-store").body(body);
    }

    /** A message page outside the member frame (ログイン失敗 etc.), in the public page design. */
    private String publicMessage(HttpServletRequest request, Model model, String title, String html) {
        model.addAttribute("siteName", siteDesignService.getSiteName());
        model.addAttribute("logoUrl", siteDesignService.getLogoUrl());
        model.addAttribute("footerHtml", publicSiteService.footerHtml("/#login"));
        model.addAttribute("pageTitle", title);
        model.addAttribute("pageHtml", html);
        return PublicSiteController.pageView(request);
    }

    private static String flash(Model model) {
        Object v = model.asMap().get("memberNote");
        return v == null ? null : String.valueOf(v);
    }

    private static String csrf(HttpServletRequest request) {
        Object t = request.getAttribute("_csrf");
        return t == null ? "" : "<input type=\"hidden\" name=\"_csrf\" value=\"" + e(String.valueOf(t)) + "\">";
    }

    /** A label + input row in the page's design. */
    private static String field(boolean fp, String label, String inner) {
        return fp ? "<div class=\"row\"><label>" + label + "</label>" + inner + "</div>"
                : "<div class=\"row\"><div class=\"key\">" + label + "</div><div class=\"field\">" + inner + "</div></div>";
    }

    /** A button that uses points to open something (本文 / プロフィール / 写真). */
    private static String openForm(HttpServletRequest request, String kind, Long ref, String back, String label, boolean fp) {
        return "<form method=\"post\" action=\"/member/open\" style=\"display:inline-block;margin:4px 6px 4px 0\">" + csrf(request)
                + "<input type=\"hidden\" name=\"kind\" value=\"" + kind + "\"><input type=\"hidden\" name=\"ref\" value=\"" + ref + "\">"
                + "<input type=\"hidden\" name=\"back\" value=\"" + e(back) + "\">"
                + (fp ? "<button>" + e(label) + "</button>" : "<button class=\"smallbtn reply\" style=\"cursor:pointer\">" + e(label) + "</button>") + "</form>";
    }

    /** 写真閲覧 / 友達追加 / プロフ閲覧 buttons of a キャラ. */
    private String charaButtons(HttpServletRequest request, CrmUser u, Chara c, boolean friend, String back, boolean profLabelShort) {
        return charaButtons(request, u, c, friend, back, profLabelShort, true);
    }

    /** The same; {@code showFriendAdded} false = no 友達追加済 (友達追加リスト — every キャラ there is a friend). */
    private String charaButtons(HttpServletRequest request, CrmUser u, Chara c, boolean friend, String back, boolean profLabelShort,
                                boolean showFriendAdded) {
        StringBuilder b = new StringBuilder();
        b.append(c.getPhotoUrl() == null ? "<span class=\"smallbtn\" style=\"opacity:.4\">写真閲覧</span>"
                : "<a class=\"smallbtn\" href=\"/member/photo?c=" + c.getId() + "\">写真閲覧</a>");
        if (friend) {
            if (showFriendAdded) b.append("<span class=\"smallbtn\" style=\"opacity:.55\">友達追加済</span>");
        } else {
            b.append("<form method=\"post\" action=\"/member/friends/add\" style=\"display:contents\">").append(csrf(request))
                    .append("<input type=\"hidden\" name=\"c\" value=\"").append(c.getId()).append("\"><input type=\"hidden\" name=\"back\" value=\"")
                    .append(e(back)).append("\"><button class=\"smallbtn\" style=\"cursor:pointer\">友達追加</button></form>");
        }
        b.append("<a class=\"smallbtn\" href=\"/member/chara?c=").append(c.getId()).append("\">").append(profLabelShort ? "プロフ閲覧" : "プロフィール閲覧").append("</a>");
        return b.toString();
    }

    /** A キャラ row (友達追加リスト — no age next to the name, no 友達追加済 / 検索結果). */
    private String person(HttpServletRequest request, CrmUser u, Chara c, boolean friend, String back, boolean showAge, boolean showFriendAdded) {
        String info = showAge ? ageText(c) + (c.getPref() == null ? "" : "　" + e(c.getPref())) : (c.getPref() == null ? "" : e(c.getPref()));
        return "<div class=\"person\"><div class=\"avatar\">" + avatar(u, c) + "</div><div class=\"grow\"><b>" + e(c.getName()) + "</b>　<small>"
                + info + "</small><div class=\"pbtns\">"
                + charaButtons(request, u, c, friend, back, true, showFriendAdded)
                + "<a class=\"smallbtn reply\" href=\"/member/reply?c=" + c.getId() + "\">メッセージ送信</a></div></div></div>";
    }

    /** The キャラ's photo as the avatar once 写真閲覧 is free / used, else the design's 👤. */
    private String avatar(CrmUser u, Chara c) {
        if (c == null) return "🎧";
        if (c.getPhotoUrl() == null || !site.isOpen(u, MemberUnlockService.PHOTO, c.getId())) return "👤";
        return "<img src=\"" + e(c.getPhotoUrl()) + "\" alt=\"\" style=\"width:100%;height:100%;object-fit:cover;border-radius:7px\">";
    }

    private String preview(CrmUser u, MemberSiteService.InboxItem it) {
        Message m = it.message;
        if (it.sent || !it.unread || site.cost(MemberSiteService.COST_BODY, u) <= 0) {
            String body = MemberSiteService.displayBody(m).replaceAll("\\s+", " ").trim();
            if (!body.isEmpty()) return body.length() > 30 ? body.substring(0, 30) + "…" : body;
        }
        String s = m.getSubject();
        return s != null && !s.trim().isEmpty() ? s.trim() : "メッセージが届いています";
    }

    /** No キャラ = サポート窓口 (キャラ指定なしの送信). */
    private static String name(Chara c) {
        return c == null ? MemberSiteService.SUPPORT_NAME : c.getName();
    }

    private static String ageText(Chara c) {
        return c == null || c.getAge() == null ? "" : c.getAge() + "歳";
    }

    private static String age(Chara c) {
        String a = ageText(c);
        return a.isEmpty() ? "" : " " + a;
    }

    private static String when(Message m) {
        LocalDateTime t = m.getSentAt() != null ? m.getSentAt() : m.getCreatedAt();
        if (t == null) return "";
        LocalDate d = t.toLocalDate(), today = LocalDate.now();
        if (d.equals(today)) return t.format(HM);
        if (d.equals(today.minusDays(1))) return "昨日 " + t.format(HM);
        return t.format(MDHM);
    }

    private static String select(String name, List<String> options, String current) {
        StringBuilder b = new StringBuilder("<select name=\"" + name + "\"><option value=\"\">指定しない</option>");
        for (String o : options) b.append("<option value=\"").append(e(o)).append("\"").append(sel(o, current)).append(">").append(e(o)).append("</option>");
        return b.append("</select>").toString();
    }

    private static String sel(String value, String current) {
        return value.equals(current) ? " selected" : "";
    }

    private static String ageOpt(String value, String label, String current) {
        return "<option value=\"" + value + "\"" + sel(value, current) + ">" + label + "</option>";
    }

    /** Only paths on this site (no open redirect). */
    private static String safeBack(String back) {
        if (back == null || !back.startsWith("/member/") || back.startsWith("//") || back.contains("\\")) return "/member/menu";
        return back;
    }

    private static String nz(String v, String dflt) {
        return v == null || v.trim().isEmpty() ? dflt : v;
    }

    private static String e(String v) {
        return HtmlUtils.htmlEscape(v == null ? "" : v, "UTF-8");
    }
}
