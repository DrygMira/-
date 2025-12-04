package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ActiveSession;

import java.sql.*;

/** Здесь мы хрним данные об активных сессиях пользователя (для wss соединений) */

public final class ActiveSessionsDAO {

    private static volatile ActiveSessionsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private ActiveSessionsDAO() {
    }

    public static ActiveSessionsDAO getInstance() {
        if (instance == null) {
            synchronized (ActiveSessionsDAO.class) {
                if (instance == null) {
                    instance = new ActiveSessionsDAO();
                }
            }
        }
        return instance;
    }

    public void insert(ActiveSession session) throws SQLException {
        String sql = """
            INSERT INTO active_sessions (
                sessionId,
                session_pwd,
                loginId,
                time_ms,
                pubkey_num,
                push_endpoint,
                push_p256dh_key,
                push_auth_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, session.getSessionId());
            ps.setString(2, session.getSessionPwd());
            ps.setLong(3, session.getLoginId());
            ps.setLong(4, session.getTimeMs());
            ps.setInt(5, session.getPubkeyNum());
            ps.setString(6, session.getPushEndpoint());
            ps.setString(7, session.getPushP256dhKey());
            ps.setString(8, session.getPushAuthKey());
            ps.executeUpdate();
        }
    }

    public ActiveSession getBySessionId(long sessionId) throws SQLException {
        String sql = """
            SELECT
                sessionId,
                session_pwd,
                loginId,
                time_ms,
                pubkey_num,
                push_endpoint,
                push_p256dh_key,
                push_auth_key
            FROM active_sessions
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapRow(rs);
            }
        }
    }

    /**
     * Удаление записи по sessionId.
     * Если записи нет — просто ничего не удалит (0 строк).
     */
    public void deleteBySessionId(long sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE sessionId = ?";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        }
    }

    private ActiveSession mapRow(ResultSet rs) throws SQLException {
        long sessionId = rs.getLong("sessionId");
        String sessionPwd = rs.getString("session_pwd");
        long loginId = rs.getLong("loginId");
        long timeMs = rs.getLong("time_ms");
        short pubkeyNum = (short) rs.getInt("pubkey_num");
        String pushEndpoint = rs.getString("push_endpoint");
        String pushP256dhKey = rs.getString("push_p256dh_key");
        String pushAuthKey = rs.getString("push_auth_key");

        return new ActiveSession(
                sessionId,
                sessionPwd,
                loginId,
                timeMs,
                pubkeyNum,
                pushEndpoint,
                pushP256dhKey,
                pushAuthKey
        );
    }
}
