package com.crm.controller;

import com.crm.interceptor.AdminIpInterceptor;
import com.crm.service.BitcashService;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ビットキャッシュ 完了通知受領URL ({@value BitcashService#NOTIFY_PATH}, POST). Only ビットキャッシュ's
 * 決済システム IPs are accepted, and the hash is checked with the 通知キー. Answers 200 once the notice
 * needs no resend; a temporary failure answers 500 so ビットキャッシュ resends (for about 5 minutes).
 */
@RestController
public class BitcashNotifyController {

    private static final Logger log = LoggerFactory.getLogger(BitcashNotifyController.class);

    private final BitcashService bitcashService;

    public BitcashNotifyController(BitcashService bitcashService) {
        this.bitcashService = bitcashService;
    }

    @PostMapping(value = BitcashService.NOTIFY_PATH, produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> notice(HttpServletRequest request) {
        String ip = AdminIpInterceptor.clientIp(request);
        if (!bitcashService.isServerIp(ip)) {
            log.warn("bitcash notice from a non-BitCash IP {} refused", LogSafe.of(ip));
            return ResponseEntity.status(403).body("forbidden");
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            params.put(e.getKey(), e.getValue() == null || e.getValue().length == 0 ? "" : e.getValue()[0]);
        }
        try {
            if (!bitcashService.handleNotice(params)) return ResponseEntity.status(400).body("hash mismatch");
        } catch (RuntimeException e) {
            log.warn("bitcash notice failed (BitCash will resend): {}", e.toString());
            return ResponseEntity.status(500).body("error");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body("OK");
    }

    /**
     * Back from ビットキャッシュ's 決済画面 (return_url {@code r=done} / cancel_url {@code r=cancel}): moves on to
     * ポイント購入 from this page (same-site), where the member's session is seen and open orders are confirmed.
     */
    @GetMapping(value = BitcashService.RETURN_PATH, produces = "text/html; charset=UTF-8")
    public ResponseEntity<String> back(@RequestParam(name = "r", required = false) String r) {
        String next = "done".equals(r) ? "/member/points?done=bitcash" : "/member/points?cancel=1";
        String html = "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"robots\" content=\"noindex\">"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + next + "\"><title>ポイント購入</title></head>"
                + "<body><script>location.replace('" + next + "');</script>"
                + "<p><a href=\"" + next + "\">ポイント購入へ戻ります…</a></p></body></html>";
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").body(html);
    }
}
