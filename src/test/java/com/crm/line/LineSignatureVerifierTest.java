package com.crm.line;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LineSignatureVerifierTest {

    private static final String SECRET = "test-channel-secret";
    private static final byte[] BODY = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);

    /** Computed independently (Python hmac/hashlib) against BODY+SECRET — a real fixture,
     *  not derived from LineSignatureVerifier itself, so this actually catches a broken
     *  implementation rather than just testing the code against itself. */
    private static final String VALID_SIGNATURE = "sKRrt+MTE71nWWZPaYrvYSdH9JGlgckmBidZxDuPgPc=";

    @Test
    void verify_correctSignature_returnsTrue() {
        assertThat(LineSignatureVerifier.verify(BODY, SECRET, VALID_SIGNATURE)).isTrue();
    }

    @Test
    void verify_wrongSecret_returnsFalse() {
        assertThat(LineSignatureVerifier.verify(BODY, "wrong-secret", VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verify_tamperedBody_returnsFalse() {
        byte[] tampered = "{\"events\":[1]}".getBytes(StandardCharsets.UTF_8);
        assertThat(LineSignatureVerifier.verify(tampered, SECRET, VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verify_garbageSignature_returnsFalse() {
        assertThat(LineSignatureVerifier.verify(BODY, SECRET, "not-a-real-signature")).isFalse();
    }

    @Test
    void verify_nullSignature_returnsFalse() {
        assertThat(LineSignatureVerifier.verify(BODY, SECRET, null)).isFalse();
    }

    @Test
    void verify_nullBody_returnsFalse() {
        assertThat(LineSignatureVerifier.verify(null, SECRET, VALID_SIGNATURE)).isFalse();
    }

    @Test
    void verify_nullOrEmptySecret_returnsFalse() {
        assertThat(LineSignatureVerifier.verify(BODY, null, VALID_SIGNATURE)).isFalse();
        assertThat(LineSignatureVerifier.verify(BODY, "", VALID_SIGNATURE)).isFalse();
    }
}
