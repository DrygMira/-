package server.logic.ws_protocol.JSON.handlers.connections.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос GetFriendsLists — получить два списка "друзей" по connections_state.
 *
 * {
 *   "op": "GetFriendsLists",
 *   "requestId": "req-100",
 *   "payload": {
 *     "login": "anya"
 *   }
 * }
 *
 * Возвращает:
 *  - out_friends: кому login поставил FRIEND
 *  - in_friends: кто поставил FRIEND этому login
 *
 * ПРО ДОСТУП (на будущее):
 * Сейчас (MVP) без ограничений. Позже можно ограничить видимость связей.
 */
public class Net_GetFriendsLists_Request extends Net_Request {

    private String login;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
}