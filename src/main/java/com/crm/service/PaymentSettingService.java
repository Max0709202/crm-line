package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 決済関連設定 — which payment methods members see on ポイント購入, and the plans
 * (表示金額 → 付与ポイント) offered for each method.
 *
 * Stored in CRM_SETTING as one JSON row: {@code payment.config} for 共通, and
 * {@code payment.folder.<folder name>} for a folder with its own settings (a folder without that
 * row, or with it blank, uses 共通). Shape:
 * <pre>{"order":["credit",...],"methods":{"credit":{"shown":true,"label":"カード決済","plans":[{"shown":true,"amount":1000,"points":1000}, ...]}}}</pre>
 * {@code order} is the display order (missing → {@link #METHODS} order); {@code label} is the
 * operator's name for the method (missing → the {@link #METHODS} name).
 * Each method has {@link #PLAN_ROWS} plan rows; an empty row (no amount and no points) is unused.
 * Defaults are the plans on the client's ガラケー ポイント購入 design (2026-09-29).
 */
@Service
public class PaymentSettingService {

    public static final int PLAN_ROWS = 8;
    public static final int MAX_AMOUNT = 1000000;
    public static final int MAX_POINTS = 10000000;
    public static final int MAX_LABEL = 30;

    private static final String KEY_COMMON = "payment.config";
    private static final String FOLDER_PREFIX = "payment.folder.";
    /** CRM_SETTING.SETTING_KEY is VARCHAR(128). */
    public static final int MAX_FOLDER_NAME = 128 - FOLDER_PREFIX.length();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Payment methods, in default display order: code → default label. */
    public static final Map<String, String> METHODS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("credit",      "クレジットカード");
        m.put("bank",        "銀行振込");
        m.put("convenience", "コンビニ決済");
        m.put("emoney",      "電子マネー");
        METHODS = Collections.unmodifiableMap(m);
    }

    private static final int[][] DEFAULT_PLANS = {
            {1000, 1000}, {3000, 3000}, {5000, 5000}, {10000, 10000}, {30000, 30000}
    };

    /** One plan row. amount / points are null on an unused row. */
    public static final class Plan {
        private final boolean shown;
        private final Integer amount;
        private final Integer points;

        public Plan(boolean shown, Integer amount, Integer points) {
            this.shown = shown;
            this.amount = amount;
            this.points = points;
        }

        public boolean isShown() { return shown; }
        public Integer getAmount() { return amount; }
        public Integer getPoints() { return points; }
        public boolean isEmpty() { return amount == null && points == null; }
        /** Listed on the member ポイント購入 page. */
        public boolean isOffered() { return shown && amount != null && points != null; }
    }

    /** One payment method with its {@link #PLAN_ROWS} plan rows. */
    public static final class Method {
        private final String code;
        private final String label;
        private final boolean shown;
        private final List<Plan> plans;

        Method(String code, String label, boolean shown, List<Plan> plans) {
            this.code = code;
            this.label = label;
            this.shown = shown;
            this.plans = Collections.unmodifiableList(plans);
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
        public String getDefaultLabel() { return METHODS.get(code); }
        public boolean isShown() { return shown; }
        public List<Plan> getPlans() { return plans; }

        /** Plans members can buy with this method (none when the method is hidden). */
        public List<Plan> getOfferedPlans() {
            List<Plan> out = new ArrayList<>();
            if (!shown) return out;
            for (Plan p : plans) if (p.isOffered()) out.add(p);
            return out;
        }
    }

    /** Submitted text of one plan row. */
    public static final class PlanInput {
        final boolean shown;
        final String amount;
        final String points;

        public PlanInput(boolean shown, String amount, String points) {
            this.shown = shown;
            this.amount = amount;
            this.points = points;
        }
    }

    /** Submitted values of one method. */
    public static final class MethodInput {
        final boolean shown;
        /** Display name; null keeps the current one, blank goes back to the default. */
        final String label;
        final List<PlanInput> plans;

        public MethodInput(boolean shown, List<PlanInput> plans) {
            this(shown, null, plans);
        }

        public MethodInput(boolean shown, String label, List<PlanInput> plans) {
            this.shown = shown;
            this.label = label;
            this.plans = plans;
        }
    }

    private final CrmSettingRepository repository;

    public PaymentSettingService(CrmSettingRepository repository) {
        this.repository = repository;
    }

    /** Settings that apply to members in {@code folder} (null/blank = 共通). */
    public List<Method> getMethods(String folder) {
        String json = null;
        if (folder != null && !folder.trim().isEmpty() && folder.trim().length() <= MAX_FOLDER_NAME) {
            json = blankToNull(get(FOLDER_PREFIX + folder.trim()));
        }
        if (json == null) json = blankToNull(get(KEY_COMMON));
        return parse(json);
    }

    /** True when {@code folder} has its own settings instead of the 共通 ones. */
    public boolean hasFolderSettings(String folder) {
        if (folder == null || folder.trim().isEmpty() || folder.trim().length() > MAX_FOLDER_NAME) return false;
        return blankToNull(get(FOLDER_PREFIX + folder.trim())) != null;
    }

    /** Saves 共通; returns the rejected rows (see the private save below). */
    @Transactional
    public List<String> saveCommon(Map<String, MethodInput> input) {
        return saveCommon(input, null);
    }

    /** Saves 共通 with the methods in {@code order} (method codes; null keeps the current order). */
    @Transactional
    public List<String> saveCommon(Map<String, MethodInput> input, List<String> order) {
        return save(KEY_COMMON, null, input, order);
    }

    /** Saves one folder's settings; with {@code own} false the folder goes back to 共通. */
    @Transactional
    public List<String> saveFolder(String folder, boolean own, Map<String, MethodInput> input) {
        return saveFolder(folder, own, input, null);
    }

    /** As {@link #saveFolder(String, boolean, Map)}, with the methods in {@code order}. */
    @Transactional
    public List<String> saveFolder(String folder, boolean own, Map<String, MethodInput> input, List<String> order) {
        if (folder == null || folder.trim().isEmpty()) throw new IllegalArgumentException("folder is required");
        String f = folder.trim();
        if (f.length() > MAX_FOLDER_NAME) {
            throw new IllegalArgumentException("フォルダ名が長すぎます（" + MAX_FOLDER_NAME + "文字まで）");
        }
        String key = FOLDER_PREFIX + f;
        if (!own) {
            if (get(key) != null) save(key, "");
            return new ArrayList<>();
        }
        return save(key, f, input, order);
    }

    /**
     * Stores every method. A method missing from {@code input} keeps its current values. A plan
     * row with both fields blank becomes unused; a row with an invalid or half-filled value keeps
     * its current values and is reported as e.g. "クレジットカード 3行目".
     */
    private List<String> save(String key, String folder, Map<String, MethodInput> input, List<String> order) {
        List<String> rejected = new ArrayList<>();
        Map<String, Object> methodsOut = new LinkedHashMap<>();
        List<Method> currentMethods = getMethods(folder);
        List<String> currentOrder = new ArrayList<>();
        for (Method m : currentMethods) currentOrder.add(m.getCode());
        for (Method current : currentMethods) {
            MethodInput in = input == null ? null : input.get(current.getCode());
            boolean shown = in == null ? current.isShown() : in.shown;
            String label = (in == null || in.label == null) ? current.getLabel() : in.label.trim();
            if (label.length() > MAX_LABEL) {
                rejected.add(current.getLabel() + " 表示名（" + MAX_LABEL + "文字まで）");
                label = current.getLabel();
            }
            List<Map<String, Object>> plansOut = new ArrayList<>();
            for (int i = 0; i < PLAN_ROWS; i++) {
                Plan cur = current.getPlans().get(i);
                PlanInput pi = (in == null || in.plans == null || i >= in.plans.size()) ? null : in.plans.get(i);
                Plan next = cur;
                if (pi != null) {
                    boolean amountBlank = isBlank(pi.amount), pointsBlank = isBlank(pi.points);
                    Integer amount = parseInt(pi.amount, 1, MAX_AMOUNT);
                    Integer points = parseInt(pi.points, 0, MAX_POINTS);
                    if (amountBlank && pointsBlank) {
                        next = new Plan(pi.shown, null, null);
                    } else if (amount != null && points != null) {
                        next = new Plan(pi.shown, amount, points);
                    } else {
                        rejected.add(current.getLabel() + " " + (i + 1) + "行目");
                    }
                }
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("shown", next.isShown());
                p.put("amount", next.getAmount());
                p.put("points", next.getPoints());
                plansOut.add(p);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shown", shown);
            if (!label.isEmpty() && !label.equals(METHODS.get(current.getCode()))) m.put("label", label);
            m.put("plans", plansOut);
            methodsOut.put(current.getCode(), m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("order", normalizeOrder(order == null ? currentOrder : order));
        root.put("methods", methodsOut);
        try {
            save(key, JSON.writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return rejected;
    }

    /** Known method codes in {@code order} (duplicates / unknown dropped), then any missing ones
     *  in the default order. */
    private static List<String> normalizeOrder(List<String> order) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (order != null) {
            for (String c : order) if (c != null && METHODS.containsKey(c.trim())) out.add(c.trim());
        }
        out.addAll(METHODS.keySet());
        return new ArrayList<>(out);
    }

    /** Builds the methods from a stored row; anything missing or unreadable gets the default. */
    private static List<Method> parse(String json) {
        JsonNode methods = null;
        List<String> order = null;
        if (json != null) {
            try {
                JsonNode root = JSON.readTree(json);
                methods = root.get("methods");
                JsonNode o = root.get("order");
                if (o != null && o.isArray()) {
                    order = new ArrayList<>();
                    for (JsonNode c : o) order.add(c.asText());
                }
            } catch (IOException e) {
                methods = null;   // unreadable row → defaults rather than a broken page
            }
        }
        List<Method> out = new ArrayList<>();
        for (String code : normalizeOrder(order)) {
            String defaultLabel = METHODS.get(code);
            JsonNode m = methods == null ? null : methods.get(code);
            if (m == null) {
                out.add(new Method(code, defaultLabel, true, defaultPlans()));
                continue;
            }
            String label = m.hasNonNull("label") ? m.get("label").asText().trim() : "";
            if (label.isEmpty()) label = defaultLabel;
            JsonNode plansNode = m.get("plans");
            List<Plan> plans = new ArrayList<>();
            for (int i = 0; i < PLAN_ROWS; i++) {
                JsonNode p = (plansNode == null || !plansNode.isArray()) ? null : plansNode.get(i);
                if (p == null) {
                    plans.add(new Plan(true, null, null));
                    continue;
                }
                Integer amount = intOrNull(p.get("amount"), 1, MAX_AMOUNT);
                Integer points = intOrNull(p.get("points"), 0, MAX_POINTS);
                if (amount == null || points == null) { amount = null; points = null; }
                plans.add(new Plan(!p.has("shown") || p.get("shown").asBoolean(true), amount, points));
            }
            boolean shown = !m.has("shown") || m.get("shown").asBoolean(true);
            out.add(new Method(code, label, shown, plans));
        }
        return out;
    }

    private static List<Plan> defaultPlans() {
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < PLAN_ROWS; i++) {
            plans.add(i < DEFAULT_PLANS.length
                    ? new Plan(true, DEFAULT_PLANS[i][0], DEFAULT_PLANS[i][1])
                    : new Plan(true, null, null));
        }
        return plans;
    }

    private static Integer intOrNull(JsonNode n, int min, int max) {
        if (n == null || !n.canConvertToInt()) return null;
        int v = n.asInt();
        return (v < min || v > max) ? null : v;
    }

    private static Integer parseInt(String raw, int min, int max) {
        if (raw == null) return null;
        String t = raw.trim().replace(",", "");
        if (t.isEmpty()) return null;
        try {
            int n = Integer.parseInt(t);
            return (n < min || n > max) ? null : n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String blankToNull(String v) {
        return (v == null || v.trim().isEmpty()) ? null : v;
    }

    private String get(String key) {
        return repository.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void save(String key, String value) {
        CrmSetting s = repository.findBySettingKey(key).orElseGet(() -> {
            CrmSetting ns = new CrmSetting();
            ns.setSettingKey(key);
            return ns;
        });
        s.setSettingValue(value);
        repository.save(s);
    }
}
