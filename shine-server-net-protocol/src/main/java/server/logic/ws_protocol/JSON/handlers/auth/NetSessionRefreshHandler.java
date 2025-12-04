package server.logic.ws_protocol.JSON.handlers.auth;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.entyties.Auth.NetSessionRefreshRequest;
import server.logic.ws_protocol.JSON.entyties.Auth.NetSessionRefreshResponse;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSession;

import java.sql.SQLException;

/**
 * Хэндлер SessionRefresh.
 *
 * Логика:
 *  - берём sessionId и sessionPwd из запроса;
 *  - ищем сессию в БД;
 *  - если не нашли или пароль не совпал → NetExceptionResponse;
 *  - если всё ок:
 *      * обновляем ConnectionContext (sessionId, sessionPwd, статус USER);
 *      * возвращаем NetSessionRefreshResponse со статусом 200.
 */
public class NetSessionRefreshHandler implements JsonMessageHandler {

    @Override
    public NetResponse handle(NetRequest request, ConnectionContext ctx) throws Exception {
        NetSessionRefreshRequest req = (NetSessionRefreshRequest) request;

        long sessionId = req.getSessionId();
        String sessionPwd = req.getSessionPwd();

        if (sessionPwd == null || sessionPwd.isEmpty()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SESSION_PWD",
                    "Пустой пароль сессии"
            );
        }

        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        ActiveSession session;
        try {
            session = dao.getBySessionId(sessionId);
        } catch (SQLException e) {
            // Ошибка БД → внутренняя ошибка сервера
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        }

        if (session == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_NOT_FOUND",
                    "Сессия не найдена"
            );
        }

        String dbPwd = session.getSessionPwd();
        if (dbPwd == null || !dbPwd.equals(sessionPwd)) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "SESSION_PWD_MISMATCH",
                    "Неверный пароль сессии"
            );
        }

        // Всё хорошо — обновляем контекст соединения
        if (ctx != null) {
            ctx.setSessionId(sessionId);
            ctx.setSessionPwd(sessionPwd);
            // Если потом добавишь в ActiveSession login / loginId — можно здесь и их проставлять
            ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);
        }

        // И возвращаем OK без доп. данных
        NetSessionRefreshResponse resp = new NetSessionRefreshResponse();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setPayload(null); // или Map.of("ok", true)
        return resp;
    }
}
