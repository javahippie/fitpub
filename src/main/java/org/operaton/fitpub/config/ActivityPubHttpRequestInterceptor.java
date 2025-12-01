package org.operaton.fitpub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.security.HttpSignatureValidator;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Intercepts outgoing HTTP requests for ActivityPub federation to add HTTP Signatures.
 * This interceptor is applied AFTER RestTemplate sets all headers (including Host),
 * ensuring the signature matches the actual headers sent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityPubHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final HttpSignatureValidator signatureValidator;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {

        // Check if this request needs HTTP Signature (look for a marker header)
        String privateKey = request.getHeaders().getFirst("X-ActivityPub-PrivateKey");
        String keyId = request.getHeaders().getFirst("X-ActivityPub-KeyId");

        if (privateKey != null && keyId != null) {
            // Remove marker headers (they shouldn't be sent)
            request.getHeaders().remove("X-ActivityPub-PrivateKey");
            request.getHeaders().remove("X-ActivityPub-KeyId");

            try {
                // Now sign the request with the actual headers that will be sent
                String method = request.getMethod().name();
                String uri = request.getURI().toString();
                String bodyString = new String(body, StandardCharsets.UTF_8);

                // Get the actual Host header that RestTemplate set
                String host = request.getHeaders().getFirst("Host");
                if (host == null) {
                    host = request.getURI().getHost();
                }

                // Get the actual Date header that was set
                String date = request.getHeaders().getFirst("Date");

                // Get the actual Digest header that was set
                String digest = request.getHeaders().getFirst("Digest");

                // Build the signing string with the ACTUAL header values
                String signingString = String.format(
                    "(request-target): %s %s%s\nhost: %s\ndate: %s\ndigest: %s",
                    method.toLowerCase(),
                    request.getURI().getPath(),
                    request.getURI().getQuery() != null ? "?" + request.getURI().getQuery() : "",
                    host,
                    date,
                    digest
                );

                // Sign the string
                String signatureBase64 = signatureValidator.sign(signingString, privateKey);

                // Build signature header
                String signatureHeader = String.format(
                    "keyId=\"%s\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date digest\",signature=\"%s\"",
                    keyId, signatureBase64
                );

                // Add signature header
                request.getHeaders().set("Signature", signatureHeader);

                log.debug("Added HTTP Signature to request: {}", request.getURI());

            } catch (Exception e) {
                log.error("Failed to sign request", e);
                throw new IOException("Failed to sign ActivityPub request", e);
            }
        }

        return execution.execute(request, body);
    }
}
