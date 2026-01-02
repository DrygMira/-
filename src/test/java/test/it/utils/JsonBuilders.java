package test.it.utils;

import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class JsonBuilders {
    private JsonBuilders(){}

    // =========================
    // AddUser USER1 (как было)
    // =========================
    public static String addUser(String requestId) {
        return addUserAny(
                requestId,
                TestConfig.LOGIN(),
                TestConfig.BCH_NAME(),
                TestConfig.LOGIN_PUBKEY_B64(),
                TestConfig.DEVICE_PUBKEY_B64()
        );
    }

    // =========================
    // AddUser USER2 (новое)
    // =========================
    public static String addUser2(String requestId) {
        return addUserAny(
                requestId,
                TestConfig.LOGIN2(),
                TestConfig.BCH_NAME2(),
                TestConfig.LOGIN2_PUBKEY_B64(),
                TestConfig.DEVICE2_PUBKEY_B64()
        );
    }

    private static String addUserAny(String requestId,
                                     String login,
                                     String blockchainName,
                                     String loginKeyB64,
                                     String deviceKeyB64) {
        return """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "blockchainName": "%s",
                    "loginKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                requestId,
                login,
                blockchainName,
                loginKeyB64,
                deviceKeyB64,
                TestConfig.TEST_BCH_LIMIT
        );
    }

    public static String authChallenge(String requestId) {
        return """
                {
                  "op": "AuthChallenge",
                  "requestId": "%s",
                  "payload": { "login": "%s" }
                }
                """.formatted(requestId, TestConfig.LOGIN());
    }

    public static String createAuthSession(String requestId, String authNonce, String storagePwd) {
        long timeMs = System.currentTimeMillis();
        String sigB64 = signAuthorificated(authNonce, timeMs, TestConfig.DEVICE_PRIV_KEY());

        return """
                {
                  "op": "CreateAuthSession",
                  "requestId": "%s",
                  "payload": {
                    "storagePwd": "%s",
                    "timeMs": %d,
                    "signatureB64": "%s",
                    "clientInfo": "%s"
                  }
                }
                """.formatted(requestId, storagePwd, timeMs, sigB64, TestConfig.TEST_CLIENT_INFO);
    }

    public static String listSessions(String requestId, long timeMs, String signatureB64) {
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "ListSessions",
              "requestId": "%s",
              "payload": {
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(requestId, timeMs, signatureB64);
    }

    public static String refreshSession(String requestId, String sessionId, String sessionPwd) {
        return """
            {
              "op": "RefreshSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "sessionPwd": "%s",
                "clientInfo": "%s"
              }
            }
            """.formatted(requestId, sessionId, sessionPwd, TestConfig.TEST_CLIENT_INFO);
    }

    public static String closeActiveSession(String requestId, String sessionId, long timeMs, String signatureB64) {
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "CloseActiveSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "timeMs": %d,
                "signatureB64": "%s"
              }
            }
            """.formatted(requestId, sessionId, timeMs, signatureB64);
    }

    /**
     * Подпись для режима AUTH_IN_PROGRESS:
     * preimage = "AUTHORIFICATED:" + timeMs + authNonce
     * подписываем devicePrivKey.
     */
    public static String signAuthorificated(String authNonce, long timeMs, byte[] devicePrivKey) {
        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
        byte[] sig = Ed25519Util.sign(preimage, devicePrivKey);
        return Base64.getEncoder().encodeToString(sig);
    }

    // старый метод оставим для совместимости
    public static String signAuthorificated(String authNonce, long timeMs) {
        return signAuthorificated(authNonce, timeMs, TestConfig.DEVICE_PRIV_KEY());
    }
}