/**
 * Module descriptor for the Apify Java client.
 *
 * <p>Only the resource-scoped, user-facing packages are exported; {@code com.apify.client.internal}
 * holds pure implementation plumbing (the HTTP orchestration core, JSON mapping, query-string and
 * resource-context helpers, URL-signature math, ...) shared across resource clients and is
 * deliberately not exported, so a consumer running on the module path sees only the public API
 * surface. (A consumer on the classpath, the common case for a Maven/Gradle dependency, is
 * unaffected by module boundaries either way.)
 */
module com.apify.client {
  requires transitive java.net.http;
  // Not used by the client itself; DocSnippetsTest (compiled against this module, since Maven
  // resolves test sources on the module path when main sources declare one) uses the in-process
  // Java compiler API to compile documentation code snippets as part of the test suite.
  requires java.compiler;
  // Jackson 3: tools.jackson.databind `requires transitive` both tools.jackson.core and
  // com.fasterxml.jackson.annotation, so this one line also makes those two modules' packages
  // (JacksonException, TypeReference, the Jackson annotations, ...) readable here.
  requires tools.jackson.databind;
  requires org.slf4j;
  requires static com.aayushatharva.brotli4j;

  exports com.apify.client;
  exports com.apify.client.actor;
  exports com.apify.client.build;
  exports com.apify.client.run;
  exports com.apify.client.dataset;
  exports com.apify.client.keyvalue;
  exports com.apify.client.requestqueue;
  exports com.apify.client.task;
  exports com.apify.client.schedule;
  exports com.apify.client.webhook;
  exports com.apify.client.user;
  exports com.apify.client.store;
  exports com.apify.client.log;
  exports com.apify.client.http;

  // Jackson deserializes directly into (private) fields (see Json's FIELD/ANY visibility config),
  // which requires reflective access opened to jackson-databind for every package holding a model
  // or request/response DTO, including the non-exported internal package.
  opens com.apify.client to
      tools.jackson.databind;
  opens com.apify.client.actor to
      tools.jackson.databind;
  opens com.apify.client.build to
      tools.jackson.databind;
  opens com.apify.client.run to
      tools.jackson.databind;
  opens com.apify.client.dataset to
      tools.jackson.databind;
  opens com.apify.client.keyvalue to
      tools.jackson.databind;
  opens com.apify.client.requestqueue to
      tools.jackson.databind;
  opens com.apify.client.task to
      tools.jackson.databind;
  opens com.apify.client.schedule to
      tools.jackson.databind;
  opens com.apify.client.webhook to
      tools.jackson.databind;
  opens com.apify.client.user to
      tools.jackson.databind;
  opens com.apify.client.store to
      tools.jackson.databind;
  opens com.apify.client.http to
      tools.jackson.databind;
  opens com.apify.client.internal to
      tools.jackson.databind;
}
