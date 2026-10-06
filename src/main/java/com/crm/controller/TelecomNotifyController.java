package com.crm.controller;

import com.crm.interceptor.AdminIpInterceptor;
import com.crm.service.TelecomCreditService;
import com.crm.util.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * テレコムクレジット 決済データ送信先URL ({@value TelecomCreditService#NOTIFY_PATH}; GET by default, POST
 * when Telecom has switched it). Only Telecom's 決済サーバー IPs are accepted. Answers
 * {@code SuccessOK} (HTTP 200) once the data needs no resend; a temporary failure answers 500 so
 * Telecom resends (every 5 minutes, for 3 hours).
 */
@RestController
public class TelecomNotifyController {

    private static final Logger log = LoggerFactory.getLogger(TelecomNotifyController.class);

    private final TelecomCreditService telecomCreditService;

    public TelecomNotifyController(TelecomCreditService telecomCreditService) {
        this.telecomCreditService = telecomCreditService;
    }

    @RequestMapping(value = TelecomCreditService.NOTIFY_PATH, method = {RequestMethod.GET, RequestMethod.POST},
            produces = "text/plain; charset=UTF-8")
    public ResponseEntity<String> notice(HttpServletRequest request) {
        String ip = AdminIpInterceptor.clientIp(request);
        if (!telecomCreditService.isServerIp(ip)) {
            log.warn("telecom notice from a non-Telecom IP {} refused", LogSafe.of(ip));
            return ResponseEntity.status(403).body("forbidden");
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            params.put(e.getKey(), e.getValue() == null || e.getValue().length == 0 ? "" : e.getValue()[0]);
        }
        try {
            telecomCreditService.handleNotice(params);
        } catch (RuntimeException e) {
            log.warn("telecom notice failed (Telecom will resend): {}", e.toString());
            return ResponseEntity.status(500).body("error");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body("SuccessOK");
    }
}
