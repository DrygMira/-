import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class TestJsonWsClient {

    // Адрес сервера
    private static final String WS_URI = "ws://localhost:7070/ws";

    // Отдельные запросы
    private static final String JSON_REQUEST_SESSION_REFRESH = """
            {
              "op": "SessionRefresh",
              "requestId": "test-1",
              "sessionId": 123,
              "sessionPwd": "test-password"
            }
            """;

    private static final String JSON_REQUEST_ADD_USER = """
            {
              "op": "AddUser",
              "requestId": "test-add-1",
              "login": "anya1111",
              "loginId": 100211,
              "bchId": 4222,
              "pubkey0": "PUB0",
              "pubkey1": "PUB1",
              "bchLimit": 1000000
            }
            """;

    private static final String JSON_REQUEST_AUTH_SESSION_NEW_STEP1 = """
            {
              "op": "AuthSessionNewStep1",
              "requestId": "test-auth-1",
              "login": "anya1111"
            }
            """;

    // МАССИВ КОНСТАНТА с запросами — добавляешь сюда любые свои JSON
    private static final String[] JSON_REQUESTS = {
            JSON_REQUEST_SESSION_REFRESH,
            JSON_REQUEST_ADD_USER,
            JSON_REQUEST_AUTH_SESSION_NEW_STEP1
    };

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        CountDownLatch latch = new CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();

        ClientListener listener = new ClientListener(JSON_REQUESTS, latch);

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), listener)
                .join();

        // Ждём, пока всё не завершится (успех/ошибка/закрытие)
        latch.await();
        System.out.println("Тест завершён, выходим.");
    }

    // Внутренний Listener, который сам по очереди шлёт запросы и печатает ответы
    private static class ClientListener implements Listener {

        private final String[] requests;
        private final CountDownLatch latch;
        private int index = 0; // какой запрос сейчас отправляем/ждём ответ

        ClientListener(String[] requests, CountDownLatch latch) {
            this.requests = requests;
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("✅ WebSocket подключен");
            sendNextRequest(webSocket);
            Listener.super.onOpen(webSocket);
        }

        // Отправка следующего запроса из массива
        private void sendNextRequest(WebSocket webSocket) {
            if (index < requests.length) {
                String json = requests[index];
                System.out.println();
                System.out.println("📤 Отправляем запрос " + (index + 1) + " из " + requests.length + ":");
                System.out.println(json);
                webSocket.sendText(json, true);
            } else {
                System.out.println("✅ Все запросы отправлены, закрываем соединение");
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "all tests done");
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket,
                                         CharSequence data,
                                         boolean last) {
            // Ответ на текущий запрос (с индексом index)
            System.out.println("📥 Ответ на запрос " + (index + 1) + ":");
            System.out.println(data.toString());
            System.out.println("-----------------------------------------------------");

            // Переходим к следующему запросу
            index++;
            sendNextRequest(webSocket);

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
    }
}
