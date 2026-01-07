// =======================
// BlockchainWriter.java  (НОВАЯ ВЕРСИЯ)
// =======================
package server.logic.ws_protocol.JSON.handlers.blockchain.Net_AddBlock_Handler_utils;

import blockchain.BchBlockEntry;
import blockchain.body.BodyHasTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shine.db.SqliteDbController;
import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;
import utils.blockchain.BlockchainNameUtil;
import utils.files.FileStoreUtil;
import shine.log.BlockchainAdminNotifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Base64;

/**
 * BlockchainWriter — единая точка записи:
 *   1) создаём новый файл <name>.tmp_bch = oldFileBytes + newBlockBytes
 *   2) атомарно фиксируем в БД:
 *        - blocks (строка блока)
 *        - blockchain_state (включая новый fileSizeBytes)
 *   3) атомарно заменяем файл:
 *        - удаляем/замещаем старый <name>.bch
 *        - переименовываем <name>.tmp_bch -> <name>.bch
 */
public final class BlockchainWriter {

    private static final Logger log = LoggerFactory.getLogger(BlockchainWriter.class);

    private final SqliteDbController db;
    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;
    private final FileStoreUtil fs;

    public BlockchainWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.db = SqliteDbController.getInstance();
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
        this.fs = FileStoreUtil.getInstance();
    }

    public void appendBlockAndState(
            String login,
            String blockchainName,
            byte[] prevGlobalHash32,
            byte[] prevLineHash32,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull
    ) throws SQLException {

        if (stOrNull == null) {
            throw new SQLException("blockchain_state not found for blockchainName=" + blockchainName + " (state обязателен)");
        }

        verifyMainFileSizeMatchesStateOrAlert(login, blockchainName, block, stOrNull);

        // bytes FULL блока (raw+sig+hash)
        final byte[] newBlockFullBytes = block.toBytes();

        final long oldFileSize = stOrNull.getFileSizeBytes();
        final long newFileSize = safeAdd(oldFileSize, newBlockFullBytes.length);

        // tmp = old + new
        final byte[] tmpBytes;
        if (oldFileSize == 0) {
            tmpBytes = newBlockFullBytes;
        } else {
            byte[] oldBytes;
            try {
                oldBytes = fs.readBlockchain(blockchainName);
            } catch (Exception e) {
                log.error("Ошибка чтения старого файла блокчейна перед записью tmp (login={}, blockchainName={}, oldFileSize={}, blockNumber={})",
                        login, blockchainName, oldFileSize, block.recordNumber, e);
                throw new SQLException("Cannot read old blockchain file for: " + blockchainName, e);
            }

            if (oldBytes.length != (int) oldFileSize) {
                String msg =
                        "Несовпадение размера файла блокчейна при чтении: " +
                        "state ожидал oldFileSize=" + oldFileSize +
                        ", а реально прочитали oldBytes.length=" + oldBytes.length +
                        " (login=" + login +
                        ", blockchainName=" + blockchainName +
                        ", blockNumber=" + block.recordNumber + ").";
                BlockchainAdminNotifier.critical(msg, null);
                throw new SQLException(msg);
            }

            tmpBytes = concat(oldBytes, newBlockFullBytes);
        }

        try {
            fs.writeBlockchainTmp(blockchainName, tmpBytes);
        } catch (Exception e) {
            log.error("Ошибка записи tmp файла блокчейна (login={}, blockchainName={}, tmpBytesLen={}, oldFileSize={}, newFileSize={}, blockNumber={})",
                    login, blockchainName, tmpBytes.length, oldFileSize, newFileSize, block.recordNumber, e);
            throw new SQLException("Cannot write tmp blockchain file for: " + blockchainName, e);
        }

        // атомарно БД
        try (Connection c = db.getConnection()) {

            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);

            boolean committed = false;

            try {
                insertBlockRow(c, login, blockchainName, prevGlobalHash32, prevLineHash32, block);
                appendState(c, blockchainName, block, stOrNull, newFileSize);

                c.commit();
                committed = true;

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}

                log.error("Ошибка транзакции БД при добавлении блока (rollback выполнен) (login={}, blockchainName={}, blockNumber={}, oldFileSize={}, newFileSize={})",
                        login, blockchainName, block.recordNumber, oldFileSize, newFileSize, e);

                if (e instanceof SQLException se) throw se;
                throw new SQLException("appendBlockAndState failed (db tx)", e);

            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }

            // после коммита БД — атомарно заменяем файл
            if (committed) {
                try {
                    fs.atomicReplaceBlockchainFile(blockchainName);
                } catch (Exception moveError) {
                    log.error("БД закоммичена, но атомарная замена файла блокчейна не удалась. tmp оставлен для recovery. (login={}, blockchainName={}, blockNumber={})",
                            login, blockchainName, block.recordNumber, moveError);

                    throw new SQLException(
                            "DB committed but file replace failed; tmp kept for recovery. blockchainName=" + blockchainName,
                            moveError
                    );
                }
            }
        }
    }

    private void verifyMainFileSizeMatchesStateOrAlert(
            String login,
            String blockchainName,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull
    ) throws SQLException {

        if (stOrNull == null) return;

        long expected = stOrNull.getFileSizeBytes();
        if (expected <= 0) return;

        String mainFileName = fs.buildBlockchainFileName(blockchainName);

        if (!fs.exists(mainFileName)) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА КОНСИСТЕНТНОСТИ: state ожидает основной файл, но его нет. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ".";
            BlockchainAdminNotifier.critical(msg, null);
            throw new SQLException(msg);
        }

        long real;
        try {
            real = fs.size(mainFileName);
        } catch (Exception e) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА: не удалось получить размер основного файла блокчейна. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ".";
            BlockchainAdminNotifier.critical(msg, e);
            throw new SQLException(msg, e);
        }

        if (real != expected) {
            String msg =
                    "КРИТИЧЕСКАЯ ОШИБКА КОНСИСТЕНТНОСТИ: размер файла блокчейна НЕ СОВПАДАЕТ с state. " +
                    "login=" + login +
                    ", blockchainName=" + blockchainName +
                    ", expectedSizeFromState=" + expected +
                    ", realMainFileSize=" + real +
                    ", blockNumber=" + (block != null ? block.recordNumber : -1) + ". " +
                    "Похоже на внешнее вмешательство/порчу файла. Запись нового блока остановлена.";
            BlockchainAdminNotifier.critical(msg, null);
            throw new SQLException(msg);
        }
    }

    private void appendState(
            Connection c,
            String blockchainName,
            BchBlockEntry block,
            BlockchainStateEntry stOrNull,
            long newFileSizeBytes
    ) throws SQLException {

        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            throw new SQLException("blockchain_state not found for blockchainName=" + blockchainName);
        }

        // глобальная цепочка
        st.setLastGlobalNumber(block.recordNumber);
        st.setLastGlobalHash(block.getHash32());

        // линия
        int li = block.lineIndex;
        st.setLastLineNumber(li, block.lineNumber);
        st.setLastLineHash(li, block.getHash32());

        // file size
        st.setFileSizeBytes(newFileSizeBytes);

        // timestamp
        st.setUpdatedAtMs(System.currentTimeMillis());

        stateDAO.upsert(c, st);
    }

    /**
     * Вставка/апдейт строки блока в blocks (BLOB-вариант).
     */
    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            byte[] prevGlobalHash32,
            byte[] prevLineHash32,
            BchBlockEntry block
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(block.recordNumber);
        e.setBlockGlobalPreHashe(prevGlobalHash32);

        e.setBlockLineIndex(block.lineIndex);
        e.setBlockLineNumber(block.lineNumber);
        e.setBlockLinePreHashe(prevLineHash32);

        e.setMsgType(block.body.type());
        e.setMsgSubType(block.body.subType());

        // ВАЖНО: здесь ты кладёшь FULL bytes (raw+sig+hash). Это ок, ты так задумал.
        e.setBlockByte(block.toBytes());

        // to-поля
        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        if (block.body instanceof BodyHasTarget tf) {
            e.setToLogin(tf.toLogin());
            e.setToBchName(tf.toBchName());
            e.setToBlockGlobalNumber(tf.toBlockGlobalNumber());
            e.setToBlockHashe(tf.toBlockHasheBytes());

            // если to_login не пришёл, но есть to_bch_name — восстановим логин из имени цепочки
            if (e.getToLogin() == null && e.getToBchName() != null) {
                String toLogin = BlockchainNameUtil.loginFromBlockchainName(e.getToBchName());
                if (toLogin != null && !toLogin.isBlank()) {
                    e.setToLogin(toLogin);
                }
            }
        }

        // новое: хэш и подпись самого блока
        e.setBlockHash(block.getHash32());
        e.setBlockSignature(block.getSignature64());

        // новое: не трогаем (NULL); триггер пометит исходный блок
        e.setEditedByBlockGlobalNumber(null);

        blocksDAO.upsert(c, e);
    }

    // -------------------- utils --------------------

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static long safeAdd(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new IllegalArgumentException("fileSizeBytes overflow: " + x + " + " + y);
        }
        return r;
    }

    // Если у тебя где-то ещё остался String-хэш (legacy), используй это в месте парсинга JSON,
    // но НЕ в writer. Оставляю тут только на всякий случай для миграции:
    @SuppressWarnings("unused")
    private static byte[] decodeHashStringLenient(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // hex 64
        if (t.length() == 64 && t.matches("^[0-9a-fA-F]+$")) {
            byte[] out = new byte[32];
            for (int i = 0; i < 32; i++) {
                int hi = Character.digit(t.charAt(i * 2), 16);
                int lo = Character.digit(t.charAt(i * 2 + 1), 16);
                out[i] = (byte) ((hi << 4) | lo);
            }
            return out;
        }

        // base64 (часто у тебя так)
        try {
            byte[] b = Base64.getDecoder().decode(t);
            return (b != null && b.length == 32) ? b : b;
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }
}