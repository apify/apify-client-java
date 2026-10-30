package com.apify.client.internal;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes absolute URLs the way the WHATWG URL serializer does, so every Apify client returns
 * the same string for the same {@code format: uri} field of the API: lowercase scheme and host,
 * punycode host, default port dropped, an empty path serialized as {@code /}, dot segments
 * resolved, and unsafe characters percent-encoded. Internal to the client.
 */
public final class UrlNormalizer {

  /**
   * {@code scheme://authority[path][?query][#fragment]}, the only shape the spec's URL fields have.
   */
  private static final Pattern URL =
      Pattern.compile(
          "^(?<scheme>[A-Za-z][A-Za-z0-9+.-]*)://(?<authority>[^/?#]*)(?<path>[^?#]*)"
              + "(?<query>\\?[^#]*)?(?<fragment>#.*)?$",
          Pattern.DOTALL);

  /** The highest TCP port; a larger one makes the URL invalid. */
  private static final int MAX_PORT = 65535;

  /** Schemes with a default port and a mandatory (possibly empty) path. */
  private static final Map<String, Integer> SPECIAL_SCHEMES =
      Map.of("http", 80, "https", 443, "ws", 80, "wss", 443, "ftp", 21);

  private UrlNormalizer() {}

  /**
   * Returns the normalized form of {@code value}. A {@code null} value, and a value that is not an
   * absolute {@code scheme://host...} URL, is returned unchanged: the client never fails to read a
   * response because of one odd URL field.
   */
  public static String normalize(String value) {
    if (value == null) {
      return null;
    }
    Matcher m = URL.matcher(value.strip());
    if (!m.matches()) {
      return value;
    }
    String scheme = m.group("scheme").toLowerCase(Locale.ROOT);
    Integer defaultPort = SPECIAL_SCHEMES.get(scheme);
    boolean special = defaultPort != null;

    String authority = m.group("authority");
    String userInfo = "";
    int at = authority.lastIndexOf('@');
    if (at >= 0) {
      String encoded = encodeUserInfo(authority.substring(0, at));
      userInfo = encoded.isEmpty() ? "" : encoded + "@";
      authority = authority.substring(at + 1);
    }
    String host = authority;
    String port = "";
    int colon = authority.lastIndexOf(':');
    boolean hasPortSeparator = colon >= 0 && authority.indexOf(']') < colon;
    if (hasPortSeparator) {
      host = authority.substring(0, colon);
      port = authority.substring(colon + 1);
    }
    // An empty host is valid only for a non-special scheme without credentials or a port separator.
    if (host.isEmpty() && (special || at >= 0 || hasPortSeparator)) {
      return value;
    }
    host = normalizeHost(host);
    if (host == null || !port.matches("\\d*")) {
      return value;
    }
    // Leading zeros do not count; a port with more digits than MAX_PORT has is above it, which also
    // keeps parseInt from overflowing.
    String digits = port.replaceFirst("^0+(?=\\d)", "");
    if (digits.length() > String.valueOf(MAX_PORT).length()) {
      return value;
    }
    port = "";
    if (!digits.isEmpty()) {
      int number = Integer.parseInt(digits);
      if (number > MAX_PORT) {
        return value;
      }
      if (defaultPort == null || number != defaultPort) {
        port = ":" + number;
      }
    }

    String rawPath = special ? m.group("path").replace('\\', '/') : m.group("path");
    String path =
        special ? resolveDotSegments(encode(rawPath, PATH_UNSAFE)) : encode(rawPath, PATH_UNSAFE);
    if (special && path.isEmpty()) {
      path = "/";
    }
    String rawQuery = m.group("query");
    String query =
        rawQuery == null ? "" : encode(rawQuery, special ? SPECIAL_QUERY_UNSAFE : QUERY_UNSAFE);
    String rawFragment = m.group("fragment");
    String fragment = rawFragment == null ? "" : encode(rawFragment, FRAGMENT_UNSAFE);
    return scheme + "://" + userInfo + host + port + path + query + fragment;
  }

  /**
   * Encodes the {@code username[:password]} part of the authority: each of the two is encoded on
   * its own, so the separator between them stays a colon, and an empty one is dropped.
   */
  private static String encodeUserInfo(String userInfo) {
    int colon = userInfo.indexOf(':');
    String username = encode(colon < 0 ? userInfo : userInfo.substring(0, colon), USERINFO_UNSAFE);
    String password = colon < 0 ? "" : encode(userInfo.substring(colon + 1), USERINFO_UNSAFE);
    // An empty username or password is dropped, along with the separator when the password is.
    return password.isEmpty() ? username : username + ":" + password;
  }

  /**
   * Lowercases the host and converts a non-ASCII one to punycode; {@code null} if it is invalid.
   */
  private static String normalizeHost(String host) {
    if (host.startsWith("[")) {
      return host.toLowerCase(Locale.ROOT);
    }
    try {
      return IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Resolves {@code .} and {@code ..} segments of an already-encoded path. */
  private static String resolveDotSegments(String path) {
    if (path.isEmpty()) {
      return path;
    }
    List<String> out = new ArrayList<>();
    String[] segments = path.substring(1).split("/", -1);
    for (int i = 0; i < segments.length; i++) {
      String seg = segments[i];
      boolean last = i == segments.length - 1;
      String lower = seg.toLowerCase(Locale.ROOT);
      boolean dotDot =
          lower.equals("..")
              || lower.equals(".%2e")
              || lower.equals("%2e.")
              || lower.equals("%2e%2e");
      boolean dot = lower.equals(".") || lower.equals("%2e");
      if (dotDot) {
        if (!out.isEmpty()) {
          out.remove(out.size() - 1);
        }
        if (last) {
          out.add("");
        }
      } else if (dot) {
        if (last) {
          out.add("");
        }
      } else {
        out.add(seg);
      }
    }
    return "/" + String.join("/", out);
  }

  private static final String USERINFO_UNSAFE = " \"#<>?`{}/:;=@[\\]^|";
  private static final String PATH_UNSAFE = " \"#<>?`{}";
  private static final String QUERY_UNSAFE = " \"#<>";
  private static final String SPECIAL_QUERY_UNSAFE = " \"#<>'";
  private static final String FRAGMENT_UNSAFE = " \"<>`";

  /**
   * Percent-encodes the ASCII characters in {@code unsafe}, control characters and every non-ASCII
   * character (as UTF-8). An existing {@code %XX} escape is kept as it is.
   */
  private static String encode(String s, String unsafe) {
    StringBuilder sb = new StringBuilder(s.length());
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      int c = b & 0xFF;
      if (c < 0x20 || c >= 0x7F || unsafe.indexOf(c) >= 0) {
        sb.append('%').append(String.format("%02X", c));
      } else {
        sb.append((char) c);
      }
    }
    return sb.toString();
  }
}
