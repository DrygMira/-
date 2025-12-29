package test.it;

import org.junit.jupiter.api.Test;
import test.it.utils.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class IT_01_AddUser {

    // ANSI цвета
    private static final String R = "\u001B[0m";
    private static final String G = "\u001B[32m";
    private static final String Y = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String C = "\u001B[36m";

    private static void line() {
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    private static void title(String s) {
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    private static void ok(String s) {
        System.out.println(G + "✅ " + s + R);
    }

    private static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    public static void main(String[] args) {
        // чтобы тест можно было запускать вообще без JUnit
        ItRunContext.initIfNeeded();
        new IT_01_AddUser().addUser_shouldReturn200_orAlreadyExists();
    }

    @Test
    void addUser_shouldReturn200_orAlreadyExists() {
        ItRunContext.initIfNeeded();

        title("AddUserIT: проверка добавления пользователя (200 OK) или 'уже существует' (409 USER_ALREADY_EXISTS)");
        System.out.println("Используем:");
        System.out.println("  login          = " + TestConfig.LOGIN());
        System.out.println("  blockchainName = " + TestConfig.BCH_NAME());
        System.out.println("Ожидание:");
        System.out.println("  - 200 (создан)");
        System.out.println("  - или 409 + payload.code=USER_ALREADY_EXISTS\n");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            String reqId = "it-adduser-1";
            String reqJson = JsonBuilders.addUser(reqId);

            System.out.println("📤 Отправляем AddUser запрос:");
            System.out.println(reqJson);
            line();

            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));

            System.out.println("📥 Ответ сервера:");
            System.out.println(resp);
            line();

            int st = JsonParsers.status(resp);
            System.out.println("ℹ️ status=" + st);

            boolean created = (st == 200);
            boolean already = (st == 409);

            if (already) {
                String code = JsonParsers.errorCode(resp);
                System.out.println("ℹ️ server_code=" + code);

                try {
                    assertEquals("USER_ALREADY_EXISTS", code,
                            "Expected code=USER_ALREADY_EXISTS, but got: " + code + ", resp=" + resp);
                    ok("409 получен корректно: USER_ALREADY_EXISTS");
                } catch (AssertionError ae) {
                    boom("409 получен, но code не тот. " + ae.getMessage());
                    throw ae;
                }
            }

            if (created) {
                ok("ТЕСТ ПРОЙДЕН: AddUser создан/добавлен (status=200)");
            } else if (already) {
                ok("ТЕСТ ПРОЙДЕН: AddUser уже есть в системе (status=409, USER_ALREADY_EXISTS)");
            } else {
                boom("Неожиданный status=" + st + ", resp=" + resp);
                fail("❌ AddUser: неожиданный status=" + st + ", resp=" + resp);
            }

        } catch (AssertionError | RuntimeException e) {
            boom("ТЕСТ УПАЛ: AddUserIT. Причина: " + e.getMessage());
            throw e;
        }
    }
}