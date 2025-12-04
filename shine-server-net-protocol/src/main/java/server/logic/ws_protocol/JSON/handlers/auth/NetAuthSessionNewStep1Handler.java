package server.logic.ws_protocol.JSON.handlers.auth;

import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.*;
import server.logic.ws_protocol.JSON.entyties.Auth.NetAuthSessionNewStep1Request;
import server.logic.ws_protocol.JSON.entyties.Auth.NetAuthSessionNewStep1Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.SolanaUser;

import java.security.SecureRandom;
import java.util.Map;

public class NetAuthSessionNewStep1Handler implements JsonMessageHandler {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public NetResponse handle(NetRequest baseReq, ConnectionContext ctx) throws Exception {

        NetAuthSessionNewStep1Request req = (NetAuthSessionNewStep1Request) baseReq;

        String login = req.getLogin();
        if (login == null || login.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_LOGIN",
                    "Пустой логин"
            );
        }

        // 1) Проверка: в контексте никто не авторизован
        if (ctx.getLogin() != null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "ALREADY_AUTHED",
                    "Попытка повторной авторификации для уже заданного login=" + ctx.getLogin()
            );
        }

        // 2) Ищем пользователя в локальной БД
        SolanaUser solanaUser = SolanaUsersDAO.getInstance().getByLogin(login);

        if (solanaUser == null) {
            // TODO позже — запрос в Solana, если не нашли локально
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "UNKNOWN_USER",
                    "Пользователь с таким логином не найден"
            );
        }

        // 3) Заполняем контекст полями пользователя
        ctx.setLogin(solanaUser.getLogin());
        ctx.setLoginId(solanaUser.getLoginId());
        ctx.setBchId(solanaUser.getBchId());
        ctx.setPubkey0(solanaUser.getPubkey0());
        ctx.setPubkey1(solanaUser.getPubkey1());
        ctx.setBchLimit(solanaUser.getBchLimit());

        // 4) Генерируем надёжный sessionPwd
        // SecureRandom + время → достаточно
        String sessionPwd = Long.toHexString(System.nanoTime()) +
                Long.toHexString(RANDOM.nextLong());

        ctx.setSessionPwd(sessionPwd);

        // 5) Формируем ответ
        NetAuthSessionNewStep1Response resp = new NetAuthSessionNewStep1Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setPayload(Map.of("sessionPwd", sessionPwd));

        return resp;
    }
}
