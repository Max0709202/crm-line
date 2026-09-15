package com.crm.service;

/**
 * LINE channel adapter. Mirrors {@link OutboundSmsService}'s shape (its own request type,
 * the same three-way {@code SendResult}) rather than folding into {@link OutboundMailService} —
 * a LINE push call is a bearer-token JSON POST to LINE's own API, not SMTP-shaped.
 *
 * <p>{@link StubOutboundLineService} is active by default so account management, inbound
 * webhook handling, and the rest of the pipeline are all testable before a real Channel
 * Access Token exists. Switching to the real adapter ({@link com.crm.service.LineMessagingOutboundService},
 * added once outbound sending is implemented) is a one-line env var change
 * ({@code app.line.adapter=line}), no code change — see {@code application.yml}.
 */
public interface OutboundLineService {

    SendResult send(LineSendRequest req);

    class LineSendRequest {
        public final String accessToken;
        public final String toLineUserId;
        public final String body;
        /** Persona override (support-character/group-chat mode) — null means send as the
         *  Official Account itself with no override. Populated starting in the phase that
         *  adds {@code LineAccount.isGroupChatMode}. */
        public final String senderName;
        public final String senderIconUrl;

        public LineSendRequest(String accessToken, String toLineUserId, String body,
                                String senderName, String senderIconUrl) {
            this.accessToken = accessToken;
            this.toLineUserId = toLineUserId;
            this.body = body;
            this.senderName = senderName;
            this.senderIconUrl = senderIconUrl;
        }
    }

    class SendResult {
        public final boolean success;
        /** True when the failure is transient (e.g. LINE 429/5xx) and the caller should retry. */
        public final boolean retriable;
        public final String errorMessage;
        private SendResult(boolean success, boolean retriable, String errorMessage) {
            this.success = success;
            this.retriable = retriable;
            this.errorMessage = errorMessage;
        }
        public static SendResult ok() { return new SendResult(true, false, null); }
        public static SendResult fail(String msg) { return new SendResult(false, false, msg); }
        public static SendResult retriable(String msg) { return new SendResult(false, true, msg); }
    }
}
