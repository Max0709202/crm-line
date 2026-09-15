package com.crm.line;

import com.crm.line.dto.LineBotInfoResponse;
import com.crm.util.LogSafe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "...");
    }
}
