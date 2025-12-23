package server.logic.ws_protocol.JSON.handlers.blockchain;

import blockchain_new.BchBlockEntry_new;
import blockchain_new.BchCryptoVerifier_new;
import server.logic.ws_protocol.WireCodes;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.dao.SolanaUsersDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import shine.db.entities.SolanaUserEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;

/**
 * BlockchainStateService_new — атомарное добавление блока (НОВЫЙ формат):
 *  - decode Base64 -> FULL block bytes
 *  - parse block (recordSize must match)
 *  - взять loginKey (publicKey32) пользователя
 *  - взять prevGlobalHash / prevLineHash из DB-состояния
 *  - собрать preimage -> sha256 -> verify signature
 *  - вставить blocks
 *  - обновить blockchain_state: lastGlobalNumber/lastGlobalHash (и позже line stuff)
 *
 * Ответ наружу: только reasonCode + serverLastGlobalNumber/serverLastGlobalHash
 */
public final class BlockchainStateService_new {

    public static final class AddBlockResult {
        public final int httpStatus;
        public final String reasonCode;               // null если ok
        public final Integer serverLastGlobalNumber;  // может быть null при ошибке
        public final String serverLastGlobalHash;     // может быть null при ошибке

        public AddBlockResult(int httpStatus, String reasonCode,
                              Integer serverLastGlobalNumber, String serverLastGlobalHash) {
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.serverLastGlobalNumber = serverLastGlobalNumber;
            this.serverLastGlobalHash = serverLastGlobalHash;
        }

        public boolean isOk() {
            return httpStatus == WireCodes.Status.OK;
        }
    }

    private static volatile BlockchainStateService_new instance;

    private final SqliteDbController db = SqliteDbController.getInstance();
    private final BlocksDAO blocksDAO = BlocksDAO.getInstance();
    private final BlockchainStateDAO stateDAO = BlockchainStateDAO.getInstance();
    private final SolanaUsersDAO solanaUsersDAO = SolanaUsersDAO.getInstance();

    private BlockchainStateService_new() {}

    public static BlockchainStateService_new getInstance() {
        if (instance == null) {
            synchronized (BlockchainStateService_new.class) {
                if (instance == null) instance = new BlockchainStateService_new();
            }
        }
        return instance;
    }

    public AddBlockResult addBlockAtomically(
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashFromClient,
            String blockBytesB64
    ) {
        byte[] fullBytes;
        try {
            fullBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_base64", null, null);
        }

        if (login == null || login.isBlank())
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_login", null, null);

        if (blockchainName == null || blockchainName.isBlank())
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_blockchain_name", null, null);

        if (fullBytes == null || fullBytes.length == 0)
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "empty_block_bytes", null, null);

        // Разбор блока (проверит recordSize == fullBytes.length)
        final BchBlockEntry_new block;
        try {
            block = new BchBlockEntry_new(fullBytes);
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_format", null, null);
        }

        // Минимальные sanity-checks запроса vs блока
        if (block.recordNumber != globalNumber) {
            return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "global_number_mismatch", null, null);
        }

        try (Connection c = db.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) user by login (loginKey нужен для подписи)
                SolanaUserEntry u = solanaUsersDAO.getByLogin(c, login);
                if (u == null) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.NOT_FOUND, "user_not_found", null, null);
                }

                byte[] loginKey32 = u.getLoginKeyByte();
                if (loginKey32 == null || loginKey32.length != 32) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_login_key", null, null);
                }

                // 2) состояние цепочки по blockchainName
                BlockchainStateEntry st = stateDAO.getByBlockchainName(c, blockchainName);
                if (st == null) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.NOT_FOUND, "blockchain_state_not_found", null, null);
                }

                // 3) проверка последовательности globalNumber (по DB, а не по клиенту)
                int expected = st.getLastGlobalNumber() + 1;
                if (globalNumber != expected) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_global_sequence",
                            st.getLastGlobalNumber(), st.getLastGlobalHash());
                }

                // 4) prev hashes берём с сервера
                byte[] prevGlobalHash32 = hexToBytes32(st.getLastGlobalHash());
                short line = block.line;
                int lineIndex = normalizeLineIndex(line);
                byte[] prevLineHash32 = hexToBytes32(st.getLastLineHash(lineIndex));

                // (опционально) можно сверить, что клиент прислал то же ожидание:
                if (prevGlobalHashFromClient != null && !prevGlobalHashFromClient.isBlank()) {
                    String a = nn(prevGlobalHashFromClient).trim();
                    String b = nn(st.getLastGlobalHash()).trim();
                    if (!a.equalsIgnoreCase(b)) {
                        c.rollback();
                        return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "prev_global_hash_mismatch",
                                st.getLastGlobalNumber(), st.getLastGlobalHash());
                    }
                }

                // 5) verify signature
                byte[] rawBytes = block.getRawBytes();
                byte[] preimage = BchCryptoVerifier_new.buildPreimage(
                        login,
                        prevGlobalHash32,
                        prevLineHash32,
                        rawBytes
                );
                byte[] computedHash32 = BchCryptoVerifier_new.sha256(preimage);

                // hash, присланный в блоке
                byte[] blockHash32 = block.getHash32();
                if (!equals32(computedHash32, blockHash32)) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_hash",
                            st.getLastGlobalNumber(), st.getLastGlobalHash());
                }

                boolean sigOk = BchCryptoVerifier_new.verifySignature(
                        computedHash32,
                        block.getSignature64(),
                        loginKey32
                );
                if (!sigOk) {
                    c.rollback();
                    return new AddBlockResult(WireCodes.Status.BAD_REQUEST, "bad_block_signature",
                            st.getLastGlobalNumber(), st.getLastGlobalHash());
                }

                // 6) вставляем блок в blocks (пока line stuff MVP)
                insertBlockRow(c, login, blockchainName, globalNumber, st.getLastGlobalHash(), fullBytes, lineIndex, block.lineNumber);

                // 7) обновляем агрегатное состояние
                st.setLastGlobalNumber(globalNumber);
                st.setLastGlobalHash(toHexLower(computedHash32));
                st.setUpdatedAtMs(System.currentTimeMillis());

                // линии (пока минимально)
                st.setLastLineNumber(lineIndex, block.lineNumber);
                st.setLastLineHash(lineIndex, toHexLower(computedHash32)); // пока можно тем же, позже разделим

                stateDAO.upsert(c, st);

                c.commit();
                return new AddBlockResult(WireCodes.Status.OK, null, st.getLastGlobalNumber(), st.getLastGlobalHash());

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}
                return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "internal_error", null, null);
            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }
        } catch (Exception e) {
            return new AddBlockResult(WireCodes.Status.INTERNAL_ERROR, "db_error", null, null);
        }
    }

    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashServer,
            byte[] blockBytes,
            int lineIndex,
            int lineNumber
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(nn(prevGlobalHashServer));

        e.setBlockLineIndex(lineIndex);
        e.setBlockLineNumber(lineNumber);
        e.setBlockLinePreHashe(nn("")); // можно потом хранить prevLineHash

        e.setMsgType(0);
        e.setBlockByte(blockBytes);

        // nullable links
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        blocksDAO.upsert(c, e);
    }

    // -------------------- utils --------------------

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static byte[] decodeBase64(String s) {
        if (s == null || s.isBlank()) return null;
        return Base64.getDecoder().decode(s);
    }

    private static int normalizeLineIndex(short line) {
        int v = line & 0xFFFF;
        // пока поддержим 0..7 как “линии”
        if (v < 0 || v > 7) return 0;
        return v;
    }

    private static boolean equals32(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != 32 || b.length != 32) return false;
        int x = 0;
        for (int i = 0; i < 32; i++) x |= (a[i] ^ b[i]);
        return x == 0;
    }

    private static byte[] hexToBytes32(String hex) {
        if (hex == null) return new byte[32];
        String s = hex.trim();
        if (s.isEmpty()) return new byte[32];
        if (s.length() != 64 || !s.matches("^[0-9a-fA-F]{64}$")) return new byte[32];

        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String toHexLower(byte[] b32) {
        if (b32 == null) return "";
        StringBuilder sb = new StringBuilder(b32.length * 2);
        for (byte b : b32) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}