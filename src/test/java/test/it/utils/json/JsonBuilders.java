package test.it.utils.json;

import test.it.utils.TestIds;
import test.it.utils.TestConfig;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Builder'ы JSON запросов. Внутри автоматически генерим requestId. */
public final class JsonBuilders {
    private JsonBuilders() {}

    // ---------------- AddUser ----------------

    public static String addUser(String login) {
        String requestId = TestIds.next("adduser");
        String blockchainName = TestConfig.getBlockchainName(login);

        String solanaKeyB64 = TestConfig.solanaPublicKeyB64(login);
        String blockchainKeyB64 = TestConfig.blockchainPublicKeyB64(login);
        String deviceKeyB64 = TestConfig.devicePublicKeyB64(login);

        return """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "blockchainName": "%s",
                    "solanaKey": "%s",
                    "blockchainKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                requestId,
                login,
                blockchainName,
                solanaKeyB64,
                blockchainKeyB64,
                deviceKeyB64,
                TestConfig.TEST_BCH_LIMIT
        );
    }

    // ---------------- GetUser ----------------

    public static String getUser(String login) {
        String requestId = TestIds.next("getuser");
        return """
                {
                  "op": "GetUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s"
                  }
                }
                """.formatted(requestId, login);
    }

    // ---------------- SearchUsers ----------------

    public static String searchUsers(String prefix) {
        String requestId = TestIds.next("searchusers");
        return """
                {
                  "op": "SearchUsers",
                  "requestId": "%s",
                  "payload": {
                    "prefix": "%s"
                  }
                }
                """.formatted(requestId, prefix);
    }

    // ---------------- GetFriendsLists ----------------

    public static String getFriendsLists(String login) {
        String requestId = TestIds.next("friends");
        return """
            {
              "op": "GetFriendsLists",
              "requestId": "%s",
              "payload": {
                "login": "%s"
              }
            }
            """.formatted(requestId, login);
    }

    // ---------------- Ping ----------------

    public static String ping(long ts) {
        String requestId = TestIds.next("ping");
        return """
                {
                  "op": "Ping",
                  "requestId": "%s",
                  "payload": {
                    "ts": %d
                  }
                }
                """.formatted(requestId, ts);
    }

    // ---------------- GetServerInfo ----------------

    public static String getServerInfo() {
        String requestId = TestIds.next("serverinfo");
        return """
                {
                  "op": "GetServerInfo",
                  "requestId": "%s",
                  "payload": {
                  }
                }
                """.formatted(requestId);
    }

    // ---------------- AuthChallenge ----------------

    public static String authChallenge(String login) {
        String requestId = TestIds.next("auth");
        return """
                {
                  "op": "AuthChallenge",
                  "requestId": "%s",
                  "payload": { "login": "%s" }
                }
                """.formatted(requestId, login);
    }

    // ---------------- CreateAuthSession (v2) ----------------
    // Подпись CreateAuthSession делается deviceKey над строкой:
    //   preimage = "AUTH_CREATE_SESSION:" + login + ":" + sessionKey + ":" + storagePwd + ":" + timeMs + ":" + authNonce

    public static String createAuthSessionV2(String login, String authNonce, String storagePwd, String sessionKey) {
        long timeMs = System.currentTimeMillis();

        byte[] devicePriv = TestConfig.getDevicePrivatKey(login);
        String deviceKey = TestConfig.devicePublicKeyB64(login);
        String sigB64 = signAuthCreateSession(login, sessionKey, storagePwd, timeMs, authNonce, devicePriv);

        String requestId = TestIds.next("create");
        return """
                {
                  "op": "CreateAuthSession",
                  "requestId": "%s",
                  "payload": {
                    "login": "%s",
                    "storagePwd": "%s",
                    "sessionKey": "%s",
                    "timeMs": %d,
                    "authNonce": "%s",
                    "deviceKey": "%s",
                    "signatureB64": "%s",
                    "clientInfo": "%s"
                  }
                }
                """.formatted(
                requestId,
                login,
                storagePwd,
                sessionKey,
                timeMs,
                authNonce,
                deviceKey,
                sigB64,
                TestConfig.TEST_CLIENT_INFO
        );
    }

    // ---------------- SessionChallenge (v2) ----------------

    public static String sessionChallenge(String sessionId) {
        String requestId = TestIds.next("sch");
        return """
            {
              "op": "SessionChallenge",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s"
              }
            }
            """.formatted(requestId, sessionId);
    }

    // ---------------- SessionLogin (v2) ----------------
    // Подпись SessionLogin по-прежнему делается sessionPrivKey:
    // preimage = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce

    public static String sessionLogin(String sessionId, String sessionKey, String nonce, byte[] sessionPrivKey) {
        long timeMs = System.currentTimeMillis();
        String sigB64 = signSessionLogin(sessionId, timeMs, nonce, sessionPrivKey);

        String requestId = TestIds.next("slogin");
        return """
            {
              "op": "SessionLogin",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s",
                "sessionKey": "%s",
                "timeMs": %d,
                "signatureB64": "%s",
                "clientInfo": "%s"
              }
            }
            """.formatted(requestId, sessionId, sessionKey, timeMs, sigB64, TestConfig.TEST_CLIENT_INFO);
    }

    // ---------------- ListSessions ----------------

    public static String listSessions(long timeMs, String signatureB64) {
        String requestId = TestIds.next("list");
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "ListSessions",
              "requestId": "%s",
              "payload": {
              }
            }
            """.formatted(requestId, timeMs, signatureB64);
    }

    // ---------------- CloseActiveSession ----------------

    public static String closeActiveSession(String sessionId, long timeMs, String signatureB64) {
        String requestId = TestIds.next("close");
        if (signatureB64 == null) signatureB64 = "";
        return """
            {
              "op": "CloseActiveSession",
              "requestId": "%s",
              "payload": {
                "sessionId": "%s"
              }
            }
            """.formatted(requestId, sessionId, timeMs, signatureB64);
    }

    // ---------------- ListSubscribedChannels ----------------

    public static String listSubscribedChannels(String login) {
        String requestId = TestIds.next("subs");
        return """
        {
          "op": "ListSubscribedChannels",
          "requestId": "%s",
          "payload": { "login": "%s" }
        }
        """.formatted(requestId, login);
    }

    public static String listSubscriptionsFeed(String login, int limit) {
        String requestId = TestIds.next("subsfeed");
        return """
        {
          "op": "ListSubscriptionsFeed",
          "requestId": "%s",
          "payload": {
            "login": "%s",
            "limit": %d
          }
        }
        """.formatted(requestId, login, limit);
    }

    public static String getChannelMessages(String ownerBlockchainName, int channelRootBlockNumber, String channelRootBlockHash, int limit, String sort) {
        String requestId = TestIds.next("chmsg");
        String hash = channelRootBlockHash == null ? "" : channelRootBlockHash;
        return """
        {
          "op": "GetChannelMessages",
          "requestId": "%s",
          "payload": {
            "channel": {
              "ownerBlockchainName": "%s",
              "channelRootBlockNumber": %d,
              "channelRootBlockHash": "%s"
            },
            "limit": %d,
            "sort": "%s"
          }
        }
        """.formatted(requestId, ownerBlockchainName, channelRootBlockNumber, hash, limit, sort == null ? "asc" : sort);
    }

    public static String getMessageThread(String blockchainName, int blockNumber, String blockHash, int depthUp, int depthDown, int limitChildrenPerNode) {
        String requestId = TestIds.next("thread");
        String hash = blockHash == null ? "" : blockHash;
        return """
        {
          "op": "GetMessageThread",
          "requestId": "%s",
          "payload": {
            "message": {
              "blockchainName": "%s",
              "blockNumber": %d,
              "blockHash": "%s"
            },
            "depthUp": %d,
            "depthDown": %d,
            "limitChildrenPerNode": %d
          }
        }
        """.formatted(requestId, blockchainName, blockNumber, hash, depthUp, depthDown, limitChildrenPerNode);
    }

    /**
     * Подпись CreateAuthSession(v2):
     * preimage = "AUTH_CREATE_SESSION:" + login + ":" + sessionKey + ":" + storagePwd + ":" + timeMs + ":" + authNonce
     * подписываем devicePrivKey.
     */
    public static String signAuthCreateSession(String login, String sessionKey, String storagePwd, long timeMs, String authNonce, byte[] devicePrivKey) {
        String preimageStr = "AUTH_CREATE_SESSION:" + login + ":" + sessionKey + ":" + storagePwd + ":" + timeMs + ":" + authNonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
        byte[] sig = Ed25519Util.sign(preimage, devicePrivKey);
        return Base64.getEncoder().encodeToString(sig);
    }

    /**
     * Подпись для SessionLogin(v2):
     * preimage = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce
     * подписываем sessionPrivKey.
     */
    public static String signSessionLogin(String sessionId, long timeMs, String nonce, byte[] sessionPrivKey) {
        String preimageStr = "SESSION_LOGIN:" + sessionId + ":" + timeMs + ":" + nonce;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
        byte[] sig = Ed25519Util.sign(preimage, sessionPrivKey);
        return Base64.getEncoder().encodeToString(sig);
    }
}
