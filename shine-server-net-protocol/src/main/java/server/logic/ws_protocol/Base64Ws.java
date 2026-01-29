package server.logic.ws_protocol;

import java.util.Base64;

/**
 * Единая утилита Base64 для всего WS-протокола.
 *
 * ВАЖНО:
 * - Используем ТОЛЬКО стандартный Base64 (RFC 4648) алфавит: '+' и '/'.
 * - Без padding '=' (чтобы строки были короче и стабильнее для JSON).
 * - Декодер при этом спокойно принимает и с '=' и без '='.
 */
public final class Base64Ws {

    private static final Base64.Encoder ENC = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getDecoder();

    private Base64Ws() {}

    public static String encode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes == null");
        return ENC.encodeToString(bytes);
    }

    public static byte[] decode(String b64) throws IllegalArgumentException {
        if (b64 == null) throw new IllegalArgumentException("base64 is null");
        String s = b64.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("base64 is empty");
        return DEC.decode(s);
    }

    public static byte[] decodeLen(String b64, int expectedLen, String fieldName) throws IllegalArgumentException {
        byte[] v = decode(b64);
        if (v.length != expectedLen) {
            String f = (fieldName == null || fieldName.isBlank()) ? "value" : fieldName;
            throw new IllegalArgumentException(f + " must be " + expectedLen + " bytes, got " + v.length);
        }
        return v;
    }
}