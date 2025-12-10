package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Запрос RefreshSession.
 *
 * Используется для повторного входа без повторной подписи:
 * клиент хранит sessionId и sessionPwd, которые получил на шаге 2.
 *
 * JSON (payload):
 * {
 *   "sessionId": "base64-id-сессии",
 *   "sessionPwd": "base64-sessionPwd"
 * }
 */
public class Net_RefreshSession_Request extends NetRequest {

    private String sessionId;
    private String sessionPwd;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }

    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }
}
