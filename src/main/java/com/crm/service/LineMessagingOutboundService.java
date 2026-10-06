package com.crm.service;

import com.crm.line.LineApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Real LINE Messaging API adapter. Active when {@code app.line.adapter=line}; maps
 * {@link LineApiClient.PushResult}'s bare HTTP outcome to this app's retriable/fail/success
 * contract — that mapping decision belongs here (com.crm.service), not in the portable
 * {@code com.crm.line} client.
 */
@Service
@ConditionalOnProperty(name = "app.line.adapter", havingValue = "line")
public class LineMessagingOutboundService implements OutboundLineService {

    private static final Logger log = LoggerFactory.getLogger(LineMessagingOutboundService.class);

    private final LineApiClient lineApiClient;

    public LineMessagingOutboundService(LineApiClient lineApiClient) {
        this.lineApiClient = lineApiClient;
    }

    @Override
    public SendResult send(LineSendRequest req) {
        if (req.accessToken == null || req.accessToken.trim().isEmpty()) {
            return SendResult.fail("LINE Access Tokenが設定されていません");
        }
        if (req.toLineUserId == null || req.toLineUserId.trim().isEmpty()) {
            return SendResult.fail("送信先のLINEユーザーIDがありません");
        }

        LineApiClient.PushResult result = req.imageUrls.isEmpty()
                ? lineApiClient.push(req.accessToken, req.toLineUserId, req.body, req.senderName, req.senderIconUrl)
                : lineApiClient.push(req.accessToken, req.toLineUserId, req.body, req.senderName, req.senderIconUrl, req.imageUrls);

        if (result.isSuccess()) {
            log.info("[LINE] sent: to={}", req.toLineUserId);
            return SendResult.ok();
        }
        // 429 (rate limit) and 5xx are transient; 400/401/403 (bad request/token/permission)
        // are not — same three-way mapping OutboundSmsService/OutboundMailService already use,
        // which plugs straight into MessageService.sendNow()'s existing backoff logic.
        boolean retriable = result.httpStatus == 429 || result.httpStatus >= 500 || result.httpStatus == -1;
        String msg = "LINE API returned " + result.httpStatus + ": " + truncate(result.body, 300);
        return retriable ? SendResult.retriable(msg) : SendResult.fail(msg);
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "...");
    }
}
