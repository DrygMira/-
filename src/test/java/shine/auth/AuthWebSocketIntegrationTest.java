//package shine.auth;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//import utils.crypto.Ed25519Util;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.WebSocket;
//import java.nio.charset.StandardCharsets;
//import java.time.Duration;
//import java.util.Base64;
//import java.util.UUID;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.CompletionStage;
//import java.util.concurrent.TimeUnit;
//
///**
// * Интеграционные тесты авторификации по JSON-протоколу через WebSocket.
// *
// * Требуется запущенный сервер на:
// *   ws://localhost:7070/ws
// *
// * Операции:
// *  - AddUser
// *  - AuthChallenge
// *  - CreateAuthSession
// *  - RefreshSession
// *  - CloseActiveSession
// *  - (позже) ListSessions
// */
//public class AuthWebSocketIntegrationTest {
//
//    private static final String WS_URI = "ws://localhost:7070/ws";
//    private static final ObjectMapper JSON = new ObjectMapper();
//    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
//
//    /** Таймаут ожидания ответа от сервера в каждом helper-е (секунд). */
//    private static final long WS_TIMEOUT_SEC = 15;
//
//    // ========================================================================
//    //                                DTO
//    // ========================================================================
//
//    /** Тестовый пользователь. */
//    private static class TestUser {
//        String login;
//        long loginId;
//        long bchId;
//
//        byte[] loginPriv;
//        byte[] devicePriv;
//
//        String loginPubB64;
//        String devicePubB64;
//    }
//
//    /** Токены созданной сессии. */
//    private static class SessionTokens {
//        String sessionId;
//        String sessionPwd;
//        String storagePwd;
//    }
//
//    // ========================================================================
//    //                            ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
//    // ========================================================================
//
//    /**
//     * Создать тестового пользователя с уникальным логином и ключами Ed25519.
//     */
//    private TestUser createRandomUser() {
//        TestUser u = new TestUser();
//
//        long ts = System.currentTimeMillis();
//        u.login = "anya_test_auth_scenario_" + ts;
//        u.loginId = ts;          // просто уникальный long
//        u.bchId = ts % 1_000_000; // что-нибудь псевдоуникальное
//
//        // Генерируем ключи детерминированно от логина — чтобы AddUser и Auth совпадали
//        u.loginPriv = Ed25519Util.generatePrivateKeyFromString("login-key-" + u.login);
//        u.devicePriv = Ed25519Util.generatePrivateKeyFromString("device-key-" + u.login);
//
//        byte[] loginPub = Ed25519Util.derivePublicKey(u.loginPriv);
//        byte[] devicePub = Ed25519Util.derivePublicKey(u.devicePriv);
//
//        u.loginPubB64 = Ed25519Util.keyToBase64(loginPub);
//        u.devicePubB64 = Ed25519Util.keyToBase64(devicePub);
//
//        return u;
//    }
//
//    /**
//     * Универсальный helper для одношаговой операции (AddUser, RefreshSession и т.п.).
//     * Открывает WebSocket, отправляет JSON, ждёт один ответ.
//     *
//     * @param requestJson JSON-запрос
//     * @param label       ярлык для логов
//     * @return JsonNode root ответа
//     */
//    private JsonNode callSingleJsonOp(String requestJson, String label) throws Exception {
//        System.out.println();
//        System.out.println("===== " + label + " =====");
//        System.out.println("📤 Request:");
//        System.out.println(requestJson);
//
//        CompletableFuture<JsonNode> future = new CompletableFuture<>();
//
//        HTTP_CLIENT.newWebSocketBuilder()
//                .connectTimeout(Duration.ofSeconds(WS_TIMEOUT_SEC))
//                .buildAsync(URI.create(WS_URI), new WebSocket.Listener() {
//
//                    @Override
//                    public void onOpen(WebSocket webSocket) {
//                        webSocket.request(1);
//                        webSocket.sendText(requestJson, true);
//                    }
//
//                    @Override
//                    public CompletionStage<?> onText(WebSocket webSocket,
//                                                     CharSequence data,
//                                                     boolean last) {
//                        String msg = data.toString();
//                        System.out.println("📥 Response:");
//                        System.out.println(msg);
//                        System.out.println("----------------------------------------");
//                        try {
//                            JsonNode root = JSON.readTree(msg);
//                            future.complete(root);
//                        } catch (Exception e) {
//                            future.completeExceptionally(e);
//                        } finally {
//                            try {
//                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test done");
//                            } catch (Exception ignored) {
//                            }
//                        }
//                        webSocket.request(1);
//                        return CompletableFuture.completedFuture(null);
//                    }
//
//                    @Override
//                    public void onError(WebSocket webSocket, Throwable error) {
//                        if (!future.isDone()) {
//                            future.completeExceptionally(error);
//                        }
//                    }
//
//                    @Override
//                    public CompletionStage<?> onClose(WebSocket webSocket,
//                                                      int statusCode,
//                                                      String reason) {
//                        if (!future.isDone()) {
//                            future.completeExceptionally(new IllegalStateException(
//                                    "WebSocket closed before response. code=" + statusCode + ", reason=" + reason));
//                        }
//                        return CompletableFuture.completedFuture(null);
//                    }
//                });
//
//        return future.get(WS_TIMEOUT_SEC, TimeUnit.SECONDS);
//    }
//
//    /**
//     * Helper для двухшагового сценария:
//     *   1) AuthChallenge
//     *   2) CreateAuthSession
//     */
//    private SessionTokens createSessionForUser(TestUser user, String logPrefix) throws Exception {
//        System.out.println();
//        System.out.println("===== " + logPrefix + " createSessionForUser: " + user.login + " =====");
//
//        CompletableFuture<SessionTokens> resultFuture = new CompletableFuture<>();
//
//        HTTP_CLIENT.newWebSocketBuilder()
//                .connectTimeout(Duration.ofSeconds(WS_TIMEOUT_SEC))
//                .buildAsync(URI.create(WS_URI), new WebSocket.Listener() {
//
//                    int step = 0;
//                    WebSocket ws;
//                    String currentAuthNonce;
//
//                    @Override
//                    public void onOpen(WebSocket webSocket) {
//                        this.ws = webSocket;
//                        webSocket.request(1);
//
//                        // Шаг 1: AuthChallenge
//                        String reqId = "auth-challenge-" + UUID.randomUUID();
//                        String json = """
//                                {
//                                  "op": "AuthChallenge",
//                                  "requestId": "%s",
//                                  "payload": {
//                                    "login": "%s"
//                                  }
//                                }
//                                """.formatted(reqId, user.login);
//
//                        System.out.println();
//                        System.out.println(logPrefix + " 📤 [STEP1 AuthChallenge] Request:");
//                        System.out.println(json);
//                        webSocket.sendText(json, true);
//                    }
//
//                    @Override
//                    public CompletionStage<?> onText(WebSocket webSocket,
//                                                     CharSequence data,
//                                                     boolean last) {
//                        String msg = data.toString();
//                        System.out.println();
//                        System.out.println(logPrefix + " 📥 Incoming message (step " + step + "):");
//                        System.out.println(msg);
//                        System.out.println("--------------------------------------------------");
//
//                        try {
//                            if (step == 0) {
//                                // Ответ на AuthChallenge
//                                JsonNode root = JSON.readTree(msg);
//                                int status = root.path("status").asInt();
//                                if (status != 200) {
//                                    String code = root.path("payload").path("code").asText();
//                                    String message = root.path("payload").path("message").asText();
//                                    throw new IllegalStateException(
//                                            "AuthChallenge failed: status=" + status +
//                                                    ", code=" + code +
//                                                    ", message=" + message);
//                                }
//
//                                currentAuthNonce = root.path("payload").path("authNonce").asText(null);
//                                if (currentAuthNonce == null || currentAuthNonce.isBlank()) {
//                                    throw new IllegalStateException("AuthChallenge: empty authNonce in response");
//                                }
//                                System.out.println(logPrefix + " 🔑 authNonce = " + currentAuthNonce);
//
//                                // Шаг 2: CreateAuthSession
//                                long timeMs = System.currentTimeMillis();
//                                String signatureB64 = buildAuthorificatedSignature(
//                                        user.devicePriv,
//                                        currentAuthNonce,
//                                        timeMs
//                                );
//                                String storagePwd = generateFakeStoragePwd();
//
//                                String reqId2 = "create-session-" + UUID.randomUUID();
//                                String json2 = """
//                                        {
//                                          "op": "CreateAuthSession",
//                                          "requestId": "%s",
//                                          "payload": {
//                                            "storagePwd": "%s",
//                                            "timeMs": %d,
//                                            "signatureB64": "%s",
//                                            "clientInfo": "AuthTestClient/1.0"
//                                          }
//                                        }
//                                        """.formatted(
//                                        reqId2,
//                                        storagePwd,
//                                        timeMs,
//                                        signatureB64
//                                );
//
//                                System.out.println();
//                                System.out.println(logPrefix + " 📤 [STEP2 CreateAuthSession] Request:");
//                                System.out.println(json2);
//
//                                step = 1;
//                                webSocket.sendText(json2, true);
//                            } else if (step == 1) {
//                                // Ответ на CreateAuthSession
//                                JsonNode root = JSON.readTree(msg);
//                                int status = root.path("status").asInt();
//                                if (status != 200) {
//                                    String code = root.path("payload").path("code").asText();
//                                    String message = root.path("payload").path("message").asText();
//                                    throw new IllegalStateException(
//                                            "CreateAuthSession failed: status=" + status +
//                                                    ", code=" + code +
//                                                    ", message=" + message);
//                                }
//
//                                String sessionId = root.path("payload").path("sessionId").asText(null);
//                                String sessionPwd = root.path("payload").path("sessionPwd").asText(null);
//                                if (sessionId == null || sessionPwd == null) {
//                                    throw new IllegalStateException("CreateAuthSession: sessionId or sessionPwd is null");
//                                }
//
//                                SessionTokens tokens = new SessionTokens();
//                                tokens.sessionId = sessionId;
//                                tokens.sessionPwd = sessionPwd;
//                                tokens.storagePwd = null; // мы знаем, какой отправляли, при желании можно сохранить
//
//                                System.out.println(logPrefix + " 🆔 sessionId = " + sessionId);
//                                System.out.println(logPrefix + " 🔐 sessionPwd = " + sessionPwd);
//
//                                resultFuture.complete(tokens);
//
//                                try {
//                                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "session created");
//                                } catch (Exception ignored) {
//                                }
//                            } else {
//                                // Лишние сообщения — считаем ошибкой
//                                throw new IllegalStateException("Unexpected extra message on step=" + step);
//                            }
//                        } catch (Exception ex) {
//                            if (!resultFuture.isDone()) {
//                                resultFuture.completeExceptionally(ex);
//                            }
//                            try {
//                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "error in test");
//                            } catch (Exception ignored) {
//                            }
//                        } finally {
//                            webSocket.request(1);
//                        }
//
//                        return CompletableFuture.completedFuture(null);
//                    }
//
//                    @Override
//                    public void onError(WebSocket webSocket, Throwable error) {
//                        System.out.println(logPrefix + " ❌ WebSocket error: " + error.getMessage());
//                        if (!resultFuture.isDone()) {
//                            resultFuture.completeExceptionally(error);
//                        }
//                    }
//
//                    @Override
//                    public CompletionStage<?> onClose(WebSocket webSocket,
//                                                      int statusCode,
//                                                      String reason) {
//                        System.out.println(logPrefix + " 🔚 WebSocket closed. code=" + statusCode + ", reason=" + reason);
//                        if (!resultFuture.isDone()) {
//                            resultFuture.completeExceptionally(
//                                    new IllegalStateException("Closed before session tokens were received"));
//                        }
//                        return CompletableFuture.completedFuture(null);
//                    }
//                });
//
//        // ждём результат или ошибку
//        return resultFuture.get(WS_TIMEOUT_SEC, TimeUnit.SECONDS);
//    }
//
//    /**
//     * Собрать подпись над строкой "AUTHORIFICATED:" + timeMs + authNonce
//     * приватным ключом устройства.
//     */
//    private String buildAuthorificatedSignature(byte[] devicePrivKey,
//                                                String authNonce,
//                                                long timeMs) {
//        String preimageStr = "AUTHORIFICATED:" + timeMs + authNonce;
//        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);
//        byte[] sig = Ed25519Util.sign(preimage, devicePrivKey);
//        return Base64.getEncoder().encodeToString(sig);
//    }
//
//    /** Просто base64 от 32 байт 1..32 — для storagePwd. */
//    private String generateFakeStoragePwd() {
//        byte[] data = new byte[32];
//        for (int i = 0; i < data.length; i++) {
//            data[i] = (byte) (i + 1);
//        }
//        return Base64.getEncoder().encodeToString(data);
//    }
//
//    // ========================================================================
//    //                                ТЕСТЫ
//    // ========================================================================
//
//    /**
//     * 1) Регистрируем пользователя через AddUser
//     */
//    @Test
//    void addUser_shouldSucceed() throws Exception {
//        TestUser user = createRandomUser();
//
//        String reqId = "add-" + UUID.randomUUID();
//        String json = """
//                {
//                  "op": "AddUser",
//                  "requestId": "%s",
//                  "payload": {
//                    "login": "%s",
//                    "loginId": %d,
//                    "bchId": %d,
//                    "loginKey": "%s",
//                    "deviceKey": "%s",
//                    "bchLimit": 1000000
//                  }
//                }
//                """.formatted(
//                reqId,
//                user.login,
//                user.loginId,
//                user.bchId,
//                user.loginPubB64,
//                user.devicePubB64
//        );
//
//        JsonNode resp = callSingleJsonOp(json, "TEST addUser_shouldSucceed");
//        int status = resp.path("status").asInt();
//        String code = resp.path("payload").path("code").asText(null);
//
//        Assertions.assertEquals(
//                200,
//                status,
//                "Ожидался status=200 при AddUser, но сервер вернул: status=" + status + ", code=" + code
//        );
//
//        System.out.println("✅ [TEST] AddUser прошёл успешно для login=" + user.login);
//    }
//
//    /**
//     * 2) Создать пользователя и сразу сделать CreateAuthSession (AuthChallenge + CreateAuthSession).
//     */
//    @Test
//    void createSession_flow_shouldReturnSessionIdAndPwd() throws Exception {
//        TestUser user = createRandomUser();
//
//        // Сначала регистрируем пользователя
//        {
//            String reqId = "add-" + UUID.randomUUID();
//            String json = """
//                    {
//                      "op": "AddUser",
//                      "requestId": "%s",
//                      "payload": {
//                        "login": "%s",
//                        "loginId": %d,
//                        "bchId": %d,
//                        "loginKey": "%s",
//                        "deviceKey": "%s",
//                        "bchLimit": 1000000
//                      }
//                    }
//                    """.formatted(
//                    reqId,
//                    user.login,
//                    user.loginId,
//                    user.bchId,
//                    user.loginPubB64,
//                    user.devicePubB64
//            );
//            JsonNode resp = callSingleJsonOp(json, "TEST createSession_flow / AddUser");
//            int status = resp.path("status").asInt();
//            Assertions.assertEquals(200, status, "AddUser должен вернуть 200");
//        }
//
//        SessionTokens tokens = createSessionForUser(user, "[createSession_flow]");
//        Assertions.assertNotNull(tokens.sessionId, "sessionId не должен быть null");
//        Assertions.assertNotNull(tokens.sessionPwd, "sessionPwd не должен быть null");
//
//        System.out.println("✅ [TEST] Сессия успешно создана: sessionId=" + tokens.sessionId);
//    }
//
//    /**
//     * 3) Сценарий с двумя сессиями (упрощённая версия):
//     *    - создаём пользователя
//     *    - создаём первую сессию
//     *    - создаём вторую сессию
//     *    - убеждаемся, что sessionId разные
//     *
//     * Полный цикл с ListSessions / CloseActiveSession можно будет нарастить поверх
//     * этого теста, когда добавим JSON-обработчик Net_ListSessions.
//     */
//    @Test
//    void fullTwoSessionLifecycleScenario() throws Exception {
//        TestUser user = createRandomUser();
//
//        // 1) AddUser
//        String reqId = "add-" + UUID.randomUUID();
//        String jsonAdd = """
//                {
//                  "op": "AddUser",
//                  "requestId": "%s",
//                  "payload": {
//                    "login": "%s",
//                    "loginId": %d,
//                    "bchId": %d,
//                    "loginKey": "%s",
//                    "deviceKey": "%s",
//                    "bchLimit": 1000000
//                  }
//                }
//                """.formatted(
//                reqId,
//                user.login,
//                user.loginId,
//                user.bchId,
//                user.loginPubB64,
//                user.devicePubB64
//        );
//
//        JsonNode addResp = callSingleJsonOp(jsonAdd, "SCENARIO fullTwoSessionLifecycle / AddUser");
//        int addStatus = addResp.path("status").asInt();
//        Assertions.assertEquals(200, addStatus, "AddUser должен вернуть 200");
//
//        System.out.println("✅ [SC] Пользователь создан: " + user.login);
//
//        // 2) Первая сессия
//        SessionTokens s1 = createSessionForUser(user, "[SC S1]");
//        // 3) Вторая сессия
//        SessionTokens s2 = createSessionForUser(user, "[SC S2]");
//
//        Assertions.assertNotEquals(
//                s1.sessionId,
//                s2.sessionId,
//                "Первая и вторая сессия должны иметь разные sessionId"
//        );
//
//        System.out.println();
//        System.out.println("✅ [SC] Полный сценарий (упрощённый) успешно отработал:");
//        System.out.println("   session1 = " + s1.sessionId);
//        System.out.println("   session2 = " + s2.sessionId);
//
//        System.out.println("\nℹ️ ListSessions / CloseActiveSession / повторные проверки можно будет добавить, " +
//                "когда JSON-обработчик Net_ListSessions будет реализован на сервере.");
//    }
//}
