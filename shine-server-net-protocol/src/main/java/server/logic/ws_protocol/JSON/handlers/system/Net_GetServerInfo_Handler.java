package server.logic.ws_protocol.JSON.handlers.system;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_GetServerInfo_Request;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_GetServerInfo_Response;
import server.logic.ws_protocol.WireCodes;
import utils.config.AppConfig;

/**
 * GetServerInfo — технический запрос без авторизации.
 * Возвращает базовую публичную информацию о сервере, чтобы клиент
 * мог проверить доступность узла и показать его в списке серверов.
 */
public class Net_GetServerInfo_Handler implements JsonMessageHandler {

    private static final AppConfig CONFIG = AppConfig.getInstance();

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetServerInfo_Request req = (Net_GetServerInfo_Request) baseRequest;

        Net_GetServerInfo_Response resp = new Net_GetServerInfo_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setUrl(CONFIG.getStringOrEmpty("server.info.url"));
        resp.setVersion(CONFIG.getStringOrEmpty("server.version"));
        resp.setPhysicalRegion(CONFIG.getStringOrEmpty("server.info.physicalRegion"));
        resp.setDescription(CONFIG.getStringOrEmpty("server.info.description"));
        resp.setOrigin(CONFIG.getStringOrEmpty("server.info.origin"));
        resp.setExtraInfo(CONFIG.getStringOrEmpty("server.info.extraInfo"));

        return resp;
    }
}
