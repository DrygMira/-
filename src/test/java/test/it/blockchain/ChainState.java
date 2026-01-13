package test.it.blockchain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ChainState — состояние глобальной цепочки + состояние линий (только тех, где они нужны).
 *
 * Глобальная цепочка:
 *  - lastBlockNumber / lastBlockHashHex
 *  - map blockNumber -> hash32 (для ссылок reply/edit/reaction)
 *
 * Линии по ТЗ нужны только для:
 *  - TEXT (type=1)
 *  - CONNECTION (type=3)
 *  - USER_PARAM (type=4)
 *
 * prevLineNumber по ТЗ — это GLOBAL blockNumber предыдущего блока линии.
 * thisLineNumber — внутренний номер линии (мы ведём локально: 1,2,3...)
 *
 * ВАЖНО:
 *  - Здесь НЕТ обращения к blockchain.LineIndex.
 *  - Линии адресуются по msg_type (type).
 */
public final class ChainState {

    // какие msg_type имеют линейную цепочку по ТЗ
    public static final short TYPE_HEADER     = 0;
    public static final short TYPE_TEXT       = 1;
    public static final short TYPE_REACTION   = 2;
    public static final short TYPE_CONNECTION = 3;
    public static final short TYPE_USER_PARAM = 4;

    private static final byte[] ZERO32 = new byte[32];
    private static final String ZERO64 = "0".repeat(64);

    // global chain
    private int lastBlockNumber = -1;
    private String lastBlockHashHex = ZERO64;

    // header (block#0)
    private byte[] headerHash32 = null;

    /**
     * line state per TYPE (только для TEXT/CONNECTION/USER_PARAM):
     *  - lastGlobalNumber: последний GLOBAL blockNumber в линии
     *  - lastHashHex: hash последнего блока линии
     *  - lastThisLineNumber: последний thisLineNumber (внутренний)
     */
    private static final class LineState {
        int lastGlobalNumber = -1;
        String lastHashHex = "";
        int lastThisLineNumber = 0;

        void reset() {
            lastGlobalNumber = -1;
            lastHashHex = "";
            lastThisLineNumber = 0;
        }
    }

    private final LineState textLine = new LineState();
    private final LineState connectionLine = new LineState();
    private final LineState userParamLine = new LineState();

    private final Map<Integer, byte[]> hash32ByNumber = new HashMap<>();

    public ChainState() {
        textLine.reset();
        connectionLine.reset();
        userParamLine.reset();
    }

    // -------------------- global getters --------------------

    public int lastBlockNumber() { return lastBlockNumber; }
    public String lastBlockHashHex() { return lastBlockHashHex; }

    public boolean hasHeader() {
        return headerHash32 != null && headerHash32.length == 32 && lastBlockNumber >= 0;
    }

    public int nextBlockNumber() {
        return lastBlockNumber + 1;
    }

    public byte[] prevHash32ForNext() {
        if (lastBlockNumber < 0) return ZERO32;
        return hexToBytes32(lastBlockHashHex);
    }

    public byte[] headerHash32() {
        return headerHash32 == null ? null : headerHash32.clone();
    }

    public byte[] getHash32(int blockNumber) {
        byte[] h = hash32ByNumber.get(blockNumber);
        return h == null ? null : h.clone();
    }

    // -------------------- line helpers --------------------

    public static final class NextLine {
        public final int prevLineNumber;     // GLOBAL blockNumber
        public final byte[] prevLineHash32;  // 32 bytes
        public final int thisLineNumber;     // внутр. номер линии

        public NextLine(int prevLineNumber, byte[] prevLineHash32, int thisLineNumber) {
            this.prevLineNumber = prevLineNumber;
            this.prevLineHash32 = (prevLineHash32 == null ? null : prevLineHash32.clone());
            this.thisLineNumber = thisLineNumber;
        }
    }

    /** Является ли type "линейным" по ТЗ (т.е. нужно вести prevLine/thisLine). */
    public static boolean isLineType(short type) {
        int t = type & 0xFFFF;
        return t == TYPE_TEXT || t == TYPE_CONNECTION || t == TYPE_USER_PARAM;
    }

    /** Следующие line-поля для указанного TYPE (только TEXT/CONNECTION/USER_PARAM). */
    public NextLine nextLineByType(short type) {
        if (!isLineType(type)) {
            throw new IllegalArgumentException("Type " + (type & 0xFFFF) + " не использует line-поля по ТЗ");
        }
        if (!hasHeader()) {
            throw new IllegalStateException("Нельзя формировать line-поля до HEADER (нет headerHash32)");
        }

        LineState ls = lineStateByType(type);

        if (ls.lastGlobalNumber == -1) {
            // первый блок линии ссылается на HEADER (block#0)
            return new NextLine(0, headerHash32.clone(), 1);
        }

        if (ls.lastHashHex == null || ls.lastHashHex.isBlank()) {
            throw new IllegalStateException("LineState.lastHashHex пуст, но lastGlobalNumber!=-1 (type=" + (type & 0xFFFF) + ")");
        }

        return new NextLine(ls.lastGlobalNumber, hexToBytes32(ls.lastHashHex), ls.lastThisLineNumber + 1);
    }

    // -------------------- apply --------------------

    public void applyAppendedBlock(int blockNumber, byte[] hash32, boolean isHeader, short type) {
        if (hash32 == null || hash32.length != 32) {
            throw new IllegalArgumentException("hash32 must be 32 bytes");
        }
        if (blockNumber != lastBlockNumber + 1) {
            throw new IllegalStateException("blockNumber sequence broken: expected=" + (lastBlockNumber + 1) + " got=" + blockNumber);
        }

        if (isHeader) {
            if (blockNumber != 0) throw new IllegalStateException("HEADER must be blockNumber=0");
            headerHash32 = hash32.clone();
        } else {
            if (blockNumber == 0) throw new IllegalStateException("Non-header block can't be blockNumber=0");
            if (headerHash32 == null) throw new IllegalStateException("Header must be sent before non-header blocks");
        }

        String hex64 = bytesToHex64(hash32);

        lastBlockNumber = blockNumber;
        lastBlockHashHex = hex64;

        hash32ByNumber.put(blockNumber, hash32.clone());

        // обновляем line-state только если этот type по ТЗ линейный
        if (isLineType(type)) {
            LineState ls = lineStateByType(type);
            ls.lastGlobalNumber = blockNumber;
            ls.lastHashHex = hex64;
            // thisLineNumber обновляется отдельным вызовом (см. applyThisLineNumberByType)
        }
    }

    /** В тестах удобно явно обновлять thisLineNumber после успешной отправки line-body. */
    public void applyThisLineNumberByType(short type, int thisLineNumber) {
        if (!isLineType(type)) return;
        LineState ls = lineStateByType(type);
        ls.lastThisLineNumber = thisLineNumber;
    }

    private LineState lineStateByType(short type) {
        int t = type & 0xFFFF;
        return switch (t) {
            case TYPE_TEXT -> textLine;
            case TYPE_CONNECTION -> connectionLine;
            case TYPE_USER_PARAM -> userParamLine;
            default -> throw new IllegalArgumentException("Type " + t + " не имеет LineState по ТЗ");
        };
    }

    // -------------------- utils --------------------

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