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

public class Test_AddUser_FirstAuth {

    // Адрес сервера
    private static final String WS_URI = "ws://localhost:7070/ws";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // Тестовые данные пользователя
    private static final String TEST_LOGIN = "anya2";
    private static final long TEST_LOGIN_ID = 100212L;
    private static final long TEST_BCH_ID = 4222L;
    private static final int TEST_BCH_LIMIT = 1_000_000;

    // Тестовая пара ключей Ed25519 (стабильная, чтобы поведение не прыгало)
    private static final byte[] TEST_PRIV_KEY;
    private static final String TEST_PUBKEY_B64;

    static {
        // Можно сделать детерминированно от логина, чтобы всегда были одинаковые ключи
        TEST_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-" + TEST_LOGIN);
        byte[] pub = Ed25519Util.derivePublicKey(TEST_PRIV_KEY);
        TEST_PUBKEY_B64 = Ed25519Util.keyToBase64(pub);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        CountDownLatch latch = new CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();

        ClientListener listener = new ClientListener(latch);

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), listener)
                .join();

        // Ждём, пока всё не завершится (успех/ошибка/закрытие)
        latch.await();
        System.out.println("Тест завершён, выходим.");
    }

    // --- вспомогательные билдера JSON-запросов ---

    // 1) Добавление пользователя
    private static String buildAddUserJson() {
        return """
                {
                  "op": "AddUser",
                  "requestId": "test-add-1",
                  "payload": {
                    "login": "%s",
                    "loginId": %d,
                    "bchId": %d,
                    "pubkey0": "%s",
                    "pubkey1": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                TEST_LOGIN,
                TEST_LOGIN_ID,
                TEST_BCH_ID,
                TEST_PUBKEY_B64,   // pubkey0
                TEST_PUBKEY_B64,   // pubkey1 (для теста можно тот же)
                TEST_BCH_LIMIT
        );
    }

    // 2) Шаг 1 авторизации: запрос sessionPwd
    private static String buildAuthStep1Json() {
        return """
                {
                  "op": "AuthSessionNewStep1",
                  "requestId": "test-auth-1",
                  "payload": {
                    "login": "%s"
                  }
                }
                """.formatted(TEST_LOGIN);
    }

    // 3) Шаг 2 авторизации: подтверждение подписью
    private static String buildAuthStep2Json(String sessionPwd) {
        if (sessionPwd == null) {
            sessionPwd = "";
        }

        long timeMs = System.currentTimeMillis();

        // preimage = loginId + timeMs + sessionPwd
        String preimageStr = String.valueOf(TEST_LOGIN_ID) + timeMs + sessionPwd;
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        // Подписываем приватным ключом
        byte[] sig = Ed25519Util.sign(preimage, TEST_PRIV_KEY);
        String sigB64 = Base64.getEncoder().encodeToString(sig);

        return """
                {
                  "op": "AuthSessionNewStep2",
                  "requestId": "test-auth-2",
                  "payload": {
                    "loginId": %d,
                    "sigNum": 0,
                    "timeMs": %d,
                    "signatureB64": "%s"
                  }
                }
                """.formatted(
                TEST_LOGIN_ID,
                timeMs,
                sigB64
        );
    }

    // ================== LISTENER ==================

    // Внутренний Listener, который сам по шагам шлёт запросы и печатает ответы
    private static class ClientListener implements Listener {

        private final CountDownLatch latch;
        private int step = 0; // 0 - AddUser, 1 - AuthStep1, 2 - AuthStep2
        private String sessionPwdFromStep1;

        ClientListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("✅ WebSocket подключен");

            // Разрешаем приём первого сообщения
            webSocket.request(1);

            sendNextRequest(webSocket);
            Listener.super.onOpen(webSocket);
        }

        // Отправка следующего запроса в зависимости от шага
        private void sendNextRequest(WebSocket webSocket) {
            switch (step) {
                case 0 -> {
                    String json = buildAddUserJson();
                    System.out.println();
                    System.out.println("📤 [Шаг 1] Отправляем AddUser:");
                    System.out.println(json);
                    webSocket.sendText(json, true);
                }
                case 1 -> {
                    String json = buildAuthStep1Json();
                    System.out.println();
                    System.out.println("📤 [Шаг 2] Отправляем AuthSessionNewStep1:");
                    System.out.println(json);
                    webSocket.sendText(json, true);
                }
                case 2 -> {
                    String json = buildAuthStep2Json(sessionPwdFromStep1);
                    System.out.println();
                    System.out.println("📤 [Шаг 3] Отправляем AuthSessionNewStep2 (подпись):");
                    System.out.println(json);
                    webSocket.sendText(json, true);
                }
                default -> {
                    System.out.println("✅ Все шаги выполнены, закрываем соединение");
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "all tests done");
                }
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket,
                                         CharSequence data,
                                         boolean last) {
            String message = data.toString();
            System.out.println("📥 Ответ на шаг " + (step + 1) + ":");
            System.out.println(message);
            System.out.println("-----------------------------------------------------");

            // Если это ответ на шаг 2 (AuthSessionNewStep1) — достаем sessionPwd из payload
            if (step == 1) {
                sessionPwdFromStep1 = extractSessionPwd(message);
                System.out.println("🔑 Извлечён sessionPwd: " + sessionPwdFromStep1);
            }

            // Переходим к следующему шагу
            step++;
            sendNextRequest(webSocket);

            // Запрашиваем следующее входящее сообщение
            webSocket.request(1);

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.println("❌ Ошибка WebSocket-клиента: " + error.getMessage());
            error.printStackTrace(System.out);
            latch.countDown();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket,
                                          int statusCode,
                                          String reason) {
            System.out.println("🔚 Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        }

        private String extractSessionPwd(String json) {
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
    }
}
