package blockchain.body;

import blockchain.LineIndex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * UserParamBody — type=4, ver=1. (Параметр профиля / данные пользователя о себе)
 *
 * Идея:
 *  - Это "пользователь сам заявил параметр X со значением Y".
 *  - Один блок = один параметр (одна пара key/value).
 *    (Если нужно больше параметров — просто добавляешь несколько блоков подряд).
 *
 * Формат bodyBytes (BigEndian):
 *   [2] type=4
 *   [2] ver=1
 *
 *   [2] subType (uint16)
 *       1 = TEXT_TEXT (ключ-значение, обе строки UTF-8)
 *
 *   [2] keyLenBytes   (uint16) — длина ключа в байтах UTF-8
 *   [N] keyUtf8
 *
 *   [2] valueLenBytes (uint16) — длина значения в байтах UTF-8
 *   [M] valueUtf8
 *
 * ВАЖНО:
 *  - длины именно В БАЙТАХ UTF-8 (не в символах)
 *  - ключ и значение обязаны быть валидным UTF-8
 *  - ключ запрещаем пустым/blank (иначе нельзя идентифицировать параметр)
 *  - значение может быть пустым? (реши сам)
 *      сейчас: запрещаем пустое (len>0) и запрещаем blank, чтобы не мусорить цепочку
 *
 * ЛИНИЯ:
 *  - строго lineIndex=4 (выделенная линия под пользовательские параметры/профиль).
 */
public final class UserParamBody implements BodyRecord {

    public static final short TYPE = 4;
    public static final short VER  = 1;

    public static final int KEY = ((TYPE & 0xFFFF) << 16) | (VER & 0xFFFF);

    // subType:
    public static final short SUB_TEXT_TEXT = 1;

    public final short subType;

    /** Название параметра (пример: "firstName", "lastName", "address", "about"). */
    public final String paramKey;

    /** Значение параметра (пример: "Aidar", "Gareev", "..."). */
    public final String paramValue;

    /* ===================================================================== */
    /* ====================== Конструктор из байт =========================== */
    /* ===================================================================== */

    public UserParamBody(byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes == null");

        // минимум: type[2]+ver[2]+subType[2]+keyLen[2]+key[1]+valLen[2]+val[1]
        if (bodyBytes.length < 2 + 2 + 2 + 2 + 1 + 2 + 1) {
            throw new IllegalArgumentException("UserParamBody too short");
        }

        ByteBuffer bb = ByteBuffer.wrap(bodyBytes).order(ByteOrder.BIG_ENDIAN);

        short type = bb.getShort();
        short ver  = bb.getShort();
        if (type != TYPE || ver != VER) {
            throw new IllegalArgumentException("Not UserParamBody: type=" + type + " ver=" + ver);
        }

        this.subType = bb.getShort();
        if (this.subType != SUB_TEXT_TEXT) {
            throw new IllegalArgumentException("Bad UserParam subType: " + (this.subType & 0xFFFF));
        }

        int keyLen = Short.toUnsignedInt(bb.getShort());
        if (keyLen <= 0) throw new IllegalArgumentException("paramKeyLen is 0");
        if (bb.remaining() < keyLen + 2) throw new IllegalArgumentException("UserParam key payload too short");

        byte[] keyBytes = new byte[keyLen];
        bb.get(keyBytes);

        int valLen = Short.toUnsignedInt(bb.getShort());
        if (valLen <= 0) throw new IllegalArgumentException("paramValueLen is 0");
        if (bb.remaining() < valLen) throw new IllegalArgumentException("UserParam value payload too short");

        byte[] valBytes = new byte[valLen];
        bb.get(valBytes);

        // запрет мусора в конце
        if (bb.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected tail bytes, remaining=" + bb.remaining());
        }

        this.paramKey = strictUtf8(keyBytes, "paramKey");
        this.paramValue = strictUtf8(valBytes, "paramValue");

        if (this.paramKey.isBlank()) {
            throw new IllegalArgumentException("paramKey is blank");
        }
        if (this.paramValue.isBlank()) {
            throw new IllegalArgumentException("paramValue is blank");
        }
    }

    /* ===================================================================== */
    /* ====================== Конструктор “вручную” ========================= */
    /* ===================================================================== */

    public UserParamBody(String paramKey, String paramValue) {
        Objects.requireNonNull(paramKey, "paramKey == null");
        Objects.requireNonNull(paramValue, "paramValue == null");

        this.subType = SUB_TEXT_TEXT;

        if (paramKey.isBlank()) throw new IllegalArgumentException("paramKey is blank");
        if (paramValue.isBlank()) throw new IllegalArgumentException("paramValue is blank");

        this.paramKey = paramKey;
        this.paramValue = paramValue;
    }

    /* ===================================================================== */
    /* ====================== BodyRecord контракт =========================== */
    /* ===================================================================== */

    @Override public short type() { return TYPE; }
    @Override public short version() { return VER; }
    @Override public short subType() { return subType; }

    @Override
    public short expectedLineIndex() {
        return LineIndex.USER_PARAM;
    }

    @Override
    public UserParamBody check() {
        if (subType != SUB_TEXT_TEXT)
            throw new IllegalArgumentException("Bad UserParam subType: " + (subType & 0xFFFF));

        if (paramKey == null || paramKey.isBlank())
            throw new IllegalArgumentException("paramKey is blank");
        if (paramValue == null || paramValue.isBlank())
            throw new IllegalArgumentException("paramValue is blank");

        return this;
    }

    @Override
    public byte[] toBytes() {
        if (subType != SUB_TEXT_TEXT)
            throw new IllegalArgumentException("Bad UserParam subType: " + (subType & 0xFFFF));

        byte[] keyUtf8 = paramKey.getBytes(StandardCharsets.UTF_8);
        byte[] valUtf8 = paramValue.getBytes(StandardCharsets.UTF_8);

        if (keyUtf8.length == 0) throw new IllegalArgumentException("paramKey utf8 len is 0");
        if (valUtf8.length == 0) throw new IllegalArgumentException("paramValue utf8 len is 0");

        if (keyUtf8.length > 65535) throw new IllegalArgumentException("paramKey too long (>65535 bytes)");
        if (valUtf8.length > 65535) throw new IllegalArgumentException("paramValue too long (>65535 bytes)");

        // type[2]+ver[2]+subType[2] + keyLen[2]+key[N] + valLen[2]+val[M]
        int cap = 2 + 2 + 2 + 2 + keyUtf8.length + 2 + valUtf8.length;

        ByteBuffer bb = ByteBuffer.allocate(cap).order(ByteOrder.BIG_ENDIAN);

        bb.putShort(TYPE);
        bb.putShort(VER);

        bb.putShort(SUB_TEXT_TEXT);

        bb.putShort((short) keyUtf8.length);
        bb.put(keyUtf8);

        bb.putShort((short) valUtf8.length);
        bb.put(valUtf8);

        return bb.array();
    }

    @Override
    public String toString() {
        String st = (subType == SUB_TEXT_TEXT) ? "TEXT_TEXT (1)" : "UNKNOWN";

        return """
                UserParamBody {
                  тип записи        : USER_PARAM (type=4, ver=1)
                  ожидаемая линия   : 4
                  subType           : %s
                  paramKey          : "%s"
                  paramValue        : "%s"
                }
                """.formatted(st, paramKey, paramValue);
    }

    /* ===================================================================== */
    /* =========================== Helpers ================================== */
    /* ===================================================================== */

    private static String strictUtf8(byte[] bytes, String fieldName) {
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid UTF-8", e);
        }
    }
}