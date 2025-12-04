package server.logic.ws_protocol.JSON;

/**
 * ConnectionContext — контекст состояния одного WebSocket-соединения.
 * Живёт ровно столько же, сколько живёт подключение.
 */
public class ConnectionContext {

    // Статусы аутентификации
    public static final int AUTH_STATUS_NONE = 0; // ананимный или не авторизованный пользователь
    public static final int AUTH_STATUS_USER = 1; // авторизованный пользователь
//    public static final int AUTH_STATUS_ANON = 2; // анонимный (зарезервировано на будущее)

    private String login;
    private Long loginId;

    private Long sessionId;
    private String sessionPwd;

    // Данные пользователя / блокчейна
    private Long bchId;
    private String pubkey0;
    private String pubkey1;
    private Integer bchLimit;

    private int authenticationStatus = AUTH_STATUS_NONE;

    // --- getters / setters ---

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Long getLoginId() {
        return loginId;
    }

    public void setLoginId(Long loginId) {
        this.loginId = loginId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }

    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }

    public Long getBchId() {
        return bchId;
    }

    public void setBchId(Long bchId) {
        this.bchId = bchId;
    }

    public String getPubkey0() {
        return pubkey0;
    }

    public void setPubkey0(String pubkey0) {
        this.pubkey0 = pubkey0;
    }

    public String getPubkey1() {
        return pubkey1;
    }

    public void setPubkey1(String pubkey1) {
        this.pubkey1 = pubkey1;
    }

    public Integer getBchLimit() {
        return bchLimit;
    }

    public void setBchLimit(Integer bchLimit) {
        this.bchLimit = bchLimit;
    }

    public int getAuthenticationStatus() {
        return authenticationStatus;
    }

    public void setAuthenticationStatus(int authenticationStatus) {
        this.authenticationStatus = authenticationStatus;
    }

    public boolean isAuthenticatedUser() {
        return authenticationStatus == AUTH_STATUS_USER;
    }

    public boolean isAnonymous() {
        return authenticationStatus == AUTH_STATUS_NONE;
    }

    public void reset() {
        login = null;
        loginId = null;
        sessionId = null;
        sessionPwd = null;

        bchId = null;
        pubkey0 = null;
        pubkey1 = null;
        bchLimit = null;

        authenticationStatus = AUTH_STATUS_NONE;
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
                "login='" + login + '\'' +
                ", loginId=" + loginId +
                ", sessionId=" + sessionId +
                ", bchId=" + bchId +
                ", pubkey0='" + pubkey0 + '\'' +
                ", pubkey1='" + pubkey1 + '\'' +
                ", bchLimit=" + bchLimit +
                ", authenticationStatus=" + authenticationStatus +
                '}';
    }
}
