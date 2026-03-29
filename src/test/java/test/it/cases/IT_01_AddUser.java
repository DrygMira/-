package test.it.cases;

import test.it.utils.TestConfig;
import test.it.utils.TestIds;
import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * IT_01_AddUser
 * Создаёт 3 пользователей: TestUser1/2/3 (200 OK или 409 USER_ALREADY_EXISTS).
 *
 * Обновление:
 * - теперь AddUser может вернуть 409 не только USER_ALREADY_EXISTS,
 *   но и BLOCKCHAIN_ALREADY_EXISTS / BLOCKCHAIN_STATE_ALREADY_EXISTS.
 * - дополнительно проверяем GetUser (status=200 всегда).
 * - добавлен SearchUsers: поиск по префиксу (первые 3 символа).
 */
public class IT_01_AddUser {

    public static void main(String[] args) {
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_01_AddUser");

        Duration t = Duration.ofSeconds(5);

        try (WsSession ws = WsSession.open()) {

            checkPingAndServerInfo(r, ws, t);

            r.ok("AddUser USER1: " + TestConfig.LOGIN());
            String resp1 = ws.call("AddUser#USER1", JsonBuilders.addUser(TestConfig.LOGIN()), t);
            checkAddUser200or409(r, resp1);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN(), t);

            r.ok("AddUser USER2: " + TestConfig.LOGIN2());
            String resp2 = ws.call("AddUser#USER2", JsonBuilders.addUser(TestConfig.LOGIN2()), t);
            checkAddUser200or409(r, resp2);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN2(), t);

            r.ok("AddUser USER3: " + TestConfig.LOGIN3());
            String resp3 = ws.call("AddUser#USER3", JsonBuilders.addUser(TestConfig.LOGIN3()), t);
            checkAddUser200or409(r, resp3);
            checkGetUserMustExist(r, ws, TestConfig.LOGIN3(), t);

            // Доп: проверяем case-insensitive поиск в GetUser
            String mixed = mixCase(TestConfig.LOGIN());
            r.ok("GetUser case-insensitive: запрос=" + mixed + " (должен найти " + TestConfig.LOGIN() + ")");
            checkGetUserMustExist(r, ws, mixed, t);

            // Доп: проверяем "не существует" (но status=200)
            String missing = "NoSuchUser_987654321";
            r.ok("GetUser missing: " + missing);
            checkGetUserMustNotExist(r, ws, missing, t);

            // SearchUsers: один раз ищем по первым трём символам логина USER1
            String prefix3 = first3(TestConfig.LOGIN());
            String prefix3Mixed = mixCase(prefix3);
            r.ok("SearchUsers: prefix(3)='" + prefix3Mixed + "' (должен вернуть список и содержать " + TestConfig.LOGIN() + ")");
            checkSearchUsersMustContain(r, ws, prefix3Mixed, TestConfig.LOGIN(), t);

            checkNegativeRequests(r, ws, t);

        } catch (Throwable e) {
            r.fail("IT_01_AddUser упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static void checkPingAndServerInfo(TestResult r, WsSession ws, Duration t) {
        String pingResp = ws.call("Ping", JsonBuilders.ping(System.currentTimeMillis()), t);
        if (JsonParsers.status(pingResp) != 200 || !Boolean.TRUE.equals(JsonParsers.ok(pingResp))) {
            r.fail("Ping: ожидали status=200 и ok=true, resp=" + pingResp);
            fail("Ping unexpected response");
        }

        Long serverTs = JsonParsers.pingTs(pingResp);
        if (serverTs == null || serverTs <= 0) {
            r.fail("Ping: сервер не вернул ts, resp=" + pingResp);
            fail("Ping missing ts");
        }
        r.ok("Ping: ok, serverTs=" + serverTs);

        String infoResp = ws.call("GetServerInfo", JsonBuilders.getServerInfo(), t);
        if (JsonParsers.status(infoResp) != 200 || !Boolean.TRUE.equals(JsonParsers.ok(infoResp))) {
            r.fail("GetServerInfo: ожидали status=200 и ok=true, resp=" + infoResp);
            fail("GetServerInfo unexpected response");
        }
        if (!JsonParsers.payloadIsObject(infoResp)) {
            r.fail("GetServerInfo: payload должен быть объектом, resp=" + infoResp);
            fail("GetServerInfo payload is not object");
        }

        r.ok("GetServerInfo: ok, url='" + safe(JsonParsers.payloadText(infoResp, "url"))
                + "', version='" + safe(JsonParsers.payloadText(infoResp, "version"))
                + "', physicalRegion='" + safe(JsonParsers.payloadText(infoResp, "physicalRegion"))
                + "', description='" + safe(JsonParsers.payloadText(infoResp, "description"))
                + "', origin='" + safe(JsonParsers.payloadText(infoResp, "origin"))
                + "', extraInfo='" + safe(JsonParsers.payloadText(infoResp, "extraInfo")) + "'");
    }

    private static void checkAddUser200or409(TestResult r, String resp) {
        int st = JsonParsers.status(resp);
        if (st == 200) {
            r.ok("AddUser: status=200 (создан)");
            return;
        }
        if (st == 409) {
            String code = JsonParsers.errorCode(resp);

            // раньше был только USER_ALREADY_EXISTS, теперь добавились ещё варианты
            if ("USER_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 USER_ALREADY_EXISTS (уже был)");
                return;
            }
            if ("BLOCKCHAIN_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 BLOCKCHAIN_ALREADY_EXISTS (blockchainName уже занят)");
                return;
            }
            if ("BLOCKCHAIN_STATE_ALREADY_EXISTS".equals(code)) {
                r.ok("AddUser: status=409 BLOCKCHAIN_STATE_ALREADY_EXISTS (blockchain_state уже есть)");
                return;
            }

            r.fail("AddUser: status=409 но code=" + code + ", resp=" + resp);
            fail("AddUser unexpected 409 code=" + code);
        }
        r.fail("AddUser: неожиданный status=" + st + ", resp=" + resp);
        fail("AddUser unexpected status=" + st);
    }

    private static void checkGetUserMustExist(TestResult r, WsSession ws, String loginQuery, Duration t) {
        String resp = ws.call("GetUser#" + loginQuery, JsonBuilders.getUser(loginQuery), t);

        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("GetUser: ожидали status=200, получили " + st + ", resp=" + resp);
            fail("GetUser unexpected status=" + st);
        }

        Boolean exists = JsonParsers.exists(resp);
        if (exists == null || !exists) {
            r.fail("GetUser: ожидали exists=true, resp=" + resp);
            fail("GetUser expected exists=true");
        }

        // Проверяем, что сервер возвращает данные
        String login = JsonParsers.userLogin(resp);
        String blockchainName = JsonParsers.userBlockchainName(resp);
        String solanaKey = JsonParsers.userSolanaKey(resp);
        String blockchainKey = JsonParsers.userBlockchainKey(resp);
        String deviceKey = JsonParsers.userDeviceKey(resp);

        if (isBlank(login) || isBlank(blockchainName) || isBlank(solanaKey) || isBlank(blockchainKey) || isBlank(deviceKey)) {
            r.fail("GetUser: exists=true, но поля пустые/неполные, resp=" + resp);
            fail("GetUser returned incomplete user data");
        }

        // ВАЖНО:
        // Поиск делается без учета регистра, но login/blockchainName должны вернуться как в БД.
        // Для тех логинов, которые мы создаем в тесте, это ровно TestConfig.LOGIN*().
        // Поэтому если запрос был смешанный регистр — сравниваем не с loginQuery, а с "каноничным" логином из конфига.
        String canonical = canonicalLogin(loginQuery);
        if (canonical != null) {
            if (!login.equals(canonical)) {
                r.fail("GetUser: login должен вернуться как в БД. expected=" + canonical + ", got=" + login + ", resp=" + resp);
                fail("GetUser wrong login case");
            }

            String expectedBch = TestConfig.getBlockchainName(canonical);
            if (!blockchainName.equals(expectedBch)) {
                r.fail("GetUser: blockchainName должен вернуться как в БД. expected=" + expectedBch + ", got=" + blockchainName + ", resp=" + resp);
                fail("GetUser wrong blockchainName");
            }

            // ключи должны совпадать с теми, что AddUser использует при регистрации
            String expSol = TestConfig.solanaPublicKeyB64(canonical);
            String expBchKey = TestConfig.blockchainPublicKeyB64(canonical);
            String expDev = TestConfig.devicePublicKeyB64(canonical);

            if (!solanaKey.equals(expSol)) {
                r.fail("GetUser: solanaKey mismatch, resp=" + resp);
                fail("GetUser solanaKey mismatch");
            }
            if (!blockchainKey.equals(expBchKey)) {
                r.fail("GetUser: blockchainKey mismatch, resp=" + resp);
                fail("GetUser blockchainKey mismatch");
            }
            if (!deviceKey.equals(expDev)) {
                r.fail("GetUser: deviceKey mismatch, resp=" + resp);
                fail("GetUser deviceKey mismatch");
            }
        }

        r.ok("GetUser: exists=true, login=" + login + ", blockchainName=" + blockchainName);
    }

    private static void checkGetUserMustNotExist(TestResult r, WsSession ws, String loginQuery, Duration t) {
        String resp = ws.call("GetUser#" + loginQuery, JsonBuilders.getUser(loginQuery), t);

        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("GetUser(not exist): ожидали status=200, получили " + st + ", resp=" + resp);
            fail("GetUser(not exist) unexpected status=" + st);
        }

        Boolean exists = JsonParsers.exists(resp);
        if (exists == null) {
            r.fail("GetUser(not exist): payload.exists отсутствует, resp=" + resp);
            fail("GetUser(not exist) missing exists");
        }
        if (exists) {
            r.fail("GetUser(not exist): ожидали exists=false, resp=" + resp);
            fail("GetUser(not exist) expected exists=false");
        }

        r.ok("GetUser: exists=false (ok)");
    }

    private static void checkSearchUsersMustContain(TestResult r, WsSession ws, String prefix, String expectedLogin, Duration t) {
        String resp = ws.call("SearchUsers#" + prefix, JsonBuilders.searchUsers(prefix), t);

        int st = JsonParsers.status(resp);
        if (st != 200) {
            r.fail("SearchUsers: ожидали status=200, получили " + st + ", resp=" + resp);
            fail("SearchUsers unexpected status=" + st);
        }

        List<String> logins = JsonParsers.searchLogins(resp);
        if (logins == null || logins.isEmpty()) {
            r.fail("SearchUsers: ожидали непустой список, resp=" + resp);
            fail("SearchUsers expected non-empty list");
        }

        // ВАЖНО: ожидаемый логин должен быть в ответе в регистре БД (каноничный expectedLogin)
        boolean found = false;
        for (String s : logins) {
            if (expectedLogin.equals(s)) {
                found = true;
                break;
            }
        }
        if (!found) {
            r.fail("SearchUsers: ожидаемый логин не найден. expected=" + expectedLogin + ", got=" + logins + ", resp=" + resp);
            fail("SearchUsers expected login not found");
        }

        r.ok("SearchUsers: ok, prefix=" + prefix + ", results=" + logins.size() + ", contains=" + expectedLogin);
    }

    private static void checkNegativeRequests(TestResult r, WsSession ws, Duration t) {
        String badAddUserReqId = TestIds.next("bad-adduser");
        String badAddUser = """
                {
                  "op": "AddUser",
                  "requestId": "%s",
                  "payload": {
                    "login": "",
                    "blockchainName": "%s",
                    "solanaKey": "%s",
                    "blockchainKey": "%s",
                    "deviceKey": "%s",
                    "bchLimit": %d
                  }
                }
                """.formatted(
                badAddUserReqId,
                TestConfig.BCH_NAME(),
                TestConfig.SOLANA_PUBKEY_B64(),
                TestConfig.BLOCKCHAIN_PUBKEY_B64(),
                TestConfig.DEVICE_PUBKEY_B64(),
                TestConfig.TEST_BCH_LIMIT
        );
        String badAddUserResp = ws.call("AddUser#NEGATIVE", badAddUser, t);
        assertErrorFormat(badAddUserResp, "AddUser", badAddUserReqId, "BAD_FIELDS");
        r.ok("Negative AddUser: error format OK");

        String badGetUserReqId = TestIds.next("bad-getuser");
        String badGetUser = """
                {
                  "op": "GetUser",
                  "requestId": "%s",
                  "payload": {
                    "login": ""
                  }
                }
                """.formatted(badGetUserReqId);
        String badGetUserResp = ws.call("GetUser#NEGATIVE", badGetUser, t);
        assertErrorFormat(badGetUserResp, "GetUser", badGetUserReqId, "BAD_FIELDS");
        r.ok("Negative GetUser: error format OK");

        String badSearchReqId = TestIds.next("bad-searchusers");
        String badSearch = """
                {
                  "op": "SearchUsers",
                  "requestId": "%s",
                  "payload": {
                    "prefix": ""
                  }
                }
                """.formatted(badSearchReqId);
        String badSearchResp = ws.call("SearchUsers#NEGATIVE", badSearch, t);
        assertErrorFormat(badSearchResp, "SearchUsers", badSearchReqId, "BAD_FIELDS");
        r.ok("Negative SearchUsers: error format OK");
    }

    private static void assertErrorFormat(String resp, String op, String requestId, String code) {
        int status = JsonParsers.status(resp);
        if (status >= 200 && status < 300) fail("Expected non-2xx status: " + resp);
        if (!Boolean.FALSE.equals(JsonParsers.ok(resp))) fail("Expected ok=false: " + resp);
        if (!op.equals(JsonParsers.op(resp))) fail("Unexpected op: " + resp);
        if (!requestId.equals(JsonParsers.requestId(resp))) fail("Unexpected requestId: " + resp);
        if (!code.equals(JsonParsers.errorCode(resp))) fail("Unexpected error code: " + resp);
        if (!JsonParsers.payloadIsObject(resp)) fail("payload must be object: " + resp);
        if (JsonParsers.payloadSize(resp) != 0) fail("error payload must be empty object: " + resp);
        if (isBlank(JsonParsers.message(resp))) fail("error message must be present: " + resp);
    }

    private static String canonicalLogin(String anyCaseLogin) {
        if (anyCaseLogin == null) return null;
        String x = anyCaseLogin.trim();
        if (x.isEmpty()) return null;

        // Привязка только к нашим тестовым логинам, чтобы не гадать.
        if (x.equalsIgnoreCase(TestConfig.LOGIN())) return TestConfig.LOGIN();
        if (x.equalsIgnoreCase(TestConfig.LOGIN2())) return TestConfig.LOGIN2();
        if (x.equalsIgnoreCase(TestConfig.LOGIN3())) return TestConfig.LOGIN3();

        return null;
    }

    private static String mixCase(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.length() < 2) return x;
        // простой "микс" без рандома, чтобы тест был детерминированный
        return Character.toUpperCase(x.charAt(0)) + x.substring(1).toLowerCase();
    }

    private static String first3(String s) {
        if (s == null) return "";
        String x = s.trim();
        if (x.length() <= 3) return x;
        return x.substring(0, 3);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
