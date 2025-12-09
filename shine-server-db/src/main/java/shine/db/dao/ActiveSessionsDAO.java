package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.ActiveSession;

import java.sql.*;

/** Здесь мы храним данные об активных сессиях пользователя (для wss соединений). */
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
                loginId,
                session_pwd,
                storage_pwd,
                session_created_ms,
                last_auth_ms,
                push_endpoint,
                push_p256dh_key,
                push_auth_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, session.getSessionId());
            ps.setLong(2, session.getLoginId());
            ps.setString(3, session.getSessionPwd());
            ps.setString(4, session.getStoragePwd());
            ps.setLong(5, session.getSessionCreatedAtMs());
            ps.setLong(6, session.getLastAuthirificatedAtMs());
            ps.setString(7, session.getPushEndpoint());
            ps.setString(8, session.getPushP256dhKey());
            ps.setString(9, session.getPushAuthKey());
            ps.executeUpdate();
        }
    }

    public ActiveSession getBySessionId(String sessionId) throws SQLException {
        String sql = """
            SELECT
                sessionId,
                loginId,
                session_pwd,
                storage_pwd,
                session_created_ms,
                last_auth_ms,
                push_endpoint,
                push_p256dh_key,
                push_auth_key
            FROM active_sessions
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId);
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
    public void deleteBySessionId(String sessionId) throws SQLException {
        String sql = "DELETE FROM active_sessions WHERE sessionId = ?";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    /**
     * Обновить поле last_auth_ms (lastAuthirificatedAtMs) для конкретной сессии.
     * Остальные поля записи не меняются.
     */
    public void updateLastAuthirificatedAtMs(String sessionId, long newTimeMs) throws SQLException {
        String sql = """
            UPDATE active_sessions
            SET last_auth_ms = ?
            WHERE sessionId = ?
            """;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, newTimeMs);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        }
    }

    private ActiveSession mapRow(ResultSet rs) throws SQLException {
        String sessionId = rs.getString("sessionId");
        long loginId = rs.getLong("loginId");
        String sessionPwd = rs.getString("session_pwd");
        String storagePwd = rs.getString("storage_pwd");
        long sessionCreatedMs = rs.getLong("session_created_ms");
        long lastAuthMs = rs.getLong("last_auth_ms");
        String pushEndpoint = rs.getString("push_endpoint");
        String pushP256dhKey = rs.getString("push_p256dh_key");
        String pushAuthKey = rs.getString("push_auth_key");

        return new ActiveSession(
                sessionId,
                loginId,
                sessionPwd,
                storagePwd,
                sessionCreatedMs,
                lastAuthMs,
                pushEndpoint,
                pushP256dhKey,
                pushAuthKey
        );
    }
}
