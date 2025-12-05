package Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class Test_SessionRefreshClient {

    // Адрес сервера
    private static final String WS_URI = "ws://localhost:7070/ws";

    // ==== ЗДЕСЬ ПОДСТАВИШЬ СВОИ ДАННЫЕ СЕССИИ ====
    private static final long   SESSION_ID  = 7599553208996461137L;          // TODO: подставь реальный sessionId
    private static final String SESSION_PWD = "11b3508f37ae7b41816f42031b90";    // TODO: подставь реальный sessionPwd
    // =============================================

    public static void main(String[] args) throws Exception {
        System.out.println("Подключаемся к " + WS_URI);

        CountDownLatch latch = new CountDownLatch(1);

        HttpClient client = HttpClient.newHttpClient();

        ClientListener listener = new ClientListener(latch);

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), listener)
                .join();

        latch.await();
        System.out.println("Тест SessionRefresh завершён, выходим.");
    }

    private static String buildSessionRefreshJson() {
        return """
                {
                  "op": "SessionRefresh",
                  "requestId": "test-session-refresh-1",
                  "sessionId": %d,
                  "sessionPwd": "%s"
                }
                """.formatted(SESSION_ID, SESSION_PWD);
    }

    private static class ClientListener implements Listener {

        private final CountDownLatch latch;
        private boolean sent = false;

        ClientListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("✅ WebSocket подключен");

            webSocket.request(1); // разрешаем принимать одно сообщение

            // сразу отправляем запрос SessionRefresh
            String json = buildSessionRefreshJson();
            System.out.println();
            System.out.println("📤 Отправляем SessionRefresh:");
            System.out.println(json);
            webSocket.sendText(json, true);
            sent = true;

            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket,
                                         CharSequence data,
                                         boolean last) {
            System.out.println("📥 Ответ от сервера:");
            System.out.println(data.toString());
            System.out.println("-----------------------------------------------------");

            // После одного ответа просто закрываем соединение
            System.out.println("✅ Получен ответ на SessionRefresh, закрываем соединение");
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "session refresh test done");

            // запрашиваем следующее сообщение на всякий случай (хотя уже закрываемся)
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
    }
}
