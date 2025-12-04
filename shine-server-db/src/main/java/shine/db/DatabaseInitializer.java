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
            // создаём директорию, если нужно
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

            // включаем внешние ключи на этом соединении (для инициализации тоже)
            st.execute("PRAGMA foreign_keys = ON");

            // 1. Таблица solana_users
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS solana_users (
                    login       TEXT    NOT NULL,
                    loginId     INTEGER NOT NULL PRIMARY KEY,
                    bchId       INTEGER NOT NULL,
                    pubkey0     TEXT,
                    pubkey1     TEXT,
                    bchLimit    INTEGER         -- может быть NULL
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_solana_users_login
                ON solana_users (login);
                """);

            // 2. Таблица active_sessions
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS active_sessions (
                    sessionId        INTEGER NOT NULL PRIMARY KEY,
                    session_pwd      TEXT    NOT NULL,
                    loginId          INTEGER NOT NULL,
                    time_ms          INTEGER NOT NULL,
                    pubkey_num       INTEGER NOT NULL,
                    push_endpoint    TEXT,
                    push_p256dh_key  TEXT,
                    push_auth_key    TEXT,
                    FOREIGN KEY (loginId) REFERENCES solana_users(loginId)
                );
                """);

            // 3. Таблица users_params
            // Важно: пара (loginId, param) должна быть уникальна
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users_params (
                    loginId        INTEGER NOT NULL,
                    param          TEXT    NOT NULL,
                    bch_channel_id INTEGER NOT NULL DEFAULT 0,
                    value          TEXT,
                    time_ms        INTEGER NOT NULL,
                    pubkey_num     INTEGER NOT NULL,
                    signature      TEXT,
                    FOREIGN KEY (loginId) REFERENCES solana_users(loginId),
                    UNIQUE (loginId, param)
                );
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_users_params_loginId
                ON users_params (loginId);
                """);
        }
    }
}
