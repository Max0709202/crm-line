package com.crm.service;

import com.crm.entity.CrmUser;
import com.crm.entity.Payment;
import com.crm.entity.RelayRoute;
import com.crm.entity.RelayServer;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.PaymentRepository;
import com.crm.repository.RelayRouteRepository;
import com.crm.repository.RelayServerRepository;
import com.crm.util.LogSafe;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * リレーサーバー設定 — ユーザーごとの送信経路. The 使用中 relays are looked at in priority order
 * and a mail goes out through the first one whose 対象条件 (フォルダ・入金回数) the recipient
 * matches; a relay with no condition takes everyone. When nothing matches (or nothing is 使用中)
 * the mail is delivered by this server's local postfix (MX lookup).
 */
@Service
public class RelayRoutingService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoutingService.class);

    public static final int MAX_NAME = 30;
    public static final int MAX_MEMO = 200;
    public static final String CHECK_OK = "ok";
    public static final String CHECK_DENIED = "denied";
    public static final String CHECK_UNREACHABLE = "unreachable";

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern HOST = Pattern.compile("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final int CHECK_TIMEOUT_MS = 6000;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One relay as the settings page shows it. */
    public static final class View {
        public String id;
        public String name;
        public String ip;
        public int port;
        public boolean active;
        public String memo;
        public List<String> folders;
        public Integer payMin;
        public Integer payMax;
        public Map<String, String> check;   // status, message, at — null = 未確認
    }

    /** What the settings form submits. */
    public static final class Input {
        public String name;
        public String ip;
        public String port;
        public List<String> folders;
        public String payMin;
        public String payMax;
        public boolean active;
        public String memo;
    }

    public static final class CheckResult {
        public final String status;
        public final String message;
        public final LocalDateTime at;
        CheckResult(String status, String message) { this.status = status; this.message = message; this.at = LocalDateTime.now(); }
    }

    public static class RelayException extends RuntimeException {
        public RelayException(String msg) { super(msg); }
    }

    private final RelayServerRepository relayRepository;
    private final RelayRouteRepository routeRepository;
    private final CrmUserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final DomainSettingService domainSettingService;
    private final String bridgeUrl;

    public RelayRoutingService(RelayServerRepository relayRepository, RelayRouteRepository routeRepository,
                               CrmUserRepository userRepository, PaymentRepository paymentRepository,
                               DomainSettingService domainSettingService,
                               @Value("${app.relay-server.url:}") String bridgeUrl) {
        this.relayRepository = relayRepository;
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.domainSettingService = domainSettingService;
        this.bridgeUrl = bridgeUrl;
    }

    /** Host of the HTTP-bridge 転送機 (SSH+HTTP API); other relays get direct SMTP. */
    public String bridgeHost() {
        try {
            String h = URI.create(bridgeUrl).getHost();
            return h == null ? "" : h;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** This server's sending IP as relays see it — the main domain's address, "" when unknown. */
    public String ourIp() {
        try {
            String base = domainSettingService.getReplyBaseUrl();
            String host = base == null ? null : URI.create(base.trim()).getHost();
            return host == null ? "" : InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    /* ===================== 一覧・登録・編集 ===================== */

    /** All relays, highest priority first. */
    public List<View> list() {
        Map<Long, RelayRoute> routes = routes();
        List<RelayServer> relays = ordered(relayRepository.findAll(), routes);
        List<View> out = new ArrayList<>();
        for (RelayServer r : relays) out.add(view(r, routes.get(r.getId())));
        return out;
    }

    @Transactional
    public View save(Long id, Input in) {
        String name = trim(in.name);
        if (name.isEmpty()) throw new RelayException("名前を入力してください");
        if (name.length() > MAX_NAME) throw new RelayException("名前は" + MAX_NAME + "文字までです");
        String ip = trim(in.ip);
        if (!validHost(ip)) throw new RelayException("IPアドレス（またはホスト名）を正しく入力してください");
        int port = parsePort(in.port);
        Integer payMin = parseCount(in.payMin), payMax = parseCount(in.payMax);
        if (payMin != null && payMax != null && payMin > payMax) {
            throw new RelayException("入金回数の「以上」は「以下」より小さい数にしてください");
        }
        String memo = trim(in.memo);
        if (memo.length() > MAX_MEMO) throw new RelayException("メモは" + MAX_MEMO + "文字までです");

        RelayServer r;
        boolean isNew = id == null;
        if (isNew) {
            if (relayRepository.existsByName(name)) throw new RelayException("この名前は既に登録されています");
            r = new RelayServer();
        } else {
            r = relayRepository.findById(id).orElseThrow(() -> new RelayException("リレーサーバーが見つかりません"));
            if (!name.equals(r.getName()) && relayRepository.existsByName(name)) {
                throw new RelayException("この名前は既に登録されています");
            }
        }
        boolean addrChanged = !isNew && (!ip.equals(r.getIpAddress()) || r.getPort() == null || r.getPort() != port);
        r.setName(name);
        r.setIpAddress(ip);
        r.setPort(port);
        r.setIsActive(in.active);
        r.setMemo(memo.isEmpty() ? null : memo);
        r = relayRepository.save(r);

        RelayRoute route = routeRepository.findById(r.getId()).orElse(null);
        if (route == null) {
            route = new RelayRoute();
            route.setRelayId(r.getId());
            route.setPriority(nextPriority());   // 新規は一番下（優先順位が最も低い）
        }
        route.setFolders(writeFolders(in.folders));
        route.setPayMin(payMin);
        route.setPayMax(payMax);
        if (addrChanged) {   // 接続先を変えたら確認し直し
            route.setCheckStatus(null);
            route.setCheckMessage(null);
            route.setCheckedAt(null);
        }
        routeRepository.save(route);
        return view(r, route);
    }

    @Transactional
    public int delete(List<Long> ids) {
        int n = 0;
        if (ids == null) return 0;
        for (Long id : ids) {
            if (id == null || !relayRepository.existsById(id)) continue;
            relayRepository.deleteById(id);
            if (routeRepository.existsById(id)) routeRepository.deleteById(id);
            n++;
        }
        return n;
    }

    /** New order, highest priority first; relays not listed keep their place after them. */
    @Transactional
    public void reorder(List<Long> ids) {
        Map<Long, RelayRoute> routes = routes();
        List<RelayServer> current = ordered(relayRepository.findAll(), routes);
        List<Long> order = new ArrayList<>();
        if (ids != null) for (Long id : ids) if (id != null && !order.contains(id)) order.add(id);
        for (RelayServer r : current) if (!order.contains(r.getId())) order.add(r.getId());
        int p = 0;
        for (Long id : order) {
            if (!relayRepository.existsById(id)) continue;
            RelayRoute route = routes.get(id);
            if (route == null) {
                route = new RelayRoute();
                route.setRelayId(id);
            }
            route.setPriority(p++);
            routeRepository.save(route);
        }
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        RelayServer r = relayRepository.findById(id).orElseThrow(() -> new RelayException("リレーサーバーが見つかりません"));
        r.setIsActive(active);
        relayRepository.save(r);
    }

    /* ===================== 疎通確認 ===================== */

    /**
     * Connects to the relay and asks whether this server may send through it. SMTP relays get
     * EHLO / MAIL FROM / RCPT TO (no DATA, so nothing is delivered) and the RCPT reply decides;
     * the HTTP-bridge 転送機 is checked by connecting to its SSH and HTTP ports. A result for a saved
     * relay ({@code id} not null, same address) is stored and shown in the list. Not transactional:
     * the network part can take several seconds and must not hold a DB connection meanwhile.
     */
    public CheckResult check(Long id, String rawIp, String rawPort) {
        String ip = trim(rawIp);
        if (!validHost(ip)) throw new RelayException("IPアドレス（またはホスト名）を正しく入力してください");
        int port = parsePort(rawPort);
        CheckResult res = ip.equalsIgnoreCase(bridgeHost()) ? checkBridge(ip) : checkSmtp(ip, port);
        if (id != null) {
            Optional<RelayServer> r = relayRepository.findById(id);
            if (r.isPresent() && ip.equals(r.get().getIpAddress()) && r.get().getPort() != null && r.get().getPort() == port) {
                RelayRoute route = routeRepository.findById(id).orElseGet(() -> {
                    RelayRoute n = new RelayRoute();
                    n.setRelayId(id);
                    n.setPriority(nextPriority());
                    return n;
                });
                route.setCheckStatus(res.status);
                route.setCheckMessage(cut(res.message, 500));
                route.setCheckedAt(res.at);
                routeRepository.save(route);
            }
        }
        log.info("[RELAY CHECK] {}:{} → {} {}", LogSafe.of(ip), port, res.status, LogSafe.of(res.message));
        return res;
    }

    private CheckResult checkBridge(String host) {
        int bridgePort = 80;
        try {
            URI u = URI.create(bridgeUrl);
            if (u.getPort() > 0) bridgePort = u.getPort();
            else if ("https".equalsIgnoreCase(u.getScheme())) bridgePort = 443;
        } catch (RuntimeException ignored) { /* default 80 */ }
        String ssh = tcp(host, 22), http = tcp(host, bridgePort);
        if (ssh == null && http == null) return new CheckResult(CHECK_OK, "SSH接続・HTTP API（" + bridgePort + "）接続OK");
        return new CheckResult(CHECK_UNREACHABLE, "接続できません（" + (ssh != null ? "SSH: " + ssh : "HTTP: " + http) + "）");
    }

    /** null when the port answers, else the reason. */
    private static String tcp(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), CHECK_TIMEOUT_MS);
            return null;
        } catch (IOException e) {
            return host + ":" + port + " " + e.getMessage();
        }
    }

    private CheckResult checkSmtp(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), CHECK_TIMEOUT_MS);
            s.setSoTimeout(CHECK_TIMEOUT_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = s.getOutputStream();
            String greet = reply(in);
            if (!greet.startsWith("2")) return new CheckResult(CHECK_DENIED, "接続OK・接続拒否：" + greet);
            String domain = senderDomain();
            String ehlo = command(out, in, "EHLO " + domain);
            if (!ehlo.startsWith("2")) ehlo = command(out, in, "HELO " + domain);
            String from = command(out, in, "MAIL FROM:<relay-check@" + domain + ">");
            if (!from.startsWith("2")) return denied(from);
            String rcpt = command(out, in, "RCPT TO:<relay-check@gmail.com>");
            try { command(out, in, "RSET"); command(out, in, "QUIT"); } catch (IOException ignored) { /* closing */ }
            if (rcpt.startsWith("2")) return new CheckResult(CHECK_OK, "接続OK・送信許可（" + rcpt + "）");
            return denied(rcpt);
        } catch (IOException e) {
            return new CheckResult(CHECK_UNREACHABLE, "接続できません（" + host + ":" + port + " " + e.getMessage() + "）");
        }
    }

    private CheckResult denied(String reply) {
        String ip = ourIp();
        return new CheckResult(CHECK_DENIED, "接続OK・送信拒否：" + reply + (ip.isEmpty() ? "" : "（" + ip + " が未許可の可能性）"));
    }

    private String senderDomain() {
        try {
            String base = domainSettingService.getReplyBaseUrl();
            String host = base == null ? null : URI.create(base.trim()).getHost();
            return host == null || host.isEmpty() ? "localhost" : host;
        } catch (RuntimeException e) {
            return "localhost";
        }
    }

    private static String command(OutputStream out, BufferedReader in, String cmd) throws IOException {
        out.write((cmd + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return reply(in);
    }

    /** Last line of a (multi-line) SMTP reply, e.g. "250 OK". */
    private static String reply(BufferedReader in) throws IOException {
        String line;
        String last = "";
        while ((line = in.readLine()) != null) {
            last = line;
            if (line.length() < 4 || line.charAt(3) != '-') break;
        }
        if (line == null && last.isEmpty()) throw new IOException("応答がありません");
        return cut(last, 200);
    }

    /* ===================== 送信時の振り分け ===================== */

    /**
     * The relay a mail to {@code toAddress} goes through, or null for local postfix. The
     * recipient's フォルダ and 入金回数 come from the CRM user with that address (unknown address:
     * no folder, 0 回). Read on every send so a settings change applies right away.
     */
    public RelayServer pickFor(String toAddress) {
        Map<Long, RelayRoute> routes = routes();
        List<RelayServer> active = new ArrayList<>();
        for (RelayServer r : ordered(relayRepository.findAll(), routes)) {
            if (Boolean.TRUE.equals(r.getIsActive())) active.add(r);
        }
        if (active.isEmpty()) return null;
        String folder = null;
        Long userId = null;
        if (toAddress != null && !toAddress.trim().isEmpty()) {
            Optional<CrmUser> u = userRepository.findByEmail(toAddress.trim());
            if (!u.isPresent()) u = userRepository.findByEmail(toAddress.trim().toLowerCase());
            if (u.isPresent()) {
                folder = u.get().getFolder();
                userId = u.get().getId();
            }
        }
        Long paid = null;   // counted only when a relay has a 入金回数 condition
        for (RelayServer r : active) {
            RelayRoute route = routes.get(r.getId());
            List<String> folders = route == null ? Collections.<String>emptyList() : readFolders(route.getFolders());
            if (!folders.isEmpty() && (folder == null || !folders.contains(folder))) continue;
            Integer min = route == null ? null : route.getPayMin();
            Integer max = route == null ? null : route.getPayMax();
            if (min != null || max != null) {
                if (paid == null) paid = userId == null ? 0L : paymentRepository.countByUserIdAndStatus(userId, Payment.STATUS_PAID);
                if (min != null && paid < min) continue;
                if (max != null && paid > max) continue;
            }
            return r;
        }
        return null;
    }

    /* ===================== helpers ===================== */

    private Map<Long, RelayRoute> routes() {
        Map<Long, RelayRoute> m = new HashMap<>();
        for (RelayRoute r : routeRepository.findAll()) m.put(r.getRelayId(), r);
        return m;
    }

    /** Priority order; relays saved before priorities existed come after, by name. */
    private static List<RelayServer> ordered(List<RelayServer> all, Map<Long, RelayRoute> routes) {
        List<RelayServer> out = new ArrayList<>(all);
        out.sort(Comparator.<RelayServer>comparingInt(r -> routes.containsKey(r.getId()) ? routes.get(r.getId()).getPriority() : Integer.MAX_VALUE)
                .thenComparing(r -> r.getName() == null ? "" : r.getName())
                .thenComparing(RelayServer::getId));
        return out;
    }

    private int nextPriority() {
        int max = -1;
        for (RelayRoute r : routeRepository.findAll()) max = Math.max(max, r.getPriority());
        return max + 1;
    }

    private View view(RelayServer r, RelayRoute route) {
        View v = new View();
        v.id = String.valueOf(r.getId());
        v.name = r.getName();
        v.ip = r.getIpAddress();
        v.port = r.getPort() == null ? 25 : r.getPort();
        v.active = Boolean.TRUE.equals(r.getIsActive());
        v.memo = r.getMemo() == null ? "" : r.getMemo();
        v.folders = route == null ? Collections.<String>emptyList() : readFolders(route.getFolders());
        v.payMin = route == null ? null : route.getPayMin();
        v.payMax = route == null ? null : route.getPayMax();
        if (route != null && route.getCheckStatus() != null) {
            Map<String, String> c = new LinkedHashMap<>();
            c.put("status", route.getCheckStatus());
            c.put("message", route.getCheckMessage() == null ? "" : route.getCheckMessage());
            c.put("at", route.getCheckedAt() == null ? "" : isoMinutes(route.getCheckedAt()));
            v.check = c;
        }
        return v;
    }

    public static String isoMinutes(LocalDateTime t) {
        return t.withSecond(0).withNano(0).toString().substring(0, 16);
    }

    private static List<String> readFolders(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            List<String> l = JSON.readValue(json, new TypeReference<List<String>>() {});
            return l == null ? Collections.<String>emptyList() : l;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String writeFolders(List<String> folders) {
        List<String> clean = new ArrayList<>();
        if (folders != null) {
            for (String f : folders) {
                String t = trim(f);
                if (!t.isEmpty() && t.length() <= 64 && !clean.contains(t)) clean.add(t);
            }
        }
        if (clean.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(clean);
        } catch (IOException e) {
            throw new RelayException("フォルダを保存できませんでした");
        }
    }

    private static boolean validHost(String v) {
        if (IPV4.matcher(v).matches()) {
            for (String n : v.split("\\.")) if (Integer.parseInt(n) > 255) return false;
            return true;
        }
        return HOST.matcher(v).matches();
    }

    private static int parsePort(String v) {
        String t = trim(v);
        if (t.isEmpty()) return 25;
        try {
            int p = Integer.parseInt(t);
            if (p >= 1 && p <= 65535) return p;
        } catch (NumberFormatException ignored) { /* below */ }
        throw new RelayException("ポートは 1〜65535 で入力してください");
    }

    private static Integer parseCount(String v) {
        String t = trim(v);
        if (t.isEmpty()) return null;
        try {
            int n = Integer.parseInt(t);
            if (n >= 0 && n <= 100000) return n;
        } catch (NumberFormatException ignored) { /* below */ }
        throw new RelayException("入金回数は0以上の数で入力してください");
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }

    private static String cut(String v, int max) { return v == null ? "" : (v.length() > max ? v.substring(0, max) : v); }
}
