package server.logic.ws_protocol.JSON.entyties.tempToTest;

import server.logic.ws_protocol.JSON.entyties.NetRequest;

/**
 * Запрос AddUser.
 *
 * Ожидаемый JSON:
 * {
 *   "op": "AddUser",
 *   "requestId": "...",
 *   "login": "...",
 *   "loginId": 123,
 *   "bchId": 456,
 *   "pubkey0": "...",
 *   "pubkey1": "...",
 *   "bchLimit": 1000
 * }
 */
public class NetAddUserRequest extends NetRequest {

    private String login;
    private long loginId;
    private long bchId;
    private String pubkey0;
    private String pubkey1;
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

    public String getPubkey0() {
        return pubkey0;
    }

    public void setPubkey0(String pubkey0) {
        this.pubkey0 = pubkey0;
    }

    public String getPubkey1() {
        return pubkey1;
    }

    public void setPubkey1(String pubkey1) {
        this.pubkey1 = pubkey1;
    }

    public Integer getBchLimit() {
        return bchLimit;
    }

    public void setBchLimit(Integer bchLimit) {
        this.bchLimit = bchLimit;
    }
}
