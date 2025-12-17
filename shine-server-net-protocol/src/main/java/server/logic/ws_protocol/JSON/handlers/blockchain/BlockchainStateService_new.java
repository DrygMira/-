package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain_new.BchBlockEntry_new;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.entities.BlockchainStateEntry;
import utils.files.FileStoreUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

public final class BlockchainStateService_new {

    public static final class Result {
        public final int httpStatus;
        public final String reasonCode; // null если ok
        public final BlockchainStateEntry stateAfter;
        public final int lineIndex;

        public Result(int httpStatus, String reasonCode, BlockchainStateEntry stateAfter, int lineIndex) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.stateAfter = stateAfter;
            this.lineIndex = lineIndex;
        }

        public boolean isOk() { return reasonCode == null && httpStatus == 200; }
    }

    private static final BlockchainStateService_new INSTANCE = new BlockchainStateService_new();
    public static BlockchainStateService_new getInstance() { return INSTANCE; }
    private BlockchainStateService_new() {}

    public Result addBlockAtomically(
            String login,
            long blockchainId,
            int globalNumber,
            String prevGlobalHashHex,
            String blockBase64
    ) throws SQLException {

        if (login == null || login.isBlank())
            return new Result(400, "EMPTY_LOGIN", null, -1);
        if (blockchainId <= 0)
            return new Result(400, "BAD_BLOCKCHAIN_ID", null, -1);
        if (globalNumber < 0)
            return new Result(400, "BAD_GLOBAL_NUMBER", null, -1);
        if (blockBase64 == null || blockBase64.isBlank())
            return new Result(400, "EMPTY_BLOCK", null, -1);

        byte[] fullBytes;
        try {
            fullBytes = Base64.getDecoder().decode(blockBase64);
        } catch (IllegalArgumentException e) {
            return new Result(400, "BAD_BASE64_BLOCK", null, -1);
        }

        BchBlockEntry_new block;
        try {
            block = new BchBlockEntry_new(fullBytes);
        } catch (Exception e) {
            return new Result(400, "BAD_BLOCK_FORMAT", null, -1);
        }

        int lineIndex = block.line; // short -> int
        if (lineIndex < 0 || lineIndex > 7)
            return new Result(400, "BAD_LINE_INDEX", null, lineIndex);

        Connection conn = SqliteDbController.getInstance().getConnection();
        boolean oldAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try (Statement st = conn.createStatement()) {
            // важно: заранее берём write lock
            st.execute("BEGIN IMMEDIATE");

            BlockchainStateEntry state = BlockchainStateDAO.getInstance().getByBlockchainId(blockchainId);
            if (state == null) {
                conn.rollback();
                return new Result(404, "UNKNOWN_BLOCKCHAIN", null, lineIndex);
            }

            // 1) защита от подмены логина
            if (!login.equals(state.getUserLogin())) {
                conn.rollback();
                return new Result(403, "LOGIN_MISMATCH", state, lineIndex);
            }

            // 2) проверяем ожидаемый global
            int expectedGlobal = state.getLastGlobalNumber() + 1;
            if (globalNumber != expectedGlobal) {
                conn.rollback();
                return new Result(409, "OUT_OF_SEQUENCE_GLOBAL", state, lineIndex);
            }

            // 3) проверяем prev global hash
            String dbPrevGlobalHash = nn(state.getLastGlobalHash());
            if (!eqHash(prevGlobalHashHex, dbPrevGlobalHash)) {
                conn.rollback();
                return new Result(409, "GLOBAL_HASH_MISMATCH", state, lineIndex);
            }

            // 4) проверяем lineNumber
            int expectedLineNumber = state.getLastLineNumber(lineIndex) + 1;
            if (block.lineNumber != expectedLineNumber) {
                conn.rollback();
                return new Result(409, "OUT_OF_SEQUENCE_LINE", state, lineIndex);
            }

            // 5) prevLineHash берём из БД (он хранится!)
            String dbPrevLineHashHex = nn(state.getLastLineHash(lineIndex));

            // 6) полноценная крипто-проверка (хэш/подпись)
            // TODO: тут подключи твой реальный verifier:
            // - посчитать preimage по твоим правилам (login + prevGlobalHash32 + prevLineHash32 + rawBytes)
            // - сверить sha256(preimage) == block.hash32
            // - проверить Ed25519 подпись
            //
            // Если не ок:
            // conn.rollback(); return new Result(422, "CRYPTO_INVALID", state, lineIndex);

            // 7) запись блока в файл (append)
            FileStoreUtil.getInstance().addDataToBlockchain(blockchainId, block.toBytes());

            // 8) апдейт состояния в БД
            state.setLastGlobalNumber(globalNumber);
            state.setLastGlobalHash(bytesToHex(block.getHash32())); // новый global hash = hash блока

            state.setLastLineNumber(lineIndex, block.lineNumber);
            // ВАЖНО: line hash тоже логично сделать = hash блока (если так задумано)
            state.setLastLineHash(lineIndex, bytesToHex(block.getHash32()));

            // size_bytes += len(fullBytes)
            state.setSizeBytes(state.getSizeBytes() + fullBytes.length);
            state.setUpdatedAtMs(System.currentTimeMillis());

            BlockchainStateDAO.getInstance().upsert(state);

            conn.commit();
            return new Result(200, null, state, lineIndex);

        } catch (SQLException e) {
            conn.rollback();
            // если хочешь красиво: SQLITE_BUSY → 503 RETRY
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }

    private static String nn(String s) { return s == null ? "" : s; }

    private static boolean eqHash(String a, String b) {
        String x = nn(a).trim();
        String y = nn(b).trim();
        return x.equalsIgnoreCase(y);
    }

    private static String bytesToHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }
}
