package com.crm.service;

import com.crm.entity.CrmSetting;
import com.crm.repository.CrmSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * セキュリティ設定 › 管理画面IP許可設定: while 有効, the admin screens (/manager/**, login included) answer
 * only the 許可するIP — single addresses ({@code 203.0.113.10}) or ranges ({@code 203.0.113.0/24},
 * IPv6 too). Stored in CRM_SETTING ({@code security.admin_ip.enabled} / {@code .list} as JSON) and
 * cached in memory, since every admin request is checked.
 */
@Service
public class AdminIpService {

    private static final String KEY_ENABLED = "security.admin_ip.enabled";
    private static final String KEY_LIST = "security.admin_ip.list";
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_MEMO = 40;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern V4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern V6 = Pattern.compile("^[0-9a-fA-F:.]+(%\\w+)?$");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/M/d");

    /** One 許可するIP row. */
    public static final class Entry {
        public String ip;
        public String memo;
        public String addedAt;

        public Entry() {}
        Entry(String ip, String memo, String addedAt) { this.ip = ip; this.memo = memo; this.addedAt = addedAt; }

        public String getIp() { return ip; }
        public String getMemo() { return memo; }
        public String getAddedAt() { return addedAt; }
    }

    private static final class Config {
        final boolean enabled;
        final List<Entry> list;
        Config(boolean enabled, List<Entry> list) { this.enabled = enabled; this.list = list; }
    }

    private final CrmSettingRepository repository;
    private volatile Config cache;

    public AdminIpService(CrmSettingRepository repository) {
        this.repository = repository;
    }

    public boolean isEnabled() { return config().enabled; }

    public List<Entry> list() { return config().list; }

    /** May {@code ip} open the admin screens? */
    public boolean isAllowed(String ip) {
        Config c = config();
        if (!c.enabled) return true;
        return inList(c.list, ip);
    }

    /** {@code ip} is on the 許可するIP list (regardless of 有効). */
    public boolean isListed(String ip) {
        return inList(config().list, ip);
    }

    /**
     * Saves the setting. {@code ips} / {@code memos} are the rows in order; a row keeps its 追加日
     * when the IP was already listed. Throws IllegalArgumentException on a bad row or an enabled
     * empty list (nobody could open the admin screens).
     */
    @Transactional
    public void save(boolean enabled, List<String> ips, List<String> memos) {
        Map<String, String> addedBefore = new LinkedHashMap<>();
        for (Entry e : config().list) addedBefore.put(e.ip, e.addedAt);
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int n = ips == null ? 0 : ips.size();
        for (int i = 0; i < n; i++) {
            String ip = ips.get(i) == null ? "" : ips.get(i).trim();
            if (ip.isEmpty()) continue;
            String norm = normalize(ip);
            if (norm == null) throw new IllegalArgumentException("「" + ip + "」はIPアドレス（例 203.0.113.10）または範囲（例 203.0.113.0/24）の形ではありません");
            if (!seen.add(norm)) continue;
            String memo = memos != null && i < memos.size() && memos.get(i) != null ? memos.get(i).trim() : "";
            if (memo.length() > MAX_MEMO) memo = memo.substring(0, MAX_MEMO);
            String added = addedBefore.get(norm);
            out.add(new Entry(norm, memo, added != null ? added : LocalDate.now().format(DAY)));
        }
        if (out.size() > MAX_ENTRIES) throw new IllegalArgumentException("許可するIPは" + MAX_ENTRIES + "件までです");
        if (enabled && out.isEmpty()) throw new IllegalArgumentException("IP制限を有効にする場合は、許可するIPを1件以上登録してください");
        try {
            put(KEY_LIST, JSON.writeValueAsString(out));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        put(KEY_ENABLED, String.valueOf(enabled));
        cache = new Config(enabled, Collections.unmodifiableList(out));
    }

    /** "203.0.113.10" / "203.0.113.0/24" / IPv6 in a canonical form, or null when not an IP / range. */
    static String normalize(String raw) {
        String s = raw.trim();
        String[] p = s.split("/", -1);
        if (p.length > 2) return null;
        byte[] addr = parse(p[0]);
        if (addr == null) return null;
        int max = addr.length * 8;
        if (p.length == 2) {
            if (!p[1].matches("\\d{1,3}")) return null;
            int bits = Integer.parseInt(p[1]);
            if (bits < 0 || bits > max) return null;
            if (bits == max) return host(p[0], addr);
            return host(p[0], addr) + "/" + bits;
        }
        return host(p[0], addr);
    }

    private static String host(String text, byte[] addr) {
        return addr.length == 4 ? text.trim() : text.trim().toLowerCase(Locale.ROOT);
    }

    /** Does entry (IP or range) cover {@code ip}? */
    static boolean covers(String entry, String ip) {
        if (entry == null || ip == null) return false;
        String[] p = entry.trim().split("/", -1);
        byte[] net = parse(p[0]);
        byte[] a = parse(ip.trim());
        if (net == null || a == null) return false;
        if (net.length != a.length) {           // IPv4-mapped IPv6 (::ffff:1.2.3.4) vs IPv4
            a = unmapV4(a);
            net = unmapV4(net);
            if (a == null || net == null || net.length != a.length) return false;
        }
        int bits = net.length * 8;
        if (p.length == 2) {
            try { bits = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return false; }
            if (bits < 0 || bits > net.length * 8) return false;
        }
        for (int i = 0; i < net.length && bits > 0; i++, bits -= 8) {
            int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
            if ((net[i] & mask) != (a[i] & mask)) return false;
        }
        return true;
    }

    private static byte[] unmapV4(byte[] a) {
        if (a.length == 4) return a;
        for (int i = 0; i < 10; i++) if (a[i] != 0) return null;
        if ((a[10] & 0xFF) != 0xFF || (a[11] & 0xFF) != 0xFF) return null;
        return Arrays.copyOfRange(a, 12, 16);
    }

    /** IP literal → bytes; never a DNS lookup (only literal forms are passed to InetAddress). */
    private static byte[] parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (V4.matcher(t).matches()) {
            String[] o = t.split("\\.");
            byte[] b = new byte[4];
            for (int i = 0; i < 4; i++) {
                int v = Integer.parseInt(o[i]);
                if (v > 255) return null;
                b[i] = (byte) v;
            }
            return b;
        }
        if (t.indexOf(':') >= 0 && V6.matcher(t).matches()) {
            try {
                return InetAddress.getByName(t).getAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean inList(List<Entry> list, String ip) {
        for (Entry e : list) if (covers(e.ip, ip)) return true;
        return false;
    }

    private Config config() {
        Config c = cache;
        if (c == null) {
            c = load();
            cache = c;
        }
        return c;
    }

    private Config load() {
        boolean enabled = "true".equals(get(KEY_ENABLED));
        List<Entry> list = new ArrayList<>();
        String json = get(KEY_LIST);
        if (json != null && !json.trim().isEmpty()) {
            try {
                for (Entry e : JSON.readValue(json, new TypeReference<List<Entry>>() {})) {
                    String norm = e.ip == null ? null : normalize(e.ip);
                    if (norm != null) list.add(new Entry(norm, e.memo == null ? "" : e.memo, e.addedAt == null ? "" : e.addedAt));
                }
            } catch (IOException e) {
                list.clear();   // unreadable row: treated as an empty list
            }
        }
        return new Config(enabled, Collections.unmodifiableList(list));
    }

    private String get(String key) {
        return repository.findBySettingKey(key).map(CrmSetting::getSettingValue).orElse(null);
    }

    private void put(String key, String value) {
        CrmSetting s = repository.findBySettingKey(key).orElseGet(() -> {
            CrmSetting n = new CrmSetting();
            n.setSettingKey(key);
            return n;
        });
        s.setSettingValue(value);
        repository.save(s);
    }
}
