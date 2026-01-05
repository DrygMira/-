package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.UserParamEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * UserParamsDAO — хранение сохранённых параметров пользователя.
 *
 * Правило:
 * - методы с Connection НЕ закрывают соединение
 * - методы без Connection сами открывают и закрывают соединение
 *
 * ВАЖНО по логике времени:
 * - сам DAO делает "технический upsert"
 * - правила "не принимать более старый time_ms" должны проверяться в handler-е, в транзакции.
 */
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
    public void upsert(Connection c, UserParamEntry e) throws SQLException {
        String sql = """
            INSERT INTO users_params (
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(login, param)
            DO UPDATE SET
                time_ms    = excluded.time_ms,
                value      = excluded.value,
                device_key = excluded.device_key,
                signature  = excluded.signature
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getLogin());
            ps.setString(2, e.getParam());
            ps.setLong(3, e.getTimeMs());
            ps.setString(4, e.getValue());

            if (e.getDeviceKey() != null) ps.setString(5, e.getDeviceKey());
            else ps.setNull(5, Types.VARCHAR);

            if (e.getSignature() != null) ps.setString(6, e.getSignature());
            else ps.setNull(6, Types.VARCHAR);

            ps.executeUpdate();
        }
    }

    /** UPSERT без внешнего соединения. Сам открывает/закрывает. */
    public void upsert(UserParamEntry e) throws SQLException {
        try (Connection c = db.getConnection()) {
            upsert(c, e);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить параметр по (login,param) с внешним соединением. Соединение НЕ закрывает. */
    public UserParamEntry getByLoginAndParam(Connection c, String login, String param) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            FROM users_params
            WHERE login = ? AND param = ?
            LIMIT 1
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, param);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить параметр по (login,param) без внешнего соединения. Сам открывает/закрывает. */
    public UserParamEntry getByLoginAndParam(String login, String param) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLoginAndParam(c, login, param);
        }
    }

    /** Получить все параметры пользователя с внешним соединением. */
    public List<UserParamEntry> getByLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT
                login,
                param,
                time_ms,
                value,
                device_key,
                signature
            FROM users_params
            WHERE login = ?
            ORDER BY time_ms DESC
            """;

        List<UserParamEntry> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Получить все параметры пользователя без внешнего соединения. */
    public List<UserParamEntry> getByLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLogin(c, login);
        }
    }

    // -------------------- MAPPER --------------------

    private static UserParamEntry mapRow(ResultSet rs) throws SQLException {
        UserParamEntry e = new UserParamEntry();
        e.setLogin(rs.getString("login"));
        e.setParam(rs.getString("param"));
        e.setTimeMs(rs.getLong("time_ms"));
        e.setValue(rs.getString("value"));

        String dk = rs.getString("device_key");
        if (rs.wasNull()) dk = null;
        e.setDeviceKey(dk);

        String sig = rs.getString("signature");
        if (rs.wasNull()) sig = null;
        e.setSignature(sig);

        return e;
    }
}