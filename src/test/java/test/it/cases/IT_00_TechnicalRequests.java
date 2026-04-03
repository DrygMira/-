package test.it.cases;

import test.it.utils.json.JsonBuilders;
import test.it.utils.json.JsonParsers;
import test.it.utils.log.TestResult;
import test.it.utils.ws.WsSession;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * IT_00_TechnicalRequests
 * Проверяет технические запросы без авторизации:
 * - Ping
 * - GetServerInfo
 */
public class IT_00_TechnicalRequests {

    public static void main(String[] args) {
        String summary = run();
        System.out.println(summary);
    }

    public static String run() {
        TestResult r = new TestResult("IT_00_TechnicalRequests");
        Duration t = Duration.ofSeconds(5);

        try (WsSession ws = WsSession.open()) {
            checkPing(r, ws, t);
            checkGetServerInfo(r, ws, t);
        } catch (Throwable e) {
            r.fail("IT_00_TechnicalRequests упал: " + e.getMessage());
        }

        return r.summaryLine();
    }

    private static void checkPing(TestResult r, WsSession ws, Duration t) {
        String resp = ws.call("Ping", JsonBuilders.ping(System.currentTimeMillis()), t);

        if (JsonParsers.status(resp) != 200) {
            r.fail("Ping: ожидали status=200, resp=" + resp);
            fail("Ping unexpected status");
        }
        if (!Boolean.TRUE.equals(JsonParsers.ok(resp))) {
            r.fail("Ping: ожидали ok=true, resp=" + resp);
            fail("Ping unexpected ok");
        }

        Long ts = JsonParsers.pingTs(resp);
        if (ts == null || ts <= 0) {
            r.fail("Ping: сервер не вернул payload.ts, resp=" + resp);
            fail("Ping missing ts");
        }

        r.ok("Ping: OK, ts=" + ts);
    }

    private static void checkGetServerInfo(TestResult r, WsSession ws, Duration t) {
        String resp = ws.call("GetServerInfo", JsonBuilders.getServerInfo(), t);

        if (JsonParsers.status(resp) != 200) {
            r.fail("GetServerInfo: ожидали status=200, resp=" + resp);
            fail("GetServerInfo unexpected status");
        }
        if (!Boolean.TRUE.equals(JsonParsers.ok(resp))) {
            r.fail("GetServerInfo: ожидали ok=true, resp=" + resp);
            fail("GetServerInfo unexpected ok");
        }
        if (!JsonParsers.payloadIsObject(resp)) {
            r.fail("GetServerInfo: payload должен быть объектом, resp=" + resp);
            fail("GetServerInfo payload is not object");
        }

        assertStringField(resp, "url", r);
        String version = assertStringField(resp, "version", r);
        assertStringField(resp, "physicalRegion", r);
        assertStringField(resp, "description", r);
        assertStringField(resp, "origin", r);
        assertStringField(resp, "extraInfo", r);

        r.ok("GetServerInfo: OK, version=" + version);
    }

    private static String assertStringField(String resp, String field, TestResult r) {
        String value = JsonParsers.payloadText(resp, field);
        if (value == null) {
            r.fail("GetServerInfo: отсутствует поле payload." + field + ", resp=" + resp);
            fail("GetServerInfo missing field: " + field);
        }
        return value;
    }
}
