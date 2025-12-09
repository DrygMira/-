package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.entyties.Auth.NetSessionRefreshRequest;
import server.logic.ws_protocol.JSON.entyties.Auth.NetSessionRefreshResponse;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.ActiveSession;
import shine.db.entities.SolanaUser;

import java.sql.SQLException;

/**
 * Хэндлер SessionRefresh.
 *
 * При успешной проверке sessionId + sessionPwd:
 *  - подтягивает пользователя по loginId из сессии;
 *  - заполняет ConnectionContext;
 *  - обновляет lastAuthirificatedAtMs в БД на текущее время;
 *  - возвращает storagePwd в payload.
 */
public class NetSessionRefreshHandler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NetSessionRefreshHandler.class);

    @Override
    public NetResponse handle(NetRequest request, ConnectionContext ctx) throws Exception {
        NetSessionRefreshRequest req = (NetSessionRefreshRequest) request;

        String sessionId = req.getSessionId();
        String sessionPwd = req.getSessionPwd();

        if (sessionId == null || sessionId.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SESSION_ID",
                    "Пустой идентификатор сессии"
            );
        }

        if (sessionPwd == null || sessionPwd.isEmpty()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SESSION_PWD",
                    "Пустой пароль сессии"
            );
        }

        ActiveSessionsDAO sessionsDao = ActiveSessionsDAO.getInstance();
        ActiveSession session;
        try {
            session = sessionsDao.getBySessionId(sessionId);
        } catch (SQLException e) {
            log.error("Ошибка БД при поиске сессии sessionId={}", sessionId, e);
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

        // --- достаём пользователя по loginId из сессии ---
        SolanaUser solanaUser = null;
        long loginId = session.getLoginId();
        try {
            SolanaUsersDAO usersDao = SolanaUsersDAO.getInstance();
            solanaUser = usersDao.getByLoginId(loginId);
        } catch (SQLException e) {
            log.error("Ошибка БД при поиске пользователя по loginId={} из сессии", loginId, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_USER_LOOKUP",
                    "Ошибка доступа к базе данных при получении пользователя для сессии"
            );
        }

        if (solanaUser == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "USER_NOT_FOUND_FOR_SESSION",
                    "Пользователь для данной сессии не найден"
            );
        }

        // Всё хорошо — обновляем контекст соединения
        if (ctx != null) {
            ctx.setActiveSession(session);
            ctx.setSolanaUser(solanaUser);
            ctx.setSessionId(sessionId);
            ctx.setSessionPwd(sessionPwd);
            ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

            // Регистрируем это подключение в глобальном реестре активных соединений
            ActiveConnectionsRegistry.getInstance().register(ctx);
        }

        // Обновляем lastAuthirificatedAtMs в БД
        try {
            long nowMs = System.currentTimeMillis();
            sessionsDao.updateLastAuthirificatedAtMs(sessionId, nowMs);
        } catch (SQLException e) {
            log.error("Ошибка БД при обновлении lastAuthirificatedAtMs для sessionId={}", sessionId, e);
        }

        // Возвращаем OK + storagePwd
        NetSessionRefreshResponse resp = new NetSessionRefreshResponse();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setStoragePwd(session.getStoragePwd());
        return resp;
    }
}
