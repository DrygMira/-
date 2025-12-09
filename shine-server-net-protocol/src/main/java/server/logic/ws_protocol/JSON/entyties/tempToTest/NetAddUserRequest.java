package server.logic.ws_protocol.JSON.entyties.tempToTest;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Запрос AddUser — временная/тестовая регистрация локального пользователя.
 *
 * Клиент отправляет:
 *
 * {
 *   "op": "AddUser",
 *   "requestId": "test-add-1",
 *   "payload": {
 *     "login": "anya",
 *     "loginId": 100211,
 *     "bchId": 4222,
 *     "loginKey": "base64-ed25519-public-key-login",
 *     "deviceKey": "base64-ed25519-public-key-device",
 *     "bchLimit": 1000000
 *   }
 * }
 *
 * Все поля лежат внутри payload.
 */
public class NetAddUserRequest extends NetRequest {

    private String login;
    private long loginId;
    private long bchId;
    private String loginKey;
    private String deviceKey;
    private Integer bchLimit;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public long getLoginId() {
        return loginId;
    }

    public void setLoginId(long loginId) {
        this.loginId = loginId;
    }

    public long getBchId() {
        return bchId;
    }

    public void setBchId(long bchId) {
        this.bchId = bchId;
    }

    public String getLoginKey() {
        return loginKey;
    }

    public void setLoginKey(String loginKey) {
        this.loginKey = loginKey;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public Integer getBchLimit() {
        return bchLimit;
    }

    public void setBchLimit(Integer bchLimit) {
        this.bchLimit = bchLimit;
    }
}
