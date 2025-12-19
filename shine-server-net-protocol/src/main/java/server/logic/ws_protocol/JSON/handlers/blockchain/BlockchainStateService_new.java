package server.logic.ws_protocol.JSON.handlers.blockchain;

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
import java.sql.Types;
import java.util.Base64;

/**
 * BlockchainStateService_new — атомарное добавление блока:
 *  - (опционально) проверки
 *  - вставка строки блока в таблицу blocks
 *  - обновление агрегатного состояния blockchain_state
 *
 * Важно:
 * - всё делается в одной транзакции
 * - DAO-методы с Connection НЕ закрывают соединение
 */
public final class BlockchainStateService_new {

    /** Результат атомарного addBlock */
    public static final class AddBlockResult {
        public final int lineIndex;                  // 0..7 (пока ставим 0)
        public final int httpStatus;                 // WireCodes.Status.*
        public final String reasonCode;              // null если ok
        public final BlockchainStateEntry stateAfter; // состояние после (может быть null)

        public AddBlockResult(int lineIndex, int httpStatus, String reasonCode, BlockchainStateEntry stateAfter) {
            this.lineIndex = lineIndex;
            this.httpStatus = httpStatus;
            this.reasonCode = reasonCode;
            this.stateAfter = stateAfter;
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

    /**
     * Атомарно добавляет блок (в рамках одной транзакции) и возвращает результат,
     * чтобы хэндлер мог заполнить ответ клиенту.
     */
    public AddBlockResult addBlockAtomically(
            String login,
            long blockchainId,
            int globalNumber,
            String prevGlobalHash,
            String blockBytesB64
    ) {

        // Пока не парсим lineIndex из блока — ставим 0, чтобы протокол работал.
        // Позже сделаем реальный разбор (и это же место будет правильным для вычисления хэшей).
        final int lineIndex = 0;

        byte[] blockBytes;
        try {
            blockBytes = decodeBase64(blockBytesB64);
        } catch (Exception e) {
            return new AddBlockResult(
                    lineIndex,
                    WireCodes.Status.BAD_REQUEST,
                    "bad_block_base64",
                    null
            );
        }

        try (Connection c = db.getConnection()) {
            boolean oldAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) получаем loginId по login
                SolanaUserEntry u = solanaUsersDAO.getByLogin(c, login);
                if (u == null) {
                    c.rollback();
                    return new AddBlockResult(
                            lineIndex,
                            WireCodes.Status.NOT_FOUND,
                            "user_not_found",
                            null
                    );
                }
                long loginId = u.getLoginId();

                // 2) вставляем блок в blocks
                insertBlockRow(c, loginId, blockchainId, globalNumber, prevGlobalHash, blockBytes, lineIndex);

                // 3) обновляем агрегатное состояние blockchain_state
                BlockchainStateEntry st = stateDAO.getByBlockchainId(c, blockchainId);
                if (st == null) {
                    c.rollback();
                    return new AddBlockResult(
                            lineIndex,
                            WireCodes.Status.NOT_FOUND,
                            "blockchain_state_not_found",
                            null
                    );
                }

                // MVP: обновляем “последний глобальный номер”.
                // Хэш тут сейчас оставлен как заглушка — лучше поставить фактический хэш нового блока.
                st.setLastGlobalNumber(globalNumber);
                st.setLastGlobalHash(nn(prevGlobalHash)); // TODO: заменить на hash нового блока
                st.setUpdatedAtMs(System.currentTimeMillis());

                // (линии пока не трогаем — позже внесём логику lineNumber/lineHash)
                stateDAO.upsert(c, st);

                c.commit();
                return new AddBlockResult(lineIndex, WireCodes.Status.OK, null, st);

            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException ignore) {}
                return new AddBlockResult(
                        lineIndex,
                        WireCodes.Status.INTERNAL_ERROR,
                        "internal_error",
                        null
                );
            } finally {
                try { c.setAutoCommit(oldAutoCommit); } catch (SQLException ignore) {}
            }
        } catch (Exception e) {
            return new AddBlockResult(
                    lineIndex,
                    WireCodes.Status.INTERNAL_ERROR,
                    "db_error",
                    null
            );
        }
    }

    private void insertBlockRow(
            Connection c,
            long loginId,
            long blockchainId,
            int globalNumber,
            String prevGlobalHash,
            byte[] blockBytes,
            int lineIndex
    ) throws SQLException {

        BlockEntry e = new BlockEntry();
        e.setLoginId(loginId);
        e.setBlockchainId(blockchainId);

        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(nn(prevGlobalHash));

        // Заглушки под линии — позже заменим на реальную логику из blockBytes.
        e.setBlockLineIndex(lineIndex);
        e.setBlockLineNumber(0);
        e.setBlockLinePreHashe("");

        e.setMsgType(0);

        e.setBlockByte(blockBytes);

        e.setToLoginId(0);
        e.setToBlockchainId(0);
        e.setToBlockGlobalNumber(0);
        e.setToBlockHashe("");

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
}