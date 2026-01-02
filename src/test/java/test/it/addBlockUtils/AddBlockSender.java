package test.it.addBlockUtils;

import blockchain.BchBlockEntry;
import blockchain.BchCryptoVerifier;
import blockchain.body.BodyRecord;
import test.it.utils.TestConfig;
import test.it.utils.TestLog;
import utils.crypto.Ed25519Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AddBlockSender — "одна кнопка":
 *  - принимает ГОТОВЫЙ Body (HeaderBody/TextBody/ReactionBody)
 *  - сам берёт номера/prev-hash из ChainState
 *  - строит raw/hash/signature
 *  - собирает BchBlockEntry (старый, без изменений)
 *  - отправляет AddBlock
 *  - проверяет serverLastGlobalHash == localHash
 *  - обновляет ChainState
 *
 * В тестах:
 *   sender.send(body, timeout);
 */
public final class AddBlockSender {

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    private final ChainState state;

    public AddBlockSender(ChainState state) {
        this.state = state;
    }

    public ChainState state() {
        return state;
    }

    /**
     * Отправить следующий блок по body.expectedLineIndex().
     * Ничего не возвращает — состояние хранится в ChainState.
     */
    public void send(BodyRecord body, Duration timeout) {
        if (body == null) throw new IllegalArgumentException("body == null");

        short lineIndex = body.expectedLineIndex();

        // header должен быть первым
        if (lineIndex == 0) {
            if (state.globalLastNumber() != -1) {
                throw new IllegalStateException("HEADER должен быть первым: globalLastNumber уже " + state.globalLastNumber());
            }
        } else {
            if (!state.hasHeader()) {
                throw new IllegalStateException("Нельзя слать line=" + lineIndex + " до HEADER (нет headerHash32)");
            }
        }

        int globalNumber = state.nextGlobalNumber();
        int lineNumber = state.nextLineNumber(lineIndex);

        byte[] prevGlobalHash32 = (lineIndex == 0) ? ZERO32 : state.prevGlobalHash32ForNext(lineIndex);
        byte[] prevLineHash32   = (lineIndex == 0) ? ZERO32 : state.prevLineHash32ForNext(lineIndex);

        long ts = System.currentTimeMillis() / 1000L;

        byte[] bodyBytes = body.toBytes();

        // RAW bytes (ровно то, что подписываем/хэшируем)
        int recordSize = BchBlockEntry.RAW_HEADER_SIZE + bodyBytes.length;

        byte[] rawBytes = ByteBuffer.allocate(recordSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineNumber)
                .put(bodyBytes)
                .array();

        // preimage -> sha256 -> signature
        byte[] preimage = BchCryptoVerifier.buildPreimage(
                TestConfig.LOGIN(),
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );
        byte[] hash32 = BchCryptoVerifier.sha256(preimage);
        byte[] signature64 = Ed25519Util.sign(hash32, TestConfig.LOGIN_PRIV_KEY());

        // Собираем полный блок (BchBlockEntry не меняем)
        BchBlockEntry entry = new BchBlockEntry(
                globalNumber,
                ts,
                lineIndex,
                lineNumber,
                bodyBytes,
                signature64,
                hash32
        );

        // отправляем JSON
        String prevGlobalHashHex = (globalNumber == 0) ? ZERO64 : state.globalLastHashHex();

        String req = buildAddBlockJson(
                TestConfig.BCH_NAME(),
                globalNumber,
                prevGlobalHashHex,
                base64(entry.toBytes())
        );

        String op = "AddBlock (global=" + globalNumber + ", line=" + lineIndex + ", lineNum=" + lineNumber + ")";
        String resp = WsJsonOneShot.request(op, req, timeout);

        assert200(op, resp);

        String serverLastGlobalHash = extractPayloadString(resp, "serverLastGlobalHash");
        assertNotNull(serverLastGlobalHash, op + ": payload.serverLastGlobalHash must not be null");
        assertEquals(64, serverLastGlobalHash.trim().length(), op + ": serverLastGlobalHash must be 64 hex chars");

        String localHashHex = bytesToHex64(hash32);

        if (TestConfig.DEBUG()) {
            TestLog.ok(op + ": localHash=" + localHashHex);
            TestLog.ok(op + ": serverLastGlobalHash=" + serverLastGlobalHash);
        }

        assertEquals(localHashHex, serverLastGlobalHash, op + ": serverLastGlobalHash must match local hash");

        // обновляем ChainState
        state.applyAppendedBlock(globalNumber, lineIndex, lineNumber, hash32);

        if (TestConfig.DEBUG()) {
            TestLog.ok(op + ": state updated");
        }
    }

    // -------------------- json helpers --------------------

    private static String buildAddBlockJson(String blockchainName,
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
            """.formatted(WsJsonOneShot.FIXED_REQUEST_ID, blockchainName, globalNumber, prevGlobalHashHex, blockBytesB64);
    }

    private static void assert200(String op, String resp) {
        int st = test.it.utils.JsonParsers.status(resp);
        assertEquals(200, st, op + ": expected status=200, but got=" + st + ", resp=" + resp);
        if (TestConfig.DEBUG()) TestLog.ok(op + ": status=200");
    }

    private static String extractPayloadString(String json, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode payload = root.get("payload");
            if (payload != null && payload.has(field)) return payload.get(field).asText();
        } catch (Exception ignore) {}
        return null;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String bytesToHex64(byte[] b32) {
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