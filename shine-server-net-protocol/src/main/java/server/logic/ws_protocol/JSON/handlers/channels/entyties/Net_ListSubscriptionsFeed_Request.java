package server.logic.ws_protocol.JSON.handlers.channels.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

public class Net_ListSubscriptionsFeed_Request extends Net_Request {
    private String login;
    private Integer limit;

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
