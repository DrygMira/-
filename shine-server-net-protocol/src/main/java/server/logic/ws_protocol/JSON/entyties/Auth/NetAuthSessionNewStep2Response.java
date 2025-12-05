package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Ответ на AuthSessionNewStep2.
 *
 * Успешный JSON:
 * {
 *   "op": "AuthSessionNewStep2",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionId": 1234567890
 *   }
 * }
 */
public class NetAuthSessionNewStep2Response extends NetResponse {

    private Long sessionId;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
