package org.operaton.fitpub.security;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.operaton.fitpub.config.TestcontainersConfiguration;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to validate that users' public and private keys match.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Slf4j
public class KeyPairValidationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testAllUsersKeysMatch() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            log.info("Validating key pair for user: {}", user.getUsername());

            try {
                // Parse public key
                PublicKey publicKey = parsePublicKey(user.getPublicKey());

                // Parse private key
                PrivateKey privateKey = parsePrivateKey(user.getPrivateKey());

                // Test if they match by signing and verifying
                String testData = "Test data for " + user.getUsername();
                byte[] signature = signData(testData.getBytes(StandardCharsets.UTF_8), privateKey);
                boolean verified = verifySignature(testData.getBytes(StandardCharsets.UTF_8), signature, publicKey);

                assertTrue(verified, "Public key does NOT match private key for user: " + user.getUsername());
                log.info("✓ Key pair is valid for user: {}", user.getUsername());

            } catch (Exception e) {
                fail("Failed to validate keys for user " + user.getUsername() + ": " + e.getMessage());
            }
        }
    }

    private PublicKey parsePublicKey(String publicKeyPem) throws Exception {
        String publicKeyContent = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    private byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private boolean verifySignature(byte[] data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(signatureBytes);
    }
}
