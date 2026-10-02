package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入金レポート (管理トップ ▸ 入金レポート). Builds the 明細 rows; the page's own script groups them
 * into the 日別 / 月別 views. Client design: nyukin_report (2).html, 2026-10-01.
 *
 * <ul>
 *   <li>入金回数 — the payment's position among that user's payments (cancelled ones included).</li>
 *   <li>ポイント — Payment has no points column, so it is the 決済関連設定 plan with the same amount
 *       (the user's folder settings, same method first); "—" when no plan matches.</li>
 *   <li>ログイン数 — per day, users whose 最終ログイン fell on that day ({@link LoginCountService});
 *       a month is the sum of its days.</li>
 * </ul>
 */
@Service
public class PaymentReportService {

    public static final int MEMO_MAX = 200;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final DateTimeFormatter REGISTERED = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Payment.PAYMENT_METHOD → 決済関連設定 method code. */
    private static final Map<String, String> SETTING_CODE = new HashMap<>();
    /** Payment.PAYMENT_METHOD → display name. */
    private static final Map<String, String> METHOD_LABEL = new HashMap<>();
    static {
        SETTING_CODE.put(Payment.METHOD_CREDIT_CARD, "credit");
        SETTING_CODE.put(Payment.METHOD_BANK_TRANSFER, "bank");
        METHOD_LABEL.put(Payment.METHOD_CREDIT_CARD, "クレジットカード");
        METHOD_LABEL.put(Payment.METHOD_BANK_TRANSFER, "銀行振込");
        METHOD_LABEL.put(Payment.METHOD_CASH, "現金");
    }

    /** One 明細 row. */
    public static final class Row {
        private Long id;
        private Long userId;
        private String userName;
        private String day;
        private String time;
        private String adCode;
        /** CRM_USER.GENDER ("M" / "F" / null) — tints the row 男=薄いブルー / 女=薄いピンク. */
        private String gender;
        private String folder;
        /** フォルダ設定 color (#rrggbb) of {@link #folder}, null when none is set. */
        private String folderColor;
        /** 登録日 = CRM_USER.CREATED_AT. */
        private String registeredDay;
        private int count;
        private BigDecimal amount;
        private Integer points;
        private String method;
        private boolean canceled;
        private String memo;

        public Long getId() { return id; }
        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getDay() { return day; }
        public String getTime() { return time; }
        public String getAdCode() { return adCode; }
        public String getGender() { return gender; }
        public String getFolder() { return folder; }
        public String getFolderColor() { return folderColor; }
        public String getRegisteredDay() { return registeredDay; }
        public int getCount() { return count; }
        public BigDecimal getAmount() { return amount; }
        public Integer getPoints() { return points; }
        public String getMethod() { return method; }
        public boolean isCanceled() { return canceled; }
        public String getMemo() { return memo; }
    }

    private final PaymentRepository paymentRepository;
    private final CrmUserRepository userRepository;
    private final LoginCountService loginCountService;
    private final PaymentSettingService paymentSettingService;
    private final FolderSettingService folderSettingService;

    public PaymentReportService(PaymentRepository paymentRepository,
                                CrmUserRepository userRepository,
                                LoginCountService loginCountService,
                                PaymentSettingService paymentSettingService,
                                FolderSettingService folderSettingService) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.loginCountService = loginCountService;
        this.paymentSettingService = paymentSettingService;
        this.folderSettingService = folderSettingService;
    }

    public List<Row> rows() {
        List<Payment> payments = paymentRepository.findForPaymentReport();
        Set<Long> userIds = new HashSet<>();
        for (Payment p : payments) userIds.add(p.getUserId());
        Map<Long, CrmUser> users = new HashMap<>();
        for (CrmUser u : userRepository.findAllById(userIds)) users.put(u.getId(), u);

        Map<String, String> folderColors = folderSettingService.colorMap();
        Map<String, List<PaymentSettingService.Method>> settingsByFolder = new HashMap<>();
        Map<Long, Integer> countByUser = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (Payment p : payments) {
            CrmUser u = users.get(p.getUserId());
            Row r = new Row();
            r.id = p.getId();
            r.userId = p.getUserId();
            r.userName = u == null ? "" : (u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                    ? u.getDisplayName() : u.getEmail());
            r.day = p.getPaidAt().format(DAY);
            r.time = p.getPaidAt().format(TIME);
            r.adCode = u == null ? null : blankToNull(u.getAdCode());
            r.gender = u == null ? null : blankToNull(u.getGender());
            r.folder = u == null ? null : blankToNull(u.getFolder());
            r.folderColor = r.folder == null ? null : folderColors.get(r.folder);
            r.registeredDay = u == null || u.getCreatedAt() == null ? null : u.getCreatedAt().format(REGISTERED);
            r.count = countByUser.merge(p.getUserId(), 1, Integer::sum);
            r.amount = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            String folder = u == null || u.getFolder() == null ? "" : u.getFolder();
            List<PaymentSettingService.Method> methods =
                    settingsByFolder.computeIfAbsent(folder, paymentSettingService::getMethods);
            r.points = pointsFor(methods, SETTING_CODE.get(p.getPaymentMethod()), r.amount);
            r.method = p.getPaymentMethod() == null ? "—"
                    : METHOD_LABEL.getOrDefault(p.getPaymentMethod(), p.getPaymentMethod());
            r.canceled = !Payment.STATUS_PAID.equals(p.getStatus());
            r.memo = p.getMemo() == null ? "" : p.getMemo().trim();
            rows.add(r);
        }
        return rows;
    }

    /** {"daily": {yyyy-MM-dd: n}} for the page's loginData block; the page sums months / total. */
    public Map<String, Object> loginCounts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daily", loginCountService.dailyCounts());
        return out;
    }

    /** Today's ログイン数, 0:00 → now. */
    public long todayLoginCount() {
        return loginCountService.countFor(java.time.LocalDate.now());
    }

    /** Saves (or with a blank memo, clears) 備考 on a payment. */
    @Transactional
    public void saveMemo(Long paymentId, String memo) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("入金が見つかりません"));
        String text = memo == null ? "" : memo.trim();
        if (text.length() > MEMO_MAX) throw new IllegalArgumentException("備考は" + MEMO_MAX + "文字までです");
        p.setMemo(text.isEmpty() ? null : text);
        paymentRepository.save(p);
    }

    private static Integer pointsFor(List<PaymentSettingService.Method> methods, String code, BigDecimal amount) {
        if (code != null) {
            for (PaymentSettingService.Method m : methods) {
                if (code.equals(m.getCode())) {
                    Integer pts = match(m, amount);
                    if (pts != null) return pts;
                }
            }
        }
        for (PaymentSettingService.Method m : methods) {
            Integer pts = match(m, amount);
            if (pts != null) return pts;
        }
        return null;
    }

    private static Integer match(PaymentSettingService.Method m, BigDecimal amount) {
        for (PaymentSettingService.Plan plan : m.getPlans()) {
            if (plan.getAmount() != null && plan.getPoints() != null
                    && amount.compareTo(BigDecimal.valueOf(plan.getAmount())) == 0) {
                return plan.getPoints();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
