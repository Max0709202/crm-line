package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.MemberConfirmToken;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.MemberConfirmTokenRepository;
import com.crm.util.CsvUtil;
import com.crm.util.LogSafe;
import com.crm.util.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Member self-registration from the pre-login top page:
 * <ol>
 *   <li>{@link #register}: the form (性別・ニックネーム・メールアドレス・パスワード) creates a 仮登録 user
 *       (STATUS = PENDING) with the next login ID (10000, 10001, …) and the member's own password,
 *       and mails the 本登録 link;</li>
 *   <li>{@link #confirm}: opening the link turns the user ACTIVE (and grants the 初期ポイント).</li>
 * </ol>
 * Registering again with the address of a user still in 仮登録 updates that user and mails a
 * fresh link, so a lost mail never locks the address.
 */
@Service
public class MemberRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationService.class);

    public static final int TOKEN_VALID_HOURS = 24;
    private static final int MAX_NICKNAME = 12;
    private static final int MAX_PASSWORD = 32;
    /** Per client IP: at most this many registrations in {@link #WINDOW_MS} (mail flooding guard). */
    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    public static class RegistrationException extends RuntimeException {
        private final List<String> errors;
        public RegistrationException(List<String> errors) { super(String.join(" / ", errors)); this.errors = errors; }
        public List<String> getErrors() { return errors; }
    }

    /** Result of opening a 本登録 link. */
    public enum ConfirmResult { CONFIRMED, ALREADY_ACTIVE, EXPIRED, INVALID }

    public static final class Confirmation {
        public final ConfirmResult result;
        public final CrmUser user;
        Confirmation(ConfirmResult result, CrmUser user) { this.result = result; this.user = user; }
    }

    private final CrmUserRepository userRepository;
    private final MemberConfirmTokenRepository tokenRepository;
    private final MemberLoginIdService loginIdService;
    private final PasswordEncoder passwordEncoder;
    private final PointSettingService pointSettingService;
    private final UserPointService userPointService;
    private final MailTemplateService mailTemplateService;
    private final Map<String, Deque<Long>> recentByIp = new ConcurrentHashMap<>();

    public MemberRegistrationService(CrmUserRepository userRepository, MemberConfirmTokenRepository tokenRepository,
                                     MemberLoginIdService loginIdService, PasswordEncoder passwordEncoder,
                                     PointSettingService pointSettingService, UserPointService userPointService,
                                     MailTemplateService mailTemplateService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.loginIdService = loginIdService;
        this.passwordEncoder = passwordEncoder;
        this.pointSettingService = pointSettingService;
        this.userPointService = userPointService;
        this.mailTemplateService = mailTemplateService;
    }

    /**
     * Creates (or refreshes) the 仮登録 user and mails the 本登録 link.
     *
     * @param confirmBaseUrl e.g. {@code https://example.jp/member/confirm} — the token is appended
     */
    @Transactional
    public CrmUser register(String gender, String nickname, String email, String password, boolean agreed,
                            String clientIp, String confirmBaseUrl) {
        String mail = email == null ? "" : email.trim().toLowerCase();
        String nick = nickname == null ? "" : nickname.trim();
        String pw = password == null ? "" : password;
        List<String> errors = new ArrayList<>();
        if (!"male".equals(gender) && !"female".equals(gender)) errors.add("性別を選んでください");
        if (nick.isEmpty()) errors.add("ニックネームを入力してください");
        else if (nick.length() > MAX_NICKNAME) errors.add("ニックネームは" + MAX_NICKNAME + "文字までです");
        if (mail.isEmpty() || !CsvUtil.isValidEmail(mail)) errors.add("メールアドレスを正しく入力してください");
        if (pw.length() < 8 || pw.length() > MAX_PASSWORD || !pw.matches("[A-Za-z0-9]+")
                || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*[0-9].*")) {
            errors.add("パスワードは英字と数字を両方ふくむ8〜" + MAX_PASSWORD + "文字の半角英数字で入力してください");
        }
        if (!agreed) errors.add("利用規約とプライバシーポリシーへの同意が必要です");
        if (!errors.isEmpty()) throw new RegistrationException(errors);
        if (!allow(clientIp)) {
            throw new RegistrationException(java.util.Collections.singletonList(
                    "短時間に何度も登録が行われました。しばらくしてからもう一度お試しください。"));
        }

        Optional<CrmUser> existing = userRepository.findByEmail(mail);
        CrmUser u;
        if (existing.isPresent()) {
            u = existing.get();
            if (!CrmUser.STATUS_PENDING.equals(u.getStatus())) {
                throw new RegistrationException(java.util.Collections.singletonList(
                        "このメールアドレスはすでに登録されています。ログインしてご利用ください。"));
            }
        } else {
            u = new CrmUser();
            u.setEmail(mail);
            u.setStatus(CrmUser.STATUS_PENDING);
            u.setLoginId(loginIdService.next());
            int at = mail.indexOf('@');
            u.setCarrierDomain(mail.substring(at + 1));
        }
        u.setDisplayName(nick);
        u.setGender("male".equals(gender) ? "M" : "F");
        u.setLoginPassword(passwordEncoder.encode(pw));
        u = userRepository.save(u);

        MemberConfirmToken t = new MemberConfirmToken();
        t.setToken(TokenGenerator.generateReplyToken());
        t.setUserId(u.getId());
        t.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS));
        tokenRepository.save(t);

        // メールテンプレート設定 › 仮登録通知 (the built-in text while it is unwritten)
        mailTemplateService.sendProvisional(u, confirmBaseUrl + "?token=" + t.getToken(), t.getExpiresAt());
        return u;
    }

    /** Opens a 本登録 link: PENDING → ACTIVE (+ 初期ポイント). Reopening a used link is harmless. */
    @Transactional
    public Confirmation confirm(String token) {
        if (token == null || token.trim().isEmpty()) return new Confirmation(ConfirmResult.INVALID, null);
        Optional<MemberConfirmToken> found = tokenRepository.findById(token.trim());
        if (!found.isPresent()) return new Confirmation(ConfirmResult.INVALID, null);
        MemberConfirmToken t = found.get();
        CrmUser u = userRepository.findById(t.getUserId()).orElse(null);
        if (u == null) return new Confirmation(ConfirmResult.INVALID, null);
        if (!CrmUser.STATUS_PENDING.equals(u.getStatus())) {
            return new Confirmation(t.getUsedAt() != null ? ConfirmResult.ALREADY_ACTIVE : ConfirmResult.INVALID, u);
        }
        if (t.getUsedAt() != null || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            return new Confirmation(ConfirmResult.EXPIRED, u);
        }
        u.setStatus(CrmUser.STATUS_ACTIVE);
        userRepository.save(u);
        t.setUsedAt(LocalDateTime.now());
        tokenRepository.save(t);
        int initial = pointSettingService.getInitialPoints();
        if (initial > 0 && userPointService.get(u.getId()) == 0) userPointService.set(u.getId(), initial);
        log.info("member confirmed: user={} loginId={}", u.getId(), LogSafe.of(u.getLoginId()));
        mailTemplateService.sendRegistered(u);   // メールテンプレート設定 › 本登録通知
        return new Confirmation(ConfirmResult.CONFIRMED, u);
    }

    private boolean allow(String ip) {
        String key = ip == null ? "" : ip;
        long now = System.currentTimeMillis();
        if (recentByIp.size() > 10000) {   // drop IPs with nothing left in the window
            recentByIp.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long last = e.getValue().peekLast();
                    return last == null || now - last > WINDOW_MS;
                }
            });
        }
        Deque<Long> q = recentByIp.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            if (q.size() >= MAX_PER_WINDOW) return false;
            q.addLast(now);
            return true;
        }
    }
}
