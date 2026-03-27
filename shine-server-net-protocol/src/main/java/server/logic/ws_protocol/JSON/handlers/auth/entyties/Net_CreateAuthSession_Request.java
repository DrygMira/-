package server.logic.ws_protocol.JSON.handlers.auth.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Шаг 2 (v2): создание новой сессии ТОЛЬКО через deviceKey.
 *
 * Шаги:
 *  1) AuthChallenge(login) -> authNonce
 *  2) CreateAuthSession(login, sessionKey, storagePwd, timeMs, authNonce, deviceKey, signatureB64, clientInfo)
 *
 * Подпись deviceKey делается над строкой (UTF-8):
 *   AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
 *
 * Важно:
 * - sessionKey генерируется на клиенте и передаётся на сервер целиком одной строкой.
 * - В БД active_sessions.session_key хранится sessionKey целиком одной строкой.
 */
public class Net_CreateAuthSession_Request extends Net_Request {

    private String login;

    /** Клиентский пароль для хранения данных (base64 от 32 байт). */
    private String storagePwd;

    /** Публичный ключ сессии в API-формате, например ed25519/BASE64_PUBLIC_KEY. */
    private String sessionKey;

    /** Время на стороне клиента (мс с 1970-01-01). */
    private long timeMs;

    /** Nonce из AuthChallenge. */
    private String authNonce;

    /** Публичный ключ устройства пользователя. */
    private String deviceKey;

    /** Подпись Ed25519(deviceKey) над строкой AUTH_CREATE_SESSION:... (base64). */
    private String signatureB64;

    /** Краткая строка от клиента (до 50 символов) с описанием устройства/клиента. */
    private String clientInfo;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public String getAuthNonce() {
        return authNonce;
    }

    public void setAuthNonce(String authNonce) {
        this.authNonce = authNonce;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public String getSignatureB64() {
        return signatureB64;
    }

    public void setSignatureB64(String signatureB64) {
        this.signatureB64 = signatureB64;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }
}
