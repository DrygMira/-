package server.logic.ws_protocol.JSON.entyties.Auth;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Шаг 1 авторизации: запрос выдачи временного пароля сессии (sessionPwd).
 *
 * Клиент по логину просит сервер сгенерировать случайный секрет sessionPwd,
 * который будет использован на втором шаге при подписи.
 *
 * Формат входящего JSON:
 * {
 *   "op": "AuthChallenge",
 *   "requestId": "...",
 *   "payload": {
 *     "login": "someLogin"
 *   }
 * }
 *
 * Формат успешного ответа:
 * {
 *   "op": "AuthChallenge",
 *   "requestId": "...",
 *   "status": 200,
 *   "payload": {
 *     "sessionPwd": "base64-строка-от-32-байт"
 *   }
 * }
 */
public class Net_AuthChallenge_Request extends NetRequest {

    /**
     * Логин пользователя, для которого запускается авторизация.
     */
    private String login;

    public String getLogin() {
        return login;
    }
    public void setLogin(String login) {
        this.login = login;
    }
}
