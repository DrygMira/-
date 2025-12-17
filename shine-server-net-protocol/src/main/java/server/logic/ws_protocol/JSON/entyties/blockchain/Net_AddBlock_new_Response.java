package server.logic.ws_protocol.JSON.entyties.blockchain;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

public final class Net_AddBlock_new_Response extends Net_Response {

    private String reasonCode;               // null если ok

    private int serverLastGlobalNumber;
    private String serverLastGlobalHash;

    private int serverLastLineNumber;        // для линии блока
    private String serverLastLineHash;

    private int lineIndex;                   // какую линию сервер применил (из блока)

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public int getServerLastGlobalNumber() { return serverLastGlobalNumber; }
    public void setServerLastGlobalNumber(int v) { this.serverLastGlobalNumber = v; }

    public String getServerLastGlobalHash() { return serverLastGlobalHash; }
    public void setServerLastGlobalHash(String v) { this.serverLastGlobalHash = v; }

    public int getServerLastLineNumber() { return serverLastLineNumber; }
    public void setServerLastLineNumber(int v) { this.serverLastLineNumber = v; }

    public String getServerLastLineHash() { return serverLastLineHash; }
    public void setServerLastLineHash(String v) { this.serverLastLineHash = v; }

    public int getLineIndex() { return lineIndex; }
    public void setLineIndex(int lineIndex) { this.lineIndex = lineIndex; }
}
