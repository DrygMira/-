package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetResponse;

/**
 * Ответ на AuthSessionNewStep1.
 *
 * При успехе сервер возвращает временный секрет sessionPwd,
 * который клиент обязан использовать на втором шаге при формировании подписи.
 *
 * JSON:
 * {
 *   "op": "AuthSessionNewStep1",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionPwd": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class NetAuthSessionNewStep1Response extends NetResponse {

    /**
     * Временный секрет, сгенерированный сервером.
     * Строка — это base64-представление 32 случайных байт.
     */
    private String sessionPwd;

    public String getSessionPwd() {
        return sessionPwd;
    }
    public void setSessionPwd(String sessionPwd) {
        this.sessionPwd = sessionPwd;
    }
}
