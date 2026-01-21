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
 *  - connections_state
 *  - message_stats
 */
public class DatabaseInitializer {

    /* ===================== TEXT (msg_type=1) ===================== */

    /** Новое сообщение (начало ветки). */
    public static final short TEXT_NEW = 1;

    /** Ответ на сообщение (reply). */
    public static final short TEXT_REPLY = 2;

    /** Репост (repost). */
    public static final short TEXT_REPOST = 3;

    /** Редактирование (edit). ВАЖНО: серверное значение = 10. */
    public static final short TEXT_EDIT = 10;

    /* ===================== REACTION (msg_type=2) ===================== */

    /** Лайк (LIKE). */
    public static final short REACTION_LIKE = 1;

    /* ===================== CONNECTION (msg_type=3) ===================== */
    // Приведено к твоему shine.db.MsgSubType:
    // FRIEND=10/11, CONTACT=20/21, FOLLOW=30/31
    public static final short CONNECTION_FRIEND     = 10;
    public static final short CONNECTION_UNFRIEND   = 11;

    public static final short CONNECTION_CONTACT    = 20;
    public static final short CONNECTION_UNCONTACT  = 21;

    public static final short CONNECTION_FOLLOW     = 30;
    public static final short CONNECTION_UNFOLLOW   = 31;

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
                    device_key  TEXT    NOT NULL,
                    solana_key  TEXT
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_solana_users_login
                ON solana_users (login);
                """);

            // 2. active_sessions
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_sessions (
                    session_id                 TEXT    NOT NULL PRIMARY KEY,
                    login                      TEXT    NOT NULL,
                    session_pwd                TEXT    NOT NULL,
                    storage_pwd                TEXT    NOT NULL,
                    session_created_at_ms      INTEGER NOT NULL,
                    last_authirificated_at_ms  INTEGER NOT NULL,
                    push_endpoint              TEXT,
                    push_p256dh_key            TEXT,
                    push_auth_key              TEXT,
                    client_ip                  TEXT,
                    client_info_from_client    TEXT,
                    client_info_from_request   TEXT,
                    user_language              TEXT,
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
                    blockchain_name   TEXT    NOT NULL PRIMARY KEY,
                    login             TEXT    NOT NULL,
                    blockchain_key    TEXT    NOT NULL,

                    size_limit        INTEGER NOT NULL,
                    file_size_bytes   INTEGER NOT NULL,

                    last_block_number INTEGER NOT NULL,
                    last_block_hash   BLOB,

                    updated_at_ms     INTEGER NOT NULL,

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

            // 6. blocks (+ line_code)
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS blocks (
                    login                TEXT    NOT NULL,
                    bch_name             TEXT    NOT NULL,
                    block_number         INTEGER NOT NULL CHECK(block_number >= 0),

                    msg_type             INTEGER NOT NULL,
                    msg_sub_type         INTEGER NOT NULL,

                    block_bytes          BLOB    NOT NULL,

                    -- target (reply/like/edit и т.д.)
                    to_login             TEXT,
                    to_bch_name          TEXT,
                    to_block_number      INTEGER CHECK(to_block_number IS NULL OR to_block_number >= 0),
                    to_block_hash        BLOB,

                    -- собственные данные
                    block_hash           BLOB    NOT NULL,
                    block_signature      BLOB    NOT NULL,

                    -- если этот блок был изменён последним edit'ом
                    edited_by_block_number INTEGER CHECK(edited_by_block_number IS NULL OR edited_by_block_number >= 0),

                    -- линейность (опционально)
                    line_code           INTEGER CHECK(line_code IS NULL OR line_code >= 0),
                    prev_line_number    INTEGER CHECK(prev_line_number IS NULL OR prev_line_number >= 0),
                    prev_line_hash      BLOB,
                    this_line_number    INTEGER CHECK(this_line_number IS NULL OR this_line_number >= 0),

                    FOREIGN KEY (login) REFERENCES solana_users(login),
                    FOREIGN KEY (bch_name) REFERENCES blockchain_state(blockchain_name),

                    UNIQUE (bch_name, block_number)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_by_chain_number
                ON blocks (bch_name, block_number);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_to_target
                ON blocks (to_login, to_bch_name, to_block_number);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_blocks_by_line
                ON blocks (bch_name, line_code, this_line_number);
                """);

            // 6.1) TRIGGER: проверка целостности линии (только если line-поля реально переданы)
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_line_integrity_bi
                BEFORE INSERT ON blocks
                WHEN
                    NEW.line_code IS NOT NULL
                    OR NEW.prev_line_number IS NOT NULL
                    OR NEW.prev_line_hash IS NOT NULL
                    OR NEW.this_line_number IS NOT NULL
                BEGIN
                    -- ============================================================
                    -- LINE-INTEGRITY (BodyHasLine)
                    --
                    -- Этот триггер срабатывает ТОЛЬКО если при вставке передали хотя бы одно line-поле.
                    --
                    -- Типы, которые МОГУТ быть линейными (BodyHasLine в коде проекта):
                    --   - TECH        (msg_type=0): CreateChannelBody (и т.п. тех-блоки с линией)
                    --   - TEXT        (msg_type=1): TextBody в режиме линии (пост/редактирование поста в канале)
                    --   - CONNECTION  (msg_type=3): ConnectionBody
                    --   - USER_PARAM  (msg_type=4): UserParamBody
                    --
                    -- Проверки:
                    --  1) Если передали line-поля -> обязаны передать ВСЕ 4:
                    --     line_code, prev_line_number, prev_line_hash, this_line_number.
                    --  2) prev блок линии существует и p.block_hash == NEW.prev_line_hash
                    --  3) line_code корректный:
                    --     - либо NEW.prev_line_number == NEW.line_code (первый шаг после root),
                    --     - либо у prev блока p.line_code == NEW.line_code
                    --  4) this_line_number корректный:
                    --     - первый шаг после root:
                    --         TEXT: this=0
                    --         TECH/CONNECTION/USER_PARAM: this=1
                    --     - дальше:
                    --         TEXT: допускаем this = prev.this или prev.this + 1
                    --         TECH/CONNECTION/USER_PARAM: строго this = prev.this + 1
                    --
                    -- Ошибки: RAISE(ABORT, 'LINE_ERR_...') — чтобы Java могла понять причину.
                    -- ============================================================

                    -- 0) line-поля нельзя у неожиданных типов
                    SELECT RAISE(ABORT,
                        'LINE_ERR_UNSUPPORTED_TYPE_WITH_LINE: msg_type=' || NEW.msg_type || ' msg_sub_type=' || NEW.msg_sub_type
                    )
                    WHERE NOT (NEW.msg_type IN (0, 1, 3, 4));

                    -- 1) line-поля должны быть заполнены полностью (без “частично”)
                    SELECT RAISE(ABORT,
                        'LINE_ERR_PARTIAL_FIELDS: all of (line_code, prev_line_number, prev_line_hash, this_line_number) must be NOT NULL'
                    )
                    WHERE NEW.line_code IS NULL
                       OR NEW.prev_line_number IS NULL
                       OR NEW.prev_line_hash IS NULL
                       OR NEW.this_line_number IS NULL;

                    -- 2) prev существует?
                    SELECT RAISE(ABORT,
                        'LINE_ERR_NO_PREV: bch=' || NEW.bch_name || ' block=' || NEW.block_number || ' prev=' || NEW.prev_line_number
                    )
                    WHERE NOT EXISTS(
                        SELECT 1
                        FROM blocks p
                        WHERE p.bch_name = NEW.bch_name
                          AND p.block_number = NEW.prev_line_number
                        LIMIT 1
                    );

                    -- 3) prev hash совпадает?
                    SELECT RAISE(ABORT,
                        'LINE_ERR_PREV_HASH_MISMATCH: bch=' || NEW.bch_name || ' block=' || NEW.block_number || ' prev=' || NEW.prev_line_number
                    )
                    WHERE NOT EXISTS(
                        SELECT 1
                        FROM blocks p
                        WHERE p.bch_name = NEW.bch_name
                          AND p.block_number = NEW.prev_line_number
                          AND p.block_hash = NEW.prev_line_hash
                        LIMIT 1
                    );

                    -- 4) line_code корректный:
                    --    либо это первый шаг после root (prev_line_number == line_code),
                    --    либо prev уже в этой линии (p.line_code == NEW.line_code).
                    SELECT RAISE(ABORT,
                        'LINE_ERR_LINE_CODE_MISMATCH: bch=' || NEW.bch_name || ' block=' || NEW.block_number ||
                        ' line_code=' || NEW.line_code || ' prev=' || NEW.prev_line_number
                    )
                    WHERE NEW.prev_line_number <> NEW.line_code
                      AND NOT EXISTS(
                        SELECT 1
                        FROM blocks p
                        WHERE p.bch_name = NEW.bch_name
                          AND p.block_number = NEW.prev_line_number
                          AND p.line_code = NEW.line_code
                        LIMIT 1
                      );

                    -- 5) первый шаг после root: this_line_number
                    SELECT RAISE(ABORT,
                        'LINE_ERR_FIRST_STEP_BAD_THIS: expected this_line_number=0 for TEXT or =1 for other types'
                    )
                    WHERE NEW.prev_line_number = NEW.line_code
                      AND NEW.this_line_number <> (CASE WHEN NEW.msg_type = 1 THEN 0 ELSE 1 END);

                    -- 6) обычный шаг: this_line_number относительно prev
                    SELECT RAISE(ABORT,
                        'LINE_ERR_THIS_LINE_BAD_STEP: bch=' || NEW.bch_name || ' block=' || NEW.block_number ||
                        ' this=' || NEW.this_line_number || ' prev=' || NEW.prev_line_number
                    )
                    WHERE NEW.prev_line_number <> NEW.line_code
                      AND NOT EXISTS(
                        SELECT 1
                        FROM blocks p
                        WHERE p.bch_name = NEW.bch_name
                          AND p.block_number = NEW.prev_line_number
                          AND p.this_line_number IS NOT NULL
                          AND (
                                -- TEXT: допускаем same или +1 (поддерживает “edit не увеличивает thisLineNumber”)
                                (NEW.msg_type = 1 AND
                                    (NEW.this_line_number = p.this_line_number OR NEW.this_line_number = p.this_line_number + 1)
                                )
                                OR
                                -- TECH/CONNECTION/USER_PARAM: строго +1
                                (NEW.msg_type IN (0,3,4) AND
                                    NEW.this_line_number = p.this_line_number + 1
                                )
                              )
                        LIMIT 1
                      );
                END;
                """);

            // 7) connections_state
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS connections_state (
                    login           TEXT    NOT NULL,
                    rel_type        INTEGER NOT NULL,
                    to_login        TEXT    NOT NULL,
                    to_bch_name     TEXT    NOT NULL,
                    to_block_number INTEGER,
                    to_block_hash   BLOB,

                    FOREIGN KEY (login) REFERENCES solana_users(login),

                    UNIQUE (login, rel_type, to_login)
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

            // 8) Trigger: connection state
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_connection_state_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 3
                BEGIN

                    INSERT INTO connections_state (
                        login, rel_type, to_login, to_bch_name, to_block_number, to_block_hash
                    )
                    SELECT
                        NEW.login,
                        NEW.msg_sub_type,
                        NEW.to_login,
                        NEW.to_bch_name,
                        NEW.to_block_number,
                        NEW.to_block_hash
                    WHERE NEW.msg_sub_type IN (%d, %d, %d)
                      AND NEW.to_login IS NOT NULL
                      AND NEW.to_bch_name IS NOT NULL
                    ON CONFLICT(login, rel_type, to_login)
                    DO UPDATE SET
                        to_bch_name     = excluded.to_bch_name,
                        to_block_number = excluded.to_block_number,
                        to_block_hash   = excluded.to_block_hash;

                    DELETE FROM connections_state
                    WHERE login = NEW.login
                      AND to_login = NEW.to_login
                      AND rel_type = CASE NEW.msg_sub_type
                          WHEN %d THEN %d
                          WHEN %d THEN %d
                          WHEN %d THEN %d
                          ELSE rel_type
                      END
                      AND NEW.msg_sub_type IN (%d, %d, %d);

                END;
                """.formatted(
                    (int) CONNECTION_FRIEND,
                    (int) CONNECTION_CONTACT,
                    (int) CONNECTION_FOLLOW,

                    (int) CONNECTION_UNFRIEND,  (int) CONNECTION_FRIEND,
                    (int) CONNECTION_UNCONTACT, (int) CONNECTION_CONTACT,
                    (int) CONNECTION_UNFOLLOW,  (int) CONNECTION_FOLLOW,

                    (int) CONNECTION_UNFRIEND,
                    (int) CONNECTION_UNCONTACT,
                    (int) CONNECTION_UNFOLLOW
                ));

            // 9) message_stats
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS message_stats (
                    to_login          TEXT    NOT NULL,
                    to_bch_name       TEXT    NOT NULL,
                    to_block_number   INTEGER NOT NULL,
                    to_block_hash     BLOB    NOT NULL,

                    likes_count       INTEGER NOT NULL DEFAULT 0,
                    replies_count     INTEGER NOT NULL DEFAULT 0,
                    edits_count       INTEGER NOT NULL DEFAULT 0,

                    UNIQUE (
                        to_login,
                        to_bch_name,
                        to_block_number,
                        to_block_hash
                    )
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_target
                ON message_stats (to_bch_name, to_block_number, to_block_hash);
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_message_stats_login
                ON message_stats (to_login);
                """);

            // 10) Trigger: LIKE
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_like_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 2 AND NEW.msg_sub_type = %d
                BEGIN
                    INSERT INTO message_stats (
                        to_login,
                        to_bch_name,
                        to_block_number,
                        to_block_hash,
                        likes_count,
                        replies_count,
                        edits_count
                    )
                    SELECT
                        NEW.to_login,
                        NEW.to_bch_name,
                        NEW.to_block_number,
                        NEW.to_block_hash,
                        1,
                        0,
                        0
                    WHERE NEW.to_login IS NOT NULL
                      AND NEW.to_bch_name IS NOT NULL
                      AND NEW.to_block_number IS NOT NULL
                      AND NEW.to_block_hash IS NOT NULL
                    ON CONFLICT(to_login, to_bch_name, to_block_number, to_block_hash)
                    DO UPDATE SET
                        likes_count = message_stats.likes_count + 1;
                END;
                """.formatted((int) REACTION_LIKE));

            // 11) Trigger: REPLY
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_reply_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 1 AND NEW.msg_sub_type = %d
                BEGIN
                    INSERT INTO message_stats (
                        to_login,
                        to_bch_name,
                        to_block_number,
                        to_block_hash,
                        likes_count,
                        replies_count,
                        edits_count
                    )
                    SELECT
                        NEW.to_login,
                        NEW.to_bch_name,
                        NEW.to_block_number,
                        NEW.to_block_hash,
                        0,
                        1,
                        0
                    WHERE NEW.to_login IS NOT NULL
                      AND NEW.to_bch_name IS NOT NULL
                      AND NEW.to_block_number IS NOT NULL
                      AND NEW.to_block_hash IS NOT NULL
                    ON CONFLICT(to_login, to_bch_name, to_block_number, to_block_hash)
                    DO UPDATE SET
                        replies_count = message_stats.replies_count + 1;
                END;
                """.formatted((int) TEXT_REPLY));

            // 12) Trigger: EDIT
            st.executeUpdate("""
                CREATE TRIGGER IF NOT EXISTS trg_blocks_edit_apply_ai
                AFTER INSERT ON blocks
                WHEN NEW.msg_type = 1 AND NEW.msg_sub_type = %d
                BEGIN
                    UPDATE blocks
                    SET edited_by_block_number = NEW.block_number
                    WHERE login = NEW.login
                      AND bch_name = NEW.bch_name
                      AND block_number = NEW.to_block_number;

                    INSERT INTO message_stats (
                        to_login,
                        to_bch_name,
                        to_block_number,
                        to_block_hash,
                        likes_count,
                        replies_count,
                        edits_count
                    )
                    SELECT
                        NEW.to_login,
                        NEW.to_bch_name,
                        NEW.to_block_number,
                        NEW.to_block_hash,
                        0,
                        0,
                        1
                    WHERE NEW.to_login IS NOT NULL
                      AND NEW.to_bch_name IS NOT NULL
                      AND NEW.to_block_number IS NOT NULL
                      AND NEW.to_block_hash IS NOT NULL
                    ON CONFLICT(to_login, to_bch_name, to_block_number, to_block_hash)
                    DO UPDATE SET
                        edits_count = message_stats.edits_count + 1;
                END;
                """.formatted((int) TEXT_EDIT));
        }
    }
}