package com.apify.client.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Known-answer tests pinning the URL-signing scheme against upstream {@code @apify/utilities}. */
class SignatureTest {

  @Test
  void hmacSignatureMatchesUpstream() {
    // HMAC-SHA256 -> hex -> first 30 hex chars -> big integer -> base62 (alphabet 0-9a-zA-Z).
    assertEquals("11GYWmGxviysIBMtnQHBk", Signatures.createHmacSignature("secret", "message"));
  }

  @Test
  void base62Encoding() {
    assertEquals("0", Signatures.toBase62(BigInteger.ZERO));
    assertEquals("Z", Signatures.toBase62(BigInteger.valueOf(61)));
    assertEquals("10", Signatures.toBase62(BigInteger.valueOf(62)));
  }

  @Test
  void storageContentSignatureNonExpiring() {
    // A non-expiring signature uses version "0" and expiresAt 0: base64url("0.0.<hmac over
    // 0.0.RESID>").
    String sig = Signatures.signStorageContent("secret", "RESID", null);
    String decoded = new String(Base64.getUrlDecoder().decode(sig), StandardCharsets.UTF_8);
    String expected = "0.0." + Signatures.createHmacSignature("secret", "0.0.RESID");
    assertEquals(expected, decoded);
  }

  @Test
  void storageContentSignatureExpiresInTheValidityAfterSigning() {
    long before = System.currentTimeMillis();
    String sig = Signatures.signStorageContent("secret", "RESID", 60L);
    long after = System.currentTimeMillis();
    String decoded = new String(Base64.getUrlDecoder().decode(sig), StandardCharsets.UTF_8);
    long expiresAt = Long.parseLong(decoded.split("\\.")[1]);
    assertTrue(expiresAt >= before + 60_000L && expiresAt <= after + 60_000L, decoded);
  }

  @ParameterizedTest
  @ValueSource(
      longs = {0L, -1L, Long.MIN_VALUE, Signatures.MAX_EXPIRES_IN_SECS + 1, Long.MAX_VALUE})
  void signingRejectsAnExpiryThatIsNotPositiveOrTooLarge(long expiresInSecs) {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> Signatures.signStorageContent("secret", "RESID", expiresInSecs));
    assertTrue(e.getMessage().contains("expiresInSecs"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 3600L, Signatures.MAX_EXPIRES_IN_SECS})
  void signingAcceptsAPositiveExpiryUpToTheMaximum(long expiresInSecs) {
    assertDoesNotThrow(() -> Signatures.signStorageContent("secret", "RESID", expiresInSecs));
    assertEquals(4_611_686_018_427_387L, Signatures.MAX_EXPIRES_IN_SECS);
  }
}
