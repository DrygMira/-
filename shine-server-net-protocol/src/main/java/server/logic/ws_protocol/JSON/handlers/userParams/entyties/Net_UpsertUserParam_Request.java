package server.logic.ws_protocol.JSON.handlers.userParams.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * Запрос UpsertUserParam — добавить/обновить сохранённый параметр пользователя.
 *
 * Клиент отправляет:
 *
 * {
 *   "op": "UpsertUserParam",
 *   "requestId": "req-123",
 *   "payload": {
 *     "login": "anya",
 *     "param": "feed:lastSeenGlobal",
 *     "time_ms": 1736000000123,
 *     "value": "105",
 *     "device_key": "base64-ed25519-public-key-32",
 *     "signature": "base64-ed25519-signature-64"
 *   }
 * }
 *
 * Подпись считается от UTF-8 строки:
 *   USER_PARAMETER_PREFIX + login + param + time_ms + value
 */
public class Net_UpsertUserParam_Request extends Net_Request {

    private String login;
    private String param;
    private Long time_ms;
    private String value;

    private String device_key;
    private String signature;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    public Long getTime_ms() { return time_ms; }
    public void setTime_ms(Long time_ms) { this.time_ms = time_ms; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDevice_key() { return device_key; }
    public void setDevice_key(String device_key) { this.device_key = device_key; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}