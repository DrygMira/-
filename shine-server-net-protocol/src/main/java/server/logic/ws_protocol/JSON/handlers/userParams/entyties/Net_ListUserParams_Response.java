package server.logic.ws_protocol.JSON.handlers.userParams.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ ListUserParams — список всех параметров пользователя.
 *
 * {
 *   "op": "ListUserParams",
 *   "requestId": "req-2",
 *   "status": 200,
 *   "payload": {
 *     "login": "anya",
 *     "params": [
 *       {
 *         "login": "anya",
 *         "param": "feed:lastSeenGlobal",
 *         "time_ms": 1736000000123,
 *         "value": "105",
 *         "device_key": "base64-32",
 *         "signature": "base64-64"
 *       },
 *       ...
 *     ]
 *   }
 * }
 */
public class Net_ListUserParams_Response extends Net_Response {

    private String login;
    private List<Item> params = new ArrayList<>();

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public List<Item> getParams() { return params; }
    public void setParams(List<Item> params) { this.params = params; }

    public static class Item {
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
}