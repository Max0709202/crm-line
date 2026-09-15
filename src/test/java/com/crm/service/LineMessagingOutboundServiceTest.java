package com.crm.service;

import com.crm.line.LineApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineMessagingOutboundServiceTest {

    private LineApiClient lineApiClient;
    private LineMessagingOutboundService svc;

    @BeforeEach
    void setUp() {
        lineApiClient = mock(LineApiClient.class);
        svc = new LineMessagingOutboundService(lineApiClient);
    }

    private static OutboundLineService.LineSendRequest req() {
        return new OutboundLineService.LineSendRequest("token", "Uabc", "hello", null, null);
    }

    @Test
    void send_200_isSuccess() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(200, "{}"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.success).isTrue();
    }

    @Test
    void send_429_isRetriable() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(429, "rate limited"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.success).isFalse();
        assertThat(result.retriable).isTrue();
    }

    @Test
    void send_500_isRetriable() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(500, "server error"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.retriable).isTrue();
    }

    @Test
    void send_networkFailure_isRetriable() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(-1, "timeout"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.retriable).isTrue();
    }

    @Test
    void send_400_isPermanentFailure() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(400, "bad request"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.success).isFalse();
        assertThat(result.retriable).isFalse();
    }

    @Test
    void send_401_isPermanentFailure() {
        when(lineApiClient.push(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new LineApiClient.PushResult(401, "invalid token"));
        OutboundLineService.SendResult result = svc.send(req());
        assertThat(result.retriable).isFalse();
    }

    @Test
    void send_missingAccessToken_failsWithoutCallingApi() {
        OutboundLineService.LineSendRequest req = new OutboundLineService.LineSendRequest(null, "Uabc", "hi", null, null);
        OutboundLineService.SendResult result = svc.send(req);
        assertThat(result.success).isFalse();
        assertThat(result.retriable).isFalse();
    }

    @Test
    void send_missingToUserId_failsWithoutCallingApi() {
        OutboundLineService.LineSendRequest req = new OutboundLineService.LineSendRequest("token", null, "hi", null, null);
        OutboundLineService.SendResult result = svc.send(req);
        assertThat(result.success).isFalse();
    }
}
