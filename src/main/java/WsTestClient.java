import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class WsTestClient {

    // ==== Настройки клиента ====
    static final String WS_URL = "wss://shineup.me/ws";// "ws://localhost:8080/ws";

    // ==== Тестовые параметры ====
    static final String FIXED_PRIVATE_KEY_STRING = "SHiNE_TEST_FIXED_PRIVATE_KEY_2025";
    static final long   BLOCKCHAIN_ID   = 351130785469109974L;//777_000_001L;
    static final int    BLOCKCHAIN_TYPE = 0;
    static final int    BLOCKCHAIN_NUM  = 0;
    static final short  VERSION_USER_BCH = 1;
    static final long   PREV_USER_BCH_ID = 0L;
    static final String USER_LOGIN = "test_user";

    // ==== Опкоды ====
    static final int OP_ADD_BLOCK      = 1;
    static final int OP_GET_BLOCKCHAIN = 2;

    // ==== Статусы ====
    static final int STATUS_OK             = 200;
    static final int STATUS_BAD_REQUEST    = 400;
    static final int STATUS_ALREADY_EXISTS = 409;
    static final int STATUS_NON_SEQUENTIAL = 412;
    static final int STATUS_UNVERIFIED     = 422;
    static final int STATUS_INTERNAL       = 500;

    // ==== Типы блоков ====
    static final short TYPE_HEADER = 0;
    static final short TYPE_TEXT   = 1;
    static final short RECORD_TYPE_VERSION = 1; // Новое поле

    // ==== Константы формата ====
    static final int SIGNATURE_LEN = 64;
    static final int HASH_LEN = 32;
    static final int RAW_HEADER_SIZE = 4 + 4 + 8 + 2 + 2; // Теперь 20 байт

    public static void main(String[] args) throws Exception {
        System.out.println("=== WsTestClient v1.1 ===");

        byte[] priv32 = HashUtil.sha256(FIXED_PRIVATE_KEY_STRING.getBytes(StandardCharsets.UTF_8));
        byte[] pub32  = Ed25519Util.derivePublicKey(priv32);

        WsBinaryCollector reader = new WsBinaryCollector();
        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), reader)
                .join();
        System.out.println("✅ Connected to " + WS_URL);

        // === 1. Создание заглавного блока ===
        byte[] headerBody = buildHeaderBody(USER_LOGIN, BLOCKCHAIN_ID, BLOCKCHAIN_TYPE, BLOCKCHAIN_NUM,
                VERSION_USER_BCH, PREV_USER_BCH_ID, pub32);

        long ts = Instant.now().getEpochSecond();
        byte[] rawHeader = buildRawRecord(0, ts, TYPE_HEADER, RECORD_TYPE_VERSION, headerBody);
        byte[] fullHeader = signAndPack(rawHeader, USER_LOGIN, BLOCKCHAIN_ID, new byte[32], priv32, pub32);

        byte[] addHeaderMsg = concat(beInt(OP_ADD_BLOCK), beLong(BLOCKCHAIN_ID), fullHeader);
        int st1 = sendAndReadStatus(ws, addHeaderMsg, reader);
        System.out.println("ADD HEADER → " + st1 + " (" + statusName(st1) + ")");

        // === 2. Получаем всю цепочку ===
        ResponseWithPayload chainResp = sendAndReadPayload(ws, concat(beInt(OP_GET_BLOCKCHAIN), beLong(BLOCKCHAIN_ID)), reader);
        System.out.println("GET_BLOCKCHAIN → " + chainResp.status + " (" + statusName(chainResp.status) + ")");
        if (chainResp.status != STATUS_OK) return;

        List<BlockParsed> blocks = parseAllBlocks(chainResp.payload);
        System.out.println("Chain contains " + blocks.size() + " blocks:");

        for (BlockParsed bp : blocks) {
            printBlock(bp);
        }

        // === 3. Добавление нового текстового блока ===
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        System.out.print("\nВведите текст для добавления в блокчейн (Enter — пропустить): ");
        String text = sc.nextLine().trim();
        if (!text.isEmpty()) {
            byte[] lastHash = blocks.isEmpty() ? new byte[32] : blocks.get(blocks.size() - 1).hash32;
            int nextNum = blocks.isEmpty() ? 0 : (blocks.get(blocks.size() - 1).recordNumber + 1);

            byte[] textBody = text.getBytes(StandardCharsets.UTF_8);
            byte[] rawText = buildRawRecord(nextNum, Instant.now().getEpochSecond(), TYPE_TEXT, RECORD_TYPE_VERSION, textBody);
            byte[] fullText = signAndPack(rawText, USER_LOGIN, BLOCKCHAIN_ID, lastHash, priv32, pub32);

            int st2 = sendAndReadStatus(ws, concat(beInt(OP_ADD_BLOCK), beLong(BLOCKCHAIN_ID), fullText), reader);
            System.out.println("ADD TEXT → " + st2 + " (" + statusName(st2) + ")");
        }

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        System.out.println("=== Done ===");
    }

    // ==============================================================
    //                        БЛОКИ
    // ==============================================================

    static byte[] buildRawRecord(int recordNumber, long timestampSec,
                                 short recordType, short recordTypeVersion, byte[] body) {
        int recordSize = RAW_HEADER_SIZE + body.length;
        ByteBuffer buf = ByteBuffer.allocate(recordSize).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(recordSize);
        buf.putInt(recordNumber);
        buf.putLong(timestampSec);
        buf.putShort(recordType);
        buf.putShort(recordTypeVersion);
        buf.put(body);
        return buf.array();
    }

    static byte[] buildHeaderBody(String userLogin, long blockchainId, int blockchainType,
                                  int blockchainNumber, short versionUserBch,
                                  long prevUserBchId, byte[] publicKey32) {
        byte[] tag = "SHiNE001".getBytes(StandardCharsets.US_ASCII);
        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        if (loginUtf8.length > 255) throw new IllegalArgumentException("Логин слишком длинный");

        int cap = 8 + 8 + 1 + loginUtf8.length + 4 + 4 + 2 + 8 + 32;
        ByteBuffer buf = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
        buf.put(tag);
        buf.putLong(blockchainId);
        buf.put((byte) loginUtf8.length);
        buf.put(loginUtf8);
        buf.putInt(blockchainType);
        buf.putInt(blockchainNumber);
        buf.putShort(versionUserBch);
        buf.putLong(prevUserBchId);
        buf.put(publicKey32);
        return buf.array();
    }

    static byte[] signAndPack(byte[] rawBytes, String userLogin, long blockchainId,
                              byte[] prevHash32, byte[] privateKey32, byte[] publicKey32) {
        byte[] preimage = buildPreimage(userLogin, blockchainId, prevHash32, rawBytes);
        byte[] hash32 = HashUtil.sha256(preimage);
        byte[] sig64 = Ed25519Util.sign(preimage, privateKey32);
        return concat(rawBytes, sig64, hash32);
    }

    // ==============================================================
    //                        ПАРСИНГ
    // ==============================================================

    static class BlockParsed {
        int recordSize;
        int recordNumber;
        long timestamp;
        short recordType;
        short recordTypeVersion;
        byte[] body;
        byte[] signature64;
        byte[] hash32;
    }

    static List<BlockParsed> parseAllBlocks(byte[] file) {
        List<BlockParsed> out = new ArrayList<>();
        int p = 0;
        while (p + 4 <= file.length) {
            int recordSize = beInt(file, p);
            int total = recordSize + SIGNATURE_LEN + HASH_LEN;
            if (p + total > file.length) break;

            ByteBuffer raw = ByteBuffer.wrap(file, p, recordSize).order(ByteOrder.BIG_ENDIAN);
            BlockParsed bp = new BlockParsed();
            bp.recordSize = raw.getInt();
            bp.recordNumber = raw.getInt();
            bp.timestamp = raw.getLong();
            bp.recordType = raw.getShort();
            bp.recordTypeVersion = raw.getShort();
            int bodyLen = bp.recordSize - RAW_HEADER_SIZE;
            bp.body = new byte[bodyLen];
            raw.get(bp.body);
            bp.signature64 = Arrays.copyOfRange(file, p + recordSize, p + recordSize + SIGNATURE_LEN);
            bp.hash32 = Arrays.copyOfRange(file, p + recordSize + SIGNATURE_LEN, p + recordSize + SIGNATURE_LEN + HASH_LEN);
            out.add(bp);
            p += total;
        }
        return out;
    }

    static void printBlock(BlockParsed b) {
        System.out.println("------------------------------------------------------------");
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(b.timestamp));
        System.out.printf("num=%d, type=%d, ver=%d, ts=%s, size=%d%n",
                b.recordNumber, b.recordType, b.recordTypeVersion, ts, b.recordSize);

        if (b.recordType == TYPE_HEADER)
            printHeaderBody(b.body);
        else if (b.recordType == TYPE_TEXT)
            System.out.println("TEXT: " + new String(b.body, StandardCharsets.UTF_8));
        else
            System.out.println("UNKNOWN BODY (" + b.body.length + " bytes)");

        System.out.println("hash=" + toHex(b.hash32));
    }

    static void printHeaderBody(byte[] body) {
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        byte[] tag = new byte[8]; buf.get(tag);
        long id = buf.getLong();
        int n = Byte.toUnsignedInt(buf.get());
        byte[] login = new byte[n]; buf.get(login);
        int type = buf.getInt();
        int num = buf.getInt();
        buf.getShort(); buf.getLong(); // version + prev
        byte[] pub = new byte[32]; buf.get(pub);

        System.out.println("HEADER: login=" + new String(login, StandardCharsets.UTF_8) +
                ", id=" + id + ", type=" + type + ", num=" + num);
        System.out.println("(pubkey first 4 bytes: " + toHex(Arrays.copyOf(pub, 4)) + "...)");
    }

    // ==============================================================
    //                   Вебсокет и вспомогательные классы
    // ==============================================================

    static int sendAndReadStatus(WebSocket ws, byte[] payload, WsBinaryCollector reader) {
        ws.sendBinary(ByteBuffer.wrap(payload), true).join();
        byte[] resp = reader.collect(ws);
        if (resp == null || resp.length < 4) throw new IllegalStateException("empty response");
        return beInt(resp, 0);
    }

    static class ResponseWithPayload {
        int status;
        byte[] payload;
    }

    static ResponseWithPayload sendAndReadPayload(WebSocket ws, byte[] payload, WsBinaryCollector reader) {
        ws.sendBinary(ByteBuffer.wrap(payload), true).join();
        byte[] resp = reader.collect(ws);
        ResponseWithPayload out = new ResponseWithPayload();
        out.status = beInt(resp, 0);
        if (out.status == STATUS_OK) {
            int len = beInt(resp, 4);
            out.payload = Arrays.copyOfRange(resp, 8, 8 + len);
        }
        return out;
    }

    static class WsBinaryCollector implements WebSocket.Listener {
        private volatile CompletableFuture<byte[]> future = new CompletableFuture<>();
        private ByteBuffer acc = ByteBuffer.allocate(0);

        public synchronized byte[] collect(WebSocket ws) {
            acc = ByteBuffer.allocate(0);
            future = new CompletableFuture<>();
            ws.request(1);
            return future.join();
        }

        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ByteBuffer newBuf = ByteBuffer.allocate(acc.remaining() + data.remaining());
            newBuf.put(acc); newBuf.put(data); newBuf.flip();
            acc = newBuf;
            if (last) {
                byte[] all = new byte[acc.remaining()];
                acc.get(all);
                future.complete(all);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (last) future.complete(data.toString().getBytes(StandardCharsets.UTF_8));
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws, Throwable error) { future.completeExceptionally(error); }
    }

    // ==============================================================
    //                     Крипто и утилиты
    // ==============================================================

    static byte[] buildPreimage(String userLogin, long blockchainId, byte[] prevHash32, byte[] rawBytes) {
        byte[] loginUtf8 = userLogin.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(loginUtf8.length + 8 + 32 + rawBytes.length).order(ByteOrder.BIG_ENDIAN);
        buf.put(loginUtf8);
        buf.putLong(blockchainId);
        buf.put(prevHash32);
        buf.put(rawBytes);
        return buf.array();
    }

    static final class HashUtil {
        static byte[] sha256(byte[] data) {
            org.bouncycastle.crypto.digests.SHA256Digest d = new org.bouncycastle.crypto.digests.SHA256Digest();
            d.update(data, 0, data.length);
            byte[] out = new byte[32];
            d.doFinal(out, 0);
            return out;
        }
    }

    static final class Ed25519Util {
        static byte[] derivePublicKey(byte[] privateKey32) {
            var priv = new org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKey32, 0);
            return priv.generatePublicKey().getEncoded();
        }
        static byte[] sign(byte[] data, byte[] privateKey32) {
            var priv = new org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privateKey32, 0);
            var signer = new org.bouncycastle.crypto.signers.Ed25519Signer();
            signer.init(true, priv);
            signer.update(data, 0, data.length);
            return signer.generateSignature();
        }
    }

    // ==== Утилиты ====
    static byte[] concat(byte[]... parts) {
        int n = Arrays.stream(parts).mapToInt(a -> a.length).sum();
        byte[] out = new byte[n];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    static byte[] beInt(int v) { return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array(); }
    static byte[] beLong(long v) { return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(v).array(); }
    static int beInt(byte[] a, int off) { return ByteBuffer.wrap(a, off, 4).order(ByteOrder.BIG_ENDIAN).getInt(); }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String statusName(int code) {
        return switch (code) {
            case STATUS_OK -> "OK";
            case STATUS_BAD_REQUEST -> "BAD_REQUEST";
            case STATUS_ALREADY_EXISTS -> "ALREADY_EXISTS";
            case STATUS_NON_SEQUENTIAL -> "NON_SEQUENTIAL";
            case STATUS_UNVERIFIED -> "UNVERIFIED";
            case STATUS_INTERNAL -> "INTERNAL_ERROR";
            default -> "UNKNOWN";
        };
    }
}

