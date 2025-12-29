package test.it.ws;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.HeaderBody;
import blockchain.body.ReactionBody;
import blockchain.body.TextBody;
import test.it.utils.TestConfig;
import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

/**
 * AddBlockScenarioRunner
 *
 * Хранит локальное состояние:
 *  - globalLastHashHex / globalLastNumber
 *  - lineLastNumber[line] / lineLastHashHex[line]
 *  - headerHash32 (нужен как prevLineHash для первых блоков линий)
 *
 * Умеет:
 *  - собрать блок (header/text/react)
 *  - отправить AddBlock по сети (каждый запрос = новое WS соединение)
 *  - обновить локальное состояние
 */
public final class AddBlockScenarioRunner {

    // requestId делаем фиксированный (как ты попросил)
    private static final String FIXED_REQUEST_ID = "it03";

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    private final String wsUri;
    private final String blockchainName;

    // Локальное состояние (как и было в тесте)
    private final int[] lineLastNumber = new int[8];
    private final String[] lineLastHashHex = new String[8];

    private int globalLastNumber = -1;
    private String globalLastHashHex = ZERO64;

    private byte[] headerHash32 = null;

    public AddBlockScenarioRunner(String wsUri, String blockchainName) {
        this.wsUri = wsUri;
        this.blockchainName = blockchainName;

        for (int i = 0; i < 8; i++) lineLastHashHex[i] = "";
    }

    // =================================================================================
    // PUBLIC API
    // =================================================================================

    public String getGlobalLastHashHex() {
        return globalLastHashHex;
    }

    public int getGlobalLastNumber() {
        return globalLastNumber;
    }

    public int getLineLastNumber(int lineIndex) {
        return lineLastNumber[lineIndex];
    }

    public String getLineLastHashHex(int lineIndex) {
        return lineLastHashHex[lineIndex];
    }

    /** Добавить HEADER (global=0, line=0, lineNum=0). */
    public AddBlockResult addHeader(short lineHeader) {
        BuiltBlock header = buildHeaderBlock(
                0,
                lineHeader,
                0,
                ZERO32,
                ZERO32
        );

        String reqJson = buildAddBlockJson(FIXED_REQUEST_ID, blockchainName, 0, ZERO64, base64(header.fullBytes));
        String resp = WsJsonRoundtripClient.sendOnce(wsUri, reqJson, Duration.ofSeconds(8));

        // локальный hash
        String localHash0 = bytesToHex64(header.hash32);

        // обновляем состояние (как раньше)
        headerHash32 = header.hash32;
        globalLastNumber = 0;
        globalLastHashHex = localHash0;
        lineLastNumber[0] = 0;
        lineLastHashHex[0] = localHash0;

        return new AddBlockResult(reqJson, resp, localHash0);
    }

    /** Добавить TEXT в lineText, следующим lineNum, global=globalNumber. */
    public AddBlockResult addText(int globalNumber, short lineText, String text) {
        int lineNum = nextLineNum(lineText);
        byte[] prevLineHash = prevLineHash32(lineText);

        BuiltBlock b = buildTextBlock(
                globalNumber,
                lineText,
                lineNum,
                hexToBytes32(globalLastHashHex),
                prevLineHash,
                text
        );

        String reqJson = buildAddBlockJson(FIXED_REQUEST_ID, blockchainName, globalNumber, globalLastHashHex, base64(b.fullBytes));
        String resp = WsJsonRoundtripClient.sendOnce(wsUri, reqJson, Duration.ofSeconds(8));

        String localHash = bytesToHex64(b.hash32);

        // обновляем состояние
        globalLastNumber = globalNumber;
        globalLastHashHex = localHash;
        lineLastNumber[lineText] = lineNum;
        lineLastHashHex[lineText] = localHash;

        return new AddBlockResult(reqJson, resp, localHash, b.hash32);
    }

    /** Добавить REACT в lineReact, следующим lineNum, global=globalNumber, ссылка на (toGlobal,toHash32). */
    public AddBlockResult addReaction(int globalNumber,
                                      short lineReact,
                                      int reactionCode,
                                      String toBlockchainName,
                                      int toBlockGlobalNumber,
                                      byte[] toBlockHash32) {

        int lineNum = nextLineNum(lineReact);
        byte[] prevLineHash = prevLineHash32(lineReact);

        BuiltBlock b = buildReactionBlock(
                globalNumber,
                lineReact,
                lineNum,
                hexToBytes32(globalLastHashHex),
                prevLineHash,
                reactionCode,
                toBlockchainName,
                toBlockGlobalNumber,
                toBlockHash32
        );

        String reqJson = buildAddBlockJson(FIXED_REQUEST_ID, blockchainName, globalNumber, globalLastHashHex, base64(b.fullBytes));
        String resp = WsJsonRoundtripClient.sendOnce(wsUri, reqJson, Duration.ofSeconds(8));

        String localHash = bytesToHex64(b.hash32);

        // обновляем состояние
        globalLastNumber = globalNumber;
        globalLastHashHex = localHash;
        lineLastNumber[lineReact] = lineNum;
        lineLastHashHex[lineReact] = localHash;

        return new AddBlockResult(reqJson, resp, localHash, b.hash32);
    }

    // =================================================================================
    // RESULT HOLDER
    // =================================================================================

    public static final class AddBlockResult {
        public final String requestJson;
        public final String responseJson;

        /** локально вычисленный hash (HEX64) именно для этого блока */
        public final String localHashHex;

        /** локальный hash32 (если надо ссылаться на блок дальше) */
        public final byte[] localHash32;

        public AddBlockResult(String requestJson, String responseJson, String localHashHex) {
            this(requestJson, responseJson, localHashHex, null);
        }

        public AddBlockResult(String requestJson, String responseJson, String localHashHex, byte[] localHash32) {
            this.requestJson = requestJson;
            this.responseJson = responseJson;
            this.localHashHex = localHashHex;
            this.localHash32 = localHash32;
        }
    }

    // =================================================================================
    // LINE HELPERS
    // =================================================================================

    /** Следующий lineNum: если в линии было N блоков, новый будет N+1 (для line>0). Для line0 тут только 0. */
    private int nextLineNum(short lineIndex) {
        if (lineIndex < 0 || lineIndex > 7) throw new IllegalArgumentException("lineIndex must be 0..7");
        if (lineIndex == 0) return 0;
        return lineLastNumber[lineIndex] + 1;
    }

    /**
     * prevLineHash32 по твоему правилу:
     *  - для первого блока линии (lineLastNumber[line]==0): prevLineHash = hash(нулевого блока)
     *  - иначе: prevLineHash = hash последнего блока этой линии
     *
     * Важно: для line0 здесь не используем (header имеет prevLine=ZERO32).
     */
    private byte[] prevLineHash32(short lineIndex) {
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

    // =================================================================================
    // BUILD BLOCKS
    // =================================================================================

    /** Небольшой холдер, чтобы сценарий мог использовать hash32 как prevGlobal/prevLine и как toBlockHash. */
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

        // ⚠️ ВАЖНО:
        // У тебя сервер ругается: "Body is in wrong lineIndex expected=1 actual=0 (type=1 ver=1)".
        // Это значит, что lineIndex хранится ВНУТРИ bodyBytes.
        // Ниже — безопасный патч: предполагаем формат "type(1) + ver(1) + lineIndex(2)" и проставляем lineIndex.
        bodyBytes = patchBodyLineIndexIfPresent(bodyBytes, lineIndex);

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

        // Аналогично TextBody — если внутри есть lineIndex, проставляем.
        bodyBytes = patchBodyLineIndexIfPresent(bodyBytes, lineIndex);

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
        // Поэтому мы обязаны передавать сюда ровно тот же prevLineHash32 (см. prevLineHash32()).
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

    /**
     * Патч lineIndex внутри bodyBytes.
     *
     * Предположение (по твоей ошибке type=1 ver=1):
     *  bodyBytes[0] = type
     *  bodyBytes[1] = ver
     *  bodyBytes[2..3] = lineIndex (big-endian short)
     *
     * Если формат другой — скажешь, поменяю оффсет/проверки.
     */
    private static byte[] patchBodyLineIndexIfPresent(byte[] bodyBytes, short lineIndex) {
        if (bodyBytes == null) return null;
        if (bodyBytes.length < 4) return bodyBytes;

        // Патчим только для line>0 (для header line=0 и так норм).
        if (lineIndex <= 0) return bodyBytes;

        ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN).putShort(2, lineIndex);
        return bodyBytes;
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