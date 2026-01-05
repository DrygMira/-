package shine.db;

import utils.config.AppConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseInitializer — создание новой SQLite-БД по схеме SHiNE.
 *
 * Таблицы:
 *  - solana_users
 *  - active_sessions
 *  - users_params
 *  - ip_geo_cache
 *  - blockchain_state
 *  - blocks
 *  - connections_state   (текущее состояние связей)
 *
 */
public class DatabaseInitializer {

    public static void createNewDB(String[] args) {
        AppConfig config = AppConfig.getInstance();
        String dbPath = config.getParam("db.path");

        if (dbPath == null || dbPath.isBlank()) {
            System.err.println("Параметр db.path не задан в application.properties");
            return;
        }

        Path dbFile = Paths.get(dbPath);
        try {
            Path parent = dbFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(dbFile)) {
                System.out.println("Файл базы данных уже существует: " + dbFile.toAbsolutePath());
                System.out.print("Пересоздать БД (СТАРАЯ БУДЕТ УДАЛЕНА)? [y/N]: ");

                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String answer = reader.readLine();
                if (!"y".equalsIgnoreCase(answer) && !"yes".equalsIgnoreCase(answer)) {
                    System.out.println("Операция отменена. БД не изменена.");
                    return;
                }

                Files.delete(dbFile);
                System.out.println("Старый файл БД удалён.");
            }

            createSchema("jdbc:sqlite:" + dbPath);
            System.out.println("Новая БД успешно создана по пути: " + dbFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Ошибка работы с файлом БД: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Ошибка создания схемы БД: " + e.getMessage());
        }
    }

    private static void createSchema(String jdbcUrl) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {

            st.execute("PRAGMA foreign_keys = ON");

            // 1. solana_users
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS solana_users (
                    login       TEXT    NOT NULL PRIMARY KEY,
                    deviceKey   TEXT    NOT NULL,
                    solanaKey   TEXT
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_solana_users_login
                ON solana_users (login);
                """);

            // 2. active_sessions
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_sessions (
                    sessionId                TEXT    NOT NULL PRIMARY KEY,
                    login                    TEXT    NOT NULL,
                    sessionPwd               TEXT    NOT NULL,
                    storagePwd               TEXT    NOT NULL,
                    sessionCreatedAtMs       INTEGER NOT NULL,
                    lastAuthirificatedAtMs   INTEGER NOT NULL,
                    pushEndpoint             TEXT,
                    pushP256dhKey            TEXT,
                    pushAuthKey              TEXT,
                    clientIp                 TEXT,
                    clientInfoFromClient     TEXT,
                    clientInfoFromRequest    TEXT,
                    userLanguage             TEXT,
                    FOREIGN KEY (login) REFERENCES solana_users(login)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_active_sessions_login
                ON active_sessions (login);
                """);

            // 3. users_params
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users_params (
                    login          TEXT    NOT NULL,
                    param          TEXT    NOT NULL,
                    time_ms        INTEGER NOT NULL,
                    value          TEXT    NOT NULL,
                    device_key     TEXT,
                    signature      TEXT,
                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    UNIQUE (login, param)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_users_params_login
                ON users_params (login);
                """);

            // 4. ip_geo_cache
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS ip_geo_cache (
                    ip             TEXT    NOT NULL PRIMARY KEY,
                    geo            TEXT,
                    updated_at_ms  INTEGER NOT NULL
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_ip_geo_cache_updated_at
                ON ip_geo_cache (updated_at_ms);
                """);

            // 5. blockchain_state
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blockchain_state (
                    blockchainName         TEXT    NOT NULL PRIMARY KEY,
                    login                  TEXT    NOT NULL,
                    blockchainKey          TEXT    NOT NULL,
                
                    size_limit             INTEGER NOT NULL,
                    file_size_bytes        INTEGER NOT NULL,
                
                    last_global_number     INTEGER NOT NULL,
                    last_global_hash       TEXT    NOT NULL,
                    updated_at_ms          INTEGER NOT NULL,
                
                    line0_last_number      INTEGER NOT NULL,
                    line0_last_hash        TEXT    NOT NULL,
                    line1_last_number      INTEGER NOT NULL,
                    line1_last_hash        TEXT    NOT NULL,
                    line2_last_number      INTEGER NOT NULL,
                    line2_last_hash        TEXT    NOT NULL,
                    line3_last_number      INTEGER NOT NULL,
                    line3_last_hash        TEXT    NOT NULL,
                    line4_last_number      INTEGER NOT NULL,
                    line4_last_hash        TEXT    NOT NULL,
                    line5_last_number      INTEGER NOT NULL,
                    line5_last_hash        TEXT    NOT NULL,
                    line6_last_number      INTEGER NOT NULL,
                    line6_last_hash        TEXT    NOT NULL,
                    line7_last_number      INTEGER NOT NULL,
                    line7_last_hash        TEXT    NOT NULL,
                    
                    FOREIGN KEY (login) REFERENCES solana_users(login)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blockchain_state_login
                ON blockchain_state (login);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blockchain_state_updated_at
                ON blockchain_state (updated_at_ms);
                """);

            // 6. blocks
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blocks (
                    login                TEXT    NOT NULL,
                    bchName              TEXT    NOT NULL,
                    blockGlobalNumber    INTEGER NOT NULL,
                    blockGlobalPreHashe  TEXT    NOT NULL,

                    blockLineIndex       INTEGER NOT NULL,
                    blockLineNumber      INTEGER NOT NULL,
                    blockLinePreHashe    TEXT    NOT NULL,

                    msgType              INTEGER NOT NULL,
                    msgSubType           INTEGER NOT NULL,

                    blockByte            BLOB,

                    to_login             TEXT,
                    toBchName            TEXT,
                    toBlockGlobalNumber  INTEGER,
                    toBlockHashe         TEXT,

                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    FOREIGN KEY (bchName) REFERENCES blockchain_state(blockchainName)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_chain_global
                ON blocks (login, bchName, blockGlobalNumber);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_to_target
                ON blocks (to_login, toBchName, toBlockGlobalNumber);
                """);

            // =====================================================================
            // 7) connections_state — текущее состояние "кто с кем и какая связь"
            // =====================================================================
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS connections_state (
                    login               TEXT    NOT NULL,
                    relType             INTEGER NOT NULL,   -- 10/20/30 (FRIEND/CONTACT/FOLLOW)
                    to_login            TEXT    NOT NULL,
                    toBchName           TEXT    NOT NULL,
                    toBlockGlobalNumber INTEGER,
                    toBlockHashe        TEXT,

                    FOREIGN KEY (login) REFERENCES solana_users(login),

                    -- состояние уникально по пользователю, типу связи и цели
                    UNIQUE (login, relType, to_login)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_connections_state_login
                ON connections_state (login);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_connections_state_to_login
                ON connections_state (to_login);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_connections_state_pair
                ON connections_state (login, to_login);
                """);

            // =====================================================================
            // 8) Trigger: при вставке connection-блоков в blocks — обновлять connections_state
            //
            // Правило:
            //  - msgType=3 (ConnectionBody)
            //  - subType 10/20/30  => добавить/обновить запись состояния
            //  - subType 11/21/31  => удалить запись состояния (без ошибок, даже если её нет)
            //
            // Примечание:
            //  - "повторное добавление" не должно падать => используем UPSERT (DO UPDATE)
            //  - "удаление того, чего нет" не падает => обычный DELETE
            // =====================================================================
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_connection_state_ai
                AFTER INSERT ON blocks
                WHEN NEW.msgType = 3
                BEGIN

                    -- ADD / UPDATE: 10/20/30
                    INSERT INTO connections_state (
                        login, relType, to_login, toBchName, toBlockGlobalNumber, toBlockHashe
                    )
                    SELECT
                        NEW.login,
                        NEW.msgSubType,
                        NEW.to_login,
                        NEW.toBchName,
                        NEW.toBlockGlobalNumber,
                        NEW.toBlockHashe
                    WHERE NEW.msgSubType IN (10, 20, 30)
                      AND NEW.to_login IS NOT NULL
                      AND NEW.toBchName IS NOT NULL
                    ON CONFLICT(login, relType, to_login)
                    DO UPDATE SET
                        toBchName = excluded.toBchName,
                        toBlockGlobalNumber = excluded.toBlockGlobalNumber,
                        toBlockHashe = excluded.toBlockHashe;

                    -- DELETE: 11/21/31 => удалить соответствующую "положительную" связь (10/20/30)
                    DELETE FROM connections_state
                    WHERE login = NEW.login
                      AND to_login = NEW.to_login
                      AND relType = CASE NEW.msgSubType
                          WHEN 11 THEN 10
                          WHEN 21 THEN 20
                          WHEN 31 THEN 30
                          ELSE relType
                      END
                      AND NEW.msgSubType IN (11, 21, 31);

                END;
                """);
        }
    }
}