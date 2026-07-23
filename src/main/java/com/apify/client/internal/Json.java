package com.apify.client.internal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared JSON (de)serialization for the client, centered on a single configured {@link
 * ObjectMapper}. Internal to the client.
 *
 * <p>The mapper ignores unknown properties (models also collect them in an {@code extra} map for
 * forward compatibility), renders dates as ISO-8601 strings, and omits {@code null} fields when
 * serializing request bodies.
 *
 * <p>Built with Jackson 3's immutable {@link JsonMapper.Builder}: unlike Jackson 2's mutable {@code
 * ObjectMapper}, every setting is fixed at construction time, so the resulting mapper needs no
 * further synchronization to be safely shared across threads. Java-time (de)serialization ({@code
 * Instant}, {@code Duration}, ...) is built into jackson-databind itself in Jackson 3, so (unlike
 * Jackson 2) no separate {@code JavaTimeModule} registration is needed.
 */
public final class Json {

  static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          // Several model classes use primitive numeric fields (long/double) for API values that
          // are usually present but can legitimately be absent/null early in a resource's
          // lifecycle (e.g. ActorRunStats fields before a run's stats are fully populated).
          // Jackson 2 coerced a JSON null into a primitive's zero value by default; Jackson 3 does
          // not, so this is set explicitly to keep that lenient, non-throwing behavior against the
          // live API instead of every such field having to be widened to a boxed type.
          .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
          .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .changeDefaultPropertyInclusion(
              incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
          // Bind directly to (private) fields so models need no setters — getters remain the
          // public read surface. Any-getters/setters are still honored for the `extra` map.
          .changeDefaultVisibility(
              vc ->
                  vc.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                      .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                      .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE))
          .build();

  private Json() {}

  /**
   * Serializes a value to JSON bytes.
   *
   * @throws JacksonException (unchecked, per Jackson 3) if serialization fails
   */
  public static byte[] toBytes(Object value) {
    return MAPPER.writeValueAsBytes(value);
  }

  /** Parses JSON bytes into the given type. */
  public static <T> T parse(byte[] body, JavaType type) {
    return MAPPER.readValue(body, type);
  }

  /** Parses JSON bytes into the given class. */
  public static <T> T parse(byte[] body, Class<T> type) {
    return MAPPER.readValue(body, type);
  }

  /**
   * Parses JSON bytes into the given class, returning {@code null} instead of throwing if the body
   * is missing, empty, or not valid JSON for that shape. For call sites (like error-body parsing)
   * where malformed input is an expected, non-fatal possibility rather than a programming error.
   */
  static <T> T tryParse(byte[] body, Class<T> type) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      return MAPPER.readValue(body, type);
    } catch (JacksonException e) {
      return null;
    }
  }

  /** Parses JSON bytes into the given {@link TypeReference} (for generic types). */
  public static <T> T parse(byte[] body, TypeReference<T> type) {
    return MAPPER.readValue(body, type);
  }

  /** Constructs a {@link JavaType} for a raw class. */
  public static JavaType type(Class<?> raw) {
    return MAPPER.getTypeFactory().constructType(raw);
  }

  /** Constructs a parametric {@link JavaType}, e.g. {@code PaginationList<Actor>}. */
  public static JavaType parametric(Class<?> raw, JavaType... params) {
    return MAPPER.getTypeFactory().constructParametricType(raw, params);
  }

  /**
   * Parses a JSON response body wrapped in a {@code {"data": ...}} envelope, returning the
   * unwrapped {@code data} value of the given type.
   */
  public static <T> T parseData(byte[] body, JavaType dataType) {
    JavaType envelopeType = parametric(DataEnvelope.class, dataType);
    DataEnvelope<T> envelope = parse(body, envelopeType);
    return envelope.getData();
  }

  /** Parses a data-envelope whose {@code data} is of the given class. */
  public static <T> T parseData(byte[] body, Class<T> dataClass) {
    return parseData(body, type(dataClass));
  }
}
