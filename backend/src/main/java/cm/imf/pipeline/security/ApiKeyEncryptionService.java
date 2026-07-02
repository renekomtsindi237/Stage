package cm.imf.pipeline.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement/déchiffrement AES-256-GCM des clés API brutes.
 *
 * Format du texte chiffré stocké : Base64(IV[12 bytes] || ciphertext || GCM-tag[16 bytes])
 */
@Service
public class ApiKeyEncryptionService {

    private static final String  ALGORITHM  = "AES/GCM/NoPadding";
    private static final int     TAG_BITS   = 128;
    private static final int     IV_BYTES   = 12;

    private final SecretKey secretKey;

    public ApiKeyEncryptionService(
            @Value("${app.api.encryption-key}") String rawKey) {
        byte[] keyBytes = rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Tronquer ou padder à 32 bytes pour AES-256
        byte[] key32 = new byte[32];
        System.arraycopy(keyBytes, 0, key32, 0, Math.min(keyBytes.length, 32));
        this.secretKey = new SecretKeySpec(key32, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Préfixer avec l'IV pour déchiffrement
            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de chiffrement AES-GCM", e);
        }
    }

    public String decrypt(String base64Ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Ciphertext);
            byte[] iv         = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de déchiffrement AES-GCM", e);
        }
    }
}
