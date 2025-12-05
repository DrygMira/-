package server.logic.ws_protocol.JSON;

import shine.db.entities.SolanaUser;
import shine.db.entities.ActiveSession;

/**
 * ConnectionContext — контекст состояния одного WebSocket-соединения.
 * Живёт ровно столько же, сколько живёт подключение.
 */
public class ConnectionContext {

    // Статусы аутентификации
    public static final int AUTH_STATUS_NONE = 0; // анонимный или не авторизованный пользователь
    public static final int AUTH_STATUS_USER = 1; // авторизованный пользователь

    // Полный пользователь из БД (solana_users)
    private SolanaUser solanaUser;

    // Активная сессия из БД (active_sessions)
    private ActiveSession activeSession;

    private Long sessionId;
    private String sessionPwd;

    private int authenticationStatus = AUTH_STATUS_NONE;

    // --- SolanaUser / ActiveSession ---

    public SolanaUser getSolanaUser() {
        return solanaUser;
    }

    public void setSolanaUser(SolanaUser solanaUser) {
        this.solanaUser = solanaUser;
    }

    public ActiveSession getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(ActiveSession activeSession) {
        this.activeSession = activeSession;
    }

    // --- Удобные геттеры для логина ---

    public String getLogin() {
        return solanaUser != null ? solanaUser.getLogin() : null;
    }

    public Long getLoginId() {
        return solanaUser != null ? solanaUser.getLoginId() : null;
    }

    // --- sessionId / sessionPwd ---

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

    // --- auth status ---

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
        solanaUser = null;
        activeSession = null;

        sessionId = null;
        sessionPwd = null;

        authenticationStatus = AUTH_STATUS_NONE;
    }

    @Override
    public String toString() {
        return "ConnectionContext{" +
                "login='" + getLogin() + '\'' +
                ", loginId=" + getLoginId() +
                ", sessionId=" + sessionId +
                ", authenticationStatus=" + authenticationStatus +
                '}';
    }
}
