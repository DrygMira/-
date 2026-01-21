package blockchain.body;

import blockchain.MsgSubType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * TextReplyBody — type=1, ver=1.
 *
 * subType:
 *  - REPLY      (20)
 *  - EDIT_REPLY (21)
 *
 * Форматы bodyBytes (BigEndian):
 *
 * REPLY:
 *   [1] toBlockchainNameLen (uint8)
 *   [N] toBlockchainName UTF-8
 *   [4] toBlockGlobalNumber
 *   [32] toBlockHash32
 *   [2] textLenBytes (uint16)
 *   [M] text UTF-8
 *
 * EDIT_REPLY:
 *   [4] toBlockGlobalNumber
 *   [32] toBlockHash32
 *   [2] textLenBytes (uint16)
 *   [N] text UTF-8
 */
public final class TextReplyBody implements BodyRecord, BodyHasTarget {

    public static final short TYPE = 1;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    public final short subType;   // из header
    public final short version;   // (=1)

    // target
    public final String toBlockchainName;     // nullable для EDIT_REPLY
    public final int toBlockGlobalNumber;
    public final byte[] toBlockHash32;        // 32

    // text
    public final String message;

    public TextReplyBody(short subType, short version, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        this.subType = subType;
        this.version = version;

        if ((this.version & 0xFFFF) != (VER & 0xFFFF)) {
            throw new IllegalArgumentException("TextReplyBody version must be 1, got=" + (this.version & 0xFFFF));
        }

        int st = this.subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_REPLY & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            throw new IllegalArgumentException("TextReplyBody supports only REPLY/EDIT_REPLY, got subType=" + st);
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            // минимум: nameLen[1]+name[1]+global[4]+hash[32]+textLen[2]
            ensureMin(bb, 1 + 1 + 4 + 32 + 2, "REPLY too short");

            int nameLen = Byte.toUnsignedInt(bb.get());
            if (nameLen <= 0) throw new IllegalArgumentException("REPLY toBlockchainNameLen is 0");
            ensureMin(bb, nameLen + 4 + 32 + 2, "REPLY payload too short");

            byte[] nameBytes = new byte[nameLen];
            bb.get(nameBytes);
            this.toBlockchainName = new String(nameBytes, StandardCharsets.UTF_8);

            this.toBlockGlobalNumber = bb.getInt();

            this.toBlockHash32 = new byte[32];
            bb.get(this.toBlockHash32);

        } else {
            // EDIT_REPLY: target без имени
            ensureMin(bb, (4 + 32) + 2, "EDIT_REPLY too short");

            this.toBlockchainName = null;
            this.toBlockGlobalNumber = bb.getInt();

            this.toBlockHash32 = new byte[32];
            bb.get(this.toBlockHash32);
        }

        this.message = readStrictUtf8Len16(bb, "TextReplyBody text");
        ensureNoTail(bb, "TextReplyBody");
    }

    public TextReplyBody(short subType,
                         int toBlockGlobalNumber,
                         byte[] toBlockHash32,
                         String toBlockchainName,
                         String message) {

        Objects.requireNonNull(message, "message == null");
        Objects.requireNonNull(toBlockHash32, "toBlockHash32 == null");

        int st = subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_REPLY & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF)) {
            throw new IllegalArgumentException("TextReplyBody supports only REPLY/EDIT_REPLY");
        }

        if (message.isBlank()) throw new IllegalArgumentException("message is blank");
        if (toBlockGlobalNumber < 0) throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32.length != 32) throw new IllegalArgumentException("toBlockHash32 != 32");

        if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            Objects.requireNonNull(toBlockchainName, "toBlockchainName == null");
            if (toBlockchainName.isBlank()) throw new IllegalArgumentException("toBlockchainName is blank");
            this.toBlockchainName = toBlockchainName;
        } else {
            // EDIT_REPLY: имя не хранить
            this.toBlockchainName = null;
        }

        this.subType = subType;
        this.version = VER;

        this.toBlockGlobalNumber = toBlockGlobalNumber;
        this.toBlockHash32 = Arrays.copyOf(toBlockHash32, 32);

        this.message = message;
    }

    @Override
    public TextReplyBody check() {
        int st = subType & 0xFFFF;
        if (st != (MsgSubType.TEXT_REPLY & 0xFFFF) && st != (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF))
            throw new IllegalArgumentException("Bad TextReplyBody subType: " + st);

        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Text message is blank");

        if (toBlockGlobalNumber < 0)
            throw new IllegalArgumentException("toBlockGlobalNumber < 0");
        if (toBlockHash32 == null || toBlockHash32.length != 32)
            throw new IllegalArgumentException("toBlockHash32 invalid");

        if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            if (toBlockchainName == null || toBlockchainName.isBlank())
                throw new IllegalArgumentException("REPLY toBlockchainName is blank");
        } else {
            if (toBlockchainName != null)
                throw new IllegalArgumentException("EDIT_REPLY must not contain toBlockchainName");
        }

        return this;
    }

    @Override
    public byte[] toBytes() {
        byte[] msgUtf8 = message.getBytes(StandardCharsets.UTF_8);
        if (msgUtf8.length == 0) throw new IllegalArgumentException("Text payload is empty");
        if (msgUtf8.length > 65535) throw new IllegalArgumentException("Text too long (>65535 bytes)");

        int st = subType & 0xFFFF;

        if (st == (MsgSubType.TEXT_REPLY & 0xFFFF)) {
            if (toBlockchainName == null) throw new IllegalArgumentException("REPLY missing toBlockchainName");

            byte[] nameUtf8 = toBlockchainName.getBytes(StandardCharsets.UTF_8);
            if (nameUtf8.length == 0 || nameUtf8.length > 255)
                throw new IllegalArgumentException("REPLY toBlockchainName utf8 len must be 1..255");

            int cap = 1 + nameUtf8.length + 4 + 32 + 2 + msgUtf8.length;

            ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
            bb.put((byte) nameUtf8.length);
            bb.put(nameUtf8);
            bb.putInt(toBlockGlobalNumber);
            bb.put(toBlockHash32);
            bb.putShort((short) msgUtf8.length);
            bb.put(msgUtf8);

            return bb.array();
        }

        // EDIT_REPLY
        int cap = (4 + 32) + 2 + msgUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(toBlockGlobalNumber);
        bb.put(toBlockHash32);
        bb.putShort((short) msgUtf8.length);
        bb.put(msgUtf8);

        return bb.array();
    }

    /* ====================== BodyHasTarget ====================== */

    @Override public String toBchName() { return toBlockchainName; }
    @Override public Integer toBlockGlobalNumber() { return toBlockGlobalNumber; }
    @Override public byte[] toBlockHashBytes() { return toBlockHash32; }

    public boolean isEditReply() {
        return (subType & 0xFFFF) == (MsgSubType.TEXT_EDIT_REPLY & 0xFFFF);
    }

    /* ====================== helpers ====================== */

    private static String readStrictUtf8Len16(ByteBuffer bb, String fieldName) {
        int len = Short.toUnsignedInt(bb.getShort());
        if (len <= 0) throw new IllegalArgumentException(fieldName + " is empty");
        if (bb.remaining() < len) throw new IllegalArgumentException(fieldName + " payload too short (len=" + len + ")");

        byte[] bytes = new byte[len];
        bb.get(bytes);

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String s = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            if (s.isBlank()) throw new IllegalArgumentException(fieldName + " is blank");
            return s;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", e);
        }
    }

    private static void ensureMin(ByteBuffer bb, int need, String msg) {
        if (bb.remaining() < need) throw new IllegalArgumentException(msg + " (need=" + need + ", remaining=" + bb.remaining() + ")");
    }

    private static void ensureNoTail(ByteBuffer bb, String ctx) {
        if (bb.remaining() != 0) throw new IllegalArgumentException("Unexpected tail bytes for " + ctx + ", remaining=" + bb.remaining());
    }
}