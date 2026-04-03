package server.logic.ws_protocol.JSON.handlers.system.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

/**
 * Ответ GetServerInfo:
 * {
 *   "op": "GetServerInfo",
 *   "requestId": "req-1",
 *   "status": 200,
 *   "payload": {
 *     "url": "...",
 *     "version": "...",
 *     "physicalRegion": "...",
 *     "description": "...",
 *     "origin": "...",
 *     "extraInfo": "..."
 *   }
 * }
 */
public class Net_GetServerInfo_Response extends Net_Response {

    private String url;
    private String version;
    private String physicalRegion;
    private String description;
    private String origin;
    private String extraInfo;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPhysicalRegion() { return physicalRegion; }
    public void setPhysicalRegion(String physicalRegion) { this.physicalRegion = physicalRegion; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(String extraInfo) { this.extraInfo = extraInfo; }
}
