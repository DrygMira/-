package server.logic.ws_protocol.JSON.handlers.blockchain;

import shine.db.dao.BlockchainStateDAO;
import shine.db.dao.BlocksDAO;
import shine.db.entities.BlockEntry;
import shine.db.entities.BlockchainStateEntry;

import java.sql.Connection;
import java.sql.SQLException;

public final class BlockchainDbWriter {

    private final BlocksDAO blocksDAO;
    private final BlockchainStateDAO stateDAO;

    public BlockchainDbWriter(BlocksDAO blocksDAO, BlockchainStateDAO stateDAO) {
        this.blocksDAO = blocksDAO;
        this.stateDAO = stateDAO;
    }

    public void appendBlockAndState(
            Connection c,
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes,
            BlockchainStateEntry stOrNull,
            String newHashHex
    ) throws SQLException {

        insertBlockRow(c, login, blockchainName, globalNumber, prevGlobalHashHex, blockBytes);

        BlockchainStateEntry st = stOrNull;
        if (st == null) {
            st = new BlockchainStateEntry();
            st.setBlockchainName(blockchainName);
        }

        st.setLastGlobalNumber(globalNumber);
        st.setLastGlobalHash(newHashHex);

        st.setLastLineNumber(0, globalNumber);
        st.setLastLineHash(0, newHashHex);

        st.setUpdatedAtMs(System.currentTimeMillis());
        stateDAO.upsert(c, st);
    }

    private void insertBlockRow(
            Connection c,
            String login,
            String blockchainName,
            int globalNumber,
            String prevGlobalHashHex,
            byte[] blockBytes
    ) throws SQLException {

        BlockEntry e = new BlockEntry();

        e.setLogin(login);
        e.setBchName(blockchainName);

        e.setBlockGlobalNumber(globalNumber);
        e.setBlockGlobalPreHashe(prevGlobalHashHex);

        e.setBlockLineIndex(0);
        e.setBlockLineNumber(globalNumber);
        e.setBlockLinePreHashe(prevGlobalHashHex);

        e.setMsgType(0);
        e.setBlockByte(blockBytes);

        e.setToLogin(null);
        e.setToBchName(null);
        e.setToBlockGlobalNumber(null);
        e.setToBlockHashe(null);

        blocksDAO.upsert(c, e);
    }
}