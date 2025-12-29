package test.it.ws;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.it.utils.ItRunContext;
import test.it.utils.JsonBuilders;
import test.it.utils.JsonParsers;
import test.it.utils.TestConfig;
import test.it.utils.WsTestClient;
import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT_03_AddBlock_NoAuth
 *
 * Интеграционный тест добавления блоков в персональный блокчейн без отдельной авторизации.
 *
 * Сценарий (как ты попросил):
 *  1) AddBlock: HEADER   (global=0, line=0, lineNum=0, prevGlobalHash=ZERO64) -> 200
 *  2) AddBlock: TEXT#1   (global=1, line=1, lineNum=1, prevGlobalHash=hash(0)) -> 200
 *  3) AddBlock: TEXT#2   (global=2, line=1, lineNum=2, prevGlobalHash=hash(1)) -> 200
 *  4) AddBlock: TEXT#3   (global=3, line=1, lineNum=3, prevGlobalHash=hash(2)) -> 200
 *  5) AddBlock: REACT#1  (global=4, line=2, lineNum=1, prevGlobalHash=hash(3)) -> 200
 *     - реакция на TEXT#1 (toBchName, toGlobal=1, toHash=hash(TEXT#1))
 *
 * Важно по линиям (твоя договорённость):
 *  - line 0: нулевой блок (HEADER) один на весь блокчейн (глобальный 0)
 *  - line 1 и line 2: первый блок каждой линии ссылается prevLineHash на hash(нулевого блока)
 *
 * В этом тесте мы ведём 2 массива:
 *  - lineLastNumber[line] — сколько блоков в линии (то есть последний lineNum)
 *  - lineLastHashHex[line] — hash последнего блока линии (HEX64)
 */
public class IT_03_AddBlock_NoAuth {

    // ANSI цвета
    private static final String R   = "\u001B[0m";
    private static final String G   = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String C   = "\u001B[36m";

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    private static final short LINE_HEADER = 0;
    private static final short LINE_TEXT   = 1;
    private static final short LINE_REACT  = 2;

    public static void main(String[] args) {
        ItRunContext.initIfNeeded();
        ensureUserExists();
        new IT_03_AddBlock_NoAuth().addBlock_shouldAppendHeaderThenTextThenReaction();
    }

    private static void line() {
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    private static void title(String s) {
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    private static void stepTitle(String s) {
        System.out.println(C + "\n-------------------- " + s + " --------------------" + R);
    }

    private static void ok(String s) {
        System.out.println(G + "✅ " + s + R);
    }

    private static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    private static void send(String op, String json) {
        System.out.println("📤 [" + op + "] Request JSON:");
        System.out.println(json);
        line();
    }

    private static void recv(String op, String json) {
        System.out.println("📥 [" + op + "] Response JSON:");
        System.out.println(json);
        line();
    }

    private static void assert200(String op, String resp) {
        int st = JsonParsers.status(resp);
        try {
            assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
            ok(op + ": status=200");
        } catch (AssertionError ae) {
            boom(op + ": ожидали 200, но получили " + st);
            throw ae;
        }
    }

    @BeforeAll
    static void ensureUserExists() {
        ItRunContext.initIfNeeded();

        title("AddBlockIT (BeforeAll): предусловие — пользователь должен существовать (AddUser: 200 или 409)");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {
            String reqId = "it03-adduser-beforeall";
            String reqJson = JsonBuilders.addUser(reqId);

            send("AddUser(BeforeAll)", reqJson);
            String resp = client.request(reqId, reqJson, Duration.ofSeconds(5));
            recv("AddUser(BeforeAll)", resp);

            int st = JsonParsers.status(resp);

            if (st == 200) {
                ok("BeforeAll: пользователь создан/добавлен (status=200)");
            } else if (st == 409) {
                String code = JsonParsers.errorCode(resp);
                if ("USER_ALREADY_EXISTS".equals(code)) {
                    ok("BeforeAll: пользователь уже есть (status=409, USER_ALREADY_EXISTS)");
                } else {
                    boom("BeforeAll: status=409, но code неожиданный: " + code);
                    fail("User precondition failed. status=409, code=" + code + ", resp=" + resp);
                }
            } else {
                boom("BeforeAll: предусловие не выполнено. status=" + st);
                fail("User precondition failed. status=" + st + ", resp=" + resp);
            }
        }
    }

    @Test
    void addBlock_shouldAppendHeaderThenTextThenReaction() {
        ItRunContext.initIfNeeded();

        title("AddBlockIT: HEADER(0) + TEXT(1,2,3) + REACT(4->text1) без auth");
        System.out.println("Используем:");
        System.out.println("  login          = " + TestConfig.LOGIN());
        System.out.println("  blockchainName = " + TestConfig.BCH_NAME());
        System.out.println("Ожидание:");
        System.out.println("  1) HEADER  (global=0, line=0, lineNum=0, prevGlobal=ZERO64) -> 200");
        System.out.println("  2) TEXT#1  (global=1, line=1, lineNum=1, prevGlobal=hash0, prevLine=hash0) -> 200");
        System.out.println("  3) TEXT#2  (global=2, line=1, lineNum=2, prevGlobal=hash1, prevLine=hash1) -> 200");
        System.out.println("  4) TEXT#3  (global=3, line=1, lineNum=3, prevGlobal=hash2, prevLine=hash2) -> 200");
        System.out.println("  5) REACT#1 (global=4, line=2, lineNum=1, prevGlobal=hash3, prevLine=hash0) -> 200 (to TEXT#1)\n");

        try (WsTestClient client = new WsTestClient(TestConfig.WS_URI)) {

            // ============================
            // Локальное состояние теста
            // ============================
            int[]    lineLastNumber = new int[8];
            String[] lineLastHashHex = new String[8];
            for (int i = 0; i < 8; i++) lineLastHashHex[i] = "";

            int globalLastNumber = -1;
            String globalLastHashHex = ZERO64;

            byte[] headerHash32 = null; // понадобится как prevLineHash для первых блоков линий 1/2

            // =========================================================
            // ШАГ 1: HEADER (global=0, line=0, lineNum=0)
            // =========================================================
            stepTitle("ШАГ 1: AddBlock HEADER (global=0, line=0, lineNum=0)");

            BuiltBlock header = buildHeaderBlock(
                    0,
                    LINE_HEADER,
                    0,
                    ZERO32, // prevGlobalHash32
                    ZERO32  // prevLineHash32
            );

            String reqId1 = "it03-add-header";
            String reqJson1 = buildAddBlockJson(reqId1, TestConfig.BCH_NAME(), 0, ZERO64, base64(header.fullBytes));

            send("AddBlock(" + reqId1 + ")", reqJson1);
            String resp1 = client.request(reqId1, reqJson1, Duration.ofSeconds(8));
            recv("AddBlock(" + reqId1 + ")", resp1);

            assert200("AddBlock(" + reqId1 + ")", resp1);

            String serverLastGlobalHash0 = extractPayloadString(resp1, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash0, "HEADER: payload.serverLastGlobalHash must not be null");
            assertEquals(64, serverLastGlobalHash0.trim().length(), "HEADER: serverLastGlobalHash must be 64 hex chars");

            String localHash0 = bytesToHex64(header.hash32);
            assertEquals(localHash0, serverLastGlobalHash0, "HEADER: serverLastGlobalHash должен совпасть с локальным hash");

            // обновляем локальное состояние
            headerHash32 = header.hash32;
            globalLastNumber = 0;
            globalLastHashHex = localHash0;

            lineLastNumber[0] = 0;
            lineLastHashHex[0] = localHash0;

            ok("HEADER принят. serverLastGlobalHash=" + serverLastGlobalHash0);

            // =========================================================
            // Общая проверка: headerHash32 уже есть
            // =========================================================
            assertNotNull(headerHash32, "internal: headerHash32 must be set after header step");

            // =========================================================
            // ШАГ 2: TEXT#1 (global=1, line=1, lineNum=1)
            // prevLineHash для первого блока линии = hash(нулевого блока)
            // =========================================================
            stepTitle("ШАГ 2: AddBlock TEXT#1 (global=1, line=1, lineNum=1)");

            int text1LineNum = nextLineNum(lineLastNumber, LINE_TEXT);
            byte[] prevLineHashText1 = prevLineHash32(lineLastNumber, lineLastHashHex, headerHash32, LINE_TEXT);

            BuiltBlock text1 = buildTextBlock(
                    1,
                    LINE_TEXT,
                    text1LineNum,
                    hexToBytes32(globalLastHashHex), // prevGlobalHash32
                    prevLineHashText1,               // prevLineHash32
                    "Hello #1 from IT_03 test"
            );

            String reqId2 = "it03-add-text-1";
            String reqJson2 = buildAddBlockJson(reqId2, TestConfig.BCH_NAME(), 1, globalLastHashHex, base64(text1.fullBytes));

            send("AddBlock(" + reqId2 + ")", reqJson2);
            String resp2 = client.request(reqId2, reqJson2, Duration.ofSeconds(8));
            recv("AddBlock(" + reqId2 + ")", resp2);

            assert200("AddBlock(" + reqId2 + ")", resp2);

            String serverLastGlobalHash1 = extractPayloadString(resp2, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash1, "TEXT#1: payload.serverLastGlobalHash must not be null");
            assertEquals(64, serverLastGlobalHash1.trim().length(), "TEXT#1: serverLastGlobalHash must be 64 hex chars");

            String localHash1 = bytesToHex64(text1.hash32);
            assertEquals(localHash1, serverLastGlobalHash1, "TEXT#1: serverLastGlobalHash должен совпасть с локальным hash");

            // обновляем состояние
            globalLastNumber = 1;
            globalLastHashHex = localHash1;
            lineLastNumber[LINE_TEXT] = text1LineNum;
            lineLastHashHex[LINE_TEXT] = localHash1;

            ok("TEXT#1 принят. hash1=" + serverLastGlobalHash1);

            // =========================================================
            // ШАГ 3: TEXT#2 (global=2, line=1, lineNum=2)
            // prevLineHash для второго блока линии = hash(TEXT#1)
            // =========================================================
            stepTitle("ШАГ 3: AddBlock TEXT#2 (global=2, line=1, lineNum=2)");

            int text2LineNum = nextLineNum(lineLastNumber, LINE_TEXT);
            byte[] prevLineHashText2 = prevLineHash32(lineLastNumber, lineLastHashHex, headerHash32, LINE_TEXT);

            BuiltBlock text2 = buildTextBlock(
                    2,
                    LINE_TEXT,
                    text2LineNum,
                    hexToBytes32(globalLastHashHex),
                    prevLineHashText2,
                    "Hello #2 from IT_03 test"
            );

            String reqId3 = "it03-add-text-2";
            String reqJson3 = buildAddBlockJson(reqId3, TestConfig.BCH_NAME(), 2, globalLastHashHex, base64(text2.fullBytes));

            send("AddBlock(" + reqId3 + ")", reqJson3);
            String resp3 = client.request(reqId3, reqJson3, Duration.ofSeconds(8));
            recv("AddBlock(" + reqId3 + ")", resp3);

            assert200("AddBlock(" + reqId3 + ")", resp3);

            String serverLastGlobalHash2 = extractPayloadString(resp3, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash2, "TEXT#2: payload.serverLastGlobalHash must not be null");
            assertEquals(64, serverLastGlobalHash2.trim().length(), "TEXT#2: serverLastGlobalHash must be 64 hex chars");

            String localHash2 = bytesToHex64(text2.hash32);
            assertEquals(localHash2, serverLastGlobalHash2, "TEXT#2: serverLastGlobalHash должен совпасть с локальным hash");

            // обновляем состояние
            globalLastNumber = 2;
            globalLastHashHex = localHash2;
            lineLastNumber[LINE_TEXT] = text2LineNum;
            lineLastHashHex[LINE_TEXT] = localHash2;

            ok("TEXT#2 принят. hash2=" + serverLastGlobalHash2);

            // =========================================================
            // ШАГ 4: TEXT#3 (global=3, line=1, lineNum=3)
            // prevLineHash = hash(TEXT#2)
            // =========================================================
            stepTitle("ШАГ 4: AddBlock TEXT#3 (global=3, line=1, lineNum=3)");

            int text3LineNum = nextLineNum(lineLastNumber, LINE_TEXT);
            byte[] prevLineHashText3 = prevLineHash32(lineLastNumber, lineLastHashHex, headerHash32, LINE_TEXT);

            BuiltBlock text3 = buildTextBlock(
                    3,
                    LINE_TEXT,
                    text3LineNum,
                    hexToBytes32(globalLastHashHex),
                    prevLineHashText3,
                    "Hello #3 from IT_03 test"
            );

            String reqId4 = "it03-add-text-3";
            String reqJson4 = buildAddBlockJson(reqId4, TestConfig.BCH_NAME(), 3, globalLastHashHex, base64(text3.fullBytes));

            send("AddBlock(" + reqId4 + ")", reqJson4);
            String resp4 = client.request(reqId4, reqJson4, Duration.ofSeconds(8));
            recv("AddBlock(" + reqId4 + ")", resp4);

            assert200("AddBlock(" + reqId4 + ")", resp4);

            String serverLastGlobalHash3 = extractPayloadString(resp4, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash3, "TEXT#3: payload.serverLastGlobalHash must not be null");
            assertEquals(64, serverLastGlobalHash3.trim().length(), "TEXT#3: serverLastGlobalHash must be 64 hex chars");

            String localHash3 = bytesToHex64(text3.hash32);
            assertEquals(localHash3, serverLastGlobalHash3, "TEXT#3: serverLastGlobalHash должен совпасть с локальным hash");

            // обновляем состояние
            globalLastNumber = 3;
            globalLastHashHex = localHash3;
            lineLastNumber[LINE_TEXT] = text3LineNum;
            lineLastHashHex[LINE_TEXT] = localHash3;

            ok("TEXT#3 принят. hash3=" + serverLastGlobalHash3);

            // =========================================================
            // ШАГ 5: REACT#1 (global=4, line=2, lineNum=1) -> на TEXT#1
            // prevLineHash для первого блока line2 = hash(нулевого блока)
            // =========================================================
            stepTitle("ШАГ 5: AddBlock REACT#1 (global=4, line=2, lineNum=1) -> to TEXT#1");

            int react1LineNum = nextLineNum(lineLastNumber, LINE_REACT);
            byte[] prevLineHashReact1 = prevLineHash32(lineLastNumber, lineLastHashHex, headerHash32, LINE_REACT);

            // ссылка на TEXT#1 (global=1, hash=text1)
            String text1HashHex = lineHashAtOrThrow(text1, "text1.hash32");

            BuiltBlock react1 = buildReactionBlock(
                    4,
                    LINE_REACT,
                    react1LineNum,
                    hexToBytes32(globalLastHashHex),
                    prevLineHashReact1,
                    1, // reactionCode (пример: 1 = like)
                    TestConfig.BCH_NAME(),
                    1,                // toBlockGlobalNumber = 1 (TEXT#1)
                    text1.hash32      // toBlockHash32 = hash(TEXT#1)
            );

            String reqId5 = "it03-add-react-1";
            String reqJson5 = buildAddBlockJson(reqId5, TestConfig.BCH_NAME(), 4, globalLastHashHex, base64(react1.fullBytes));

            send("AddBlock(" + reqId5 + ")", reqJson5);
            String resp5 = client.request(reqId5, reqJson5, Duration.ofSeconds(8));
            recv("AddBlock(" + reqId5 + ")", resp5);

            assert200("AddBlock(" + reqId5 + ")", resp5);

            String serverLastGlobalHash4 = extractPayloadString(resp5, "serverLastGlobalHash");
            assertNotNull(serverLastGlobalHash4, "REACT#1: payload.serverLastGlobalHash must not be null");
            assertEquals(64, serverLastGlobalHash4.trim().length(), "REACT#1: serverLastGlobalHash must be 64 hex chars");

            String localHash4 = bytesToHex64(react1.hash32);
            assertEquals(localHash4, serverLastGlobalHash4, "REACT#1: serverLastGlobalHash должен совпасть с локальным hash");

            // обновляем состояние
            globalLastNumber = 4;
            globalLastHashHex = localHash4;
            lineLastNumber[LINE_REACT] = react1LineNum;
            lineLastHashHex[LINE_REACT] = localHash4;

            ok("REACT#1 принят. hash4=" + serverLastGlobalHash4);

            // =========================================================
            // Итоговый контроль массивов линий
            // =========================================================
            ok("ИТОГ по линиям:");
            ok("  line0: lastNum=" + lineLastNumber[0] + ", lastHash=" + lineLastHashHex[0]);
            ok("  line1: lastNum=" + lineLastNumber[1] + ", lastHash=" + lineLastHashHex[1]);
            ok("  line2: lastNum=" + lineLastNumber[2] + ", lastHash=" + lineLastHashHex[2]);

            ok("ТЕСТ ПРОЙДЕН: HEADER + 3xTEXT(line1) + 1xREACT(line2) успешно добавлены и согласованы по globalHash/lineHash");

        } catch (AssertionError | RuntimeException e) {
            boom("ТЕСТ УПАЛ: AddBlockIT. Причина: " + e.getMessage());
            throw e;
        }
    }

    // =================================================================================
    // LINE HELPERS
    // =================================================================================

    /** Следующий lineNum: если в линии было N блоков, новый будет N+1 (для line>0). Для line0 в этом тесте только 0. */
    private static int nextLineNum(int[] lineLastNumber, short lineIndex) {
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineIndex == 0) return 0; // у нас header фиксированно line0/num0
        return lineLastNumber[lineIndex] + 1;
    }

    /**
     * prevLineHash32 по твоему правилу:
     *  - для первого блока линии (lineLastNumber[line]==0): prevLineHash = hash(нулевого блока)
     *  - иначе: prevLineHash = hash последнего блока этой линии
     *
     * Важно: для line0 здесь не используем (header имеет prevLine=ZERO32).
     */
    private static byte[] prevLineHash32(int[] lineLastNumber, String[] lineLastHashHex, byte[] headerHash32, short lineIndex) {
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineIndex == 0) return ZERO32;

        if (lineLastNumber[lineIndex] == 0) {
            // первый блок линии -> от нулевого блока
            if (headerHash32 == null || headerHash32.length != 32) {
                throw new IllegalStateException("headerHash32 is not set but required for first block of line " + lineIndex);
            }
            return headerHash32;
        }

        String lastHex = lineLastHashHex[lineIndex];
        if (lastHex == null || lastHex.isBlank()) {
            throw new IllegalStateException("lineLastHashHex[" + lineIndex + "] is blank but lineLastNumber>0");
        }
        return hexToBytes32(lastHex);
    }

    private static String lineHashAtOrThrow(BuiltBlock b, String name) {
        if (b == null || b.hash32 == null || b.hash32.length != 32) throw new IllegalArgumentException(name + " must be 32 bytes");
        return bytesToHex64(b.hash32);
    }

    // =================================================================================
    // BUILD BLOCKS
    // =================================================================================

    /** Небольшой холдер, чтобы тест мог использовать hash32 как prevGlobal/prevLine и как toBlockHash. */
    private static final class BuiltBlock {
        final byte[] fullBytes;
        final byte[] hash32;

        BuiltBlock(byte[] fullBytes, byte[] hash32) {
            this.fullBytes = fullBytes;
            this.hash32 = hash32;
        }
    }

    private static BuiltBlock buildHeaderBlock(int globalNumber,
                                               short lineIndex,
                                               int lineBlockNumber,
                                               byte[] prevGlobalHash32,
                                               byte[] prevLineHash32) {

        HeaderBody body = new HeaderBody(TestConfig.LOGIN());
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildTextBlock(int globalNumber,
                                             short lineIndex,
                                             int lineBlockNumber,
                                             byte[] prevGlobalHash32,
                                             byte[] prevLineHash32,
                                             String text) {

        TextBody body = new TextBody(text);
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildReactionBlock(int globalNumber,
                                                 short lineIndex,
                                                 int lineBlockNumber,
                                                 byte[] prevGlobalHash32,
                                                 byte[] prevLineHash32,
                                                 int reactionCode,
                                                 String toBlockchainName,
                                                 int toBlockGlobalNumber,
                                                 byte[] toBlockHash32) {

        ReactionBody body = new ReactionBody(
                reactionCode,
                toBlockchainName,
                toBlockGlobalNumber,
                toBlockHash32 // [32] сырые 32 байта, как ты утвердил
        );

        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static BuiltBlock buildSignedBlockFullBytes(int globalNumber,
                                                        short lineIndex,
                                                        int lineBlockNumber,
                                                        byte[] bodyBytes,
                                                        byte[] prevGlobalHash32,
                                                        byte[] prevLineHash32) {

        long ts = System.currentTimeMillis() / 1000L;

        int recordSize = BchBlockEntry.RAW_HEADER_SIZE + bodyBytes.length;

        byte[] rawBytes = ByteBuffer.allocate(recordSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineBlockNumber)
                .put(bodyBytes)
                .array();

        // Ключевой момент: preimage должен совпасть с серверным правилом.
        // Сервер НЕ получает prevLineHash по сети — он берёт его из своего состояния линии.
        // Поэтому в тесте мы обязаны передавать сюда ровно тот же prevLineHash32 (см. prevLineHash32()).
        byte[] preimage = BchCryptoVerifier.buildPreimage(
                TestConfig.LOGIN(),
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier.sha256(preimage);

        byte[] signature64 = Ed25519Util.sign(hash32, TestConfig.LOGIN_PRIV_KEY());

        byte[] full = new BchBlockEntry(
                globalNumber,
                ts,
                lineIndex,
                lineBlockNumber,
                bodyBytes,
                signature64,
                hash32
        ).toBytes();

        return new BuiltBlock(full, hash32);
    }

    // =================================================================================
    // JSON HELPERS
    // =================================================================================

    private static String buildAddBlockJson(String requestId,
                                           String blockchainName,
                                           int globalNumber,
                                           String prevGlobalHashHex,
                                           String blockBytesB64) {
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "blockchainName": "%s",
                "globalNumber": %d,
                "prevGlobalHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
    }

    private static String extractPayloadString(String json, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field)) {
                return payload.get(field).asText();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    // =================================================================================
    // HEX HELPERS
    // =================================================================================

    private static byte[] hexToBytes32(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        String s = hex.trim();
        if (s.length() != 64) throw new IllegalArgumentException("hex must be 64 chars, got " + s.length());
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex at pos " + (i * 2));
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String bytesToHex64(byte[] b32) {
        if (b32 == null || b32.length != 32) throw new IllegalArgumentException("b32 must be 32 bytes");
        char[] out = new char[64];
        final char[] HEX = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 32; i++) {
            int v = b32[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}