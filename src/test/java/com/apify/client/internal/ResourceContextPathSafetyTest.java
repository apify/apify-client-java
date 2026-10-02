package com.apify.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ResourceContext#toSafeId} and {@link ResourceContext#encodePathSegment} against
 * path-traversal / path-restructuring inputs, mirroring the reference JS client's hardening (<a
 * href="https://github.com/apify/apify-client-js/pull/1011">apify-client-js#1011</a>).
 */
class ResourceContextPathSafetyTest {

  @Test
  void toSafeIdReplacesEveryExtraSlash() {
    // Not just the first: an id with more than one slash must not leave any literal '/' that could
    // restructure the request path.
    assertEquals("apify~hello-world", ResourceContext.toSafeId("apify/hello-world"));
    assertEquals("user~name~extra", ResourceContext.toSafeId("user/name/extra"));
    assertEquals("no-slash", ResourceContext.toSafeId("no-slash"));
  }

  @Test
  void encodePathSegmentPercentEncodesSlashesAndSpecialChars() {
    // A key/id containing slashes must become a single opaque path segment - no literal '/' must
    // survive to restructure the request path, e.g. by walking into a sibling resource.
    assertEquals(
        "..%2F..%2Factor-runs%2FVICTIM%2Fabort",
        ResourceContext.encodePathSegment("../../actor-runs/VICTIM/abort"));
    assertEquals("foo%3Finjected%3D1", ResourceContext.encodePathSegment("foo?injected=1"));
    assertEquals("foo%23frag", ResourceContext.encodePathSegment("foo#frag"));
  }

  @Test
  void encodePathSegmentRejectsDotSegmentsAndEmpty() {
    // A bare "." or ".." (or empty string) is rejected outright rather than encoded: a URL parser
    // resolves dot segments *after* percent-decoding, so an encoded ".." would still collapse
    // against its neighbours once decoded by a client-side or intermediary URL parser.
    assertThrows(IllegalArgumentException.class, () -> ResourceContext.encodePathSegment("."));
    assertThrows(IllegalArgumentException.class, () -> ResourceContext.encodePathSegment(".."));
    assertThrows(IllegalArgumentException.class, () -> ResourceContext.encodePathSegment(""));
    assertThrows(IllegalArgumentException.class, () -> ResourceContext.encodePathSegment(null));
  }

  @Test
  void encodePathSegmentKeepsOrdinaryValuesReadable() {
    assertEquals("my-key", ResourceContext.encodePathSegment("my-key"));
    assertEquals("a%20b", ResourceContext.encodePathSegment("a b"));
  }
}
