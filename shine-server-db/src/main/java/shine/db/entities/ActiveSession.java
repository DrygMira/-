package shine.db.entities;

public class ActiveSession {

    private long sessionId;
    private String sessionPwd;
    private long loginId;
    private long timeMs;       // время в мс
    private short pubkeyNum;
    private String pushEndpoint;
    private String pushP256dhKey;
    private String pushAuthKey;

    public ActiveSession() {
    }

    public ActiveSession(long sessionId,
                         String sessionPwd,
                         long loginId,
                         long timeMs,
                         short pubkeyNum,
                         String pushEndpoint,
                         String pushP256dhKey,
                         String pushAuthKey) {
        this.sessionId = sessionId;
        this.sessionPwd = sessionPwd;
        this.loginId = loginId;
        this.timeMs = timeMs;
        this.pubkeyNum = pubkeyNum;
        this.pushEndpoint = pushEndpoint;
        this.pushP256dhKey = pushP256dhKey;
        this.pushAuthKey = pushAuthKey;
    }

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

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public short getPubkeyNum() {
        return pubkeyNum;
    }

    public void setPubkeyNum(short pubkeyNum) {
        this.pubkeyNum = pubkeyNum;
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
