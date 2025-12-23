package blockchain_new;

import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public final class BchCryptoVerifier_new {

    private static final byte[] DOMAIN = "SHiNE".getBytes(StandardCharsets.US_ASCII);

    private BchCryptoVerifier_new() {}

    /**
     * preimage =
     *   "SHiNE" +
     *   [1] loginLen + loginBytes +
     *   prevGlobalHash32 +
     *   prevLineHash32 +
     *   rawBytes
     */
    public static byte[] buildPreimage(String userLogin,
                                       byte[] prevGlobalHash32,
                                       byte[] prevLineHash32,
                                       byte[] rawBytes) {

        Objects.requireNonNull(userLogin, "userLogin == null");
        Objects.requireNonNull(prevGlobalHash32, "prevGlobalHash32 == null");
        Objects.requireNonNull(prevLineHash32, "prevLineHash32 == null");
        Objects.requireNonNull(rawBytes, "rawBytes == null");

        if (prevGlobalHash32.length != 32 || prevLineHash32.length != 32)
            throw new IllegalArgumentException("hash len != 32");

        byte[] loginBytes = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginBytes.length > 255)
            throw new IllegalArgumentException("login >255 bytes");

        ByteBuffer bb = ByteBuffer.allocate(
                DOMAIN.length +
                        1 + loginBytes.length +
                        32 + 32 +
                        rawBytes.length
        ).order(ByteOrder.BIG_ENDIAN);

        bb.put(DOMAIN);
        bb.put((byte) loginBytes.length);
        bb.put(loginBytes);
        bb.put(prevGlobalHash32);
        bb.put(prevLineHash32);
        bb.put(rawBytes);

        return bb.array();
    }

    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return d.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Проверка подписи Ed25519:
     * verify(hash32, signature64, publicKey32)
     */
    public static boolean verifySignature(byte[] hash32,
                                          byte[] signature64,
                                          byte[] publicKey32) {
        Objects.requireNonNull(hash32, "hash32 == null");
        Objects.requireNonNull(signature64, "signature64 == null");
        Objects.requireNonNull(publicKey32, "publicKey32 == null");

        if (hash32.length != 32) throw new IllegalArgumentException("hash32 != 32");
        if (signature64.length != 64) throw new IllegalArgumentException("signature64 != 64");
        if (publicKey32.length != 32) throw new IllegalArgumentException("publicKey32 != 32");

        // ⚠️ Подстрой под твой Ed25519Util:
        // Идея ровно такая: verify(messageHash, signature, publicKey)
        return Ed25519Util.verify(hash32, signature64, publicKey32);
    }
}