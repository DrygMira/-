package server.logic.ws_protocol.JSON.handlers.userParams.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос ListUserParams — получить все сохранённые параметры пользователя.
 *
 * {
 *   "op": "ListUserParams",
 *   "requestId": "req-2",
 *   "payload": {
 *     "login": "anya"
 *   }
 * }
 *
 * ПРО ДОСТУП (на будущее):
 * ---------------------------------------------------------------------------------
 * Сейчас (MVP) запрос не ограничивает просмотр параметров.
 * В будущем, вероятно, потребуется проверка сессии/прав: кто может читать параметры.
 * Для MVP эти проверки не нужны.
 * ---------------------------------------------------------------------------------
 */
public class Net_ListUserParams_Request extends Net_Request {

    private String login;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
}