package server.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;

/**
 * Утилита для работы с WebSocket-подключениями.
 */
public final class WsConnectionUtils {

    private static final Logger log = LoggerFactory.getLogger(WsConnectionUtils.class);

    private WsConnectionUtils() {
        // utility
    }

    /**
     * Корректно закрывает WebSocket-соединение:
     *  - удаляет контекст из ActiveConnectionsRegistry;
     *  - очищает ConnectionContext;
     *  - закрывает сам WebSocket (если ещё открыт).
     *
     * @param ctx         контекст соединения
     * @param statusCode  код закрытия WebSocket (например, 1000, 4001)
     * @param reason      причина закрытия (для логов/клиента)
     */
    public static void closeConnection(ConnectionContext ctx, int statusCode, String reason) {
        if (ctx == null) {
            return;
        }

        Session ws = ctx.getWsSession();

        try {
            // Удаляем контекст из реестра активных соединений
            ActiveConnectionsRegistry.getInstance().remove(ctx);

            // Чистим контекст
            ctx.reset();

            // Закрываем WebSocket-сессию
            if (ws != null && ws.isOpen()) {
                try {
                    ws.close(statusCode, reason);
                } catch (Exception e) {
                    log.warn("Не удалось закрыть WebSocket-сессию (statusCode={}, reason={})", statusCode, reason, e);
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка при закрытии WebSocket-соединения", e);
        }
    }
}