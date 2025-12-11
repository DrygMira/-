package server.logic.ws_protocol.JSON.handlers;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;

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
    Net_Response handle(Net_Request request, ConnectionContext ctx) throws Exception;
}
