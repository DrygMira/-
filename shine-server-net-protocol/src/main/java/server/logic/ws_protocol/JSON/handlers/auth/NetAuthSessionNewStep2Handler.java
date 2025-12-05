package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ConnectionContext;
import server.logic.ws_protocol.JSON.entyties.NetRequest;
import server.logic.ws_protocol.JSON.entyties.NetResponse;
import server.logic.ws_protocol.JSON.entyties.Auth.NetAuthSessionNewStep2Request;
import server.logic.ws_protocol.JSON.entyties.Auth.NetAuthSessionNewStep2Response;
import server.logic.ws_protocol.JSON.handlers.JsonMessageHandler;
import server.logic.ws_protocol.JSON.utils.NetExceptionResponseFactory;
import server.logic.ws_protocol.WireCodes;
import shine.db.dao.ActiveSessionsDAO;
import shine.db.entities.ActiveSession;
import shine.db.entities.SolanaUser;
import utils.crypto.Ed25519Util;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шаг 2 авторизации: проверка подписи и создание сессии.
 *
 * Клиент присылает:
 *  - loginId
 *  - sigNum (0 или 1)
 *  - timeMs
 *  - signatureB64 от строки (loginId + timeMs + sessionPwd)
 */
public class NetAuthSessionNewStep2Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NetAuthSessionNewStep2Handler.class);

    @Override
    public NetResponse handle(NetRequest baseReq, ConnectionContext ctx) throws Exception {
        NetAuthSessionNewStep2Request req = (NetAuthSessionNewStep2Request) baseReq;

        // --- базовые проверки контекста ---
        if (ctx == null || ctx.getSolanaUser() == null || ctx.getSessionPwd() == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_STEP1_CONTEXT",
                    "Шаг 1 авторизации не был корректно выполнен для данного соединения"
            );
        }

        if (!ctx.isAnonymous()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "ALREADY_AUTHED",
                    "Пользователь уже авторизован по текущему соединению"
            );
        }

        SolanaUser user = ctx.getSolanaUser();
        long reqLoginId = req.getLoginId();
        Long ctxLoginId = user.getLoginId();

        if (ctxLoginId == null || ctxLoginId != reqLoginId) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "LOGIN_ID_MISMATCH",
                    "loginId в запросе не совпадает с пользователем из шага 1"
            );
        }

        int sigNum = req.getSigNum();
        if (sigNum != 0 && sigNum != 1) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_SIG_NUM",
                    "Номер подписи должен быть 0 или 1"
            );
        }

        String signatureB64 = req.getSignatureB64();
        if (signatureB64 == null || signatureB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_SIGNATURE",
                    "Пустая цифровая подпись"
            );
        }

        // --- выбираем публичный ключ по sigNum ---
        String pubKeyB64 = (sigNum == 0) ? user.getPubkey0() : user.getPubkey1();
        if (pubKeyB64 == null || pubKeyB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_PUBKEY",
                    "Отсутствует публичный ключ для выбранного номера подписи"
            );
        }

        byte[] publicKey32;
        byte[] signature64;
        try {
            publicKey32 = Ed25519Util.keyFromBase64(pubKeyB64);
            signature64 = Base64.getDecoder().decode(signatureB64);
        } catch (IllegalArgumentException ex) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "BAD_BASE64",
                    "Некорректный формат Base64 для ключа или подписи"
            );
        }

        // --- собираем строку для подписи: loginId + timeMs + sessionPwd ---
        long timeMs = req.getTimeMs();
        String preimageStr = String.valueOf(reqLoginId) + timeMs + ctx.getSessionPwd();
        byte[] preimage = preimageStr.getBytes(StandardCharsets.UTF_8);

        boolean sigOk = Ed25519Util.verify(preimage, signature64, publicKey32);
        if (!sigOk) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.UNVERIFIED,
                    "BAD_SIGNATURE",
                    "Подпись не прошла проверку"
            );
        }

        // --- создаём уникальный sessionId и записываем в БД ---
        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        long sessionId;
        ActiveSession activeSession;

        try {
            sessionId = generateUniqueSessionId(dao);
            long nowMs = System.currentTimeMillis();

            activeSession = new ActiveSession(
                    sessionId,
                    ctx.getSessionPwd(),
                    reqLoginId,
                    nowMs,
                    (short) sigNum, // pubkeyNum
                    null,           // pushEndpoint
                    null,           // pushP256dhKey
                    null            // pushAuthKey
            );

            dao.insert(activeSession);
        } catch (SQLException e) {
            log.error("Ошибка БД при создании новой сессии для loginId={}", reqLoginId, e);
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "DB_ERROR_SESSION_CREATE",
                    "Ошибка БД при создании сессии"
            );
        }

        // --- обновляем контекст ---
        ctx.setActiveSession(activeSession);
        ctx.setSessionId(sessionId);
        ctx.setAuthenticationStatus(ConnectionContext.AUTH_STATUS_USER);

        // --- формируем ответ ---
        NetAuthSessionNewStep2Response resp = new NetAuthSessionNewStep2Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessionId(sessionId);  // попадёт в payload.sessionId
        return resp;
    }

    /**
     * Генерация уникального sessionId с проверкой в БД.
     */
    private long generateUniqueSessionId(ActiveSessionsDAO dao) throws SQLException {
        for (int i = 0; i < 10; i++) {
            long candidate = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            ActiveSession existing = dao.getBySessionId(candidate);
            if (existing == null) {
                return candidate;
            }
        }
        throw new SQLException("Не удалось сгенерировать уникальный sessionId за разумное число попыток");
    }
}
