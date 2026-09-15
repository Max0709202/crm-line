package com.crm.line;

import com.crm.line.dto.LineBotInfoResponse;
import com.crm.line.dto.LineProfileResponse;
import com.crm.util.LogSafe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin HTTP client for LINE's Messaging API. Deliberately has zero dependency on any
 * com.crm.entity/service/repository/controller class — this is the portable half of the
 * LINE integration, meant to be lifted into a future standalone system unchanged. Callers
 * (com.crm.service.LineAccountService etc.) pass in plain strings (already-decrypted
 * tokens) and get back plain DTOs/results; all CRM-specific persistence happens outside
 * this class.
 *
 * <p>Push/multicast/profile methods are added in a later phase; this class currently only
 * implements the connection-check call needed by the account-management screen.
 */
@Component
public class LineApiClient {

    private static final Logger log = LoggerFactory.getLogger(LineApiClient.class);
    private static final String API_BASE = "https://api.line.me";

    private final ObjectMapper objectMapper;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public LineApiClient(ObjectMapper objectMapper,
                          @Value("${app.line.connect-timeout-ms:10000}") int connectTimeoutMs,
                          @Value("${app.line.read-timeout-ms:15000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Calls {@code GET /v2/bot/info} to verify a Channel Access Token actually authenticates.
     * Returns null on any non-2xx response or network error (logged, not thrown) — callers
     * treat null as "connection check failed" and flip the account's status to ERROR.
     */
    public LineBotInfoResponse getBotInfo(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            log.warn("[LINE] getBotInfo called with no access token");
            return null;
        }
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        try (CloseableHttpClient http = HttpClientBuilder.create().setDefaultRequestConfig(rc).build()) {
            HttpGet get = new HttpGet(API_BASE + "/v2/bot/info");
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = http.execute(get)) {
                int code = resp.getStatusLine().getStatusCode();
                String body = resp.getEntity() == null ? ""
                        : EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (code == 200) {
                    return objectMapper.readValue(body, LineBotInfoResponse.class);
                }
                log.warn("[LINE] getBotInfo failed: status={} body={}", code, LogSafe.of(truncate(body, 500)));
                return null;
            }
        } catch (Exception e) {
            log.warn("[LINE] getBotInfo error: {}", LogSafe.of(e.toString()));
            return null;
        }
    }

    /**
     * Calls {@code GET /v2/bot/profile/{userId}} to fetch a display name/photo for a LINE
     * contact — used only to make the unmatched-contacts screen show a human name/photo
     * instead of a bare opaque userId. Returns null on any failure (logged, not thrown);
     * a failed profile lookup is not a reason to fail processing the message itself.
     */
    public LineProfileResponse getProfile(String accessToken, String lineUserId) {
        if (accessToken == null || accessToken.trim().isEmpty() || lineUserId == null || lineUserId.trim().isEmpty()) {
            return null;
        }
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        try (CloseableHttpClient http = HttpClientBuilder.create().setDefaultRequestConfig(rc).build()) {
            String encodedUserId = java.net.URLEncoder.encode(lineUserId, StandardCharsets.UTF_8.name());
            HttpGet get = new HttpGet(API_BASE + "/v2/bot/profile/" + encodedUserId);
            get.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpResponse resp = http.execute(get)) {
                int code = resp.getStatusLine().getStatusCode();
                String body = resp.getEntity() == null ? ""
                        : EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (code == 200) {
                    return objectMapper.readValue(body, LineProfileResponse.class);
                }
                log.warn("[LINE] getProfile failed: status={} body={}", code, LogSafe.of(truncate(body, 500)));
                return null;
            }
        } catch (Exception e) {
            log.warn("[LINE] getProfile error: {}", LogSafe.of(e.toString()));
            return null;
        }
    }

    /**
     * Calls {@code POST /v2/bot/message/push} to send a text message. Always push, never
     * the reply API — LINE's {@code replyToken} expires in roughly a minute, far too short
     * for a human staffer to read an inbound message and answer it.
     *
     * <p>{@code senderName}/{@code senderIconUrl} are the support-character/group-chat
     * persona override (both null means send as the Official Account itself with no
     * override) — see LINE's {@code sender} message field.
     *
     * <p>Returns an HTTP-level result rather than throwing or returning null; mapping that
     * to this app's retriable/fail/success semantics is {@code LineMessagingOutboundService}'s
     * job, not this class's — {@code com.crm.line} stays free of any com.crm.service type.
     */
    public PushResult push(String accessToken, String toLineUserId, String text,
                            String senderName, String senderIconUrl) {
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setConnectionRequestTimeout(connectTimeoutMs)
                .setSocketTimeout(readTimeoutMs)
                .build();

        Map<String, Object> textMessage = new LinkedHashMap<>();
        textMessage.put("type", "text");
        textMessage.put("text", text);
        if (senderName != null || senderIconUrl != null) {
            Map<String, Object> sender = new LinkedHashMap<>();
            if (senderName != null) sender.put("name", senderName);
            if (senderIconUrl != null) sender.put("iconUrl", senderIconUrl);
            textMessage.put("sender", sender);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", toLineUserId);
        payload.put("messages", java.util.Collections.singletonList(textMessage));

        try (CloseableHttpClient http = HttpClientBuilder.create().setDefaultRequestConfig(rc).build()) {
            String body = objectMapper.writeValueAsString(payload);
            HttpPost post = new HttpPost(API_BASE + "/v2/bot/message/push");
            post.setHeader("Authorization", "Bearer " + accessToken);
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));

            try (CloseableHttpResponse resp = http.execute(post)) {
                int code = resp.getStatusLine().getStatusCode();
                String respBody = resp.getEntity() == null ? ""
                        : EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (code != 200) {
                    log.warn("[LINE] push failed: to={} status={} body={}",
                            LogSafe.of(toLineUserId), code, LogSafe.of(truncate(respBody, 500)));
                }
                return new PushResult(code, respBody);
            }
        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
            log.warn("[LINE] push network error: to={} error={}", LogSafe.of(toLineUserId), LogSafe.of(e.toString()));
            return new PushResult(-1, e.toString());
        } catch (Exception e) {
            log.warn("[LINE] push error: to={} error={}", LogSafe.of(toLineUserId), LogSafe.of(e.toString()));
            return new PushResult(-1, e.toString());
        }
    }

    /** {@code httpStatus} of -1 means a network-level failure (no response at all) rather
     *  than an HTTP error response — callers should treat both as retriable, but the two
     *  are worth distinguishing in logs. */
    public static class PushResult {
        public final int httpStatus;
        public final String body;
        public PushResult(int httpStatus, String body) {
            this.httpStatus = httpStatus;
            this.body = body;
        }
        public boolean isSuccess() { return httpStatus == 200; }
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "...");
    }
}
