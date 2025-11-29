package org.operaton.fitpub.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates HTTP Signatures for ActivityPub federation.
 * Implements the HTTP Signatures specification used by ActivityPub.
 *
 * Reference: https://datatracker.ietf.org/doc/html/draft-cavage-http-signatures
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpSignatureValidator {

    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
        "keyId=\"([^\"]+)\",algorithm=\"([^\"]+)\",headers=\"([^\"]+)\",signature=\"([^\"]+)\""
    );

    /**
     * Validates an HTTP signature.
     *
     * @param signatureHeader the Signature header value
     * @param headers the HTTP headers map
     * @param publicKeyPem the actor's public key in PEM format
     * @return true if signature is valid
     */
    public boolean validate(String signatureHeader, Map<String, String> headers, String publicKeyPem) {
        try {
            // Parse signature header
            Matcher matcher = SIGNATURE_PATTERN.matcher(signatureHeader);
            if (!matcher.find()) {
                log.warn("Invalid signature header format");
                return false;
            }

            String keyId = matcher.group(1);
            String algorithm = matcher.group(2);
            String headersString = matcher.group(3);
            String signatureBase64 = matcher.group(4);

            // Build signing string from specified headers
            String signingString = buildSigningString(headersString, headers);

            // Decode signature
            byte[] signature = Base64.getDecoder().decode(signatureBase64);

            // Parse public key
            PublicKey publicKey = parsePublicKey(publicKeyPem);

            // Verify signature
            return verifySignature(signingString, signature, publicKey, algorithm);

        } catch (Exception e) {
            log.error("Error validating HTTP signature", e);
            return false;
        }
    }

    /**
     * Builds the signing string from the specified headers.
     *
     * @param headersString space-separated list of header names
     * @param headers the actual header values
     * @return the signing string
     */
    private String buildSigningString(String headersString, Map<String, String> headers) {
        String[] headerNames = headersString.split(" ");
        StringBuilder signingString = new StringBuilder();

        for (int i = 0; i < headerNames.length; i++) {
            String headerName = headerNames[i].toLowerCase();
            String headerValue = headers.get(headerName);

            if (headerValue == null) {
                log.warn("Header {} specified in signature but not found in request", headerName);
                headerValue = "";
            }

            // Special handling for (request-target) pseudo-header
            if (headerName.equals("(request-target)")) {
                signingString.append(headerName).append(": ").append(headerValue);
            } else {
                signingString.append(headerName).append(": ").append(headerValue);
            }

            if (i < headerNames.length - 1) {
                signingString.append("\n");
            }
        }

        return signingString.toString();
    }

    /**
     * Parses a PEM-encoded public key.
     *
     * @param publicKeyPem the public key in PEM format
     * @return the PublicKey object
     */
    private PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        // Remove PEM headers and whitespace
        String publicKeyContent = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        // Decode Base64
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);

        // Create PublicKey
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    /**
     * Verifies the signature using the public key.
     *
     * @param signingString the string that was signed
     * @param signature the signature bytes
     * @param publicKey the public key
     * @param algorithm the signature algorithm
     * @return true if signature is valid
     */
    private boolean verifySignature(String signingString, byte[] signature, PublicKey publicKey, String algorithm)
        throws Exception {

        // Map ActivityPub algorithm names to Java algorithm names
        String javaAlgorithm = mapAlgorithm(algorithm);

        Signature sig = Signature.getInstance(javaAlgorithm);
        sig.initVerify(publicKey);
        sig.update(signingString.getBytes(StandardCharsets.UTF_8));

        return sig.verify(signature);
    }

    /**
     * Maps ActivityPub algorithm names to Java Signature algorithm names.
     *
     * @param activityPubAlgorithm the ActivityPub algorithm name
     * @return the Java algorithm name
     */
    private String mapAlgorithm(String activityPubAlgorithm) {
        switch (activityPubAlgorithm.toLowerCase()) {
            case "rsa-sha256":
                return "SHA256withRSA";
            case "rsa-sha512":
                return "SHA512withRSA";
            default:
                log.warn("Unknown signature algorithm: {}, defaulting to SHA256withRSA", activityPubAlgorithm);
                return "SHA256withRSA";
        }
    }

    /**
     * Signs a message using a private key.
     * Used for outbound federation requests.
     *
     * @param signingString the string to sign
     * @param privateKeyPem the private key in PEM format
     * @return Base64-encoded signature
     */
    public String sign(String signingString, String privateKeyPem) throws Exception {
        // Parse private key
        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        java.security.PrivateKey privateKey = keyFactory.generatePrivate(spec);

        // Sign
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingString.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();

        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Container for HTTP signature headers.
     */
    public static class SignatureHeaders {
        public final String host;
        public final String date;
        public final String digest;
        public final String signature;

        public SignatureHeaders(String host, String date, String digest, String signature) {
            this.host = host;
            this.date = date;
            this.digest = digest;
            this.signature = signature;
        }
    }

    /**
     * Signs an outbound HTTP request for ActivityPub federation.
     *
     * @param method the HTTP method (e.g., "POST")
     * @param targetUrl the target URL
     * @param body the request body
     * @param privateKeyPem the sender's private key
     * @param keyId the public key ID
     * @return SignatureHeaders containing all headers needed for the signed request
     */
    public SignatureHeaders signRequest(String method, String targetUrl, String body, String privateKeyPem, String keyId) {
        try {
            java.net.URI uri = new java.net.URI(targetUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }

            // Build request-target
            String requestTarget = method.toLowerCase() + " " + path;

            // Calculate digest
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            String digestValue = "SHA-256=" + Base64.getEncoder().encodeToString(hash);

            // Get current date in RFC 1123 format
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
            String date = now.format(formatter);

            // Build signing string
            String signingString = String.format(
                "(request-target): %s\nhost: %s\ndate: %s\ndigest: %s",
                requestTarget, host, date, digestValue
            );

            // Sign
            String signatureBase64 = sign(signingString, privateKeyPem);

            // Build signature header
            String signatureHeader = String.format(
                "keyId=\"%s\",algorithm=\"rsa-sha256\",headers=\"(request-target) host date digest\",signature=\"%s\"",
                keyId, signatureBase64
            );

            return new SignatureHeaders(host, date, digestValue, signatureHeader);

        } catch (Exception e) {
            log.error("Failed to sign request", e);
            throw new RuntimeException("Failed to sign request", e);
        }
    }
}
