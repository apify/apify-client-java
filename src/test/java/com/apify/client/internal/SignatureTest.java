package com.apify.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

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
}
