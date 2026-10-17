package com.crm.service;

import com.crm.dto.PaymentForm;
import com.crm.entity.BitcashOrder;
import com.crm.entity.CrmSetting;
import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.entity.PaymentPoint;
import com.crm.repository.BitcashOrderRepository;
import com.crm.repository.CrmSettingRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentPointRepository;
import com.crm.util.AesEncryptionUtil;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 決済関連設定 › ビットキャッシュ (画面遷移型 BitCash 決済サービス 導入マニュアル 2026/07/28 版):
 * <ul>
 *   <li>店舗ID / 認証キー / 通知キー come from BitCash Partners「システム情報」(keys stored encrypted).</li>
 *   <li>ポイント購入 → {@link #startOrder}: a BITCASH_ORDER row, then 決済開始処理 (prepareSettle) returns
 *       the 決済画面 URL the member's browser is redirected to.</li>
 *   <li>ビットキャッシュ POSTs the 完了通知 to {@value #NOTIFY_PATH}; {@link #handleNotice} checks its
 *       SHA-1 hash with the 通知キー, adds the points, records the 入金 and sends 決済入金通知.
 *       Not answering 2xx makes ビットキャッシュ resend for about 5 minutes, and a notice can arrive more
 *       than once — an order is credited only once.</li>
 *   <li>When the member comes back ({@code return_url}), {@link #confirmPending} asks 決済確認処理
 *       (confirmSettle) about their open orders, so a lost 完了通知 still credits the points.</li>
 * </ul>
 */
@Service
public class BitcashService {

    private static final Logger log = LoggerFactory.getLogger(BitcashService.class);

    public static final String NOTIFY_PATH = "/payment/bitcash/notify";
    /** return_url / cancel_url: a public page that moves on to ポイント購入 from our own site, so the
     *  SameSite=Strict session cookie is sent (a redirect straight from ビットキャッシュ would be cross-site). */
    public static final String RETURN_PATH = "/payment/bitcash/return";
    /** 支払方法選択画面 must link here (導入マニュアル 3.2 残高引継ページ). */
    public static final String MERGE_URL = "https://bitcash.jp/bitcash/merge";
    public static final int MAX_PRICE = 200000;
    /** 決済を可能とする期間 (秒) sent with 決済開始処理. */
    static final int TIMEOUT_SECONDS = 3600;

    private static final String PROD_BASE = "https://settle.bitcash.co.jp/settle/tri?act=";
    private static final String TEST_BASE = "https://test-settle.bitcash.co.jp/settle/tri?act=";
    /** 完了通知の送信元: 本番 (マニュアル 7.3) + 決済テスト環境 (4.4). */
    public static final List<String> DEFAULT_SERVER_IPS = Collections.unmodifiableList(Arrays.asList(
            "203.174.69.131", "203.174.69.132", "203.174.69.144", "203.174.70.26", "203.174.70.27"));
    public static final String RATING_ST = "19";
    public static final String RATING_EX = "99";

    private static final String P = "payment.bitcash.";
    private static final String KEY_ENABLED = P + "enabled";
    private static final String KEY_SHOP_ID = P + "shop_id";
    private static final String KEY_SHOP_KEY = P + "shop_key";
    private static final String KEY_NOTIFY_KEY = P + "notify_key";
    private static final String KEY_RATING = P + "rating";
    private static final String KEY_METHODS = P + "methods";
    private static final String KEY_TEST_MODE = P + "test_mode";
    private static final String KEY_SERVER_IPS = P + "server_ips";

    public static final class Settings {
        public boolean enabled;
        public String shopId = "";
        public boolean shopKeySet;
        public boolean notifyKeySet;
        public String rating = RATING_ST;
        /** 決済関連設定 method codes paid through ビットキャッシュ (default: 電子マネー). */
        public List<String> methods = new ArrayList<>();
        public boolean testMode;
        public List<String> serverIps = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public String getShopId() { return shopId; }
        public boolean isShopKeySet() { return shopKeySet; }
        public boolean isNotifyKeySet() { return notifyKeySet; }
        public String getRating() { return rating; }
        public List<String> getMethods() { return methods; }
        public boolean isTestMode() { return testMode; }
        public List<String> getServerIps() { return serverIps; }
        /** Ready to take payments. */
        public boolean isReady() { return enabled && !shopId.isEmpty() && shopKeySet && notifyKeySet; }
    }

    public static class CheckoutException extends RuntimeException {
        public CheckoutException(String msg) { super(msg); }
    }

    private final CrmSettingRepository settings;
    private final BitcashOrderRepository orderRepository;
    private final CrmUserRepository userRepository;
    private final PaymentService paymentService;
    private final PaymentPointRepository paymentPointRepository;
    private final PaymentSettingService paymentSettingService;
    private final UserPointService userPointService;
    private final MailTemplateService mailTemplateService;
    private final AesEncryptionUtil aes;
    private final TransactionTemplate tx;

    public BitcashService(CrmSettingRepository settings, BitcashOrderRepository orderRepository,
                          CrmUserRepository userRepository, PaymentService paymentService,
                          PaymentPointRepository paymentPointRepository, PaymentSettingService paymentSettingService,
                          UserPointService userPointService, MailTemplateService mailTemplateService,
                          AesEncryptionUtil aes, TransactionTemplate tx) {
        this.settings = settings;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.paymentPointRepository = paymentPointRepository;
        this.paymentSettingService = paymentSettingService;
        this.userPointService = userPointService;
        this.mailTemplateService = mailTemplateService;
        this.aes = aes;
        this.tx = tx;
    }

    /* ===================== 設定 ===================== */

    public Settings getSettings() {
        Settings s = new Settings();
        s.enabled = "true".equals(get(KEY_ENABLED));
        s.shopId = nz(get(KEY_SHOP_ID));
        s.shopKeySet = !nz(get(KEY_SHOP_KEY)).isEmpty();
        s.notifyKeySet = !nz(get(KEY_NOTIFY_KEY)).isEmpty();
        s.rating = RATING_EX.equals(get(KEY_RATING)) ? RATING_EX : RATING_ST;
        String methods = get(KEY_METHODS);
        s.methods = methods == null ? new ArrayList<>(Collections.singletonList("emoney")) : splitCsv(methods);
        s.testMode = "true".equals(get(KEY_TEST_MODE));
        String ips = get(KEY_SERVER_IPS);
        s.serverIps = ips == null ? new ArrayList<>(DEFAULT_SERVER_IPS) : splitCsv(ips);
        return s;
    }

    /**
     * Saves the connection. A blank 認証キー / 通知キー keeps the saved one.
     * Throws IllegalArgumentException on a bad value.
     */
    @Transactional
    public void saveSettings(boolean enabled, String shopId, String shopKey, String notifyKey, String rating,
                             List<String> methods, boolean testMode, String serverIps) {
        String id = shopId == null ? "" : shopId.trim();
        if (!id.isEmpty() && !id.matches("[A-Za-z0-9]{1,16}")) throw new IllegalArgumentException("店舗IDは半角英数字で入力してください");
        String sk = shopKey == null ? "" : shopKey.trim();
        String nk = notifyKey == null ? "" : notifyKey.trim();
        if (!sk.isEmpty() && !sk.matches("[\\x21-\\x7e]{1,100}")) throw new IllegalArgumentException("認証キーは半角英数字で入力してください");
        if (!nk.isEmpty() && !nk.matches("[\\x21-\\x7e]{1,100}")) throw new IllegalArgumentException("通知キーは半角英数字で入力してください");
        if (enabled) {
            if (id.isEmpty()) throw new IllegalArgumentException("接続を有効にする場合は店舗IDを入力してください");
            if (sk.isEmpty() && nz(get(KEY_SHOP_KEY)).isEmpty()) throw new IllegalArgumentException("接続を有効にする場合は認証キーを入力してください");
            if (nk.isEmpty() && nz(get(KEY_NOTIFY_KEY)).isEmpty()) throw new IllegalArgumentException("接続を有効にする場合は通知キーを入力してください");
        }
        List<String> codes = new ArrayList<>();
        if (methods != null) for (String m : methods) if (PaymentSettingService.METHODS.containsKey(m) && !codes.contains(m)) codes.add(m);
        List<String> ipList = new ArrayList<>();
        for (String x : splitCsv(serverIps == null ? "" : serverIps.replace('\r', ',').replace('\n', ',').replace(' ', ','))) {
            if (!x.matches("[0-9a-fA-F.:/]{2,50}")) throw new IllegalArgumentException("ビットキャッシュ決済システムのIPアドレスの形式が正しくありません: " + x);
            if (!ipList.contains(x)) ipList.add(x);
        }
        if (ipList.isEmpty()) ipList.addAll(DEFAULT_SERVER_IPS);
        if (enabled && codes.isEmpty()) throw new IllegalArgumentException("接続を有効にする場合は、この接続で決済する決済方法を1つ以上選択してください");
        put(KEY_ENABLED, String.valueOf(enabled));
        put(KEY_SHOP_ID, id);
        if (!sk.isEmpty()) put(KEY_SHOP_KEY, aes.encrypt(sk));
        if (!nk.isEmpty()) put(KEY_NOTIFY_KEY, aes.encrypt(nk));
        put(KEY_RATING, RATING_EX.equals(rating) ? RATING_EX : RATING_ST);
        put(KEY_METHODS, String.join(",", codes));
        put(KEY_TEST_MODE, String.valueOf(testMode));
        put(KEY_SERVER_IPS, String.join(",", ipList));
    }

    /** Is ポイント購入 with {@code methodCode} paid through ビットキャッシュ (and ビットキャッシュ ready)? */
    public boolean handles(String methodCode) {
        Settings s = getSettings();
        return s.isReady() && s.methods.contains(methodCode);
    }

    /* ===================== ポイント購入 (決済開始処理) ===================== */

    /**
     * Starts a ポイント購入: plan {@code planIndex} of method {@code methodCode} as offered to the
     * member's folder. Returns the 決済画面 URL to redirect the member's browser to (during a
     * ビットキャッシュ maintenance, the maintenance page — as the manual asks).
     */
    @Transactional(noRollbackFor = CheckoutException.class)
    public String startOrder(CrmUser user, String methodCode, int planIndex,
                             String notifyUrl, String returnUrl, String cancelUrl) {
        Settings s = getSettings();
        if (!s.isReady() || !s.methods.contains(methodCode)) throw new CheckoutException("この決済方法は現在ご利用いただけません");
        PaymentSettingService.Plan plan = null;
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(user.getFolder())) {
            if (!m.getCode().equals(methodCode) || !m.isShown()) continue;
            if (planIndex >= 0 && planIndex < m.getPlans().size() && m.getPlans().get(planIndex).isOffered()) plan = m.getPlans().get(planIndex);
        }
        if (plan == null) throw new CheckoutException("選択されたプランは現在購入できません");
        if (plan.getAmount() < 1 || plan.getAmount() > MAX_PRICE) {
            throw new CheckoutException("ビットキャッシュでお支払いできる金額は1回" + String.format("%,d", MAX_PRICE) + "円までです");
        }
        BitcashOrder o = new BitcashOrder();
        o.setUserId(user.getId());
        o.setMethod(methodCode);
        o.setAmount(plan.getAmount());
        o.setPoints(plan.getPoints());
        o.setStatus(BitcashOrder.STATUS_PENDING);
        o = orderRepository.save(o);

        Map<String, String> f = new LinkedHashMap<>();
        f.put("shop_id", s.shopId);
        f.put("shop_key", aes.decrypt(get(KEY_SHOP_KEY)));
        f.put("price", String.valueOf(plan.getAmount()));
        f.put("rating", s.rating);
        f.put("order_id", orderCode(o.getId()));
        f.put("timeout", String.valueOf(TIMEOUT_SECONDS));
        f.put("notify_url", notifyUrl);
        f.put("return_url", returnUrl);
        f.put("cancel_url", cancelUrl);
        Map<String, String> res;
        try {
            res = call(apiUrl(s, "prepareSettle"), f);
        } catch (IOException e) {
            log.warn("bitcash prepareSettle failed for order {}: {}", o.getId(), e.toString());
            failOrder(o, "prepareSettle: " + e);
            throw new CheckoutException("決済画面を開けませんでした。しばらくしてからもう一度お試しください");
        }
        String code = nz(res.get("status_code"));
        String settleUrl = decode(nz(res.get("settle_url")));
        o.setResultParams(cut("prepareSettle " + new TreeMap<>(res)));
        if ("0".equals(code) && settleUrl.startsWith("https://")) {
            o.setTranId(cutTranId(res.get("tran_id")));
            orderRepository.save(o);
            log.info("bitcash order {} started, member {} {}円 tran_id={}", o.getId(), user.getId(), o.getAmount(), LogSafe.of(o.getTranId()));
            return settleUrl;
        }
        failOrder(o, null);
        if ("800".equals(code) && settleUrl.startsWith("https://")) return settleUrl;   // メンテナンスページ
        log.warn("bitcash prepareSettle order {}: status {} {}", o.getId(), LogSafe.of(code), LogSafe.of(res.get("status_string")));
        throw new CheckoutException("現在ビットキャッシュでのお支払いをご利用いただけません（エラー " + code + "）");
    }

    /* ===================== 完了通知受領 ===================== */

    /** Is {@code ip} one of ビットキャッシュ's 決済システム servers (ranges allowed)? */
    public boolean isServerIp(String ip) {
        for (String entry : getSettings().serverIps) if (AdminIpService.covers(entry, ip)) return true;
        return false;
    }

    /**
     * Handles one 完了通知 (parameters already URL-decoded). Returns true when it needs no resend
     * (credited now, credited before, or not ours — logged); false when the hash does not match.
     * Throws on a temporary failure (ビットキャッシュ resends).
     */
    @Transactional
    public boolean handleNotice(Map<String, String> params) {
        String tranId = nz(params.get("tran_id"));
        String orderIdParam = nz(params.get("order_id"));
        String settleDate = nz(params.get("settle_date"));
        String price = nz(params.get("price"));
        String hash = nz(params.get("hash")).trim();
        String notifyKey = aes.decrypt(get(KEY_NOTIFY_KEY));
        if (notifyKey == null || notifyKey.isEmpty()) {
            log.warn("bitcash notice tran_id={} received but no 通知キー is set", LogSafe.of(tranId));
            throw new IllegalStateException("通知キー未設定");
        }
        if (!hashMatches(tranId, orderIdParam, settleDate, price, notifyKey, hash)) {
            log.warn("bitcash notice tran_id={} order_id={}: hash mismatch, ignored", LogSafe.of(tranId), LogSafe.of(orderIdParam));
            return false;
        }
        Long orderId = parseOrderCode(orderIdParam);
        Optional<BitcashOrder> found = orderId != null ? orderRepository.findForUpdate(orderId)
                : orderRepository.findByTranId(tranId).flatMap(x -> orderRepository.findForUpdate(x.getId()));
        if (!found.isPresent()) {
            log.warn("bitcash notice: unknown order order_id={} tran_id={} price={}", LogSafe.of(orderIdParam), LogSafe.of(tranId), LogSafe.of(price));
            return true;
        }
        BitcashOrder o = found.get();
        if (o.getTranId() != null && !o.getTranId().equals(tranId)) {
            log.warn("bitcash notice: order {} tran_id {} is not its {}, ignored", o.getId(), LogSafe.of(tranId), LogSafe.of(o.getTranId()));
            return true;
        }
        credit(o, tranId, price, null, "notify settle_date=" + settleDate + " price=" + price);
        return true;
    }

    /** 完了通知 hash (マニュアル 5.2.2): SHA-1 of the URL-encoded parameters + 通知キー, lower-case hex. */
    static boolean hashMatches(String tranId, String orderId, String settleDate, String price, String notifyKey, String hash) {
        String source = "tran_id=" + enc(tranId) + "&order_id=" + enc(orderId)
                + "&settle_date=" + enc(settleDate) + "&price=" + enc(price) + notifyKey;
        String expected = sha1Hex(source);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                hash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /* ===================== 決済確認処理 ===================== */

    /**
     * The member is back from the 決済画面: asks 決済確認処理 about their open orders of the last day
     * (one at a time — the manual asks for no parallel calls) and credits the paid ones.
     */
    public void confirmPending(Long userId) {
        Settings s = getSettings();
        if (!s.isReady()) return;
        List<BitcashOrder> open = orderRepository.findTop5ByUserIdAndStatusAndCreatedAtAfterOrderByIdDesc(
                userId, BitcashOrder.STATUS_PENDING, LocalDateTime.now().minusDays(1));
        for (BitcashOrder o : open) {
            if (o.getTranId() == null) continue;
            try {
                Long id = o.getId();
                tx.executeWithoutResult(st -> confirmOne(s, id));
            } catch (RuntimeException e) {
                log.warn("bitcash confirmSettle order {} failed: {}", o.getId(), e.toString());
            }
        }
    }

    /** One 決済確認処理, inside its own transaction (row locked against a concurrent 完了通知). */
    private void confirmOne(Settings s, Long orderId) {
        BitcashOrder o = orderRepository.findForUpdate(orderId).orElse(null);
        if (o == null || !BitcashOrder.STATUS_PENDING.equals(o.getStatus()) || o.getTranId() == null) return;
        Map<String, String> f = new LinkedHashMap<>();
        f.put("shop_id", s.shopId);
        f.put("shop_key", aes.decrypt(get(KEY_SHOP_KEY)));
        f.put("tran_id", o.getTranId());
        Map<String, String> res;
        try {
            res = call(apiUrl(s, "confirmSettle"), f);
        } catch (IOException e) {
            throw new IllegalStateException(e.toString());
        }
        String code = nz(res.get("status_code"));
        if (!"0".equals(code)) {
            if (!"400".equals(code)) log.warn("bitcash confirmSettle order {}: status {} {}", o.getId(), LogSafe.of(code), LogSafe.of(res.get("status_string")));
            return;   // 400 NOT_SETTLED: not paid (yet) — the 完了通知 may still come
        }
        if ("true".equalsIgnoreCase(nz(res.get("refunded")))) {
            log.warn("bitcash confirmSettle order {}: already refunded, not credited", o.getId());
            return;
        }
        credit(o, o.getTranId(), nz(res.get("price")), cutCno(res.get("cno")), cut("confirm " + new TreeMap<>(res)));
    }

    /* ===================== 入金 ===================== */

    /** Credits a paid order once: 入金 + ポイント + 決済入金通知. */
    private void credit(BitcashOrder o, String tranId, String price, String cno, String result) {
        if (BitcashOrder.STATUS_PAID.equals(o.getStatus())) return;   // second notice / confirm of a paid order
        if (cno != null && !cno.isEmpty()) o.setCno(cno);
        o.setResultParams(cut(result));
        Integer money = parseInt(price);
        if (money == null || !money.equals(o.getAmount())) {
            log.warn("bitcash: order {} price {} is not the order's {}, not credited", o.getId(), LogSafe.of(price), o.getAmount());
            o.setStatus(BitcashOrder.STATUS_FAILED);
            orderRepository.save(o);
            return;
        }
        if (o.getTranId() == null) o.setTranId(cutTranId(tranId));
        CrmUser user = userRepository.findById(o.getUserId()).orElse(null);
        if (user == null) {
            log.warn("bitcash: order {} member {} no longer exists", o.getId(), o.getUserId());
            o.setStatus(BitcashOrder.STATUS_FAILED);
            orderRepository.save(o);
            return;
        }

        PaymentForm form = new PaymentForm();
        form.setUserId(user.getId());
        form.setAmount(BigDecimal.valueOf(o.getAmount()));
        form.setPaymentMethod(o.getMethod());
        form.setStatus(Payment.STATUS_PAID);
        form.setInvoiceNumber(orderCode(o.getId()));
        form.setMemo("ビットキャッシュ決済（" + (o.getCno() != null ? "管理番号 " + o.getCno() : "決済トランザクションID " + o.getTranId()) + "）");
        Payment payment = paymentService.create(form);
        if (o.getPoints() > 0) {
            PaymentPoint pp = new PaymentPoint();
            pp.setPaymentId(payment.getId());
            pp.setPoints(o.getPoints());
            paymentPointRepository.save(pp);
            userPointService.add(user.getId(), o.getPoints());
        }
        o.setStatus(BitcashOrder.STATUS_PAID);
        o.setPaidAt(LocalDateTime.now());
        o.setPaymentId(payment.getId());
        orderRepository.save(o);
        log.info("bitcash: order {} paid, member {} +{}pt (payment {})", o.getId(), user.getId(), o.getPoints(), payment.getId());

        String label = o.getMethod();
        for (PaymentSettingService.Method m : paymentSettingService.getMethods(user.getFolder())) {
            if (m.getCode().equals(o.getMethod())) label = m.getLabel();
        }
        mailTemplateService.sendPayment(user, payment.getAmount(), o.getPoints(), label, payment.getPaidAt(), payment.getId());
    }

    private void failOrder(BitcashOrder o, String result) {
        o.setStatus(BitcashOrder.STATUS_FAILED);
        if (result != null) o.setResultParams(cut(result));
        orderRepository.save(o);
    }

    /* ===================== HTTP ===================== */

    private static String apiUrl(Settings s, String act) {
        return (s.testMode ? TEST_BASE : PROD_BASE) + act;
    }

    /** POSTs {@code fields} (form-encoded, UTF-8) and parses the "name=value&…" response body. */
    private static Map<String, String> call(String url, Map<String, String> fields) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(e.getKey()).append('=').append(enc(e.getValue()));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setUseCaches(false);
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            if (status / 100 != 2) throw new IOException("HTTP " + status);
            return parseResponse(readAll(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    /** "status_code=0&status_string=SUCCESS&…" → map (values left as sent; settle_url is decoded by the caller). */
    static Map<String, String> parseResponse(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : body.trim().split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) out.put(pair.substring(0, i).trim(), pair.substring(i + 1).trim());
        }
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = is.read(b)) != -1) {
                buf.write(b, 0, n);
                if (buf.size() > 65536) break;
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /* ===================== helpers ===================== */

    /** 発注コード sent as order_id (and the 入金's 請求番号). */
    static String orderCode(Long id) { return "BC" + id; }

    static Long parseOrderCode(String v) {
        if (v == null || !v.startsWith("BC")) return null;
        try { return Long.valueOf(v.substring(2)); } catch (NumberFormatException e) { return null; }
    }

    private static String enc(String v) {
        try { return URLEncoder.encode(v == null ? "" : v, "UTF-8"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    private static String decode(String v) {
        try { return URLDecoder.decode(v, "UTF-8"); } catch (UnsupportedEncodingException | IllegalArgumentException e) { return ""; }
    }

    private static String sha1Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : d) hex.append(String.format("%02x", x & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> splitCsv(String v) {
        List<String> out = new ArrayList<>();
        for (String x : v.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    private static Integer parseInt(String v) {
        try { return v == null ? null : Integer.valueOf(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String cut(String v) { return v == null ? null : v.length() > 4000 ? v.substring(0, 4000) : v; }

    private static String cutTranId(String v) { return v == null ? null : v.length() > 32 ? v.substring(0, 32) : v; }

    private static String cutCno(String v) { return v == null ? null : v.length() > 20 ? v.substring(0, 20) : v; }

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
