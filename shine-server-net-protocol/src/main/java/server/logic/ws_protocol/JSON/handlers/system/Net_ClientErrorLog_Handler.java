package server.logic.ws_protocol.JSON.handlers.system;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_ClientErrorLog_Request;
import server.logic.ws_protocol.JSON.handlers.system.entyties.Net_ClientErrorLog_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;

import java.net.SocketAddress;

/**
 * ClientErrorLog — технический endpoint для фронтенд-ошибок.
 * Не требует авторизации: клиент должен иметь возможность отправить ошибку
 * даже если логин/сессия ещё не установлены.
 */
public class Net_ClientErrorLog_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_ClientErrorLog_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_ClientErrorLog_Request req = (Net_ClientErrorLog_Request) baseRequest;

        if (req.getMessage() == null || req.getMessage().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Поле message обязательно для ClientErrorLog"
            );
        }

        long serverTs = System.currentTimeMillis();
        String login = safe(ctx != null ? ctx.getLogin() : null);
        String sessionId = safe(ctx != null ? ctx.getSessionId() : null);
        String remote = safe(remoteAddress(ctx));

        log.error(
                "CLIENT_FRONTEND_ERROR kind={} clientTs={} serverTs={} login={} sessionId={} remote={} route={} href={} sourceUrl={} line={} column={} requestOp={} requestIdRef={} message={} userAgent={} context={}",
                clip(req.getKind(), 64),
                req.getClientTs(),
                serverTs,
                clip(login, 64),
                clip(sessionId, 128),
                clip(remote, 128),
                clip(req.getRoute(), 200),
                clip(req.getHref(), 240),
                clip(req.getSourceUrl(), 240),
                req.getLineNumber(),
                req.getColumnNumber(),
                clip(req.getRequestOp(), 64),
                clip(req.getRequestIdRef(), 128),
                clip(req.getMessage(), 500),
                clip(req.getUserAgent(), 240),
                clip(req.getContextJson(), 2000)
        );

        if (req.getStack() != null && !req.getStack().isBlank()) {
            log.error("CLIENT_FRONTEND_ERROR_STACK requestId={} stack={}",
                    clip(req.getRequestId(), 128),
                    clip(req.getStack(), 8000));
        }

        Net_ClientErrorLog_Response resp = new Net_ClientErrorLog_Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setAccepted(true);
        resp.setServerTs(serverTs);
        return resp;
    }

    private static String remoteAddress(ConnectionContext ctx) {
        if (ctx == null) return "";
        Session ws = ctx.getWsSession();
        if (ws == null) return "";
        SocketAddress remote = ws.getRemoteAddress();
        return remote != null ? remote.toString() : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String clip(String value, int maxLen) {
        String cleaned = safe(value)
                .replace('\n', ' ')
                .replace('\r', ' ');
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
