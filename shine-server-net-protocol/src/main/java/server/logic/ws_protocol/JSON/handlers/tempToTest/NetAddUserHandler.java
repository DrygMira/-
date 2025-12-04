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
 * Временный Хэндлер AddUser.              Используется для тестовой регистрации!!!!!!!!
 *
 * Логика:
 *  - берём login, loginId, bchId, pubkey0, pubkey1, bchLimit;
 *  - создаём SolanaUser и вставляем через SolanaUsersDAO;
 *  - если всё ОК → NetAddUserResponse со статусом 200;
 *  - если ошибка БД или некорректные данные → NetExceptionResponse.
 */
public class NetAddUserHandler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NetAddUserHandler.class);

    @Override
    public NetResponse handle(NetRequest baseRequest, ConnectionContext ctx) throws Exception {
        NetAddUserRequest req = (NetAddUserRequest) baseRequest;

        // Одна общая проверка всех ключевых полей
        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getPubkey0() == null || req.getPubkey0().isBlank()
                || req.getPubkey1() == null || req.getPubkey1().isBlank()
                || req.getBchLimit() == null) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные или пустые поля: login, pubkey0, pubkey1, bchLimit"
            );
        }

        try {
            SolanaUsersDAO dao = SolanaUsersDAO.getInstance();

            SolanaUser user = new SolanaUser(
                    req.getLoginId(),
                    req.getLogin(),
                    req.getBchId(),
                    req.getPubkey0(),
                    req.getPubkey1(),
                    req.getBchLimit()
            );

            dao.insert(user);

            NetAddUserResponse resp = new NetAddUserResponse();
            resp.setOp(req.getOp());
            resp.setRequestId(req.getRequestId());
            resp.setStatus(WireCodes.Status.OK);
            resp.setPayload(null); // можно поставить Map.of("ok", true)
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
