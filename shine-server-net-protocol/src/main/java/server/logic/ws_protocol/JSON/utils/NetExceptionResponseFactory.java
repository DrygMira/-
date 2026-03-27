package server.logic.ws_protocol.JSON.utils;

import server.logic.ws_protocol.JSON.entyties.Net_Exception_Response;
import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Фабрика ошибок для JSON-протокола.
 * Создаёт единообразные NetExceptionResponse.
 */
public final class NetExceptionResponseFactory {

    private static final int MAX_DETAIL_LEN = 240;

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

    public static String detailedMessage(String prefix, Throwable error) {
        String safePrefix = prefix == null || prefix.isBlank()
                ? "Внутренняя ошибка сервера"
                : prefix.trim();

        if (error == null) {
            return safePrefix;
        }

        String className = error.getClass().getSimpleName();
        if (className == null || className.isBlank()) {
            className = error.getClass().getName();
        }

        String detail = error.getMessage();
        StringBuilder sb = new StringBuilder(safePrefix)
                .append(": ")
                .append(className);

        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail.trim());
        }

        String message = sb.toString()
                .replace('\n', ' ')
                .replace('\r', ' ');

        if (message.length() <= MAX_DETAIL_LEN) {
            return message;
        }
        return message.substring(0, MAX_DETAIL_LEN - 3) + "...";
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
