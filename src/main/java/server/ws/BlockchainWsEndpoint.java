package server.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.InboundMessageProcessor;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.JsonInboundProcessor;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@WebSocket
public class BlockchainWsEndpoint {
    private static final Logger log = LoggerFactory.getLogger(BlockchainWsEndpoint.class);

    private Session session;

    /** Контекст для текущего WebSocket-соединения. */
    private final ConnectionContext connectionContext = new ConnectionContext();

    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        // Привязываем WebSocket-сессию к ConnectionContext
        connectionContext.setWsSession(session);
        log.info("WS connected: {}", session.getRemoteAddress());
    }

    @OnWebSocketMessage
    public void onBinary(byte[] payload, int offset, int length) {
        byte[] msg = new byte[length];
        System.arraycopy(payload, offset, msg, 0, length);

        // Асинхронно обрабатываем входящее бинарное сообщение
        CompletableFuture
                .supplyAsync(() -> InboundMessageProcessor.process(msg))
                .thenAccept(resp -> {
                    if (resp != null && session != null && session.isOpen()) {
                        session.getRemote().sendBytes(ByteBuffer.wrap(resp), new WriteCallback() {
                            @Override
                            public void writeFailed(Throwable x) {
                                log.warn("Failed to send response", x);
                            }

                            @Override
                            public void writeSuccess() {
                                log.debug("Response sent successfully");
                            }
                        });
                    }
                })
                .exceptionally(ex -> {
                    log.error("Processing failed", ex);
                    trySendCode(500);
                    return null;
                });
    }

    private void trySendCode(int code) {
        if (session != null && session.isOpen()) {
            byte[] resp = InboundMessageProcessor.intTo4Bytes(code);
            session.getRemote().sendBytes(ByteBuffer.wrap(resp), new WriteCallback() {
                @Override
                public void writeFailed(Throwable x) {
                    log.warn("Failed to send error code", x);
                }

                @Override
                public void writeSuccess() {
                    log.debug("Error code {} sent", code);
                }
            });
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        log.info("WS closed: {} {}", statusCode, reason);
        // Удаляем это подключение из реестра активных соединений
        ActiveConnectionsRegistry.getInstance().remove(connectionContext);
        // На всякий случай очищаем контекст
        connectionContext.reset();
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        log.error("WS error", cause);
    }

    // Обработка текстовых JSON-запросов
    @OnWebSocketMessage
    public void onText(String message) {
        log.info("📥 Получено TEXT-сообщение от клиента: {}", message);

        CompletableFuture
                .supplyAsync(() -> JsonInboundProcessor.processJson(message, connectionContext))
                .thenAccept(respJson -> {
                    if (respJson != null && session != null && session.isOpen()) {

                        log.info("📤 Отправляем ответ клиенту: {}", respJson);

                        session.getRemote().sendString(respJson, new WriteCallback() {
                            @Override
                            public void writeFailed(Throwable x) {
                                log.warn("⚠️ Не удалось отправить JSON-ответ клиенту: {}", x.toString());
                            }

                            @Override
                            public void writeSuccess() {
                                log.debug("✔ JSON-ответ успешно отправлен");
                            }
                        });
                    }
                })
                .exceptionally(ex -> {
                    log.error("❌ Ошибка при обработке JSON-сообщения", ex);
                    trySendJsonError();
                    return null;
                });
    }

    private void trySendJsonError() {
        if (session != null && session.isOpen()) {
            String resp = "{\"op\":null,\"requestId\":null,\"status\":500,"
                    + "\"payload\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Ошибка сервера\"}}";

            log.info("📤 Отправляем клиенту ошибку JSON: {}", resp);

            session.getRemote().sendString(resp, new WriteCallback() {
                @Override
                public void writeFailed(Throwable x) {
                    log.warn("⚠️ Не удалось отправить JSON-ответ клиенту: {}", x.toString());
                }

                @Override
                public void writeSuccess() {
                    log.debug("✔ JSON-ошибка успешно отправлена");
                }
            });
        }
    }
}
