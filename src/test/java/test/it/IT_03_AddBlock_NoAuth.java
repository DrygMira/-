package test.it;

import blockchain.body.ConnectionBody;
import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import blockchain.body.UserParamBody;
import org.junit.jupiter.api.BeforeAll;
import test.it.addBlockUtils.AddBlockSender;
import test.it.addBlockUtils.ChainState;
import test.it.utils.*;
import utils.crypto.Ed25519Util;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * ОБЪЕДИНЕНО: прежний IT_03 + прежний IT_04 в одном тесте,
 * чтобы "четвёртый" сценарий гарантированно запускался сразу после "третьего".
 *
 * Сценарий:
 *  1) (УБРАНО) AddUser(USER1) — создаётся раньше в первом тесте
 *  2) (УБРАНО) AddUser(USER2) — создаётся раньше в первом тесте
 *
 *  3) USER1: HEADER + 3 NEW + 2 REPLY + 2 REACT + 3 EDIT (добавили)
 *      - редактируем два ранее написанных сообщения
 *      - одно сообщение редактируем два раза
 *
 *  4) USER2: HEADER + UserParams(name+address) + Connection(FRIEND -> USER1)
 *  5) USER1: UserParams(name+surname) + Connection(FRIEND -> USER2) + Connection(FOLLOW -> USER2)
 *  6) USER2: Connection(UNFRIEND -> USER1)
 *
 * Важно:
 *  - у каждого пользователя СВОЙ ChainState
 *  - AddBlockSender создаём с новой сигнатурой:
 *      new AddBlockSender(state, login, blockchainName, loginPrivKey)
 *  - USER2 ключи делаем детерминированно из login (как в ItRunContext), но локально.
 */
public class IT_03_AddBlock_NoAuth {

    // ===== USER2 (константы прямо тут, чтобы не ломать твой TestConfig) =====
    private static final String USER2_LOGIN = "Anya2";
    private static final String BCH_SUFFIX_3 = "001";
    private static final String USER2_BCH = USER2_LOGIN + BCH_SUFFIX_3;

    public static void main(String[] args) {
        int failed = run();
    }

    public static int run() {
        return TestLog.runOne("IT_03_AddBlock_NoAuth", IT_03_AddBlock_NoAuth::testBody);
    }

    @BeforeAll
    static void ensureUserExists() {
        ItRunContext.initIfNeeded();
        // можно оставить пустым, как у тебя
    }

    private static void testBody() {
        ItRunContext.initIfNeeded();
        ensureUserExists();

        Duration t = Duration.ofSeconds(1);

        // =========================================================
        // USER2 keys (детерминированно из login, как твой ItRunContext)
        // =========================================================
        byte[] user2LoginPriv = Ed25519Util.generatePrivateKeyFromString(USER2_LOGIN);

        // =========================================================
        // 3) USER1 блоки (под message_stats + edits)
        // =========================================================
        if (TestConfig.DEBUG()) {
            TestLog.titleBlock("""
                    IT_03_AddBlock_NoAuth (combined): USER1 + USER2 сценарии
                    USER1 login         = %s
                    USER1 blockchainName= %s
                    USER2 login         = %s
                    USER2 blockchainName= %s
                    """.formatted(TestConfig.LOGIN(), TestConfig.BCH_NAME(), USER2_LOGIN, USER2_BCH));
        }

        ChainState st1 = new ChainState();
        AddBlockSender sender1 = new AddBlockSender(
                st1,
                TestConfig.LOGIN(),
                TestConfig.BCH_NAME(),
                TestConfig.LOGIN_PRIV_KEY()
        );

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: HEADER");
        sender1.send(new HeaderBody(TestConfig.LOGIN()), t);
        assertTrue(st1.hasHeader());

        // 3 NEW
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#1 (NEW)  <- будет LIKE + REPLY");
        sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #1 (NEW) from IT_03 test"), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#2 (NEW)  <- будет ONLY LIKE + 2 EDIT");
        sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #2 (NEW) from IT_03 test"), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#3 (NEW)  <- будет ONLY REPLY + 1 EDIT");
        sender1.send(new TextBody(TextBody.SUB_NEW, "Hello #3 (NEW) from IT_03 test"), t);

        byte[] text1Hash = st1.getGlobalHash32(1);
        byte[] text2Hash = st1.getGlobalHash32(2);
        byte[] text3Hash = st1.getGlobalHash32(3);
        assertNotNull(text1Hash);
        assertNotNull(text2Hash);
        assertNotNull(text3Hash);

        // 2 REPLY
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#4 (REPLY -> TEXT#1)  (делает TEXT#1: replies+1)");
        sender1.send(new TextBody(
                TextBody.SUB_REPLY,
                "Reply to TEXT#1",
                TestConfig.BCH_NAME(),
                1,
                text1Hash
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#5 (REPLY -> TEXT#3)  (делает TEXT#3: replies+1)");
        sender1.send(new TextBody(
                TextBody.SUB_REPLY,
                "Reply to TEXT#3",
                TestConfig.BCH_NAME(),
                3,
                text3Hash
        ), t);

        // 2 LIKE
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: REACT#1 (LIKE -> TEXT#1)  (делает TEXT#1: likes+1)");
        sender1.send(new ReactionBody(
                ReactionBody.SUB_LIKE,
                TestConfig.BCH_NAME(),
                1,
                text1Hash
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: REACT#2 (LIKE -> TEXT#2)  (делает TEXT#2: likes+1)");
        sender1.send(new ReactionBody(
                ReactionBody.SUB_LIKE,
                TestConfig.BCH_NAME(),
                2,
                text2Hash
        ), t);

        // 3 EDIT (два сообщения исправляем, одно — два раза)
        // ВАЖНО: subType EDIT берём из TextBody.SUB_EDIT (единая константа = 10)
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#6 (EDIT -> TEXT#2)  (исправление #1)");
        sender1.send(new TextBody(
                TextBody.SUB_EDIT,
                "Hello #2 (EDIT#1) from IT_03 test",
                TestConfig.BCH_NAME(),
                2,
                text2Hash
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#7 (EDIT -> TEXT#2)  (исправление #2)");
        sender1.send(new TextBody(
                TextBody.SUB_EDIT,
                "Hello #2 (EDIT#2) from IT_03 test",
                TestConfig.BCH_NAME(),
                2,
                text2Hash
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: TEXT#8 (EDIT -> TEXT#3)  (исправление #1)");
        sender1.send(new TextBody(
                TextBody.SUB_EDIT,
                "Hello #3 (EDIT#1) from IT_03 test",
                TestConfig.BCH_NAME(),
                3,
                text3Hash
        ), t);

        assertEquals(10, st1.globalLastNumber(), "USER1: после EDIT должно быть 11 блоков: globalLastNumber=10");
        assertEquals(8, st1.lineLastNumber((short) 1), "USER1: line=1 должно быть 8 TEXT блоков (3 new + 2 reply + 3 edit)");
        assertEquals(2, st1.lineLastNumber((short) 2), "USER1: line=2 должно быть 2 REACTION блока");

        // =========================================================
        // 4) USER2: HEADER + PARAMS + FRIEND->USER1
        // =========================================================
        ChainState st2 = new ChainState();
        AddBlockSender sender2 = new AddBlockSender(
                st2,
                USER2_LOGIN,
                USER2_BCH,
                user2LoginPriv
        );

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER2: HEADER");
        sender2.send(new HeaderBody(USER2_LOGIN), t);
        assertTrue(st2.hasHeader());

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER2: UserParams (name + address)");
        sender2.send(new UserParamBody(
                "Anya",
                "Amsterdam, Example street 10"
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER2: Connection (FRIEND -> USER1)");
        sender2.send(new ConnectionBody(
                ConnectionBody.SUB_FRIEND,
                TestConfig.LOGIN(),     // to_login (USER1)
                TestConfig.BCH_NAME(),  // toBch (USER1 chain)
                0,
                new byte[32]
        ), t);

        // =========================================================
        // 5) USER1: params + взаимность + подписка (без нового HEADER!)
        // =========================================================
        // ВАЖНО: мы НЕ создаём новый ChainState для USER1, и НЕ шлём header заново.
        // Мы продолжаем тем же sender1 и st1, иначе будет пытаться начать цепочку заново.
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: UserParams (name + surname)");
        sender1.send(new UserParamBody(
                "Anna",
                "Gareeva"
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: Connection (FRIEND -> USER2)");
        sender1.send(new ConnectionBody(
                ConnectionBody.SUB_FRIEND,
                USER2_LOGIN,
                USER2_BCH,
                0,
                new byte[32]
        ), t);

        if (TestConfig.DEBUG()) TestLog.stepTitle("USER1: Connection (FOLLOW -> USER2)");
        sender1.send(new ConnectionBody(
                ConnectionBody.SUB_FOLLOW,
                USER2_LOGIN,
                USER2_BCH,
                0,
                new byte[32]
        ), t);

        // 6) USER2: Connection (UNFRIEND -> USER1)  — USER2 больше не друг USER1
        if (TestConfig.DEBUG()) TestLog.stepTitle("USER2: Connection (UNFRIEND -> USER1)");
        sender2.send(new ConnectionBody(
                ConnectionBody.SUB_UNFRIEND,
                TestConfig.LOGIN(),     // to_login (USER1)
                TestConfig.BCH_NAME(),  // toBch (USER1 chain)
                0,
                new byte[32]
        ), t);

        TestLog.pass("IT_03_AddBlock_NoAuth (combined): OK");
    }
}