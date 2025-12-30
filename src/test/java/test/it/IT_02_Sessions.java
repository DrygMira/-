package test.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.it.utils.*;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_02_Sessions
 *
 * Можно запускать:
 *  1) как JUnit тест (через Suite или выборочно)
 *  2) вручную как standalone:
 *     - main()
 *     - или через IT_RunAllMain / IT_RunAllCleanMain
 *
 * Главная цель:
 *  - иметь метод run() -> возвращает число не пройденных тестов (0 или 1)
 *  - и иметь main() для запуска одного теста
 */
public class IT_02_Sessions {

    public static void main(String[] args) {
        ItRunContext.initIfNeeded();
        int failed = run();
        System.exit(failed);
    }

    /** Запуск одного теста (standalone). Возвращает 0 если ок, 1 если упал. */
    public static int run() {
        return TestLog.runOne("IT_02_Sessions", IT_02_Sessions::testBodyStandalone);
    }

    @BeforeAll
    static void ensureUserExists() {
        ItRunContext.initIfNeeded();

        TestLog.title("SessionsIT (BeforeAll): предусловие — пользователь должен существовать (AddUser: 200 или 409)");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            String reqId = "it-adduser-beforeall";
            String reqJson = JsonBuilders.addUser(reqId);

            TestLog.send("AddUser(BeforeAll)", reqJson);
            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            TestLog.recv("AddUser(BeforeAll)", resp);

            int st = JsonParsers.status(resp);

            if (st == 200) {
                TestLog.ok("BeforeAll: пользователь создан/добавлен (status=200)");
            } else if (st == 409) {
                String code = JsonParsers.errorCode(resp);
                if ("USER_ALREADY_EXISTS".equals(code)) {
                    TestLog.ok("BeforeAll: пользователь уже есть (status=409, USER_ALREADY_EXISTS)");
                } else {
                    TestLog.boom("BeforeAll: status=409, но code неожиданный: " + code);
                    fail("User precondition failed. status=409, code=" + code + ", resp=" + resp);
                }
            } else {
                TestLog.boom("BeforeAll: предусловие не выполнено. status=" + st);
                fail("User precondition failed. status=" + st + ", resp=" + resp);
            }
        }
    }

//    @Test
    void sessions_flow_shouldCreateListRefreshCloseCorrectly() {
        // JUnit-режим: пусть падает через assert/fail как обычно
        testBodyJUnit();
    }

    /**
     * Standalone-режим: тут мы сами вызываем предусловие ensureUserExists(),
     * потому что @BeforeAll сработает только в JUnit.
     */
    private static void testBodyStandalone() {
        ensureUserExists();
        testBodyJUnit();
    }

    private static void testBodyJUnit() {
        ItRunContext.initIfNeeded();

        TestLog.titleBlock("""
                SessionsIT: полный сценарий сессий (создать 2, проверить list, refresh/close, проверить очистку)
                Используем:
                  login = %s
                Ожидание сценария:
                  1) Создаём SESSION1 через AuthChallenge + CreateAuthSession
                  2) Создаём SESSION2 и делаем ListSessions внутри неё (AUTH_STATUS_USER) → должны быть SESSION1 и SESSION2
                  3) Делаем ListSessions в AUTH_IN_PROGRESS (подпись по nonce) → должны быть SESSION1 и SESSION2
                  4) Refresh SESSION1 (входим в AUTH_STATUS_USER) и Close SESSION2
                  5) Проверяем ListSessions (AUTH_IN_PROGRESS) → осталась только SESSION1
                  6) Закрываем SESSION1 в AUTH_IN_PROGRESS
                  7) Проверяем ListSessions → пусто
                """.formatted(TestConfig.LOGIN()));

        String s1Id, s1Pwd;
        String s2Id, s2Pwd;

        // ===== helpers (локальные, чтобы не раздувать TestLog лишней логикой assert200) =====
        final java.util.function.BiConsumer<String, String> assert200 = (op, resp) -> {
            int st = JsonParsers.status(resp);
            assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
            TestLog.ok(op + ": status=200");
        };

        // ======================================================================

        TestLog.stepTitle("ШАГ 1: создать SESSION1 (AuthChallenge -> CreateAuthSession)");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-1";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge#1", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge#1", resp1);

            assert200.accept("AuthChallenge#1", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce, "AuthChallenge#1: nonce must not be null");
            TestLog.ok("AuthChallenge#1: authNonce получен: " + nonce);

            String r2 = "it-create-1";
            String storagePwd = TestConfig.fakeStoragePwd();
            String req2 = JsonBuilders.createAuthSession(r2, nonce, storagePwd);
            TestLog.send("CreateAuthSession#1", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("CreateAuthSession#1", resp2);

            assert200.accept("CreateAuthSession#1", resp2);

            s1Id = JsonParsers.sessionId(resp2);
            s1Pwd = JsonParsers.sessionPwd(resp2);
            assertNotNull(s1Id, "CreateAuthSession#1: sessionId must not be null");
            assertNotNull(s1Pwd, "CreateAuthSession#1: sessionPwd must not be null");
            TestLog.ok("SESSION1 получена: sessionId=" + s1Id + ", sessionPwd=[получен]");
        }

        TestLog.stepTitle("ШАГ 2: создать SESSION2 и ListSessions внутри неё (AUTH_STATUS_USER) → должны быть SESSION1+SESSION2");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-2";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge#2", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge#2", resp1);

            assert200.accept("AuthChallenge#2", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);
            TestLog.ok("AuthChallenge#2: authNonce получен: " + nonce);

            String r2 = "it-create-2";
            String req2 = JsonBuilders.createAuthSession(r2, nonce, TestConfig.fakeStoragePwd());
            TestLog.send("CreateAuthSession#2", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("CreateAuthSession#2", resp2);

            assert200.accept("CreateAuthSession#2", resp2);

            s2Id = JsonParsers.sessionId(resp2);
            s2Pwd = JsonParsers.sessionPwd(resp2);
            assertNotNull(s2Id);
            assertNotNull(s2Pwd);
            TestLog.ok("SESSION2 получена: sessionId=" + s2Id + ", sessionPwd=[получен]");

            String r3 = "it-list-in-session2";
            String req3 = JsonBuilders.listSessions(r3, 0L, "");
            TestLog.send("ListSessions(in SESSION2)", req3);
            String resp3 = c.request(r3, req3, Duration.ofSeconds(5));
            TestLog.recv("ListSessions(in SESSION2)", resp3);

            assert200.accept("ListSessions(in SESSION2)", resp3);
            List<String> ids = JsonParsers.sessionIds(resp3);
            TestLog.ok("ListSessions(in SESSION2): sessions=" + ids);

            assertTrue(ids.contains(s1Id), "Must contain session1");
            assertTrue(ids.contains(s2Id), "Must contain session2");
            TestLog.ok("Проверка OK: список содержит SESSION1 и SESSION2");
        }

        TestLog.stepTitle("ШАГ 3: ListSessions в AUTH_IN_PROGRESS (nonce+signature) → должны быть SESSION1+SESSION2");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge(list)", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge(list)", resp1);

            assert200.accept("AuthChallenge(list)", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);
            TestLog.ok("AuthChallenge(list): authNonce=" + nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);
            TestLog.ok("Подпись для AUTH_IN_PROGRESS: timeMs=" + timeMs + ", signatureB64=[сгенерирована]");

            String r2 = "it-list-auth-in-progress";
            String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
            TestLog.send("ListSessions(AUTH_IN_PROGRESS)", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("ListSessions(AUTH_IN_PROGRESS)", resp2);

            assert200.accept("ListSessions(AUTH_IN_PROGRESS)", resp2);

            List<String> ids = JsonParsers.sessionIds(resp2);
            TestLog.ok("ListSessions(AUTH_IN_PROGRESS): sessions=" + ids);

            assertTrue(ids.contains(s1Id));
            assertTrue(ids.contains(s2Id));
            TestLog.ok("Проверка OK: AUTH_IN_PROGRESS список содержит SESSION1 и SESSION2");
        }

        TestLog.stepTitle("ШАГ 4: Refresh SESSION1 (входим) и Close SESSION2 (из SESSION1)");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {

            String r1 = "it-refresh-s1";
            String req1 = JsonBuilders.refreshSession(r1, s1Id, s1Pwd);
            TestLog.send("RefreshSession(SESSION1)", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("RefreshSession(SESSION1)", resp1);

            assert200.accept("RefreshSession(SESSION1)", resp1);
            assertNotNull(JsonParsers.storagePwd(resp1));
            TestLog.ok("RefreshSession: storagePwd получен");

            String r2 = "it-close-s2";
            String req2 = JsonBuilders.closeActiveSession(r2, s2Id, 0L, "");
            TestLog.send("CloseActiveSession(SESSION2)", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("CloseActiveSession(SESSION2)", resp2);

            assert200.accept("CloseActiveSession(SESSION2)", resp2);
            TestLog.ok("SESSION2 закрыта");
        }

        TestLog.stepTitle("ШАГ 5: ListSessions(AUTH_IN_PROGRESS) → должна остаться только SESSION1");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list2";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge(list2)", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge(list2)", resp1);

            assert200.accept("AuthChallenge(list2)", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-list-after-close-s2";
            String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
            TestLog.send("ListSessions(after close S2)", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("ListSessions(after close S2)", resp2);

            assert200.accept("ListSessions(after close S2)", resp2);

            List<String> ids = JsonParsers.sessionIds(resp2);
            TestLog.ok("ListSessions(after close S2): sessions=" + ids);

            assertTrue(ids.contains(s1Id));
            assertFalse(ids.contains(s2Id));
            TestLog.ok("Проверка OK: осталась только SESSION1");
        }

        TestLog.stepTitle("ШАГ 6: Close SESSION1 в AUTH_IN_PROGRESS");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-close-s1";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge(close S1)", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge(close S1)", resp1);

            assert200.accept("AuthChallenge(close S1)", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-close-s1";
            String req2 = JsonBuilders.closeActiveSession(r2, s1Id, timeMs, sig);
            TestLog.send("CloseActiveSession(SESSION1)", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("CloseActiveSession(SESSION1)", resp2);

            assert200.accept("CloseActiveSession(SESSION1)", resp2);
            TestLog.ok("SESSION1 закрыта");
        }

        TestLog.stepTitle("ШАГ 7: ListSessions(AUTH_IN_PROGRESS) → ожидаем пустой список");
        try (WsTestClient c = new WsTestClient(TestConfig.WS_URI)) {
            String r1 = "it-auth-list-empty";
            String req1 = JsonBuilders.authChallenge(r1);
            TestLog.send("AuthChallenge(list empty)", req1);
            String resp1 = c.request(r1, req1, Duration.ofSeconds(5));
            TestLog.recv("AuthChallenge(list empty)", resp1);

            assert200.accept("AuthChallenge(list empty)", resp1);
            String nonce = JsonParsers.authNonce(resp1);
            assertNotNull(nonce);

            long timeMs = System.currentTimeMillis();
            String sig = JsonBuilders.signAuthorificated(nonce, timeMs);

            String r2 = "it-list-empty";
            String req2 = JsonBuilders.listSessions(r2, timeMs, sig);
            TestLog.send("ListSessions(empty)", req2);
            String resp2 = c.request(r2, req2, Duration.ofSeconds(5));
            TestLog.recv("ListSessions(empty)", resp2);

            assert200.accept("ListSessions(empty)", resp2);

            List<String> ids = JsonParsers.sessionIds(resp2);
            TestLog.ok("ListSessions(empty): sessions=" + ids);

            assertTrue(ids.isEmpty(), "Sessions must be empty");
            TestLog.ok("Проверка OK: список пуст");
        }

        TestLog.ok("ТЕСТ ПРОЙДЕН ЦЕЛИКОМ: SessionsIT (весь сценарий сессий выполнен успешно)");
    }
}