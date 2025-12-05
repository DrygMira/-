package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Шаг 2 авторизации: клиент подтверждает владение ключом.
 *
 * JSON:
 * {
 *   "op": "AuthSessionNewStep2",
 *   "requestId": "...",
 *   "loginId": 100211,
 *   "sigNum": 0,                  // номер подписи: 0 или 1
 *   "timeMs": 1733310000000,      // время в миллисекундах с 1970-01-01
 *   "signatureB64": "..."         // подпись base64 от строки loginId+timeMs+sessionPwd
 * }
 */
public class NetAuthSessionNewStep2Request extends NetRequest {

    private long loginId;
    private int sigNum;        // 0 или 1
    private long timeMs;       // миллисекунды с 1970
    private String signatureB64;

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public int getSigNum() {
        return sigNum;
    }

    public void setSigNum(int sigNum) {
        this.sigNum = sigNum;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public String getSignatureB64() {
        return signatureB64;
    }

    public void setSignatureB64(String signatureB64) {
        this.signatureB64 = signatureB64;
    }
}
