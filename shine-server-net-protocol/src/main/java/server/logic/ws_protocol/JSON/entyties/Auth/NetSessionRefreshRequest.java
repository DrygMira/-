package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Запрос SessionRefresh.
 *
 * JSON (payload):
 * {
 *   "sessionId": 123,
 *   "sessionPwd": "abcd..."
 * }
 */
public class NetSessionRefreshRequest extends NetRequest {

    private long sessionId;
    private String sessionPwd;

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }

    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }
}
