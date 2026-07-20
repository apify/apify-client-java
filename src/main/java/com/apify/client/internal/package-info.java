/**
 * Internal implementation shared across the client's resource packages.
 *
 * <p><b>Not part of the public API.</b> Classes here are {@code public} only because Java has no
 * cross-package "friend" access below {@code public} without the module system (JPMS), which this
 * project does not adopt (it would add module-path/{@code opens} complexity disproportionate to a
 * single-package split). This is the same convention used by e.g. OkHttp's {@code okhttp3.internal}
 * package: the name is the contract. Anything under {@code com.apify.client.internal} may change,
 * move, or be removed in any release without notice, including patch releases. Application code
 * must never import from this package; obtain functionality exclusively through {@link
 * com.apify.client.ApifyClient} and the resource clients it returns.
 */
package com.apify.client.internal;
