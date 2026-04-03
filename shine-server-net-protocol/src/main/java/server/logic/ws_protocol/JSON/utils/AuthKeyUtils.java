package server.logic.ws_protocol.JSON.utils;

import server.logic.ws_protocol.Base64Ws;

/**
 * Утилиты для строковых публичных ключей, используемых в auth/session API.
 *
 * Поддерживаемые форматы:
 * - legacy: BASE64(32 bytes)
 * - explicit: ed25519/BASE64(32 bytes)
 */
public final class AuthKeyUtils {

    private AuthKeyUtils() {}

    public static String normalize(String key, String fieldName) {
        if (key == null) throw new IllegalArgumentException(fieldName + " is null");
        String trimmed = key.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(fieldName + " is empty");
        return trimmed;
    }

    public static byte[] parseEd25519PublicKey(String key, String fieldName) {
        String normalized = normalize(key, fieldName);

        int slash = normalized.indexOf('/');
        if (slash < 0) {
            return Base64Ws.decodeLen(normalized, 32, fieldName);
        }

        String algorithm = normalized.substring(0, slash).trim();
        String encodedKey = normalized.substring(slash + 1).trim();

        if (algorithm.isEmpty() || encodedKey.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " has bad algorithm/key format");
        }

        if (!"ed25519".equalsIgnoreCase(algorithm)) {
            throw new UnsupportedOperationException(fieldName + " algorithm is not supported: " + algorithm);
        }

        return Base64Ws.decodeLen(encodedKey, 32, fieldName);
    }
}
