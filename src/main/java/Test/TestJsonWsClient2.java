//package Test;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.WebSocket;
//import java.net.http.WebSocket.Listener;
//import java.util.concurrent.CompletionStage;
//import java.util.concurrent.CountDownLatch;
//
//public class TestJsonWsClient2 {
//
//    public static void main(String[] args) throws Exception {
//        String uri = "ws://localhost:7070/ws";
//
//        String jsonRequestRefreshSession = """
//                {
//                  "op": "RefreshSession",
//                  "requestId": "test-1",
//                  "payload": {
//                    "sessionId": 123,
//                    "sessionPwd": "test-password"
//                  }
//                }
//                """;
//
//        String jsonRequestAddUser = """
//                {
//                  "op": "AddUser",
//                  "requestId": "test-add-1",
//                  "payload": {
//                    "login": "anya1111",
//                    "loginId": 100211,
//                    "bchId": 4222,
//                    "pubkey0": "PUB0",
//                    "pubkey1": "PUB1",
//                    "bchLimit": 1000000
//                  }
//                }
//                """;
//
//        String jsonRequestAuthChallenge = """
//                {
//                  "op": "AuthChallenge",
//                  "requestId": "test-auth-1",
//                  "payload": {
//                    "login": "anya1111"
//                  }
//                }
//                """;
//
//        // Что тестируем сейчас:
//        String jsonRequest = jsonRequestAuthChallenge;
////      String jsonRequest = jsonRequestRefreshSession;
////      String jsonRequest = jsonRequestAddUser;
//
//        System.out.println("Подключаемся к " + uri);
//
//        CountDownLatch latch = new CountDownLatch(1);
//
//        HttpClient client = HttpClient.newHttpClient();
//
//        WebSocket webSocket = client.newWebSocketBuilder()
//                .buildAsync(URI.create(uri), new Listener() {
//
//                    // 0 — ещё ничего не получили
//                    // 1 — получили 1-й ответ, отправили повторно
//                    // 2 — получили 2-й ответ, закрываемся
//                    private int responsesCount = 0;
//
//                    @Override
//                    public void onOpen(WebSocket webSocket) {
//                        System.out.println("✅ WebSocket подключен");
//
//                        System.out.println("📤 Отправляем JSON-запрос (1 раз):");
//                        System.out.println(jsonRequest);
//
//                        webSocket.sendText(jsonRequest, true);
//                        Listener.super.onOpen(webSocket);
//                    }
//
//                    @Override
//                    public CompletionStage<?> onText(WebSocket webSocket,
//                                                     CharSequence data,
//                                                     boolean last) {
//                        String message = data.toString();
//                        responsesCount++;
//
//                        System.out.println("📥 Получен TEXT-ответ #" + responsesCount + " от сервера:");
//                        System.out.println(message);
//
//                        if (responsesCount == 1) {
//                            // После первого ответа — отправляем тот же запрос ещё раз
//                            System.out.println("📤 Отправляем JSON-запрос второй раз:");
//                            System.out.println(jsonRequest);
//                            webSocket.sendText(jsonRequest, true);
//                        } else {
//                            // После второго ответа — закрываем соединение
//                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test done");
//                            latch.countDown();
//                        }
//
//                        return Listener.super.onText(webSocket, data, last);
//                    }
//
//                    @Override
//                    public void onError(WebSocket webSocket, Throwable error) {
//                        System.out.println("❌ Ошибка WebSocket-клиента: " + error.getMessage());
//                        error.printStackTrace(System.out);
//                        latch.countDown();
//                    }
//
//                    @Override
//                    public CompletionStage<?> onClose(WebSocket webSocket,
//                                                      int statusCode,
//                                                      String reason) {
//                        System.out.println("🔚 Соединение закрыто. Код=" + statusCode + ", причина=" + reason);
//                        latch.countDown();
//                        return Listener.super.onClose(webSocket, statusCode, reason);
//                    }
//                }).join();
//
//        // Ждём, пока получим ответ/ошибку/закрытие
//        latch.await();
//        System.out.println("Тест завершён, выходим.");
//    }
//}
