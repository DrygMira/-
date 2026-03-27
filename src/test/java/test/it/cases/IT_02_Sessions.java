package test.it.cases;

import test.it.utils.TestConfig;
import test.it.utils.TestIds;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestLog;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;
import utils.crypto.Ed25519Util;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_02_Sessions (v2)
 *
 * Цель:
 *  - проверить создание/листинг/вход-в-сессию(2 шага)/close
 *  - и после завершения оставить в БД 3 активных сессии (S1,S2,S3)
 *
 * Протокол v2:
 *  - создание сессии: AuthChallenge -> CreateAuthSession (deviceKey подпись, + deviceKey + sessionKey)
 *  - вход в сессию: SessionChallenge(sessionId) -> nonce, затем SessionLogin(sessionId,time,signature(sessionKey))
 *  - ListSessions и CloseActiveSession доступны только в AUTH_STATUS_USER (после SessionLogin)
 */
public class IT_02_Sessions {

    private static final String LOGIN = TestConfig.LOGIN();

    public static void main(String[] args) {
        TestLog.info("Standalone: этот тест требует заранее созданных пользователей -> сначала запускаю IT_01_AddUser");
        System.out.println(IT_01_AddUser.run());
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_02_Sessions(v2)");

        Duration t = Duration.ofSeconds(5);

        Session s1, s2, s3;

        try {
            // 1) Создаём 3 сессии (каждая — отдельным соединением)
            s1 = createSession(LOGIN, t, r, "S1");
            s2 = createSession(LOGIN, t, r, "S2");
            s3 = createSession(LOGIN, t, r, "S3");

            // 2) Входим в S1 (2 шага) и делаем ListSessions (AUTH_STATUS_USER) — должны быть S1,S2,S3
            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Login(S1)", r);

                String listResp = ws.call("ListSessions(AUTH_STATUS_USER)", JsonBuilders.listSessions(0L, ""), t);
                assertEquals(200, JsonParsers.status(listResp), "ListSessions(AUTH_STATUS_USER) must be 200");
                assertEquals(Boolean.TRUE, JsonParsers.ok(listResp), "ListSessions(AUTH_STATUS_USER) ok must be true");

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("ListSessions(AUTH_STATUS_USER): " + ids);

                assertTrue(ids.contains(s1.sessionId), "Must contain S1");
                assertTrue(ids.contains(s2.sessionId), "Must contain S2");
                assertTrue(ids.contains(s3.sessionId), "Must contain S3");
                r.ok("Проверка OK: список содержит S1,S2,S3");
            }

            // 3) Проверяем CloseActiveSession так, чтобы итогом всё равно осталось 3 сессии:
            //    создаём TEMP, логинимся в S1, закрываем TEMP, убеждаемся что S1,S2,S3 остались.
            Session temp = createSession(LOGIN, t, r, "TEMP");

            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Login(S1) for close", r);

                String closeResp = ws.call("CloseActiveSession(TEMP)", JsonBuilders.closeActiveSession(temp.sessionId, 0L, ""), t);
                assertEquals(200, JsonParsers.status(closeResp), "CloseActiveSession(TEMP) must be 200");
                r.ok("CloseActiveSession(TEMP): OK");
            }

            // 4) Финальная проверка: снова логинимся в S1 и ListSessions => S1,S2,S3 должны остаться, TEMP нет
            try (WsSession ws = WsSession.open()) {
                sessionLogin2Steps(ws, s1, t, "Final Login(S1)", r);

                String listResp = ws.call("ListSessions(final)", JsonBuilders.listSessions(0L, ""), t);
                assertEquals(200, JsonParsers.status(listResp));
                assertEquals(Boolean.TRUE, JsonParsers.ok(listResp));

                List<String> ids = JsonParsers.sessionIds(listResp);
                r.ok("Final ListSessions: " + ids);

                assertTrue(ids.contains(s1.sessionId));
                assertTrue(ids.contains(s2.sessionId));
                assertTrue(ids.contains(s3.sessionId));
                assertFalse(ids.contains(temp.sessionId));
                r.ok("ИТОГ OK: после теста в БД остались 3 активные сессии (S1,S2,S3)");
            }

            checkNegativeRequests(t, r, s1);

        } catch (Throwable e) {
            r.fail("IT_02_Sessions(v2) упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static Session createSession(String login, Duration t, TestResult r, String label) {
        try (WsSession ws = WsSession.open()) {

            // шаг 1: AuthChallenge
            String nonceResp = ws.call("AuthChallenge(" + label + ")", JsonBuilders.authChallenge(login), t);
            assertEquals(200, JsonParsers.status(nonceResp), "AuthChallenge(" + label + ") must be 200");
            assertEquals(Boolean.TRUE, JsonParsers.ok(nonceResp), "AuthChallenge(" + label + ") ok must be true");
            String authNonce = JsonParsers.authNonce(nonceResp);
            assertNotNull(authNonce, "authNonce must not be null for " + label);

            SessionMaterial sessionMaterial = newSessionMaterial();

            // storagePwd на клиенте (сохраняем, чтобы потом проверить, что сервер вернул именно его)
            String storagePwd = TestConfig.fakeStoragePwd();

            // шаг 2: CreateAuthSession (device подпись + deviceKey + sessionKey)
            String createResp = ws.call(
                    "CreateAuthSession(" + label + ")",
                    JsonBuilders.createAuthSessionV2(login, authNonce, storagePwd, sessionMaterial.sessionKey()),
                    t
            );
            assertEquals(200, JsonParsers.status(createResp), "CreateAuthSession(" + label + ") must be 200");
            assertEquals(Boolean.TRUE, JsonParsers.ok(createResp), "CreateAuthSession(" + label + ") ok must be true");

            String sid = JsonParsers.sessionId(createResp);
            assertNotNull(sid, "sessionId must not be null");

            r.ok("Создана сессия " + label + ": sessionId=" + sid);

            return new Session(sid, sessionMaterial.sessionKey(), sessionMaterial.sessionPrivKey(), storagePwd);
        }
    }

    private static void sessionLogin2Steps(WsSession ws, Session s, Duration t, String label, TestResult r) {
        // шаг 1: SessionChallenge(sessionId)
        String chResp = ws.call("SessionChallenge " + label, JsonBuilders.sessionChallenge(s.sessionId), t);
        assertEquals(200, JsonParsers.status(chResp), "SessionChallenge must be 200");
        assertEquals(Boolean.TRUE, JsonParsers.ok(chResp), "SessionChallenge ok must be true");
        String nonce = JsonParsers.sessionNonce(chResp);
        assertNotNull(nonce, "SessionChallenge nonce must not be null");

        // шаг 2: SessionLogin(sessionId, timeMs, signature(sessionKey, SESSION_LOGIN:...))
        String loginResp = ws.call("SessionLogin " + label, JsonBuilders.sessionLogin(s.sessionId, s.sessionKey, nonce, s.sessionPrivKey), t);
        assertEquals(200, JsonParsers.status(loginResp), "SessionLogin must be 200");
        assertEquals(Boolean.TRUE, JsonParsers.ok(loginResp), "SessionLogin ok must be true");

        String storagePwd = JsonParsers.storagePwd(loginResp);
        assertNotNull(storagePwd, "storagePwd must not be null after SessionLogin");
        assertEquals(s.storagePwd, storagePwd, "storagePwd must match what client provided on CreateAuthSession");

        r.ok(label + ": SessionLogin OK, storagePwd verified");
    }

    private static void checkNegativeRequests(Duration t, TestResult r, Session session) {
        try (WsSession ws = WsSession.open()) {
            String reqId = TestIds.next("bad-authchallenge");
            String badReq = """
                    {
                      "op": "AuthChallenge",
                      "requestId": "%s",
                      "payload": {
                        "login": "NoSuchUser_123456"
                      }
                    }
                    """.formatted(reqId);
            String resp = ws.call("AuthChallenge#NEGATIVE", badReq, t);
            assertErrorFormat(resp, "AuthChallenge", reqId, "UNKNOWN_USER");
            r.ok("Negative AuthChallenge: error format OK");
        }

        try (WsSession ws = WsSession.open()) {
            String nonceResp = ws.call("AuthChallenge(NEG_CREATE)", JsonBuilders.authChallenge(LOGIN), t);
            assertEquals(200, JsonParsers.status(nonceResp));
            String authNonce = JsonParsers.authNonce(nonceResp);

            SessionMaterial badSession = newSessionMaterial();
            String reqId = TestIds.next("bad-create");
            String badCreate = """
                    {
                      "op": "CreateAuthSession",
                      "requestId": "%s",
                      "payload": {
                        "login": "%s",
                        "sessionKey": "%s",
                        "storagePwd": "%s",
                        "timeMs": %d,
                        "authNonce": "%s",
                        "deviceKey": "%s",
                        "signatureB64": "%s",
                        "clientInfo": "%s"
                      }
                    }
                    """.formatted(
                    reqId,
                    LOGIN,
                    badSession.sessionKey(),
                    TestConfig.fakeStoragePwd(),
                    System.currentTimeMillis(),
                    authNonce,
                    "WRONG_DEVICE_KEY",
                    "AAAA",
                    TestConfig.TEST_CLIENT_INFO
            );
            String resp = ws.call("CreateAuthSession#NEGATIVE", badCreate, t);
            assertErrorFormat(resp, "CreateAuthSession", reqId, "DEVICE_KEY_NOT_ACTUAL");
            r.ok("Negative CreateAuthSession: error format OK");
        }

        try (WsSession ws = WsSession.open()) {
            String reqId = TestIds.next("bad-sessionchallenge");
            String badReq = """
                    {
                      "op": "SessionChallenge",
                      "requestId": "%s",
                      "payload": {
                        "sessionId": "missing-session-id"
                      }
                    }
                    """.formatted(reqId);
            String resp = ws.call("SessionChallenge#NEGATIVE", badReq, t);
            assertErrorFormat(resp, "SessionChallenge", reqId, "SESSION_NOT_FOUND");
            r.ok("Negative SessionChallenge: error format OK");
        }

        try (WsSession ws = WsSession.open()) {
            String chResp = ws.call("SessionChallenge NEG_LOGIN", JsonBuilders.sessionChallenge(session.sessionId), t);
            assertEquals(200, JsonParsers.status(chResp));
            String nonce = JsonParsers.sessionNonce(chResp);

            SessionMaterial wrongSession = newSessionMaterial();
            long timeMs = System.currentTimeMillis();
            String signatureB64 = JsonBuilders.signSessionLogin(session.sessionId, timeMs, nonce, wrongSession.sessionPrivKey());
            String reqId = TestIds.next("bad-sessionlogin");
            String badLoginReq = """
                    {
                      "op": "SessionLogin",
                      "requestId": "%s",
                      "payload": {
                        "sessionId": "%s",
                        "sessionKey": "%s",
                        "timeMs": %d,
                        "signatureB64": "%s",
                        "clientInfo": "%s"
                      }
                    }
                    """.formatted(
                    reqId,
                    session.sessionId,
                    wrongSession.sessionKey(),
                    timeMs,
                    signatureB64,
                    TestConfig.TEST_CLIENT_INFO
            );
            String badLoginResp = ws.call("SessionLogin#NEGATIVE", badLoginReq, t);
            assertErrorFormat(
                    badLoginResp,
                    "SessionLogin",
                    reqId,
                    "SESSION_KEY_NOT_ACTUAL"
            );
            r.ok("Negative SessionLogin: error format OK");
        }
    }

    private static void assertErrorFormat(String resp, String op, String requestId, String code) {
        int status = JsonParsers.status(resp);
        assertFalse(status >= 200 && status < 300, "Expected non-2xx status: " + resp);
        assertEquals(Boolean.FALSE, JsonParsers.ok(resp), "Expected ok=false: " + resp);
        assertEquals(op, JsonParsers.op(resp), "Unexpected op: " + resp);
        assertEquals(requestId, JsonParsers.requestId(resp), "Unexpected requestId: " + resp);
        assertEquals(code, JsonParsers.errorCode(resp), "Unexpected error code: " + resp);
        assertTrue(JsonParsers.payloadIsObject(resp), "payload must be object: " + resp);
        assertEquals(0, JsonParsers.payloadSize(resp), "error payload must be empty object: " + resp);
        assertNotNull(JsonParsers.message(resp), "message must be present: " + resp);
    }

    private static SessionMaterial newSessionMaterial() {
        byte[] sessionPrivKey = Ed25519Util.generatePrivateKey();
        byte[] sessionPubKey = Ed25519Util.derivePublicKey(sessionPrivKey);
        String sessionKey = "ed25519/" + Base64.getEncoder().encodeToString(sessionPubKey);
        return new SessionMaterial(sessionKey, sessionPrivKey);
    }

    private record Session(String sessionId, String sessionKey, byte[] sessionPrivKey, String storagePwd) {}
    private record SessionMaterial(String sessionKey, byte[] sessionPrivKey) {}
}
