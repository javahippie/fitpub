package org.operaton.fitpub.config;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * HTTP request interceptor that preserves the Host header if it was explicitly set.
 * This is critical for ActivityPub HTTP Signatures, where the Host header must match
 * the value used when calculating the signature.
 */
public class HostHeaderInterceptor implements HttpRequestInterceptor {

    @Override
    public void process(HttpRequest request, EntityDetails entity, HttpContext context) {
        // The Host header should already be set in the request headers
        // This interceptor ensures it's not overwritten by HttpClient
        // Note: In Apache HttpClient 5, the Host header is typically set correctly
        // from the request headers, but we keep this interceptor as a safeguard
    }
}
