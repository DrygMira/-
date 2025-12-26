package server.logic.ws_protocol.JSON.utils;

import server.logic.ws_protocol.JSON.entyties.Net_Exception_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Фабрика ошибок для JSON-протокола.
 * Создаёт единообразные NetExceptionResponse.
 */
public final class NetExceptionResponseFactory {

    private NetExceptionResponseFactory() {
        // запрет на создание объектов
    }

    public static Net_Exception_Response error(Net_Request req,
                                               int status,
                                               String code,
                                               String message) {

        Net_Exception_Response resp = new Net_Exception_Response();

        // ✅ НЕ падаем, даже если req == null
        if (req != null) {
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
        } else {
            resp.setOp(null);
            resp.setRequestId(null);
        }

        resp.setStatus(status);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }

    /**
     * Вариант для случаев, когда NetRequest ещё не распарсен,
     * но мы уже знаем op и requestId (или они null).
     */
    public static Net_Exception_Response error(String op,
                                               String requestId,
                                               int status,
                                               String code,
                                               String message) {

        Net_Exception_Response resp = new Net_Exception_Response();
        resp.setOp(op);
        resp.setRequestId(requestId);
        resp.setStatus(status);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
}