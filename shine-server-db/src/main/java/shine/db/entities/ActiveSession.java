package shine.db.entities;

/**
 * ActiveSession — запись об активной сессии пользователя.
 *
 * Поля:
 *  - sessionId      – строка (base64 от 32 байт)
 *  - loginId        – long
 *  - sessionPwd     – строка (секрет шага 1)
 *  - storagePwd     – строка (секрет клиента для хранения данных)
 *  - sessionCreatedAtMs      – long (время создания)
 *  - lastAuthirificatedAtMs  – long (последнее подтверждение/refresh)
 *  - pushEndpoint   – строка (WebPush, пока null/пусто)
 *  - pushP256dhKey  – строка (WebPush, пока null/пусто)
 *  - pushAuthKey    – строка (WebPush, пока null/пусто)
 */
public class ActiveSession {

    private String sessionId;
    private long loginId;
    private String sessionPwd;
    private String storagePwd;
    private long sessionCreatedAtMs;
    private long lastAuthirificatedAtMs;
    private String pushEndpoint;
    private String pushP256dhKey;
    private String pushAuthKey;

    public ActiveSession() {
    }

    public ActiveSession(String sessionId,
                         long loginId,
                         String sessionPwd,
                         String storagePwd,
                         long sessionCreatedAtMs,
                         long lastAuthirificatedAtMs,
                         String pushEndpoint,
                         String pushP256dhKey,
                         String pushAuthKey) {
        this.sessionId = sessionId;
        this.loginId = loginId;
        this.sessionPwd = sessionPwd;
        this.storagePwd = storagePwd;
        this.sessionCreatedAtMs = sessionCreatedAtMs;
        this.lastAuthirificatedAtMs = lastAuthirificatedAtMs;
        this.pushEndpoint = pushEndpoint;
        this.pushP256dhKey = pushP256dhKey;
        this.pushAuthKey = pushAuthKey;
    }

    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public long getLoginId() {
        return loginId;
    }
    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public String getSessionPwd() {
        return sessionPwd;
    }
    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }

    public String getStoragePwd() {
        return storagePwd;
    }
    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }

    public long getSessionCreatedAtMs() {
        return sessionCreatedAtMs;
    }
    public void setSessionCreatedAtMs(long sessionCreatedAtMs) {
        this.sessionCreatedAtMs = sessionCreatedAtMs;
    }

    public long getLastAuthirificatedAtMs() {
        return lastAuthirificatedAtMs;
    }
    public void setLastAuthirificatedAtMs(long lastAuthirificatedAtMs) {
        this.lastAuthirificatedAtMs = lastAuthirificatedAtMs;
    }

    public String getPushEndpoint() {
        return pushEndpoint;
    }
    public void setPushEndpoint(String pushEndpoint) {
        this.pushEndpoint = pushEndpoint;
    }

    public String getPushP256dhKey() {
        return pushP256dhKey;
    }
    public void setPushP256dhKey(String pushP256dhKey) {
        this.pushP256dhKey = pushP256dhKey;
    }

    public String getPushAuthKey() {
        return pushAuthKey;
    }
    public void setPushAuthKey(String pushAuthKey) {
        this.pushAuthKey = pushAuthKey;
    }
}
