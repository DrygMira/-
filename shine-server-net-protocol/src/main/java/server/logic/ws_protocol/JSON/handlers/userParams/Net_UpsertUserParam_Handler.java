package server.logic.ws_protocol.JSON.handlers.userParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.Net_Request;
import server.logic.ws_protocol.JSON.entyties.Net_Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Request;
import server.logic.ws_protocol.JSON.handlers.userParams.entyties.Net_UpsertUserParam_Response;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.SolanaUsersDAO;
import shine.db.dao.UserParamsDAO;
import shine.db.entities.SolanaUserEntry;
import shine.db.entities.UserParamEntry;
import utils.config.ShineSignatureConstants;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

/**
 * Net_UpsertUserParam_Handler
 *
 * Делает:
 *  1) Проверяет, что пользователь существует и что device_key действительно его.
 *  2) Проверяет, что нет "более нового" значения этого param (time_ms монотонно растёт).
 *  3) Проверяет подпись Ed25519 по device_key.
 *  4) Пишет в БД только если time_ms строго больше текущего сохранённого.
 *
 * БОЛЬШОЙ КОММЕНТ ПРО АВТОРИЗАЦИЮ НА БУДУЩЕЕ:
 * ---------------------------------------------------------------------------------
 * Сейчас (MVP) этот эндпоинт намеренно не делает полноценную "сессию/авторизацию",
 * потому что целостность обеспечивается криптографией: сервер проверяет подпись
 * и то, что device_key принадлежит login.
 *
 * В будущем, если понадобится "ограничить кто может писать параметры", можно добавить:
 *  - проверку активной сессии (active_sessions) и соответствие login в сессии;
 *  - rate-limit на пользователя;
 *  - отдельные права на запись конкретных param.
 *
 * Но возможно это вообще не потребуется, если модель безопасности строится
 * строго на подписи и владении device_key (как сейчас).
 * ---------------------------------------------------------------------------------
 */
public class Net_UpsertUserParam_Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(Net_UpsertUserParam_Handler.class);

    @Override
    public Net_Response handle(Net_Request baseRequest, ConnectionContext ctx) {
        Net_UpsertUserParam_Request req = (Net_UpsertUserParam_Request) baseRequest;

        if (req.getLogin() == null || req.getLogin().isBlank()
                || req.getParam() == null || req.getParam().isBlank()
                || req.getTime_ms() == null || req.getTime_ms() <= 0
                || req.getValue() == null
                || req.getDevice_key() == null || req.getDevice_key().isBlank()
                || req.getSignature() == null || req.getSignature().isBlank()) {

            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_FIELDS",
                    "Некорректные поля: login/param/time_ms/value/device_key/signature"
            );
        }

        final String login = req.getLogin().trim();
        final String param = req.getParam().trim();
        final long timeMs = req.getTime_ms();
        final String value = req.getValue();
        final String deviceKeyB64 = req.getDevice_key().trim();
        final String signatureB64 = req.getSignature().trim();

        try {
            byte[] pubKey32;
            byte[] sig64;
            try {
                pubKey32 = Base64.getDecoder().decode(deviceKeyB64);
                sig64 = Base64.getDecoder().decode(signatureB64);
            } catch (IllegalArgumentException e) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_BASE64",
                        "device_key/signature должны быть Base64"
                );
            }

            if (pubKey32.length != 32) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_DEVICE_KEY",
                        "device_key должен быть Base64(32 bytes)"
                );
            }
            if (sig64.length != 64) {
                return NetExceptionResponseFactory.error(
                        req,
                        WireCodes.Status.BAD_REQUEST,
                        "BAD_SIGNATURE",
                        "signature должна быть Base64(64 bytes)"
                );
            }

            String signText = ShineSignatureConstants.USER_PARAMETER_PREFIX
                    + login
                    + param
                    + timeMs
                    + value;

            byte[] signBytes = signText.getBytes(StandardCharsets.UTF_8);

            boolean sigOk = Ed25519Util.verify(signBytes, sig64, pubKey32);
            if (!sigOk) {
                return NetExceptionResponseFactory.error(
                        req,
                        403,
                        "SIGNATURE_INVALID",
                        "Подпись не прошла проверку"
                );
            }

            SqliteDbController db = SqliteDbController.getInstance();
            SolanaUsersDAO usersDAO = SolanaUsersDAO.getInstance();
            UserParamsDAO paramsDAO = UserParamsDAO.getInstance();

            try (Connection c = db.getConnection()) {
                boolean oldAuto = c.getAutoCommit();
                c.setAutoCommit(false);

                try (Statement st = c.createStatement()) {
                    st.execute("BEGIN IMMEDIATE");
                }

                try {
                    SolanaUserEntry user = usersDAO.getByLogin(c, login);
                    if (user == null) {
                        c.rollback();
                        return NetExceptionResponseFactory.error(
                                req,
                                404,
                                "USER_NOT_FOUND",
                                "Пользователь не найден"
                        );
                    }

                    String userDeviceKey = user.getDeviceKey();
                    if (userDeviceKey == null || userDeviceKey.isBlank()) {
                        c.rollback();
                        return NetExceptionResponseFactory.error(
                                req,
                                WireCodes.Status.SERVER_DATA_ERROR,
                                "USER_DEVICE_KEY_EMPTY",
                                "У пользователя не задан deviceKey в БД"
                        );
                    }

                    if (!userDeviceKey.trim().equals(deviceKeyB64)) {
                        c.rollback();
                        return NetExceptionResponseFactory.error(
                                req,
                                403,
                                "DEVICE_KEY_MISMATCH",
                                "device_key не соответствует пользователю"
                        );
                    }

                    UserParamEntry existing = paramsDAO.getByLoginAndParam(c, login, param);

                    // если есть более новое — запрет
                    if (existing != null && existing.getTimeMs() > timeMs) {
                        c.rollback();
                        return NetExceptionResponseFactory.error(
                                req,
                                409,
                                "PARAM_NEWER_EXISTS",
                                "Уже есть более новое значение этого параметра (time_ms больше)"
                        );
                    }

                    // если time_ms равен — ничего не делаем (твой кейс)
                    if (existing != null && existing.getTimeMs() == timeMs) {
                        c.commit();
                        c.setAutoCommit(oldAuto);

                        Net_UpsertUserParam_Response resp = new Net_UpsertUserParam_Response();
                        resp.setOp(req.getOp());
                        resp.setRequestId(req.getRequestId());
                        resp.setStatus(WireCodes.Status.OK);

                        log.info("ℹ️ UpsertUserParam noop (same time_ms): login={}, param={}, time_ms={}",
                                login, param, timeMs);
                        return resp;
                    }

                    // иначе existing==null или existingTime < timeMs -> пишем
                    UserParamEntry e = new UserParamEntry(
                            login,
                            param,
                            timeMs,
                            value,
                            deviceKeyB64,
                            signatureB64
                    );

                    paramsDAO.upsert(c, e);

                    c.commit();
                    c.setAutoCommit(oldAuto);

                    Net_UpsertUserParam_Response resp = new Net_UpsertUserParam_Response();
                    resp.setOp(req.getOp());
                    resp.setRequestId(req.getRequestId());
                    resp.setStatus(WireCodes.Status.OK);

                    log.info("✅ UpsertUserParam ok: login={}, param={}, time_ms={}", login, param, timeMs);
                    return resp;

                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(oldAuto);
                }
            }

        } catch (SQLException e) {
            log.error("❌ DB error UpsertUserParam", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR",
                    "Ошибка БД"
            );
        } catch (Exception e) {
            log.error("❌ Internal error UpsertUserParam", e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.INTERNAL_ERROR,
                    "INTERNAL_ERROR",
                    "Внутренняя ошибка сервера"
            );
        }
    }
}