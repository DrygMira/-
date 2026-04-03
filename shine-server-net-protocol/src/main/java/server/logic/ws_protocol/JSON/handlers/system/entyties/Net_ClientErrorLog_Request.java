package server.logic.ws_protocol.JSON.handlers.system.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Request;

/**
 * ClientErrorLog:
 * {
 *   "op": "ClientErrorLog",
 *   "requestId": "req-1",
 *   "payload": {
 *     "kind": "global_error",
 *     "message": "...",
 *     "stack": "...",
 *     "sourceUrl": "...",
 *     "lineNumber": 10,
 *     "columnNumber": 20,
 *     "route": "#/channel-view/own-0",
 *     "href": "https://example/#/channel-view/own-0",
 *     "userAgent": "...",
 *     "clientTs": 1700000000123,
 *     "requestOp": "GetChannelMessages",
 *     "requestIdRef": "GetChannelMessages-123",
 *     "contextJson": "{...}"
 *   }
 * }
 */
public class Net_ClientErrorLog_Request extends Net_Request {

    private String kind;
    private String message;
    private String stack;
    private String sourceUrl;
    private Integer lineNumber;
    private Integer columnNumber;
    private String route;
    private String href;
    private String userAgent;
    private Long clientTs;
    private String requestOp;
    private String requestIdRef;
    private String contextJson;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStack() { return stack; }
    public void setStack(String stack) { this.stack = stack; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public Integer getColumnNumber() { return columnNumber; }
    public void setColumnNumber(Integer columnNumber) { this.columnNumber = columnNumber; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getHref() { return href; }
    public void setHref(String href) { this.href = href; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Long getClientTs() { return clientTs; }
    public void setClientTs(Long clientTs) { this.clientTs = clientTs; }

    public String getRequestOp() { return requestOp; }
    public void setRequestOp(String requestOp) { this.requestOp = requestOp; }

    public String getRequestIdRef() { return requestIdRef; }
    public void setRequestIdRef(String requestIdRef) { this.requestIdRef = requestIdRef; }

    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
}
