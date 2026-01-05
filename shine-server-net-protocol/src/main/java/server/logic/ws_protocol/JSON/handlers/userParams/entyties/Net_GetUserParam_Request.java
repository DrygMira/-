package server.logic.ws_protocol.JSON.handlers.userParams.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос GetUserParam — получить один параметр пользователя.
 *
 * {
 *   "op": "GetUserParam",
 *   "requestId": "req-1",
 *   "payload": {
 *     "login": "anya",
 *     "param": "feed:lastSeenGlobal"
 *   }
 * }
 *
 * ПРО ДОСТУП (на будущее):
 * ---------------------------------------------------------------------------------
 * Сейчас (MVP) этот запрос не ограничивает просмотр параметров, т.к. проект в тестовом режиме.
 * Позже, вероятно, потребуется ограничить: кто и какие параметры может читать (сессия/права).
 * Но для MVP эти проверки не нужны.
 * ---------------------------------------------------------------------------------
 */
public class Net_GetUserParam_Request extends Net_Request {

    private String login;
    private String param;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }
}