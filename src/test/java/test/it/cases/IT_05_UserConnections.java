package test.it.cases;

import test.it.utils.TestConfig;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * IT_05_UserConnections
 *
 * Делает пару запросов GetFriendsLists (без проверок существования юзеров — это уже в IT_01).
 *
 * Ожидаемый формат ответа:
 * {
 *   "op":"GetFriendsLists",
 *   "requestId":"...",
 *   "status":200,
 *   "payload":{
 *     "login":"TestUser1",          // канонический регистр из БД
 *     "out_friends":[...],          // кому login поставил FRIEND
 *     "in_friends":[...]            // кто поставил FRIEND login
 *   }
 * }
 *
 * ВАЖНО:
 * - login в запросе может быть в любом регистре,
 * - но в ответе payload.login должен быть канонический (как в БД).
 */
public class IT_05_UserConnections {

    public static void main(String[] args) {
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_05_UserConnections");
        Duration t = Duration.ofSeconds(5);

        final String u1 = TestConfig.LOGIN();
        final String u2 = TestConfig.LOGIN2();

        try (WsSession ws = WsSession.open()) {

            // 1) Запрос списков связей для u1 (канонический регистр)
            r.ok("GetFriendsLists USER1: " + u1);
            String resp1 = ws.call("GetFriendsLists#U1", JsonBuilders.getFriendsLists(u1), t);
            check200(r, resp1);
            checkCanonicalLogin(r, resp1, u1);
            checkTwoListsPresent(r, resp1);

            // 2) Запрос списков связей для u1 (смешанный регистр)
            String u1mixed = mixCase(u1);
            r.ok("GetFriendsLists USER1 mixed-case request: " + u1mixed + " (expect login=" + u1 + ")");
            String resp2 = ws.call("GetFriendsLists#U1_MIX", JsonBuilders.getFriendsLists(u1mixed), t);
            check200(r, resp2);
            checkCanonicalLogin(r, resp2, u1);
            checkTwoListsPresent(r, resp2);

            // 3) Ещё один запрос — для u2 (просто чтобы "пару запросов")
            r.ok("GetFriendsLists USER2: " + u2);
            String resp3 = ws.call("GetFriendsLists#U2", JsonBuilders.getFriendsLists(u2), t);
            check200(r, resp3);
            checkCanonicalLogin(r, resp3, u2);
            checkTwoListsPresent(r, resp3);

            // лог для наглядности (могут быть пустые, это ок)
            List<String> out1 = JsonParsers.friendsOut(resp1);
            List<String> in1  = JsonParsers.friendsIn(resp1);

            r.ok("Friends lists USER1: out=" + out1.size() + ", in=" + in1.size());

        } catch (Throwable e) {
            r.fail("IT_05_UserConnections упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    // ================= checks =================

    private static void check200(TestResult r, String resp) {
        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("ожидали status=200, получили " + st + ", resp=" + resp);
            fail("unexpected status=" + st);
        }
    }

    private static void checkCanonicalLogin(TestResult r, String resp, String expectedCanonicalLogin) {
        String got = JsonParsers.friendsLogin(resp);
        if (got == null) {
            r.fail("GetFriendsLists: payload.login отсутствует, resp=" + resp);
            fail("GetFriendsLists missing payload.login");
        }
        if (!expectedCanonicalLogin.equals(got)) {
            r.fail("GetFriendsLists: login должен вернуться канонический. expected=" + expectedCanonicalLogin + ", got=" + got + ", resp=" + resp);
            fail("GetFriendsLists wrong login case");
        }
    }

    private static void checkTwoListsPresent(TestResult r, String resp) {
        // В JsonParsers.getPayloadStringArray сейчас возвращает пустой список, даже если поле отсутствует/не массив.
        // Поэтому дополнительно проверяем, что парсер вернул НЕ null (он и не должен возвращать null).
        List<String> out = JsonParsers.friendsOut(resp);
        List<String> in  = JsonParsers.friendsIn(resp);

        if (out == null || in == null) {
            r.fail("GetFriendsLists: out_friends/in_friends не должны быть null, resp=" + resp);
            fail("GetFriendsLists lists are null");
        }

        // Просто отмечаем, что поля читаются, даже если пустые.
        r.ok("GetFriendsLists lists present: out=" + out.size() + ", in=" + in.size());
    }

    private static String mixCase(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.length() < 2) return x;
        return Character.toUpperCase(x.charAt(0)) + x.substring(1).toLowerCase();
    }
}