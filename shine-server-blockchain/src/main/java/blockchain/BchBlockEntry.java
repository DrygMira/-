package blockchain;

import blockchain.body.BodyRecord;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * BchBlockEntry — универсальный блок формата SHiNE (Frame v0).
 *
 * =========================================================================
 *  FRAME v0 — ФИКСИРОВАННЫЙ ФОРМАТ БЛОКА (ДОКУМЕНТ ПРОТОКОЛА)
 * =========================================================================
 *
 * Все числа BigEndian.
 *
 * PREIMAGE (входит в blockSize, подписывается):
 *   [2]  frameCode        (uint16)   код/версия рамки:
 *                                     - 0x0000 = Frame v0 (текущий)
 *   [32] prevHash32       (bytes)    SHA-256(preimage) предыдущего блока (цепочка)
 *   [4]  blockSize        (int32)    размер preimage (в байтах), ВКЛЮЧАЯ frameCode,
 *                                   НО БЕЗ sigMarker и БЕЗ signature64
 *   [4]  blockNumber      (int32)    глобальный номер блока (>=0)
 *   [8]  timestamp        (int64)    unix seconds
 *   [2]  type             (uint16)   тип сообщения
 *   [2]  subType          (uint16)   подтип сообщения
 *   [2]  version          (uint16)   версия формата сообщения
 *   [N]  bodyBytes        (bytes)    тело сообщения (БЕЗ type/subType/version)
 *
 * TAIL (НЕ входит в blockSize, НЕ подписывается в Frame v0):
 *   [2]  sigMarker        (uint16)   маркер подписи:
 *                                     - 0x0100 (256) = далее подпись Ed25519 64 байта
 *   [64] signature64      (bytes)    Ed25519 signature над hash32
 *
 * hash32 НЕ хранится в блоке.
 * hash32 вычисляется при парсинге:
 *   preimage = первые blockSize байт
 *   hash32   = SHA-256(preimage)
 *
 * Правила MVP-парсера (Frame v0):
 *  - frameCode должен быть строго 0x0000, иначе REJECT.
 *  - sigMarker должен быть строго 0x0100, иначе REJECT.
 *  - подпись обязана присутствовать всегда (sigMarker+signature64).
 *  - НИКАКИХ fallback-веток “если маркер другой, то подписи нет/другой хвост”.
 *
 * Важно по безопасности:
 *  - sigMarker в v0 не входит в подписываемые байты → его можно подменить,
 *    поэтому единственная безопасная логика: "если не 0x0100 — reject".
 * =========================================================================
 */
public final class BchBlockEntry {

    public static final int SIGNATURE_LEN = 64;
    public static final int HASH_LEN = 32;

    public static final int FRAME_CODE_LEN = 2;
    public static final int SIG_MARKER_LEN = 2;

    /** Frame v0 */
    public static final int FRAME_CODE_V0 = 0x0000;

    /** sigMarker: 256 = 0x0100 */
    public static final int SIG_MARKER_ED25519 = 0x0100;

    /**
     * Максимальный допустимый размер блока (fullBytes = preimage + sigMarker + signature),
     * чтобы не уложить сервер по памяти/диску.
     */
    public static final int MAX_BLOCK_FULL_BYTES = 4 * 1024 * 1024;

    /**
     * Насколько блок может “обгонять” текущее время (защита от кривых часов/вбросов).
     * Если timestamp больше now + 60 сек — блок считаем неверным.
     */
    public static final long MAX_FUTURE_SECONDS = 60;

    /**
     * Размер фиксированной части PREIMAGE (без bodyBytes).
     *
     * PREIMAGE header:
     *   frameCode(2) + prevHash32(32) + blockSize(4) + blockNumber(4) + timestamp(8)
     *   + type(2) + subType(2) + version(2)
     */
    public static final int PREIMAGE_HEADER_SIZE =
            2   // frameCode
            + 32 // prevHash32
            + 4  // blockSize
            + 4  // blockNumber
            + 8  // timestamp
            + 2  // type
            + 2  // subType
            + 2; // version

    /** Минимальный полный размер блока (без bodyBytes). */
    public static final int MIN_FULL_BYTES =
            PREIMAGE_HEADER_SIZE + SIG_MARKER_LEN + SIGNATURE_LEN;

    // --- HEADER (PREIMAGE) ---
    public final int frameCode;        // uint16 (v0=0)
    public final byte[] prevHash32;    // 32
    public final int blockSize;        // preimage size (включая frameCode)
    public final int blockNumber;      // >=0
    public final long timestamp;
    public final short type;
    public final short subType;
    public final short version;

    // --- BODY (PREIMAGE) ---
    public final byte[] bodyBytes;

    /** Распарсенное тело (создаётся сразу при парсинге блока). */
    public final BodyRecord body;

    // --- TAIL ---
    public final int sigMarker;        // uint16 (v0: 0x0100)
    private final byte[] signature64;  // 64

    // --- derived ---
    private final byte[] hash32;       // 32, computed
    private final byte[] preimage;     // blockSize bytes
    private final byte[] fullBytes;    // preimage + sigMarker + signature

    /* ===================================================================== */
    /* ====================== Конструктор из байт ========================== */
    /* ===================================================================== */

    public BchBlockEntry(byte[] fullBytes) {
        Objects.requireNonNull(fullBytes, "fullBytes == null");

        if (fullBytes.length < MIN_FULL_BYTES) {
            throw new IllegalArgumentException("Block too short: " + fullBytes.length + " < " + MIN_FULL_BYTES);
        }
        if (fullBytes.length > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large: " + fullBytes.length + " > " + MAX_BLOCK_FULL_BYTES);
        }

        ByteBuffer bb = ByteBuffer.wrap(fullBytes).order(ByteOrder.BIG_ENDIAN);

        // [2] frameCode
        this.frameCode = Short.toUnsignedInt(bb.getShort());
        if (this.frameCode != FRAME_CODE_V0) {
            throw new IllegalArgumentException(String.format(
                    "Bad frameCode: 0x%04X (expected 0x%04X)", this.frameCode, FRAME_CODE_V0
            ));
        }

        // [32] prevHash32
        this.prevHash32 = new byte[32];
        bb.get(this.prevHash32);

        // [4] blockSize
        this.blockSize = bb.getInt();
        if (blockSize < PREIMAGE_HEADER_SIZE) {
            throw new IllegalArgumentException("blockSize too small: " + blockSize + " < " + PREIMAGE_HEADER_SIZE);
        }

        // fullLen must match exactly: blockSize + sigMarker(2) + signature(64)
        int expectedFullLen = blockSize + SIG_MARKER_LEN + SIGNATURE_LEN;
        if (expectedFullLen != fullBytes.length) {
            throw new IllegalArgumentException("blockSize mismatch: blockSize=" + blockSize
                    + " expectedFullLen=" + expectedFullLen
                    + " fullLen=" + fullBytes.length);
        }
        if (expectedFullLen > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large by blockSize: " + expectedFullLen + " > " + MAX_BLOCK_FULL_BYTES);
        }

        // [4] blockNumber
        this.blockNumber = bb.getInt();
        if (this.blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber < 0: " + this.blockNumber);
        }

        // [8] timestamp
        this.timestamp = bb.getLong();

        // запрет “в будущее” больше чем на 1 минуту
        long now = Instant.now().getEpochSecond();
        if (this.timestamp > now + MAX_FUTURE_SECONDS) {
            throw new IllegalArgumentException("timestamp is too far in future: ts=" + this.timestamp
                    + " now=" + now + " maxFutureSec=" + MAX_FUTURE_SECONDS);
        }

        // [2][2][2] type/subType/version
        this.type = bb.getShort();
        this.subType = bb.getShort();
        this.version = bb.getShort();

        // [N] bodyBytes
        int bodyLen = blockSize - PREIMAGE_HEADER_SIZE;
        if (bodyLen < 0) {
            throw new IllegalArgumentException("Invalid body length: " + bodyLen);
        }
        this.bodyBytes = new byte[bodyLen];
        bb.get(this.bodyBytes);

        // TAIL: [2] sigMarker
        this.sigMarker = Short.toUnsignedInt(bb.getShort());
        if (this.sigMarker != SIG_MARKER_ED25519) {
            throw new IllegalArgumentException(String.format(
                    "Bad sigMarker: 0x%04X (expected 0x%04X)", this.sigMarker, SIG_MARKER_ED25519
            ));
        }

        // TAIL: [64] signature64
        this.signature64 = new byte[SIGNATURE_LEN];
        bb.get(this.signature64);

        // preimage = первые blockSize байт (включая frameCode)
        this.preimage = Arrays.copyOfRange(fullBytes, 0, blockSize);

        // hash32 = sha256(preimage)
        this.hash32 = BchCryptoVerifier.sha256(preimage);

        // parse body по header.type/subType/version + ОБЯЗАТЕЛЬНЫЙ check()
        this.body = BodyRecordParser.parse(this.type, this.subType, this.version, this.bodyBytes);

        this.fullBytes = Arrays.copyOf(fullBytes, fullBytes.length);

        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }
    }

    /* ===================================================================== */
    /* ====================== Конструктор сборки ============================ */
    /* ===================================================================== */

    public BchBlockEntry(byte[] prevHash32,
                         int blockNumber,
                         long timestamp,
                         short type,
                         short subType,
                         short version,
                         byte[] bodyBytes,
                         byte[] signature64) {

        Objects.requireNonNull(prevHash32, "prevHash32 == null");
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");
        Objects.requireNonNull(signature64, "signature64 == null");

        if (prevHash32.length != 32) throw new IllegalArgumentException("prevHash32 != 32");
        if (signature64.length != SIGNATURE_LEN) throw new IllegalArgumentException("signature64 != 64");

        if (blockNumber < 0) {
            throw new IllegalArgumentException("blockNumber < 0: " + blockNumber);
        }

        // запрет “в будущее” больше чем на 1 минуту
        long now = Instant.now().getEpochSecond();
        if (timestamp > now + MAX_FUTURE_SECONDS) {
            throw new IllegalArgumentException("timestamp is too far in future: ts=" + timestamp
                    + " now=" + now + " maxFutureSec=" + MAX_FUTURE_SECONDS);
        }

        this.frameCode = FRAME_CODE_V0;
        this.prevHash32 = Arrays.copyOf(prevHash32, 32);
        this.blockNumber = blockNumber;
        this.timestamp = timestamp;
        this.type = type;
        this.subType = subType;
        this.version = version;
        this.bodyBytes = Arrays.copyOf(bodyBytes, bodyBytes.length);

        // blockSize = размер preimage (включая frameCode)
        this.blockSize = PREIMAGE_HEADER_SIZE + this.bodyBytes.length;

        int fullLen = this.blockSize + SIG_MARKER_LEN + SIGNATURE_LEN;
        if (fullLen > MAX_BLOCK_FULL_BYTES) {
            throw new IllegalArgumentException("Block too large: " + fullLen + " > " + MAX_BLOCK_FULL_BYTES);
        }

        // parse body по header + ОБЯЗАТЕЛЬНЫЙ check()
        this.body = BodyRecordParser.parse(this.type, this.subType, this.version, this.bodyBytes);

        // tail marker фиксирован
        this.sigMarker = SIG_MARKER_ED25519;
        this.signature64 = Arrays.copyOf(signature64, SIGNATURE_LEN);

        // build preimage
        ByteBuffer pre = ByteBuffer.allocate(blockSize).order(ByteOrder.BIG_ENDIAN);
        pre.putShort((short) (FRAME_CODE_V0 & 0xFFFF));
        pre.put(this.prevHash32);
        pre.putInt(this.blockSize);
        pre.putInt(this.blockNumber);
        pre.putLong(this.timestamp);
        pre.putShort(this.type);
        pre.putShort(this.subType);
        pre.putShort(this.version);
        pre.put(this.bodyBytes);

        this.preimage = pre.array();
        this.hash32 = BchCryptoVerifier.sha256(preimage);

        // build fullBytes: preimage + sigMarker + signature64
        ByteBuffer full = ByteBuffer.allocate(fullLen).order(ByteOrder.BIG_ENDIAN);
        full.put(this.preimage);
        full.putShort((short) (SIG_MARKER_ED25519 & 0xFFFF));
        full.put(this.signature64);
        this.fullBytes = full.array();
    }

    /* ===================================================================== */
    /* ============================ Getters ================================= */
    /* ===================================================================== */

    public byte[] getPreimageBytes() {
        return Arrays.copyOf(preimage, preimage.length);
    }

    /** Возвращает подпись Ed25519 (64 байта). */
    public byte[] getSignature64() {
        return Arrays.copyOf(signature64, SIGNATURE_LEN);
    }

    /** Возвращает hash32 = SHA-256(preimage). */
    public byte[] getHash32() {
        return Arrays.copyOf(hash32, HASH_LEN);
    }

    /** Возвращает полный блок: preimage + sigMarker + signature. */
    public byte[] toBytes() {
        return Arrays.copyOf(fullBytes, fullBytes.length);
    }

    @Override
    public String toString() {
        String timeIso;
        try {
            timeIso = Instant.ofEpochSecond(timestamp).toString();
        } catch (Exception e) {
            timeIso = "некорректныйTimestamp";
        }

        return "BchBlockEntry{"
                + "FRAME{frameCode=0x" + hex4(frameCode)
                + "}, HDR{"
                + "blockSize=" + blockSize
                + ", blockNumber=" + blockNumber
                + ", timestamp=" + timestamp + " (" + timeIso + ")"
                + ", type=" + (type & 0xFFFF)
                + ", subType=" + (subType & 0xFFFF)
                + ", version=" + (version & 0xFFFF)
                + ", prevHash32(hex)=" + toHex(prevHash32)
                + "}"
                + ", BODY{len=" + (bodyBytes == null ? -1 : bodyBytes.length) + "}"
                + ", TAIL{sigMarker=0x" + hex4(sigMarker) + ", signature64(hex)=" + toHex(signature64) + "}"
                + ", DERIVED{hash32(hex)=" + toHex(hash32) + "}"
                + "}";
    }

    private static String hex4(int v) {
        String s = Integer.toHexString(v & 0xFFFF);
        while (s.length() < 4) s = "0" + s;
        return s;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null) return "null";
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int vv = bytes[i] & 0xFF;
            out[i * 2] = HEX[vv >>> 4];
            out[i * 2 + 1] = HEX[vv & 0x0F];
        }
        return new String(out);
    }
}