package shine.db.entities;

/**
 * Модель активной сессии (таблица active_sessions).
 *
 * Поля соответствуют схеме:
 *
 * CREATE TABLE active_sessions (
 *     sessionId              TEXT    NOT NULL PRIMARY KEY,
 *     loginId                INTEGER NOT NULL,
 *     sessionPwd             TEXT    NOT NULL,
 *     storagePwd             TEXT    NOT NULL,
 *     sessionCreatedAtMs     INTEGER NOT NULL,
 *     lastAuthirificatedAtMs INTEGER NOT NULL,
 *     pushEndpoint           TEXT,
 *     pushP256dhKey          TEXT,
 *     pushAuthKey            TEXT,
 *     FOREIGN KEY (loginId) REFERENCES solana_users(loginId)
 * );
 */
public class ActiveSession {

    private String sessionId;               // TEXT base64(32 bytes)
    private long   loginId;                 // INTEGER
    private String sessionPwd;              // TEXT
    private String storagePwd;              // TEXT
    private long   sessionCreatedAtMs;      // INTEGER
    private long   lastAuthirificatedAtMs;  // INTEGER
    private String pushEndpoint;            // TEXT (nullable)
    private String pushP256dhKey;           // TEXT (nullable)
    private String pushAuthKey;             // TEXT (nullable)

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

    // --- getters / setters ---

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
