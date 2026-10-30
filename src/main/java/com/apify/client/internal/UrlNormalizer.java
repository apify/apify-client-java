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
          "^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)([^?#]*)(\\?[^#]*)?(#.*)?$", Pattern.DOTALL);

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
    String scheme = m.group(1).toLowerCase(Locale.ROOT);
    Integer defaultPort = SPECIAL_SCHEMES.get(scheme);
    boolean special = defaultPort != null;

    String authority = m.group(2);
    String userInfo = "";
    int at = authority.lastIndexOf('@');
    if (at >= 0) {
      userInfo = encode(authority.substring(0, at), USERINFO_UNSAFE) + "@";
      authority = authority.substring(at + 1);
    }
    String host = authority;
    String port = "";
    int colon = authority.lastIndexOf(':');
    if (colon >= 0 && authority.indexOf(']') < colon) {
      host = authority.substring(0, colon);
      port = authority.substring(colon + 1);
    }
    if (special && host.isEmpty()) {
      return value;
    }
    host = normalizeHost(host);
    if (host == null || !port.matches("\\d*")) {
      return value;
    }
    if (!port.isEmpty() && defaultPort != null && Integer.parseInt(port) == defaultPort) {
      port = "";
    }
    if (!port.isEmpty()) {
      port = ":" + Integer.parseInt(port);
    }

    String rawPath = special ? m.group(3).replace('\\', '/') : m.group(3);
    String path =
        special ? resolveDotSegments(encode(rawPath, PATH_UNSAFE)) : encode(rawPath, PATH_UNSAFE);
    if (special && path.isEmpty()) {
      path = "/";
    }
    String query =
        m.group(4) == null ? "" : encode(m.group(4), special ? SPECIAL_QUERY_UNSAFE : QUERY_UNSAFE);
    String fragment = m.group(5) == null ? "" : encode(m.group(5), FRAGMENT_UNSAFE);
    return scheme + "://" + userInfo + host + port + path + query + fragment;
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
