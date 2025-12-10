package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Шаг 2 авторизации: подтверждение владения ключом и установка сессии.
 *
 * Клиент:
 *  1) получает от сервера sessionPwd на шаге 1;
 *  2) генерирует свой StoragePwd (base64 от 32 байт);
 *  3) формирует строку для подписи:
 *       "AUTHORIFICATED:" + timeMs + sessionPwd
 *  4) подписывает эту строку своим приватным ключом (pubkey1),
 *     отправляет подпись и StoragePwd на сервер.
 *
 * Формат входящего JSON:
 * {
 *   "op": "CreateAuthSession",
 *   "requestId": "...",
 *   "payload": {
 *     "storagePwd": "base64-строка-от-32-байт",
 *     "timeMs": 1733310000000,
 *     "signatureB64": "base64-подпись-Ed25519"
 *   }
 * }
 *
 * При успешной проверке подписи сервер создаёт запись в active_sessions
 * и возвращает sessionId (base64-строка от 32 байт).
 */
public class Net_CreateAuthSession_Request extends NetRequest {

    /** Клиентский пароль для хранения данных (base64 от 32 байт). */
    private String storagePwd;

    /** Время на стороне клиента (мс с 1970-01-01). */
    private long timeMs;

    /** Подпись Ed25519 над строкой "AUTHORIFICATED:" + timeMs + sessionPwd (base64). */
    private String signatureB64;

    public String getStoragePwd() {
        return storagePwd;
    }

    public void setStoragePwd(String storagePwd) {
        this.storagePwd = storagePwd;
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
