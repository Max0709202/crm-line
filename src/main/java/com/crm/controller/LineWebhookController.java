package com.crm.controller;

import com.crm.entity.LineAccount;
import com.crm.line.LineSignatureVerifier;
import com.crm.line.dto.LineWebhookPayload;
import com.crm.service.LineWebhookService;
import com.crm.util.AesEncryptionUtil;
import com.crm.util.LogSafe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Webhook for LINE's Messaging API. Each {@link LineAccount} has its own URL
 * ({@code https://<host>/api/inbound/line/<webhookToken>}) registered in that Channel's own
 * LINE Developers Console page — {@code webhookToken} only routes to the right account, the
 * actual security boundary is {@link LineSignatureVerifier}'s HMAC check on
 * {@code X-Line-Signature}, keyed by that account's Channel Secret.
 *
 * <p>Body is bound as raw {@code byte[]} (not {@code String}) specifically so the signature
 * check runs over the exact bytes LINE signed — see {@link LineSignatureVerifier}'s javadoc.
 *
 * <p>Response codes follow the same policy as {@code SmsInboundApiController}: 403 for a bad
 * signature/unknown token, 200 for anything we successfully processed (including "some
 * events were skipped" — that's normal, not a failure LINE's retry logic should act on),
 * 500 only for a genuine unexpected error.
 */
@RestController
@RequestMapping("/api/inbound/line")
public class LineWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookController.class);

    private final LineWebhookService lineWebhookService;
    private final AesEncryptionUtil aes;
    private final ObjectMapper objectMapper;

    public LineWebhookController(LineWebhookService lineWebhookService, AesEncryptionUtil aes, ObjectMapper objectMapper) {
        this.lineWebhookService = lineWebhookService;
        this.aes = aes;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{webhookToken}")
    public ResponseEntity<Map<String, Object>> receive(@PathVariable String webhookToken,
                                                         @RequestHeader(value = "X-Line-Signature", required = false) String signature,
                                                         @RequestBody(required = false) byte[] rawBody) {
        Map<String, Object> resp = new HashMap<>();

        Optional<LineAccount> accountOpt = lineWebhookService.resolveAccount(webhookToken);
        if (!accountOpt.isPresent()) {
            log.warn("[LINE] webhook call with unknown token");
            resp.put("accepted", false);
            resp.put("reason", "UNKNOWN_ACCOUNT");
            return ResponseEntity.status(403).body(resp);
        }
        LineAccount account = accountOpt.get();

        String channelSecret = aes.decrypt(account.getChannelSecret());
        if (!LineSignatureVerifier.verify(rawBody, channelSecret, signature)) {
            log.warn("[LINE] webhook signature verification failed: account={}", account.getId());
            resp.put("accepted", false);
            resp.put("reason", "INVALID_SIGNATURE");
            return ResponseEntity.status(403).body(resp);
        }

        try {
            // Temporary diagnostic logging (2026-09-21) — a client account has repeatedly
            // gotten clean 200/Verify responses but never a single logged follow/message
            // event, across several re-created accounts. Logging the exact bytes LINE sent
            // settles whether real events are arriving with an unexpected shape (silently
            // hitting a skip branch) versus never arriving at all, instead of guessing.
            if (rawBody != null && rawBody.length > 0) {
                log.info("[LINE] raw webhook body: account={} body={}", account.getId(),
                        LogSafe.of(new String(rawBody, java.nio.charset.StandardCharsets.UTF_8)));
            }
            LineWebhookPayload payload = (rawBody == null || rawBody.length == 0)
                    ? null : objectMapper.readValue(rawBody, LineWebhookPayload.class);
            List<LineWebhookService.ProcessResult> results = lineWebhookService.process(account, payload);
            resp.put("accepted", true);
            resp.put("processed", results.size());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("[LINE] webhook processing error: account={} error={}", account.getId(), LogSafe.of(e.toString()), e);
            resp.put("accepted", false);
            resp.put("reason", "SERVER_ERROR");
            return ResponseEntity.status(500).body(resp);
        }
    }
}
