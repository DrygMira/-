package test.it;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class AddUserIT {

    @Test
    void addUser_shouldReturn200_orAlreadyExists() {
        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            String reqId = "it-adduser-1";
            String reqJson = JsonBuilders.addUser(reqId);

            TestLog.section("AddUserIT: AddUser");
            TestLog.req("AddUser requestId=" + reqId, reqJson);

            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            TestLog.resp("AddUser responseId=" + reqId, resp);

            int st = JsonParsers.status(resp);

            boolean created = (st == 200);
            boolean already = (st == 409);

            if (already) {
                String code = JsonParsers.errorCode(resp);
                // если сервер кладет code в payload.code — парсер должен это поддерживать (см. ниже)
                assertEquals("USER_ALREADY_EXISTS", code,
                        "Expected errorCode=USER_ALREADY_EXISTS, but got: " + code + ", resp=" + resp);
            }

            if (created) {
                System.out.println("✅ AddUser: создан/добавлен (status=200)");
            } else if (already) {
                System.out.println("✅ AddUser: уже есть в системе (status=409, USER_ALREADY_EXISTS)");
            } else {
                fail("❌ AddUser: неожиданный status=" + st + ", resp=" + resp);
            }
        }
    }
}