package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

public class NetAuthSessionNewStep1Response extends NetResponse {
    private String sessionPwd;

    public String getSessionPwd() {
        return sessionPwd;
    }
    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }
}
