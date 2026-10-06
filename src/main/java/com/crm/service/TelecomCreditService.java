package com.crm.service;

import com.crm.dto.PaymentForm;
import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.entity.PaymentPoint;
import com.crm.entity.TelecomOrder;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentPointRepository;
import com.crm.repository.TelecomOrderRepository;
import com.crm.util.AesEncryptionUtil;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 決済関連設定 › テレコムクレジット (the client's 基本の決済会社). Spec: telecomcredit.co.jp 接続仕様書
 * (パラメータについて / 決済データ受け取り説明 / クレジットカード都度決済):
 * <ul>
 *   <li>API接続コード = クライアントIP ({@code clientip}, 5 digits, issued by Telecom). The 管理画面
 *       ログインID / パスワード are kept with it (encrypted) for the operator's reference.</li>
 *   <li>ポイント購入 → {@link #startOrder}: a TELECOM_ORDER row, then the member's browser POSTs to
 *       Telecom's 決済画面 with {@code clientip / money / sendid (会員ID) / usrmail / option (注文番号)}.</li>
 *   <li>Telecom sends the 決済データ to {@code /payment/telecom/notify} (GET by default) from its
 *       決済サーバー IPs; {@link #handleNotice} adds the points (rel=yes), records the 入金 and sends
 *       決済入金通知. The page must answer {@code SuccessOK} (HTTP 200), else Telecom resends every
 *       5 minutes for 3 hours — so a resent notice (same 決済識別子 settle_uuid) is not counted twice.</li>
 * </ul>
 */
@Service
public class TelecomCreditService {

    private static final Logger log = LoggerFactory.getLogger(TelecomCreditService.class);

    /** 都度決済 (クレジットカード) 決済要求URL. */
    public static final String ORDER_URL = "https://secure.telecomcredit.co.jp/inetcredit/secure/order.pl";
    public static final String NOTIFY_PATH = "/payment/telecom/notify";
    /** 決済サーバーのIPアドレス帯 (接続仕様書 2026-10). */
    public static final List<String> DEFAULT_SERVER_IPS = Collections.unmodifiableList(Arrays.asList(
            "54.65.177.67", "52.196.8.0", "54.238.8.174", "54.95.89.20"));

    private static final String P = "payment.telecom.";
    private static final String KEY_ENABLED = P + "enabled";
    private static final String KEY_CLIENT_IP = P + "clientip";
    private static final String KEY_LOGIN_ID = P + "login_id";
    private static final String KEY_PASSWORD = P + "password";
    private static final String KEY_METHODS = P + "methods";
    private static final String KEY_SERVER_IPS = P + "server_ips";

    public static final class Settings {
        public boolean enabled;
        public String clientIp = "";
        public String loginId = "";
        public boolean passwordSet;
        /** 決済関連設定 method codes paid through Telecom (default: credit). */
        public List<String> methods = new ArrayList<>();
        public List<String> serverIps = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public String getClientIp() { return clientIp; }
        public String getLoginId() { return loginId; }
        public boolean isPasswordSet() { return passwordSet; }
        public List<String> getMethods() { return methods; }
        public List<String> getServerIps() { return serverIps; }
        /** Ready to take payments. */
        public boolean isReady() { return enabled && clientIp.matches("\\d{5}"); }
    }

    /** Where the member's browser goes for one order (POST, fields as given). */
    public static final class Checkout {
        public final String action;
        public final Map<String, String> fields;
        Checkout(String action, Map<String, String> fields) { this.action = action; this.fields = fields; }
    }

    public static class CheckoutException extends RuntimeException {
        public CheckoutException(String msg) { super(msg); }
    }

    private final CrmSettingRepository settings;
    private final TelecomOrderRepository orderRepository;
    private final CrmUserRepository userRepository;
    private final PaymentService paymentService;
    private final PaymentPointRepository paymentPointRepository;
    private final PaymentSettingService paymentSettingService;
    private final UserPointService userPointService;
    private final MailTemplateService mailTemplateService;
    private final AesEncryptionUtil aes;

    public TelecomCreditService(CrmSettingRepository settings, TelecomOrderRepository orderRepository,
                                CrmUserRepository userRepository, PaymentService paymentService,
                                PaymentPointRepository paymentPointRepository, PaymentSettingService paymentSettingService,
                                UserPointService userPointService, MailTemplateService mailTemplateService,
                                AesEncryptionUtil aes) {
        this.settings = settings;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.paymentPointRepository = paymentPointRepository;
        this.paymentSettingService = paymentSettingService;
        this.userPointService = userPointService;
        this.mailTemplateService = mailTemplateService;
        this.aes = aes;
    }

    /* ===================== 設定 ===================== */

    public Settings getSettings() {
        Settings s = new Settings();
        s.enabled = "true".equals(get(KEY_ENABLED));
        s.clientIp = nz(get(KEY_CLIENT_IP));
        s.loginId = nz(get(KEY_LOGIN_ID));
        s.passwordSet = !nz(get(KEY_PASSWORD)).isEmpty();
        String methods = get(KEY_METHODS);
        s.methods = methods == null ? new ArrayList<>(Collections.singletonList("credit")) : splitCsv(methods);
        String ips = get(KEY_SERVER_IPS);
        s.serverIps = ips == null ? new ArrayList<>(DEFAULT_SERVER_IPS) : splitCsv(ips);
        return s;
    }

    /**
     * Saves the connection. {@code password} blank keeps the saved one; {@code clearPassword}
     * removes it. Throws IllegalArgumentException on a bad value.
     */
    @Transactional
    public void saveSettings(boolean enabled, String clientIp, String loginId, String password, boolean clearPassword,
                             List<String> methods, String serverIps) {
        String ip = clientIp == null ? "" : clientIp.trim();
        if (!ip.isEmpty() && !ip.matches("\\d{5}")) throw new IllegalArgumentException("API接続コード（クライアントIP）は5桁の半角数字で入力してください");
        if (enabled && ip.isEmpty()) throw new IllegalArgumentException("接続を有効にする場合はAPI接続コード（クライアントIP）を入力してください");
        String id = loginId == null ? "" : loginId.trim();
        if (id.length() > 100) throw new IllegalArgumentException("ログインIDは100文字までです");
        if (password != null && password.length() > 200) throw new IllegalArgumentException("パスワードは200文字までです");
        List<String> codes = new ArrayList<>();
        if (methods != null) for (String m : methods) if (PaymentSettingService.METHODS.containsKey(m) && !codes.contains(m)) codes.add(m);
        List<String> ipList = new ArrayList<>();
        for (String x : splitCsv(serverIps == null ? "" : serverIps.replace('\r', ',').replace('\n', ',').replace(' ', ','))) {
            if (!x.matches("[0-9a-fA-F.:/]{2,50}")) throw new IllegalArgumentException("決済サーバーのIPアドレスの形式が正しくありません: " + x);
            if (!ipList.contains(x)) ipList.add(x);
        }
        if (ipList.isEmpty()) ipList.addAll(DEFAULT_SERVER_IPS);
        put(KEY_ENABLED, String.valueOf(enabled));
        put(KEY_CLIENT_IP, ip);
        put(KEY_LOGIN_ID, id);
        if (clearPassword) put(KEY_PASSWORD, "");
        else if (password != null && !password.isEmpty()) put(KEY_PASSWORD, aes.encrypt(password));
        put(KEY_METHODS, String.join(",", codes));
        put(KEY_SERVER_IPS, String.join(",", ipList));
    }

    /** Is ポイント購入 with {@code methodCode} paid through Telecom (and Telecom ready)? */
    public boolean handles(String methodCode) {
        Settings s = getSettings();
        return s.isReady() && s.methods.contains(methodCode);
    }

    /* ===================== ポイント購入 ===================== */

    /**
     * Starts a ポイント購入: plan {@code planIndex} of method {@code methodCode} as offered to the
     * member's folder. Returns where the member's browser posts to.
     */
    @Transactional
    public Checkout startOrder(CrmUser user, String methodCode, int planIndex, String returnUrl) {
        Settings s = getSettings();
        if (!s.isReady() || !s.methods.contains(methodCode)) throw new CheckoutException("この決済方法は現在ご利用いただけません");
        PaymentSettingService.Plan plan = null;
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(user.getFolder())) {
            if (!m.getCode().equals(methodCode) || !m.isShown()) continue;
            if (planIndex >= 0 && planIndex < m.getPlans().size() && m.getPlans().get(planIndex).isOffered()) plan = m.getPlans().get(planIndex);
        }
        if (plan == null) throw new CheckoutException("選択されたプランは現在購入できません");
        TelecomOrder o = new TelecomOrder();
        o.setUserId(user.getId());
        o.setMethod(methodCode);
        o.setAmount(plan.getAmount());
        o.setPoints(plan.getPoints());
        o.setStatus(TelecomOrder.STATUS_PENDING);
        o = orderRepository.save(o);

        Map<String, String> f = new LinkedHashMap<>();
        f.put("clientip", s.clientIp);
        f.put("money", String.valueOf(plan.getAmount()));
        f.put("sendid", String.valueOf(user.getId()));
        String mail = user.getEmail() == null ? "" : user.getEmail().trim();
        if (!mail.isEmpty() && mail.length() <= 50 && mail.matches("[\\x21-\\x7e]+")) f.put("usrmail", mail);
        f.put("option", String.valueOf(o.getId()));
        if (returnUrl != null && !returnUrl.isEmpty() && !returnUrl.endsWith("?")) f.put("redirect_back_url", returnUrl);
        return new Checkout(ORDER_URL, f);
    }

    /* ===================== 決済データ受け取り ===================== */

    /** Is {@code ip} one of Telecom's 決済サーバー (ranges allowed)? */
    public boolean isServerIp(String ip) {
        for (String entry : getSettings().serverIps) if (AdminIpService.covers(entry, ip)) return true;
        return false;
    }

    /**
     * Handles one 決済データ. Returns normally when the data needs no resend (handled now, handled
     * before, or not ours / not usable — logged); throws on a temporary failure (Telecom resends).
     */
    @Transactional
    public void handleNotice(Map<String, String> params) {
        Settings s = getSettings();
        String clientIp = nz(params.get("clientip")).trim();
        if (!s.clientIp.isEmpty() && !s.clientIp.equals(clientIp)) {
            log.warn("telecom notice: clientip mismatch ({}), ignored", LogSafe.of(clientIp));
            return;
        }
        Long orderId = parseLong(params.get("option"));
        Optional<TelecomOrder> found = orderId == null ? Optional.<TelecomOrder>empty() : orderRepository.findForUpdate(orderId);
        if (!found.isPresent()) {
            log.warn("telecom notice: unknown order option={} sendid={} money={}", LogSafe.of(params.get("option")),
                    LogSafe.of(params.get("sendid")), LogSafe.of(params.get("money")));
            return;
        }
        TelecomOrder o = found.get();
        String uuid = nz(params.get("settle_uuid")).trim();
        if (TelecomOrder.STATUS_PAID.equals(o.getStatus())) return;   // resent notice of a paid order
        String sendId = nz(params.get("sendid")).trim();
        if (!sendId.isEmpty() && !sendId.equals(String.valueOf(o.getUserId()))) {
            log.warn("telecom notice: order {} sendid {} is not its member {}, ignored", o.getId(), LogSafe.of(sendId), o.getUserId());
            return;
        }
        boolean ok = "yes".equalsIgnoreCase(nz(params.get("rel")).trim());
        o.setResultParams(maskedParams(params));
        if (!ok) {
            o.setStatus(TelecomOrder.STATUS_FAILED);
            orderRepository.save(o);
            log.info("telecom notice: order {} failed (rel={})", o.getId(), LogSafe.of(params.get("rel")));
            return;
        }
        Integer money = parseInt(params.get("money"));
        if (money == null || !money.equals(o.getAmount())) {
            log.warn("telecom notice: order {} money {} is not the order's {}, not credited", o.getId(), LogSafe.of(params.get("money")), o.getAmount());
            o.setStatus(TelecomOrder.STATUS_FAILED);
            orderRepository.save(o);
            return;
        }
        if (!uuid.isEmpty()) {
            Optional<TelecomOrder> same = orderRepository.findBySettleUuid(uuid);
            if (same.isPresent() && !same.get().getId().equals(o.getId())) {
                log.warn("telecom notice: settle_uuid already used by order {}, ignored", same.get().getId());
                return;
            }
            o.setSettleUuid(uuid.length() > 64 ? uuid.substring(0, 64) : uuid);
        }
        CrmUser user = userRepository.findById(o.getUserId()).orElse(null);
        if (user == null) {
            log.warn("telecom notice: order {} member {} no longer exists", o.getId(), o.getUserId());
            o.setStatus(TelecomOrder.STATUS_FAILED);
            orderRepository.save(o);
            return;
        }

        PaymentForm form = new PaymentForm();
        form.setUserId(user.getId());
        form.setAmount(BigDecimal.valueOf(o.getAmount()));
        form.setPaymentMethod(o.getMethod());
        form.setStatus(Payment.STATUS_PAID);
        form.setInvoiceNumber("TC" + o.getId());
        form.setMemo("テレコムクレジット決済" + (uuid.isEmpty() ? "" : "（決済識別子 " + uuid + "）"));
        Payment payment = paymentService.create(form);
        if (o.getPoints() > 0) {
            PaymentPoint pp = new PaymentPoint();
            pp.setPaymentId(payment.getId());
            pp.setPoints(o.getPoints());
            paymentPointRepository.save(pp);
            userPointService.add(user.getId(), o.getPoints());
        }
        o.setStatus(TelecomOrder.STATUS_PAID);
        o.setPaidAt(LocalDateTime.now());
        o.setPaymentId(payment.getId());
        orderRepository.save(o);
        log.info("telecom notice: order {} paid, member {} +{}pt (payment {})", o.getId(), user.getId(), o.getPoints(), payment.getId());

        String label = o.getMethod();
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(user.getFolder())) {
            if (m.getCode().equals(o.getMethod())) label = m.getLabel();
        }
        mailTemplateService.sendPayment(user, payment.getAmount(), o.getPoints(), label, payment.getPaidAt(), payment.getId());
    }

    /** The notice as received, minus the member's phone / e-mail / card name. */
    static String maskedParams(Map<String, String> params) {
        Set<String> masked = new LinkedHashSet<>(Arrays.asList("telno", "email", "username", "sendpass"));
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (b.length() > 0) b.append('&');
            String v = e.getValue() == null ? "" : e.getValue();
            if (masked.contains(e.getKey()) && !v.isEmpty()) v = "***";
            if (v.length() > 255) v = v.substring(0, 255);
            b.append(e.getKey()).append('=').append(v);
        }
        return b.length() > 4000 ? b.substring(0, 4000) : b.toString();
    }

    /* ===================== helpers ===================== */

    private static List<String> splitCsv(String v) {
        List<String> out = new ArrayList<>();
        for (String x : v.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    private static Long parseLong(String v) {
        try { return v == null ? null : Long.valueOf(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInt(String v) {
        try { return v == null ? null : Integer.valueOf(v.trim().replace(",", "")); } catch (NumberFormatException e) { return null; }
    }

    private static String nz(String v) { return v == null ? "" : v; }

    private String get(String key) {
        return settings.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void put(String key, String value) {
        CrmSetting s = settings.findBySettingKey(key).orElseGet(() -> {
            CrmSetting n = new CrmSetting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        settings.save(s);
    }
}
