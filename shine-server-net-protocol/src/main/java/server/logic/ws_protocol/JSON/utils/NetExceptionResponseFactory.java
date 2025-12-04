package server.logic.ws_protocol.JSON.utils;



import server.logic.ws_protocol.JSON.entyties.NetExceptionResponse;
import server.logic.ws_protocol.JSON.entyties.NetRequest;

import java.util.Map;

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
        resp.setPayload(Map.of(
                "code", code,
                "message", message
        ));

        return resp;
    }
}
