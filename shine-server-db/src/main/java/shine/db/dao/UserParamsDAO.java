package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.UserParamEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Здесь храним сохранённые параметры пользователей (в основном до какого сообщения просмотрены ленты) */
public final class UserParamsDAO {

    private static volatile UserParamsDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private UserParamsDAO() { }

    public static UserParamsDAO getInstance() {
        if (instance == null) {
            synchronized (UserParamsDAO.class) {
                if (instance == null) instance = new UserParamsDAO();
            }
        }
        return instance;
    }

    // -------------------- UPSERT --------------------

    /** UPSERT с внешним соединением. Соединение НЕ закрывает. */
    public void upsert(Connection c, UserParamEntry param) throws SQLException {
        String sql = """
            INSERT INTO users_params (
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(loginId, param)
            DO UPDATE SET
                bch_channel_id = excluded.bch_channel_id,
                value          = excluded.value,
                time_ms        = excluded.time_ms,
                pubkey_num     = excluded.pubkey_num,
                signature      = excluded.signature
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param.getLoginId());
            ps.setString(2, param.getParam());
            ps.setLong(3, param.getBchChannelId());
            ps.setString(4, param.getValue());
            ps.setLong(5, param.getTimeMs());
            ps.setInt(6, param.getPubkeyNum());
            ps.setString(7, param.getSignature());
            ps.executeUpdate();
        }
    }

    /** UPSERT без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(UserParamEntry param) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, param);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить параметр с внешним соединением. Соединение НЕ закрывает. */
    public UserParamEntry getByUserIdAndParam(Connection c, long loginId, String paramName) throws SQLException {
        String sql = """
            SELECT
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE loginId = ? AND param = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            ps.setString(2, paramName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить параметр без внешнего соединения. Сам открывает/закрывает. */
    public UserParamEntry getByUserIdAndParam(long loginId, String paramName) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByUserIdAndParam(c, loginId, paramName);
        }
    }

    /** Получить все параметры пользователя с внешним соединением. Соединение НЕ закрывает. */
    public List<UserParamEntry> getByUserId(Connection c, long loginId) throws SQLException {
        String sql = """
            SELECT
                loginId,
                param,
                bch_channel_id,
                value,
                time_ms,
                pubkey_num,
                signature
            FROM users_params
            WHERE loginId = ?
            ORDER BY time_ms DESC
            """;

        List<UserParamEntry> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    /** Получить все параметры пользователя без внешнего соединения. Сам открывает/закрывает. */
    public List<UserParamEntry> getByUserId(long loginId) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByUserId(c, loginId);
        }
    }

    // -------------------- MAPPER --------------------

    private UserParamEntry mapRow(ResultSet rs) throws SQLException {
        return new UserParamEntry(
                rs.getLong("loginId"),
                rs.getString("param"),
                rs.getLong("bch_channel_id"),
                rs.getString("value"),
                rs.getLong("time_ms"),
                (short) rs.getInt("pubkey_num"),
                rs.getString("signature")
        );
    }
}