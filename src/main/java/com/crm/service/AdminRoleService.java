package com.crm.service;

import com.crm.entity.AdminRole;
import com.crm.entity.AdminUser;
import com.crm.repository.AdminRoleRepository;
import com.crm.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 権限設定 — staff roles. Each role has:
 * <ul>
 *   <li>name / 担当者名 / color (the admin sidebar is tinted with the logged-in role's color);</li>
 *   <li>the user fields shown masked (住所・メールアドレス・電話番号 — see {@link #maskEmail} etc.);</li>
 *   <li>the menu items hidden from it ({@link #MENU}): removed from the sidebar and their pages
 *       refused (see {@link com.crm.interceptor.RoleInterceptor});</li>
 *   <li>its own login ID / password, stored on an {@link AdminUser} row so login and sessions work
 *       as before. The row is created when the role's first password is set.</li>
 * </ul>
 * The first role in the list (総長) can't hide 権限設定, so nobody can lock themselves out, and on
 * first start it takes over the existing admin login.
 */
@Service
public class AdminRoleService {

    private static final Logger log = LoggerFactory.getLogger(AdminRoleService.class);

    /** One sidebar item that can be hidden per role. {@code path} is its page URL prefix. */
    public static final class MenuItem {
        private final String key;
        private final String name;
        private final String path;
        MenuItem(String key, String name, String path) { this.key = key; this.name = name; this.path = path; }
        public String getKey() { return key; }
        public String getName() { return name; }
        public String getPath() { return path; }
    }

    public static final class MenuGroup {
        private final String group;
        private final List<MenuItem> items;
        MenuGroup(String group, MenuItem... items) { this.group = group; this.items = Arrays.asList(items); }
        public String getGroup() { return group; }
        public List<MenuItem> getItems() { return items; }
    }

    /** The admin sidebar's items in sidebar order (管理トップ / 入金レポート are always shown). */
    public static final List<MenuGroup> MENU = Collections.unmodifiableList(Arrays.asList(
            new MenuGroup("会員管理",
                    new MenuItem("users", "ユーザー管理", "/manager/users"),
                    new MenuItem("support", "サポート窓口", "/manager/support"),
                    new MenuItem("characters", "キャラ登録", "/manager/characters"),
                    new MenuItem("points", "ポイント設定", "/manager/settings/points"),
                    new MenuItem("folders", "フォルダ設定", "/manager/settings/folders")),
            new MenuGroup("配信管理",
                    new MenuItem("inbox", "受信/返信管理", "/manager/inbox"),
                    new MenuItem("messages", "個別メッセージ管理", "/manager/messages"),
                    new MenuItem("broadcast", "一斉送信/返信履歴", "/manager/messages/broadcast"),
                    new MenuItem("diffHistory", "差分スケジュール履歴", "/manager/settings/diff-schedule/history"),
                    new MenuItem("diffSchedule", "差分スケジュール設定", "/manager/settings/diff-schedule"),
                    new MenuItem("senderNames", "メール送信設定", "/manager/settings/sender-names"),
                    new MenuItem("templates", "定型文", "/manager/settings/message-templates"),
                    new MenuItem("line", "LINE設定", "/manager/line-settings"),
                    new MenuItem("sms", "SMS配信設定", "/manager/settings/sms"),
                    new MenuItem("carrierPool", "キャリア登録設定", "/manager/carrier-pool"),
                    new MenuItem("imapEnv", "IMAP 監視同期", "/manager/settings/imap-env"),
                    new MenuItem("externalLinks", "外部リンクドメイン生成", "/manager/settings/external-link-domains")),
            new MenuGroup("スタッフ管理",
                    new MenuItem("roles", "権限設定", "/manager/settings/roles"),
                    new MenuItem("auditLog", "操作ログ", "/manager/settings/audit-log")),
            new MenuGroup("広告管理",
                    new MenuItem("adCodes", "広告設定", "/manager/ad-codes")),
            new MenuGroup("サイト構成",
                    new MenuItem("domain", "ドメイン設定", "/manager/settings/domain"),
                    new MenuItem("payments", "決済関連設定", "/manager/settings/payments"),
                    new MenuItem("homeHtml", "本ドメイン表示設定", "/manager/settings/home-html"),
                    new MenuItem("siteDesign", "番組デザイン設定", "/manager/settings/site-design"),
                    new MenuItem("memoHtmlBulk", "HTML 一括編集", "/manager/settings/memo-html-bulk"),
                    new MenuItem("htmlImages", "HTML画像管理", "/manager/settings/html-images"),
                    new MenuItem("relayServers", "リレーサーバー設定", "/manager/settings/relay-servers"),
                    new MenuItem("backup", "バックアップ設定", "/manager/settings/backup"))));

    /** Items the top role can never hide (lock-out prevention). */
    public static final List<String> TOP_ROLE_LOCKED = Collections.singletonList("roles");
    /** Maskable user fields. */
    public static final List<String> FIELDS = Collections.unmodifiableList(Arrays.asList("address", "email", "phone"));
    public static final List<String> COLORS = Collections.unmodifiableList(Arrays.asList(
            "#e6b85c", "#b49cf7", "#7cb3f7", "#5fd3a8", "#f78fa0", "#5cc8dc", "#f6a565", "#a3b1c2"));

    private static final Pattern LOGIN_ID = Pattern.compile("^[A-Za-z0-9_.\\-]{4,20}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern THREAD_PATH = Pattern.compile("^/manager/users/\\d+/thread(/.*)?$");

    /** The logged-in role as the layout / masking need it. */
    public static final class RoleView {
        private final Long id;
        private final String name;
        private final String holder;
        private final String color;
        private final Set<String> hidden;
        private final Set<String> mask;

        RoleView(AdminRole r) {
            this.id = r.getId();
            this.name = r.getName();
            this.holder = r.getHolder();
            this.color = r.getColor();
            this.hidden = Collections.unmodifiableSet(new LinkedHashSet<>(r.getHiddenList()));
            this.mask = Collections.unmodifiableSet(new LinkedHashSet<>(r.getMaskList()));
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getHolder() { return holder; }
        public String getColor() { return color; }
        public Set<String> getHidden() { return hidden; }
        public Set<String> getMask() { return mask; }
        public boolean isHidden(String key) { return hidden.contains(key); }
        public boolean isMasked(String field) { return mask.contains(field); }
        /** True when every item of a sidebar group is hidden (the group title goes too). */
        public boolean isGroupHidden(String group) {
            for (MenuGroup g : MENU) {
                if (!g.getGroup().equals(group)) continue;
                for (MenuItem it : g.getItems()) if (!hidden.contains(it.getKey())) return false;
                return true;
            }
            return false;
        }
    }

    /** What the 権限設定 screen submits for one role. */
    public static final class RoleInput {
        public String name;
        public String holder;
        public String color;
        public String loginId;
        public List<String> mask = new ArrayList<>();
        public List<String> hidden = new ArrayList<>();
        /** null / empty = keep the current password. */
        public String password;
    }

    public static class RoleException extends RuntimeException {
        private final int status;
        public RoleException(int status, String msg) { super(msg); this.status = status; }
        public int getStatus() { return status; }
    }

    private final AdminRoleRepository repository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminRoleService(AdminRoleRepository repository, AdminUserRepository adminUserRepository,
                            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * First start: the four default roles. 総長 takes over the existing admin login (the first
     * active ADMIN_USER), so whoever logs in today keeps their ID / password and becomes 総長.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureDefaults() {
        if (repository.count() > 0) return;
        Optional<AdminUser> admin = adminUserRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .min(Comparator.comparing(AdminUser::getId));
        AdminRole top = role("総長", COLORS.get(0), admin.map(AdminUser::getLoginId).orElse("socho"), 0,
                Collections.<String>emptyList(), Collections.<String>emptyList());
        admin.ifPresent(a -> {
            top.setAdminUserId(a.getId());
            top.setPasswordUpdatedAt(a.getUpdatedAt());
            a.setName(top.getName());
            adminUserRepository.save(a);
        });
        repository.save(top);
        repository.save(role("幹部", COLORS.get(1), "kanbu", 1,
                Collections.<String>emptyList(), Collections.singletonList("domain")));
        repository.save(role("特攻隊員", COLORS.get(2), "tokkou", 2,
                Collections.singletonList("phone"), Arrays.asList("domain", "adCodes", "auditLog")));
        repository.save(role("新人", COLORS.get(3), "shinjin", 3,
                Arrays.asList("address", "email", "phone"),
                Arrays.asList("domain", "payments", "adCodes", "broadcast", "auditLog", "characters")));
        log.info("ADMIN_ROLE seeded; 総長 linked to ADMIN_USER {}", top.getAdminUserId());
    }

    private static AdminRole role(String name, String color, String loginId, int order,
                                  List<String> mask, List<String> hidden) {
        AdminRole r = new AdminRole();
        r.setName(name);
        r.setColor(color);
        r.setLoginId(loginId);
        r.setSortOrder(order);
        r.setDefaultRole(true);
        r.setMaskList(mask);
        r.setHiddenList(hidden);
        return r;
    }

    public List<AdminRole> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc();
    }

    /** The role an admin login belongs to; empty for an admin not tied to any role (no limits). */
    public Optional<RoleView> viewFor(Long adminUserId) {
        if (adminUserId == null) return Optional.empty();
        return repository.findByAdminUserId(adminUserId).map(RoleView::new);
    }

    public boolean isTop(AdminRole r) {
        List<AdminRole> all = list();
        return !all.isEmpty() && all.get(0).getId().equals(r.getId());
    }

    /** Menu key of the page at {@code path} (longest path match), or null for pages outside the menu. */
    public static String menuKeyForPath(String path) {
        if (path == null) return null;
        if (THREAD_PATH.matcher(path).matches()) return "inbox";   // where 受信/返信管理 lands
        String best = null;
        int bestLen = -1;
        for (MenuGroup g : MENU) {
            for (MenuItem it : g.getItems()) {
                String p = it.getPath();
                if ((path.equals(p) || path.startsWith(p + "/")) && p.length() > bestLen) {
                    best = it.getKey();
                    bestLen = p.length();
                }
            }
        }
        return best;
    }

    public static boolean isMenuKey(String key) {
        for (MenuGroup g : MENU) {
            for (MenuItem it : g.getItems()) if (it.getKey().equals(key)) return true;
        }
        return false;
    }

    public AdminUser linkedUser(AdminRole r) {
        return r.getAdminUserId() == null ? null : adminUserRepository.findById(r.getAdminUserId()).orElse(null);
    }

    /** Creates ({@code id == null}) or updates a role. Throws {@link RoleException} on bad input. */
    @Transactional
    public AdminRole save(Long id, RoleInput in) {
        AdminRole r = id == null ? new AdminRole()
                : repository.findById(id).orElseThrow(() -> new RoleException(404, "権限が見つかりません"));
        String name = trim(in.name);
        String holder = trim(in.holder);
        String loginId = trim(in.loginId);
        if (name.isEmpty()) throw new RoleException(400, "権限名を入力してください");
        if (name.length() > 12) throw new RoleException(400, "権限名は12文字までです");
        if (holder.length() > 20) throw new RoleException(400, "担当者名は20文字までです");
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw new RoleException(400, "ログインIDは半角英数字（_ . - も可）4〜20文字で入力してください");
        }
        Optional<AdminRole> sameId = repository.findByLoginIdIgnoreCase(loginId);
        if (sameId.isPresent() && !sameId.get().getId().equals(r.getId())) {
            throw new RoleException(409, "このログインIDはすでに使われています");
        }
        Optional<AdminUser> sameUser = adminUserRepository.findByLoginId(loginId);
        if (sameUser.isPresent() && !sameUser.get().getId().equals(r.getAdminUserId())) {
            throw new RoleException(409, "このログインIDはすでに使われています");
        }
        String pw = in.password == null ? "" : in.password;
        AdminUser user = linkedUser(r);
        if (user == null && pw.isEmpty()) throw new RoleException(400, "パスワードを設定してください（初回は必須）");
        if (!pw.isEmpty() && pw.length() < 8) throw new RoleException(400, "パスワードは8文字以上で入力してください");
        if (pw.length() > 100) throw new RoleException(400, "パスワードが長すぎます");
        String color = in.color != null && COLOR.matcher(in.color).matches() ? in.color.toLowerCase() : COLORS.get(0);

        if (id == null) {
            int max = list().stream().mapToInt(AdminRole::getSortOrder).max().orElse(-1);
            r.setSortOrder(max + 1);
        }
        r.setName(name);
        r.setHolder(holder.isEmpty() ? null : holder);
        r.setColor(color);
        r.setLoginId(loginId);
        r.setMaskList(only(in.mask, FIELDS));
        List<String> hidden = new ArrayList<>();
        boolean top = id != null && isTop(r);
        for (String k : in.hidden) {
            if (isMenuKey(k) && !(top && TOP_ROLE_LOCKED.contains(k)) && !hidden.contains(k)) hidden.add(k);
        }
        r.setHiddenList(hidden);

        if (user == null) {
            user = new AdminUser();
            user.setRole("ADMIN");
            user.setIsActive(Boolean.TRUE);
        }
        user.setLoginId(loginId);
        user.setName(holder.isEmpty() ? name : holder);
        if (!pw.isEmpty()) {
            user.setLoginPassword(passwordEncoder.encode(pw));
            r.setPasswordUpdatedAt(LocalDateTime.now());
        }
        user = adminUserRepository.save(user);
        r.setAdminUserId(user.getId());
        return repository.save(r);
    }

    /** Deletes an added role and its login. The default roles can't be deleted. */
    @Transactional
    public void delete(Long id) {
        AdminRole r = repository.findById(id).orElseThrow(() -> new RoleException(404, "権限が見つかりません"));
        if (r.isDefaultRole()) throw new RoleException(400, "既定の権限は削除できません");
        if (r.getAdminUserId() != null) adminUserRepository.deleteById(r.getAdminUserId());
        repository.delete(r);
    }

    // ===== masking (same rules as the 表示例 on 権限設定) =====

    public static String maskEmail(String v) {
        if (v == null || v.isEmpty()) return v;
        int at = v.indexOf('@');
        String local = at < 0 ? v : v.substring(0, at);
        return (local.length() > 2 ? local.substring(0, 2) : local) + "＊＊＊＊@" + (at < 0 ? "" : v.substring(at + 1));
    }

    public static String maskPhone(String v) {
        if (v == null || v.isEmpty()) return v;
        String d = v.replaceAll("\\D", "");
        if (d.length() < 7) return "＊＊＊＊";
        return d.substring(0, 3) + "-＊＊＊＊-" + d.substring(d.length() - 4);
    }

    public static String maskAddress(String v) {
        if (v == null || v.isEmpty()) return v;
        java.util.regex.Matcher m = Pattern.compile("^(.+?[都道府県])").matcher(v);
        return (m.find() ? m.group(1) : "") + "＊＊＊＊＊＊";
    }

    private static List<String> only(Collection<String> in, List<String> allowed) {
        List<String> out = new ArrayList<>();
        if (in == null) return out;
        for (String s : in) if (allowed.contains(s) && !out.contains(s)) out.add(s);
        return out;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
