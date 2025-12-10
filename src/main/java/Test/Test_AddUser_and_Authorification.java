package Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.crypto.Ed25519Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

/**
 * Полный тестовый сценарий:
 *
 *  1) AddUser  — добавляем пользователя в локальную БД
 *     (loginKey и deviceKey разные).
 *
 *  2) AuthChallenge — запрашиваем sessionPwd.
 *
 *  3) CreateAuthSession — подтверждаем владение deviceKey,
 *     создаётся сессия, сервер возвращает sessionId (строка).
 *
 *  4) Новое подключение:
 *       - отправляем RefreshSession с тем же sessionId,
 *         но заведомо неверным sessionPwd
 *         (в консоль пишем: ожидаем ОТРИЦАТЕЛЬНЫЙ ответ).
 *
 *  5) Ещё одно новое подключение:
 *       - отправляем RefreshSession с sessionId
 *         и корректным sessionPwd
 *         (в консоль пишем: ожидаем УСПЕШНЫЙ ответ).
 */
public class Test_AddUser_and_Authorification {

    // Адрес сервера
    private static final String WS_URI = "ws://localhost:7070/ws";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Тестовые данные пользователя
    private static final String TEST_LOGIN = "anya1";
    private static final long TEST_LOGIN_ID = 100310L;
    private static final long TEST_BCH_ID = 4222L;
    private static final int TEST_BCH_LIMIT = 1_000_000;

    // --- Тестовые пары ключей ---
    // loginKey — ключ аккаунта (например, "основной")
    // deviceKey — ключ устройства, которым подписываем авторизацию

    private static final byte[] LOGIN_PRIV_KEY;
    private static final String LOGIN_PUBKEY_B64;

    private static final byte[] DEVICE_PRIV_KEY;
    private static final String DEVICE_PUBKEY_B64;

    static {
        // Детерминированное "семя" для логин-ключа
        LOGIN_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-login-11" + TEST_LOGIN);
        byte[] loginPub = Ed25519Util.derivePublicKey(LOGIN_PRIV_KEY);
        LOGIN_PUBKEY_B64 = Ed25519Util.keyToBase64(loginPub);

        // Детерминированное "семя" для девайс-ключа
        DEVICE_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-device-" + TEST_LOGIN);
        byte[] devicePub = Ed25519Util.derivePublicKey(DEVICE_PRIV_KEY);
        DEVICE_PUBKEY_B64 = Ed25519Util.keyToBase64(devicePub);
    }

    // --- Глобальные переменные между сценариями ---

    /** sessionPwd, выданный на шаге AuthChallenge. */
    private static String GLOBAL_SESSION_PWD;

    /** sessionId (строка, base64-32 байта), выданный на шаге CreateAuthSession. */
    private static String GLOBAL_SESSION_ID;

    /** storagePwd, который мы отправили при CreateAuthSession (для информации). */
    private static String GLOBAL_STORAGE_PWD_SENT;

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        // Сценарий 1: регистрация + первичная авторизация
        runScenario_AddUser_And_FirstAuth();

        // Сценарий 2: новое подключение, RefreshSession с неверным sessionPwd
        runScenario_RefreshSession_WrongPwd();

        // Сценарий 3: новое подключение, RefreshSession с корректным sessionPwd
        runScenario_RefreshSession_CorrectPwd();

        System.out.println("Все тесты завершены, выходим.");
    }

    // ==========================================================
    //                 SCENARIO 1: AddUser + Auth
    // ==========================================================

    private static void runScenario_AddUser_And_FirstAuth() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 1: AddUser + AuthChallenge + CreateAuthSession ===");

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    private int step = 0; // 0 - AddUser, 1 - AuthStep1, 2 - AuthStep2

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S1] WebSocket подключен");
                        webSocket.request(1);
                        sendNextRequest(webSocket);
                        Listener.super.onOpen(webSocket);
                    }

                    private void sendNextRequest(WebSocket webSocket) {
                        switch (step) {
                            case 0 -> {
                                String json = buildAddUserJson();
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 1] Отправляем AddUser:");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            case 1 -> {
                                String json = buildAuthStep1Json();
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 2] Отправляем AuthChallenge:");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            case 2 -> {
                                GLOBAL_STORAGE_PWD_SENT = generateFakeStoragePwd();
                                String json = buildAuthStep2Json(GLOBAL_SESSION_PWD, GLOBAL_STORAGE_PWD_SENT);
                                System.out.println();
                                System.out.println("📤 [S1 / Шаг 3] Отправляем CreateAuthSession (подпись deviceKey):");
                                System.out.println(json);
                                webSocket.sendText(json, true);
                            }
                            default -> {
                                System.out.println("✅ [S1] Все шаги выполнены, закрываем соединение");
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario1 done");
                            }
                        }
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S1] Ответ на шаг " + (step + 1) + ":");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");

                        // Шаг 2: получаем sessionPwd
                        if (step == 1) {
                            GLOBAL_SESSION_PWD = extractSessionPwd(message);
                            System.out.println("🔑 [S1] Извлечён sessionPwd: " + GLOBAL_SESSION_PWD);
                        }

                        // Шаг 3: получаем sessionId
                        if (step == 2) {
                            GLOBAL_SESSION_ID = extractSessionId(message);
                            System.out.println("🆔 [S1] Извлечён sessionId: " + GLOBAL_SESSION_ID);
                            System.out.println("   (Эта sessionId и sessionPwd понадобятся в сценариях 2 и 3)");
                        }

                        step++;
                        sendNextRequest(webSocket);
                        webSocket.request(1);

                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S1] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S1] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 1 завершён ===");
    }

    // ==========================================================
    //         SCENARIO 2: RefreshSession с неправильным паролем
    // ==========================================================

    private static void runScenario_RefreshSession_WrongPwd() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 2: RefreshSession с НЕВЕРНЫМ sessionPwd ===");
        System.out.println("Ожидаем ОТРИЦАТЕЛЬНЫЙ ответ сервера (UNVERIFIED / SESSION_PWD_MISMATCH и т.п.)");

        if (GLOBAL_SESSION_ID == null || GLOBAL_SESSION_PWD == null) {
            System.out.println("⚠️ Нет sessionId или sessionPwd из сценария 1, пропускаем сценарий 2.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        // Специально подменяем пароль, чтобы сервер его НЕ принял
        String wrongPwd = GLOBAL_SESSION_PWD + "_WRONG";

        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S2] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(GLOBAL_SESSION_ID, wrongPwd, "test-refresh-wrong-1");
                        System.out.println();
                        System.out.println("📤 [S2] Отправляем RefreshSession с НЕВЕРНЫМ sessionPwd:");
                        System.out.println(json);
                        webSocket.sendText(json, true);
                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S2] Ответ сервера (ожидаем ошибку):");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");
                        System.out.println("💬 [S2] Если в ответе status != 200 и/или код ошибки про неверный пароль — это ПРАВИЛЬНОЕ поведение.");

                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario2 done");
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S2] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S2] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 2 завершён ===");
    }

    // ==========================================================
    //         SCENARIO 3: RefreshSession с правильными данными
    // ==========================================================

    private static void runScenario_RefreshSession_CorrectPwd() throws Exception {
        System.out.println();
        System.out.println("=== СЦЕНАРИЙ 3: RefreshSession с КОРРЕКТНЫМ sessionPwd ===");
        System.out.println("Ожидаем УСПЕШНЫЙ ответ сервера (status=200),");
        System.out.println(" а в payload должен вернуться актуальный storagePwd (по твоей схеме).");

        if (GLOBAL_SESSION_ID == null || GLOBAL_SESSION_PWD == null) {
            System.out.println("⚠️ Нет sessionId или sessionPwd из сценария 1, пропускаем сценарий 3.");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        WebSocket ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new Listener() {

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("✅ [S3] WebSocket подключен");
                        webSocket.request(1);

                        String json = buildRefreshSessionJson(GLOBAL_SESSION_ID, GLOBAL_SESSION_PWD, "test-refresh-ok-1");
                        System.out.println();
                        System.out.println("📤 [S3] Отправляем RefreshSession с КОРРЕКТНЫМ sessionPwd:");
                        System.out.println(json);
                        webSocket.sendText(json, true);
                        Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket,
                                                     CharSequence data,
                                                     boolean last) {
                        String message = data.toString();
                        System.out.println("📥 [S3] Ответ сервера (ожидаем успех):");
                        System.out.println(message);
                        System.out.println("-----------------------------------------------------");
                        System.out.println("💬 [S3] Если status=200 — сессия успешно восстановлена.");
                        String storagePwdFromServer = extractStoragePwd(message);
                        System.out.println("🧾 [S3] storagePwd от сервера: " + storagePwdFromServer);
                        System.out.println("   (Может совпадать с тем, что был в шаге 2, или быть обновлённым — зависит от логики сервера)");

                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "scenario3 done");
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("❌ [S3] Ошибка WebSocket-клиента: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket,
                                                      int statusCode,
                                                      String reason) {
                        System.out.println("🔚 [S3] Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
        System.out.println("=== СЦЕНАРИЙ 3 завершён ===");
    }

    // ==========================================================
    //                     JSON BUILDERS
    // ==========================================================

    // 1) AddUser с payload (loginKey != deviceKey)
    private static String buildAddUserJson() {
        return """
                {
                  "op": "AddUser",
                  "requestId": "test-add-1",
                  "payload": {
                    "login": "%s",
                    "loginId": %d,
                    "bchId": %d,
                    "loginKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                TEST_LOGIN,
                TEST_LOGIN_ID,
                TEST_BCH_ID,
                LOGIN_PUBKEY_B64,    // loginKey
                DEVICE_PUBKEY_B64,   // deviceKey
                TEST_BCH_LIMIT
        );
    }

    // 2) Шаг 1 авторизации: запрос sessionPwd
    private static String buildAuthStep1Json() {
        return """
                {
                  "op": "AuthChallenge",
                  "requestId": "test-auth-1",
                  "payload": {
                    "login": "%s"
                  }
                }
                """.formatted(TEST_LOGIN);
    }

    // 3) Шаг 2 авторизации: подтверждение подписью
    // payload: storagePwd, timeMs, signatureB64
    private static String buildAuthStep2Json(String sessionPwd, String storagePwd) {
        if (sessionPwd == null) {
            sessionPwd = "";
        }
        if (storagePwd == null || storagePwd.isBlank()) {
            storagePwd = generateFakeStoragePwd();
        }

        long timeMs = System.currentTimeMillis();

        // preimage = "AUTHORIFICATED:" + timeMs + sessionPwd
        String preimageStr = "AUTHORIFICATED:" + timeMs + sessionPwd;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        // Подписываем приватным ключом устройства (deviceKey)
        byte[] sig = Ed25519Util.sign(preimage, DEVICE_PRIV_KEY);
        String sigB64 = Base64.getEncoder().encodeToString(sig);

        return """
                {
                  "op": "CreateAuthSession",
                  "requestId": "test-auth-2",
                  "payload": {
                    "storagePwd": "%s",
                    "timeMs": %d,
                    "signatureB64": "%s"
                  }
                }
                """.formatted(
                storagePwd,
                timeMs,
                sigB64
        );
    }

    // 4) RefreshSession: всё в payload
    private static String buildRefreshSessionJson(String sessionId, String sessionPwd, String requestId) {
        return """
                {
                  "op": "RefreshSession",
                  "requestId": "%s",
                  "payload": {
                    "sessionId": "%s",
                    "sessionPwd": "%s"
                  }
                }
                """.formatted(
                requestId,
                sessionId,
                sessionPwd
        );
    }

    // просто для теста: base64 от 32 байт "storage" ключа
    private static String generateFakeStoragePwd() {
        byte[] data = new byte[32];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(data);
    }

    // ==========================================================
    //                     JSON HELPERS
    // ==========================================================

    private static String extractSessionPwd(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionPwd")) {
                return payload.get("sessionPwd").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить sessionPwd из ответа: " + e.getMessage());
        }
        return null;
    }

    private static String extractSessionId(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("sessionId")) {
                return payload.get("sessionId").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить sessionId из ответа: " + e.getMessage());
        }
        return null;
    }

    private static String extractStoragePwd(String json) {
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            JsonNode payload = root.get("payload");
            if (payload != null && payload.has("storagePwd")) {
                return payload.get("storagePwd").asText();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось распарсить storagePwd из ответа: " + e.getMessage());
        }
        return null;
    }
}
