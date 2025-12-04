package server.logic.ws_protocol.JSON.handlers;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Общий интерфейс для всех JSON-хэндлеров.
 */
public interface JsonMessageHandler {

    /**
     * Обработать запрос и вернуть ответ.
     *
     * @param request распарсенный запрос
     * @param ctx     контекст текущего WebSocket-соединения
     */
    NetResponse handle(NetRequest request, ConnectionContext ctx) throws Exception;
}
