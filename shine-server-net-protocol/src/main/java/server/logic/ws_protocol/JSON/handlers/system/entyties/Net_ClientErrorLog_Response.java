package server.logic.ws_protocol.JSON.handlers.system.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public class Net_ClientErrorLog_Response extends Net_Response {

    private long serverTs;
    private boolean accepted;

    public long getServerTs() { return serverTs; }
    public void setServerTs(long serverTs) { this.serverTs = serverTs; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
