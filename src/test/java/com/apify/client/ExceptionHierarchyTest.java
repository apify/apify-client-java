package com.apify.client;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apify.client.http.ApifyApiException;
import com.apify.client.http.ApifyClientException;
import com.apify.client.http.ApifyTransportException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the exception hierarchy's headline contract: {@code catch (ApifyClientException)} must
 * catch every failure this client throws, both the "API responded with an error" case ({@link
 * ApifyApiException}) and the "no response was produced at all" case ({@link
 * ApifyTransportException}). A regression here (e.g. one subtype quietly stops extending the common
 * base) would silently break every caller relying on the documented single catch clause.
 */
class ExceptionHierarchyTest {

  @Test
  void apiExceptionIsCatchableAsClientException() {
    ApifyClientException caught =
        assertThrows(
            ApifyClientException.class,
            () -> {
              throw new ApifyApiException(
                  404, "record-not-found", "not found", 1, "GET", "/x", Map.of());
            });
    assertInstanceOf(ApifyApiException.class, caught);
  }

  @Test
  void transportExceptionIsCatchableAsClientException() {
    ApifyClientException caught =
        assertThrows(
            ApifyClientException.class,
            () -> {
              throw new ApifyTransportException(new IOException("connection refused"));
            });
    assertInstanceOf(ApifyTransportException.class, caught);
  }
}
