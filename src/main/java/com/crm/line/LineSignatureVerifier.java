package com.crm.line;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verifies LINE's {@code X-Line-Signature} webhook header: HMAC-SHA256 over the exact raw
 * request body bytes, keyed by the channel secret, base64-encoded. Must run against the
 * raw {@code byte[]} the servlet received — a re-decoded/re-encoded String is not guaranteed
 * to reproduce the exact bytes LINE signed, which is why {@code LineWebhookController} binds
 * the request body as {@code byte[]}, not {@code String} (unlike the SMS inbound webhook,
 * which has no signature to verify and so can safely use a String).
 *
 * <p>Zero dependency on anything CRM-specific — pure protocol logic, part of the portable
 * {@code com.crm.line} package.
 */
public final class LineSignatureVerifier {

    private LineSignatureVerifier() {}

    public static boolean verify(byte[] body, String channelSecret, String signatureHeader) {
        if (body == null || channelSecret == null || channelSecret.isEmpty() || signatureHeader == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = Base64.getEncoder().encodeToString(mac.doFinal(body));
            return constantTimeEquals(computed, signatureHeader);
        } catch (Exception e) {
            return false;
        }
    }

    /** Same technique as {@code CsrfInterceptor.constantTimeEquals()} — avoid a timing
     *  side-channel on the comparison itself. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
