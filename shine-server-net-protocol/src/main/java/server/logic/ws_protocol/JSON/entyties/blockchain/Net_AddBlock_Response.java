package server.logic.ws_protocol.JSON.entyties.blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Новый укороченный ответ:
 * - reasonCode (null если ok)
 * - serverLastGlobalNumber / serverLastGlobalHash
 */
public final class Net_AddBlock_Response extends Net_Response {

    private String reasonCode; // null если ok

    private int serverLastGlobalNumber;
    private String serverLastGlobalHash;

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public int getServerLastGlobalNumber() { return serverLastGlobalNumber; }
    public void setServerLastGlobalNumber(int v) { this.serverLastGlobalNumber = v; }

    public String getServerLastGlobalHash() { return serverLastGlobalHash; }
    public void setServerLastGlobalHash(String v) { this.serverLastGlobalHash = v; }
}