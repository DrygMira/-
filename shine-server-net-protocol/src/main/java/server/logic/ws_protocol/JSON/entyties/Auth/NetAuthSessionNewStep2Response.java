package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Ответ на AuthSessionNewStep2.
 *
 * При успехе сервер создаёт запись в active_sessions
 * и возвращает идентификатор сессии sessionId.
 *
 * JSON:
 * {
 *   "op": "AuthSessionNewStep2",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionId": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class NetAuthSessionNewStep2Response extends NetResponse {

    /** Идентификатор сессии, base64 от 32 байт. */
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
