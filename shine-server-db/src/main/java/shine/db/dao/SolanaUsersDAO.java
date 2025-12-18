package shine.db.dao;

import shine.db.SqliteDbController;
import shine.db.entities.SolanaUserEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SolanaUsersDAO — локальная таблица пользователей из Solana.
 *
 * Колонки:
 *  - login       TEXT
 *  - loginId     INTEGER (PK)
 *  - bchId       INTEGER
 *  - loginKey    TEXT
 *  - deviceKey   TEXT
 *  - bchLimit    INTEGER (может быть NULL)
 *
 *   * Правило:
 *  * - методы с Connection НЕ закрывают соединение
 *  * - методы без Connection сами открывают и закрывают соединение
 */
public final class SolanaUsersDAO {

    private static volatile SolanaUsersDAO instance;
    private final SqliteDbController db = SqliteDbController.getInstance();

    private SolanaUsersDAO() {}

    public static SolanaUsersDAO getInstance() {
        if (instance == null) {
            synchronized (SolanaUsersDAO.class) {
                if (instance == null) instance = new SolanaUsersDAO();
            }
        }
        return instance;
    }

    // -------------------- INSERT --------------------

    /** Вставка с внешним соединением. Соединение НЕ закрывает. */
    public void insert(Connection c, SolanaUserEntry user) throws SQLException {
        String sql = """
            INSERT INTO solana_users (login, loginId, bchId, loginKey, deviceKey, bchLimit)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getLogin());
            ps.setLong(2, user.getLoginId());
            ps.setLong(3, user.getBchId());
            ps.setString(4, user.getLoginKey());
            ps.setString(5, user.getDeviceKey());

            if (user.getBchLimit() != null) {
                ps.setInt(6, user.getBchLimit());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.executeUpdate();
        }
    }

    /** Вставка без внешнего соединения. Сам открывает/закрывает. */
    public void insert(SolanaUserEntry user) throws SQLException {
        try (Connection c = db.getConnection()) {
            insert(c, user);
        }
    }

    // -------------------- SELECT --------------------

    /** Получить по loginId с внешним соединением. Соединение НЕ закрывает. */
    public SolanaUserEntry getByLoginId(Connection c, long loginId) throws SQLException {
        String sql = """
            SELECT login, loginId, bchId, loginKey, deviceKey, bchLimit
            FROM solana_users
            WHERE loginId = ?
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить по loginId без внешнего соединения. Сам открывает/закрывает. */
    public SolanaUserEntry getByLoginId(long loginId) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLoginId(c, loginId);
        }
    }

    /** Получить по login (case-insensitive) с внешним соединением. Соединение НЕ закрывает. */
    public SolanaUserEntry getByLogin(Connection c, String login) throws SQLException {
        String sql = """
            SELECT login, loginId, bchId, loginKey, deviceKey, bchLimit
            FROM solana_users
            WHERE LOWER(login) = LOWER(?)
            """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapRow(rs);
            }
        }
    }

    /** Получить по login (case-insensitive) без внешнего соединения. Сам открывает/закрывает. */
    public SolanaUserEntry getByLogin(String login) throws SQLException {
        try (Connection c = db.getConnection()) {
            return getByLogin(c, login);
        }
    }

    /** Поиск по префиксу с внешним соединением. Соединение НЕ закрывает. */
    public List<SolanaUserEntry> searchByLoginPrefix(Connection c, String prefix) throws SQLException {
        String sql = """
            SELECT login, loginId, bchId, loginKey, deviceKey, bchLimit
            FROM solana_users
            WHERE LOWER(login) LIKE ?
            ORDER BY login
            LIMIT 5
            """;

        List<SolanaUserEntry> result = new ArrayList<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix.toLowerCase() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }

        return result;
    }

    /** Поиск по префиксу без внешнего соединения. Сам открывает/закрывает. */
    public List<SolanaUserEntry> searchByLoginPrefix(String prefix) throws SQLException {
        try (Connection c = db.getConnection()) {
            return searchByLoginPrefix(c, prefix);
        }
    }

    // -------------------- MAPPER --------------------

    private SolanaUserEntry mapRow(ResultSet rs) throws SQLException {
        return new SolanaUserEntry(
                rs.getLong("loginId"),
                rs.getString("login"),
                rs.getLong("bchId"),
                rs.getString("loginKey"),
                rs.getString("deviceKey"),
                rs.getObject("bchLimit") != null ? rs.getInt("bchLimit") : null
        );
    }
}