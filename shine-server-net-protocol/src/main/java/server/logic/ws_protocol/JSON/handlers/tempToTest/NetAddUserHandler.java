package server.logic.ws_protocol.JSON.handlers.tempToTest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.entyties.tempToTest.NetAddUserRequest;
import server.logic.ws_protocol.JSON.entyties.tempToTest.NetAddUserResponse;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUser;

import java.sql.SQLException;

/**
 * Временный хэндлер AddUser (тестовая регистрация локального пользователя).
 *
 * Ожидаемый запрос (все поля в payload):
 * {
 *   "op": "AddUser",
 *   "requestId": "...",
 *   "payload": {
 *     "login": "anya",
 *     "loginId": 100211,
 *     "bchId": 4222,
 *     "loginKey": "base64-pubkey-login",
 *     "deviceKey": "base64-pubkey-device",
 *     "bchLimit": 1000000
 *   }
 * }
 *
 * При успехе:
 *  - пользователь сохраняется в таблицу solana_users;
 *  - возвращается status=200 и пустой payload.
 */
public class NetAddUserHandler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NetAddUserHandler.class);

    @Override
    public NetResponse handle(NetRequest baseRequest, ConnectionContext ctx) throws Exception {
        NetAddUserRequest req = (NetAddUserRequest) baseRequest;

        // Одна общая проверка всех ключевых полей
        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getLoginKey() == null || req.getLoginKey().isBlank()
                || req.getDeviceKey() == null || req.getDeviceKey().isBlank()
                || req.getBchLimit() == null) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные или пустые поля: login, loginKey, deviceKey, bchLimit"
            );
        }

        try {
            SolanaUsersDAO dao = SolanaUsersDAO.getInstance();

            SolanaUser user = new SolanaUser(
                    req.getLoginId(),
                    req.getLogin(),
                    req.getBchId(),
                    req.getLoginKey(),
                    req.getDeviceKey(),
                    req.getBchLimit()
            );

            dao.insert(user);

            NetAddUserResponse resp = new NetAddUserResponse();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            // payload станет {} через JsonInboundProcessor
            log.info("✅ Пользователь добавлен: login={}, loginId={}", req.getLogin(), req.getLoginId());
            return resp;

        } catch (SQLException e) {
            log.error("❌ Ошибка при вставке пользователя в БД", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка доступа к базе данных"
            );
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка в AddUser", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}
