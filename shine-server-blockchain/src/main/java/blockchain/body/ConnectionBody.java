package blockchain.body;

import blockchain.MsgSubType;
import utils.blockchain.BlockchainNameUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ConnectionBody — type=3, ver=1 (в заголовке блока).
 *
 * subType (в заголовке блока) как MsgSubType:
 *   FRIEND=10, UNFRIEND=11
 *   CONTACT=20, UNCONTACT=21
 *   FOLLOW=30, UNFOLLOW=31
 *
 * bodyBytes (BigEndian), новый формат (toLogin НЕ ХРАНИМ):
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 *   [1] toBlockchainNameLen (uint8)
 *   [N] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber (int32)
 *   [32] toBlockHash32 (raw 32 bytes)
 *
 * toLogin вычисляется автоматически из toBlockchainName:
 *   toLogin = BlockchainNameUtil.loginFromBlockchainName(toBlockchainName)
 */
public final class ConnectionBody implements BodyRecord, BodyHasTarget, BodyHasLine {

    public static final short TYPE = 3;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType; // из header
    public final short version; // из header

    // line
    public final int prevLineNumber;
    public final byte[] prevLineHash32;
    public final int thisLineNumber;

    // payload
    public final String toBlockchainName;
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;

    public ConnectionBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("ConnectionBody version must be 1, got=" + (this.version & 0xFFFF));
        }
        if (!isValidSubType(this.subType)) {
            throw new IllegalArgumentException("Bad connection subType: " + (this.subType & 0xFFFF));
        }

        // минимум:
        // line(4+32+4) + toBchLen[1]+toBch[1] + global[4] + hash[32]
        if (bodyBytes.length < (4 + 32 + 4) + 1 + 1 + 4 + 32) {
            throw new IllegalArgumentException("ConnectionBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        this.prevLineNumber = bb.getInt();

        this.prevLineHash32 = new byte[32];
        bb.get(this.prevLineHash32);

        this.thisLineNumber = bb.getInt();

        int bchLen = Byte.toUnsignedInt(bb.get());
        if (bchLen <= 0) throw new IllegalArgumentException("toBlockchainNameLen is 0");
        if (bb.remaining() < bchLen + 4 + 32) throw new IllegalArgumentException("Connection payload too short");

        byte[] bchBytes = new byte[bchLen];
        bb.get(bchBytes);
        this.toBlockchainName = new String(bchBytes, StandardCharsets.UTF_8);

        this.toBlockGlobalNumber = bb.getInt();

        this.toBlockHash32 = new byte[32];
        bb.get(this.toBlockHash32);

        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
    }

    public ConnectionBody(int prevLineNumber,
                          byte[] prevLineHash32,
                          int thisLineNumber,
                          short subType,
                          String toBlockchainName,
                          int toBlockGlobalNumber,
                          byte[] toBlockHash32) {

        Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));

        if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
        // Железное правило формата: bchName -> login + "-NNN"
        if (BlockchainNameUtil.loginFromBlockchainName(toBlockchainName) == null) {
            throw new IllegalArgumentException("toBlockchainName must match login+\"-NNN\": " + toBlockchainName);
        }

        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        this.prevLineNumber = prevLineNumber;
        this.prevLineHash32 = (prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        this.thisLineNumber = thisLineNumber;

        this.subType = subType;
        this.version = VER;

        this.toBlockchainName = toBlockchainName;
        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);
    }

    private static boolean isValidSubType(short st) {
        int v = st & 0xFFFF;
        return v == (MsgSubType.CONNECTION_FRIEND & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNFRIEND & 0xFFFF)
                || v == (MsgSubType.CONNECTION_CONTACT & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNCONTACT & 0xFFFF)
                || v == (MsgSubType.CONNECTION_FOLLOW & 0xFFFF)
                || v == (MsgSubType.CONNECTION_UNFOLLOW & 0xFFFF);
    }

    @Override
    public ConnectionBody check() {
        if (!isValidSubType(subType)) throw new IllegalArgumentException("Bad connection subType: " + (subType & 0xFFFF));

        // line rule
        if (prevLineNumber == -1) {
            if (!isAllZero32(prevLineHash32)) throw new IllegalArgumentException("prevLineHash32 must be zero when prevLineNumber=-1");
            if (thisLineNumber != -1) throw new IllegalArgumentException("thisLineNumber must be -1 when prevLineNumber=-1");
        } else {
            if (prevLineHash32 == null || prevLineHash32.length != 32) throw new IllegalArgumentException("prevLineHash32 invalid");
        }

        if (toBlockchainName == null || toBlockchainName.isBlank())
            throw new IllegalArgumentException("toBlockchainName is blank");

        // гарантируем вычислимый toLogin (иначе target “битый” по стандарту)
        if (BlockchainNameUtil.loginFromBlockchainName(toBlockchainName) == null)
            throw new IllegalArgumentException("toBlockchainName must match login+\"-NNN\": " + toBlockchainName);

        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32 == null || toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 invalid");

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] bchBytes = toBlockchainName.getBytes(StandardCharsets.UTF_8);
        if (bchBytes.length == 0 || bchBytes.length > 255)
            throw new IllegalArgumentException("toBlockchainName utf8 len must be 1..255");

        if (toBlockHash32 == null || toBlockHash32.length != 32)
            throw new IllegalArgumentException("toBlockHash32 != 32");

        int cap = (4 + 32 + 4)
                + 1 + bchBytes.length
                + 4 + 32;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putInt(prevLineNumber);
        bb.put(prevLineHash32 == null ? new byte[32] : Arrays.copyOf(prevLineHash32, 32));
        bb.putInt(thisLineNumber);

        bb.put((byte) bchBytes.length);
        bb.put(bchBytes);

        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);

        return bb.array();
    }

    private static boolean isAllZero32(byte[] b) {
        if (b == null || b.length != 32) return true;
        for (int i = 0; i < 32; i++) if (b[i] != 0) return false;
        return true;
    }

    /* ====================== BodyHasLine ====================== */
    @Override public int prevLineNumber() { return prevLineNumber; }
    @Override public byte[] prevLineHash32() { return prevLineHash32 == null ? null : Arrays.copyOf(prevLineHash32, 32); }
    @Override public int thisLineNumber() { return thisLineNumber; }

    /* ====================== BodyHasTarget ===================== */
    // toLogin() теперь default в интерфейсе и вычисляется из toBchName()
    @Override public String toBchName() { return toBlockchainName; }
    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }
    @Override public byte[] toBlockHashBytes() { return toBlockHash32; }
}