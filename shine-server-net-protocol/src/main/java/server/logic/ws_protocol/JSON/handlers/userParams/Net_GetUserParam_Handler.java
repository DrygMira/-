package server.logic.ws_protocol.JSON.handlers.userParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_GetUserParam_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_GetUserParam_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.UserParamsDAO;
import shine.db.entities.UserParamEntry;

import java.sql.Connection;

/**
 * GetUserParam — получить один параметр пользователя.
 *
 * ПРО ДОСТУП (на будущее):
 * ---------------------------------------------------------------------------------
 * Сейчас (MVP) запрос не ограничивает просмотр параметров.
 * В будущем, вероятно, потребуется проверка сессии/прав: кто может читать параметры.
 * Для MVP эти проверки не нужны.
 * ---------------------------------------------------------------------------------
 */
public class Net_GetUserParam_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_GetUserParam_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_GetUserParam_Request req = (Net_GetUserParam_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getParam() == null || req.getParam().isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login/param"
            );
        }

        String login = req.getLogin().trim();
        String param = req.getParam().trim();

        try {
            SqliteDbController db = SqliteDbController.getInstance();
            UserParamsDAO dao = UserParamsDAO.getInstance();

            try (Connection c = db.getConnection()) {
                UserParamEntry e = dao.getByLoginAndParam(c, login, param);

                if (e == null) {
                    Net_GetUserParam_Response resp = new Net_GetUserParam_Response();
                    resp.setOp(req.getOp());
                    resp.setRequestId(req.getRequestId());
                    resp.setStatus(404);
                    return resp;
                }

                Net_GetUserParam_Response resp = new Net_GetUserParam_Response();
                resp.setOp(req.getOp());
                resp.setRequestId(req.getRequestId());
                resp.setStatus(WireCodes.Status.OK);

                resp.setLogin(e.getLogin());
                resp.setParam(e.getParam());
                resp.setTime_ms(e.getTimeMs());
                resp.setValue(e.getValue());
                resp.setDevice_key(e.getDeviceKey());
                resp.setSignature(e.getSignature());

                return resp;
            }

        } catch (Exception e) {
            log.error("❌ Internal error GetUserParam", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}