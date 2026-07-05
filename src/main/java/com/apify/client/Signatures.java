package com.apify.client;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Apify storage-content URL signing, byte-for-byte compatible with the platform's
 * {@code @apify/utilities} implementation that the reference clients rely on. Internal to the
 * client.
 */
final class Signatures {

  /** Version tag embedded in storage-content signatures (upstream default). */
  private static final String STORAGE_CONTENT_SIGNATURE_VERSION = "0";

  /** Number of leading hex characters of the HMAC digest used. */
  private static final int HMAC_SIGNATURE_HEX_LEN = 30;

  /** Base62 alphabet (digits, then lowercase, then uppercase), matching upstream. */
  private static final String BASE62_ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private static final BigInteger BASE62 = BigInteger.valueOf(BASE62_ALPHABET.length());

  private Signatures() {}

  /**
   * Computes an Apify URL-signing signature, byte-for-byte compatible with upstream {@code
   * createHmacSignature}: HMAC-SHA256(secret, message) as lowercase hex, take the first 30 hex
   * characters, interpret them as a big integer, then base62-encode (alphabet {@code 0-9a-zA-Z}).
   */
  static String createHmacSignature(String secretKey, String message) {
    byte[] digest = hmacSha256(secretKey, message);
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    String truncated = hex.substring(0, HMAC_SIGNATURE_HEX_LEN);
    return toBase62(new BigInteger(truncated, 16));
  }

  /** Encodes a non-negative big integer in base62 using the {@code 0-9a-zA-Z} alphabet. */
  static String toBase62(BigInteger value) {
    if (value.signum() == 0) {
      return "0";
    }
    StringBuilder digits = new StringBuilder();
    BigInteger v = value;
    while (v.signum() > 0) {
      BigInteger[] divRem = v.divideAndRemainder(BASE62);
      digits.append(BASE62_ALPHABET.charAt(divRem[1].intValue()));
      v = divRem[0];
    }
    return digits.reverse().toString();
  }

  /**
   * Builds a storage-content signature for a resource's public URL, byte-for-byte compatible with
   * upstream {@code createStorageContentSignature}.
   *
   * <p>It signs the message {@code "{version}.{expiresAtMillis}.{resourceId}"} ({@code
   * expiresAtMillis} is the absolute expiry in ms, or {@code 0} for a non-expiring URL) with {@link
   * #createHmacSignature}, then returns the base64url (no padding) encoding of {@code
   * "{version}.{expiresAtMillis}.{hmac}"}.
   *
   * @param expiresInSecs optional expiry in seconds ({@code null} for a non-expiring URL)
   */
  static String signStorageContent(String secretKey, String resourceId, Long expiresInSecs) {
    long expiresAtMillis =
        expiresInSecs != null ? System.currentTimeMillis() + expiresInSecs * 1000L : 0L;
    String version = STORAGE_CONTENT_SIGNATURE_VERSION;
    String message = version + "." + expiresAtMillis + "." + resourceId;
    String hmac = createHmacSignature(secretKey, message);
    String envelope = version + "." + expiresAtMillis + "." + hmac;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(envelope.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] hmacSha256(String secretKey, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }
}
