package server.logic.ws_protocol.JSON.utils;

import server.logic.ws_protocol.JSON.entyties.NetExceptionResponse;
import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Фабрика ошибок для JSON-протокола.
 * Создаёт единообразные NetExceptionResponse.
 */
public final class NetExceptionResponseFactory {

    private NetExceptionResponseFactory() {
        // запрет на создание объектов
    }

    public static NetExceptionResponse error(NetRequest req,
                                             int status,
                                             String code,
                                             String message) {

        NetExceptionResponse resp = new NetExceptionResponse();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(status);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }

    /**
     * Вариант для случаев, когда NetRequest ещё не распарсен,
     * но мы уже знаем op и requestId (или они null).
     */
    public static NetExceptionResponse error(String op,
                                             String requestId,
                                             int status,
                                             String code,
                                             String message) {

        NetExceptionResponse resp = new NetExceptionResponse();
        resp.setOp(op);
        resp.setRequestId(requestId);
        resp.setStatus(status);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
}
