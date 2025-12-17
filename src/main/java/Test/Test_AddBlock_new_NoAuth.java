package Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utils.crypto.Ed25519Util;
import blockchain.body.HeaderBody;
import blockchain.body.TextBody;
import blockchain_new.BchCryptoVerifier_new;
import blockchain_new.BchBlockEntry_new;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class Test_AddBlock_new_NoAuth {

    private static final String WS_URI = "ws://localhost:7070/ws";
    private static final ObjectMapper JSON = new ObjectMapper();

    // ======= ДАННЫЕ (взяты по аналогии с твоим тестом) =======
    private static final String TEST_LOGIN = "anya24";
    private static final long   TEST_BCH_ID = 4222L;

    private static final byte[] LOGIN_PRIV_KEY;
    private static final byte[] LOGIN_PUB_KEY;

    static {
        LOGIN_PRIV_KEY = Ed25519Util.generatePrivateKeyFromString("test-ed25519-login-11" + TEST_LOGIN);
        LOGIN_PUB_KEY  = Ed25519Util.derivePublicKey(LOGIN_PRIV_KEY);
    }

    // Нулевой хэш (для первого блока)
    private static final byte[] ZERO32 = new byte[32];

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URI), new WebSocket.Listener() {

                    private int step = 0;

                    // сервер просил в request: blockchainId + globalNumber + prevGlobalHash + bytes блока
                    // prevLineHash сервер может не просить — но для подписи нам он нужен
                    private byte[] lastGlobalHash = ZERO32;
                    private byte[] lastLineHash   = ZERO32;

                    @Override
                    public void onOpen(WebSocket ws) {
                        System.out.println("✅ WS connected: " + WS_URI);
                        ws.request(1);

                        // 1) Header block
                        byte[] headerFull = buildHeaderBlockFullBytes(
                                /*global*/0,
                                /*lineIndex*/(short)0,
                                /*lineBlock*/0,
                                lastGlobalHash,
                                lastLineHash
                        );

                        String json = buildAddBlockJson("test-add-header", TEST_BCH_ID, 0, bytesToHex(lastGlobalHash), base64(headerFull));
                        System.out.println("\n📤 SEND #1 (HEADER):\n" + json);
                        ws.sendText(json, true);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        String msg = data.toString();
                        System.out.println("\n📥 RECV:\n" + msg);
                        System.out.println("-----------------------------------------------------");

                        try {
                            int status = extractStatus(msg);
                            if (step == 0) {
                                if (status != 200) {
                                    System.out.println("❌ HEADER rejected, status=" + status);
                                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "fail");
                                    return CompletableFuture.completedFuture(null);
                                }

                                // Обновляем prev-хэши для следующего блока: берём хэш из нашего же блока (как ожидаемую цепочку)
                                byte[] headerFull = lastSentBlockFullFromResponseOrLocalFallback(true);
                                // Fallback: просто пересоберём ровно так же (надёжнее: хранить отправленные байты)
                                headerFull = buildHeaderBlockFullBytes(0, (short)0, 0, ZERO32, ZERO32);

                                BchBlockEntry_new hb = new BchBlockEntry_new(headerFull);
                                lastGlobalHash = hb.getHash32();
                                lastLineHash   = hb.getHash32();

                                // 2) Text block
                                byte[] textFull = buildTextBlockFullBytes(
                                        /*global*/1,
                                        /*lineIndex*/(short)0,
                                        /*lineBlock*/1,
                                        lastGlobalHash,
                                        lastLineHash,
                                        "Hello from test client"
                                );

                                String json2 = buildAddBlockJson("test-add-text", TEST_BCH_ID, 1, bytesToHex(lastGlobalHash), base64(textFull));
                                System.out.println("\n📤 SEND #2 (TEXT):\n" + json2);
                                step = 1;
                                ws.sendText(json2, true);

                            } else if (step == 1) {
                                System.out.println("✅ Done. Closing.");
                                ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
                            }

                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "exception");
                        }

                        ws.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        System.out.println("❌ WS error: " + error.getMessage());
                        error.printStackTrace(System.out);
                        latch.countDown();
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        System.out.println("🔚 WS closed. code=" + statusCode + " reason=" + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }
                }).join();

        latch.await();
    }

    // =================================================================================
    //                                   BUILD BLOCKS
    // =================================================================================

    private static byte[] buildHeaderBlockFullBytes(int globalNumber,
                                                    short lineIndex,
                                                    int lineBlockNumber,
                                                    byte[] prevGlobalHash32,
                                                    byte[] prevLineHash32) {

        // bodyBytes (включая type+version внутри)
        HeaderBody body = new HeaderBody(
                TEST_BCH_ID,
                TEST_LOGIN,
                0, 0,
                (short) 1,
                0L,
                LOGIN_PUB_KEY
        );
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static byte[] buildTextBlockFullBytes(int globalNumber,
                                                  short lineIndex,
                                                  int lineBlockNumber,
                                                  byte[] prevGlobalHash32,
                                                  byte[] prevLineHash32,
                                                  String text) {
        TextBody body = new TextBody(text);
        byte[] bodyBytes = body.toBytes();

        return buildSignedBlockFullBytes(globalNumber, lineIndex, lineBlockNumber, bodyBytes, prevGlobalHash32, prevLineHash32);
    }

    private static byte[] buildSignedBlockFullBytes(int globalNumber,
                                                    short lineIndex,
                                                    int lineBlockNumber,
                                                    byte[] bodyBytes,
                                                    byte[] prevGlobalHash32,
                                                    byte[] prevLineHash32) {

        long ts = System.currentTimeMillis() / 1000L;

        // Собираем rawBytes вручную в точности как BchBlockEntry_new RAW:
        // [4]recordSize [4]recordNumber [8]ts [2]lineIndex [4]lineBlockNumber [body...]
        int recordSize =
                BchBlockEntry_new.RAW_HEADER_SIZE +
                bodyBytes.length +
                BchBlockEntry_new.SIGNATURE_LEN +
                BchBlockEntry_new.HASH_LEN;

        byte[] rawBytes = ByteBuffer.allocate(BchBlockEntry_new.RAW_HEADER_SIZE + bodyBytes.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(recordSize)
                .putInt(globalNumber)
                .putLong(ts)
                .putShort(lineIndex)
                .putInt(lineBlockNumber)
                .put(bodyBytes)
                .array();

        byte[] preimage = BchCryptoVerifier_new.buildPreimage(
                TEST_LOGIN,
                prevGlobalHash32,
                prevLineHash32,
                rawBytes
        );

        byte[] hash32 = BchCryptoVerifier_new.sha256(preimage);

        // ВАЖНО: если у тебя в протоколе подпись делается НЕ по hash32, а по preimage — замени тут на preimage
        byte[] signature64 = Ed25519Util.sign(hash32, LOGIN_PRIV_KEY);

        // FULL block
        return new BchBlockEntry_new(
                globalNumber,
                ts,
                lineIndex,
                lineBlockNumber,
                bodyBytes,
                signature64,
                hash32
        ).toBytes();
    }

    // =================================================================================
    //                                    JSON BUILD
    // =================================================================================

    private static String buildAddBlockJson(String requestId,
                                           long blockchainId,
                                           int globalNumber,
                                           String prevGlobalHashHex,
                                           String blockBytesB64) {
        // Если у тебя в Net_AddBlock_new_Request другие имена полей — скажешь, подправлю.
        return """
            {
              "op": "AddBlock",
              "requestId": "%s",
              "payload": {
                "login": "%s",
                "blockchainId": %d,
                "globalNumber": %d,
                "prevGlobalHash": "%s",
                "blockBytesB64": "%s"
              }
            }
            """.formatted(requestId, TEST_LOGIN, blockchainId, globalNumber, prevGlobalHashHex, blockBytesB64);
    }

    // =================================================================================
    //                                    HELPERS
    // =================================================================================

    private static int extractStatus(String json) {
        try {
            JsonNode root = JSON.readTree(json);
            if (root.has("status")) return root.get("status").asInt();
        } catch (Exception ignore) {}
        return -1;
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // Заглушка: в этом тесте проще хранить отправленные байты локально.
    private static byte[] lastSentBlockFullFromResponseOrLocalFallback(boolean header) {
        return null;
    }
}