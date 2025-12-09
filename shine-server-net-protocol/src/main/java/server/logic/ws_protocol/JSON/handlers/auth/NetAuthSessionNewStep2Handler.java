package server.logic.ws_protocol.JSON.handlers.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.logic.ws_protocol.JSON.ActiveConnectionsRegistry;
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
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шаг 2 авторизации: проверка подписи и создание сессии.
 *
 * Клиент присылает в payload:
 *  - storagePwd    (base64 от 32 байт)
 *  - timeMs        (long, мс с 1970-01-01)
 *  - signatureB64  (подпись Ed25519 над строкой:
 *                   "AUTHORIFICATED:" + timeMs + sessionPwd)
 *
 * Параметр sessionPwd клиент получил на шаге 1.
 * Для проверки подписи используется pubkey1 (второй публичный ключ пользователя).
 *
 * Дополнительно:
 *  - timeMs должен отличаться от текущего времени сервера не более чем на 30 секунд.
 *
 * При успехе:
 *  - создаётся запись ActiveSession в БД;
 *  - генерируется sessionId (base64 от 32 случайных байт);
 *  - sessionCreatedAtMs и lastAuthirificatedAtMs = текущее время;
 *  - pushEndpoint / pushP256dhKey / pushAuthKey остаются пустыми;
 *  - возвращается sessionId в ответе.
 */
public class NetAuthSessionNewStep2Handler implements JsonMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(NetAuthSessionNewStep2Handler.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ALLOWED_SKEW_MS = 30_000L;

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
        Long loginId = user.getLoginId();
        if (loginId == null) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.SERVER_DATA_ERROR,
                    "NO_LOGIN_ID",
                    "Для пользователя не задан loginId в БД"
            );
        }

        String storagePwd = req.getStoragePwd();
        if (storagePwd == null || storagePwd.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "EMPTY_STORAGE_PWD",
                    "Пустой storagePwd"
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

        long timeMs = req.getTimeMs();
        long nowMs = System.currentTimeMillis();

        // Проверка, что время клиента не отличается от времени сервера больше чем на 30 секунд
        long diff = Math.abs(nowMs - timeMs);
        if (diff > ALLOWED_SKEW_MS) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "TIME_SKEW",
                    "Время клиента отличается от сервера более чем на 30 секунд"
            );
        }

        // --- выбираем публичный ключ pubkey1 ---
        String pubKeyB64 = user.getDeviceKey();
        if (pubKeyB64 == null || pubKeyB64.isBlank()) {
            return NetExceptionResponseFactory.error(
                    req,
                    WireCodes.Status.BAD_REQUEST,
                    "NO_PUBKEY1",
                    "Отсутствует публичный ключ pubkey1 для пользователя"
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

        // --- собираем строку для подписи: "AUTHORIFICATED:" + timeMs + sessionPwd ---
        String preimageStr = "AUTHORIFICATED:" + timeMs + ctx.getSessionPwd();
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

        // --- создаём уникальный sessionId (base64 от 32 байт) и записываем в БД ---
        ActiveSessionsDAO dao = ActiveSessionsDAO.getInstance();
        String sessionId;
        ActiveSession activeSession;

        try {
            sessionId = generateRandomSessionId();
            long now = System.currentTimeMillis();

            activeSession = new ActiveSession(
                    sessionId,
                    loginId,
                    ctx.getSessionPwd(),
                    storagePwd,
                    now,
                    now,
                    null,   // pushEndpoint
                    null,   // pushP256dhKey
                    null    // pushAuthKey
            );

            dao.insert(activeSession);
        } catch (SQLException e) {
            log.error("Ошибка БД при создании новой сессии для loginId={}", loginId, e);
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

        // Регистрируем это подключение в глобальном реестре активных соединений
        ActiveConnectionsRegistry.getInstance().register(ctx);

        // --- формируем ответ ---
        NetAuthSessionNewStep2Response resp = new NetAuthSessionNewStep2Response();
        resp.setOp(req.getOp());
        resp.setRequestId(req.getRequestId());
        resp.setStatus(WireCodes.Status.OK);
        resp.setSessionId(sessionId);  // попадёт в payload.sessionId
        return resp;
    }

    /**
     * Генерация случайного sessionId: base64-строка от 32 байт.
     */
    private String generateRandomSessionId() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
