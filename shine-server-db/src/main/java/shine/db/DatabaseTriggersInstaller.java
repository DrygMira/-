package shine.db;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * DatabaseTriggersInstaller — устанавливает триггеры, которые поддерживают бизнес-логику БД.
 *
 * Мы специально сделали триггеры максимально "совместимыми":
 *  - НЕТ динамических сообщений в RAISE(...): только фиксированные строки.
 *    (Некоторые SQLite-сборки / просмотрщики падают на "||" внутри RAISE.)
 *  - НЕТ UPSERT "ON CONFLICT DO UPDATE" — вместо него:
 *      INSERT OR IGNORE + UPDATE
 *    (Старые SQLite не знают UPSERT.)
 *
 * =============================================================================
 * ОПИСАНИЕ ТРИГГЕРОВ
 * =============================================================================
 *
 * [1] trg_blocks_line_integrity_bi  (BEFORE INSERT ON blocks)
 *     Контроль целостности "линий" (line_code / prev_line_number / prev_line_hash / this_line_number).
 *
 *     Зачем это нужно:
 *       - В каналах/ветках/действиях ты хочешь иметь "линейную" последовательность,
 *         где каждый следующий блок явно ссылается на предыдущий блок линии
 *         и подтверждает, что ссылка не подменена.
 *
 *     Когда срабатывает:
 *       - ТОЛЬКО если при вставке передано ХОТЯ БЫ ОДНО из line-полей.
 *       - Если line-поля не переданы — триггер вообще не работает (это важно).
 *
 *     Что проверяет:
 *       A) line-поля допускаются только для msg_type:
 *          0 (TECH), 1 (TEXT), 3 (CONNECTION), 4 (USER_PARAM)
 *       B) Если пришло хоть одно line-поле — обязаны прийти ВСЕ 4 (никаких "частичных")
 *       C) prev-блок линии существует в той же цепочке bch_name
 *       D) prev_hash совпадает с block_hash найденного prev-блока
 *       E) line_code корректный:
 *          - либо первый шаг после root: prev_line_number == line_code
 *          - либо prev уже принадлежит этой линии: p.line_code == NEW.line_code
 *       F) this_line_number:
 *          - первый шаг после root:
 *              TEXT: this_line_number = 0
 *              TECH/CONNECTION/USER_PARAM: this_line_number = 1
 *          - обычный шаг:
 *              TEXT: допускаем same или +1 (чтобы "edit" мог не двигать шаг)
 *              TECH/CONNECTION/USER_PARAM: строго prev.this + 1
 *
 *     Какие ошибки кидает:
 *       - LINE_ERR_UNSUPPORTED_TYPE_WITH_LINE
 *       - LINE_ERR_PARTIAL_FIELDS
 *       - LINE_ERR_NO_PREV
 *       - LINE_ERR_PREV_HASH_MISMATCH
 *       - LINE_ERR_LINE_CODE_MISMATCH
 *       - LINE_ERR_FIRST_STEP_BAD_THIS
 *       - LINE_ERR_THIS_LINE_BAD_STEP
 *
 * [2] trg_blocks_connection_state_ai  (AFTER INSERT ON blocks WHEN msg_type=3)
 *     Поддерживает таблицу connections_state как "текущее состояние" отношений:
 *       - FRIEND/CONTACT/FOLLOW  -> добавить/обновить состояние
 *       - UNFRIEND/UNCONTACT/UNFOLLOW -> удалить соответствующее "позитивное" состояние
 *
 * [3] trg_blocks_message_stats_like_ai (AFTER INSERT ON blocks WHEN msg_type=2 AND sub_type=LIKE)
 *     Поддерживает likes_count в message_stats для цели (to_*).
 *
 * [4] trg_blocks_message_stats_reply_ai (AFTER INSERT ON blocks WHEN msg_type=1 AND sub_type=REPLY)
 *     Поддерживает replies_count в message_stats.
 *
 * [5] trg_blocks_edit_apply_ai (AFTER INSERT ON blocks WHEN msg_type=1 AND sub_type=EDIT)
 *     Логика edit:
 *       - помечает исходный блок edited_by_block_number = NEW.block_number
 *       - увеличивает edits_count в message_stats
 */
public final class DatabaseTriggersInstaller {

    private DatabaseTriggersInstaller() {}

    public static void createAllTriggers(Statement st) throws SQLException {
        // На всякий случай убираем старые "криво названные" триггеры,
        // если они когда-то попадали в БД.
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_block_lini_integriti_by;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_line_integrity_bi;");

        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_connection_state_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_message_stats_like_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_message_stats_reply_ai;");
        st.executeUpdate("DROP TRIGGER IF EXISTS trg_blocks_edit_apply_ai;");

        createLineIntegrityTrigger(st);
        createConnectionStateTrigger(st);
        createMessageStatsLikeTrigger(st);
        createMessageStatsReplyTrigger(st);
        createEditApplyTrigger(st);
    }

    private static void createLineIntegrityTrigger(Statement st) throws SQLException {
        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_line_integrity_bi
            BEFORE INSERT ON blocks
            WHEN
                NEW.line_code IS NOT NULL
                OR NEW.prev_line_number IS NOT NULL
                OR NEW.prev_line_hash IS NOT NULL
                OR NEW.this_line_number IS NOT NULL
            BEGIN
                SELECT RAISE(ABORT, 'LINE_ERR_UNSUPPORTED_TYPE_WITH_LINE')
                WHERE NOT (NEW.msg_type IN (0, 1, 3, 4));

                SELECT RAISE(ABORT, 'LINE_ERR_PARTIAL_FIELDS')
                WHERE NEW.line_code IS NULL
                   OR NEW.prev_line_number IS NULL
                   OR NEW.prev_line_hash IS NULL
                   OR NEW.this_line_number IS NULL;

                SELECT RAISE(ABORT, 'LINE_ERR_NO_PREV')
                WHERE NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                    LIMIT 1
                );

                SELECT RAISE(ABORT, 'LINE_ERR_PREV_HASH_MISMATCH')
                WHERE NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.block_hash = NEW.prev_line_hash
                    LIMIT 1
                );

                SELECT RAISE(ABORT, 'LINE_ERR_LINE_CODE_MISMATCH')
                WHERE NEW.prev_line_number <> NEW.line_code
                  AND NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.line_code = NEW.line_code
                    LIMIT 1
                  );

                SELECT RAISE(ABORT, 'LINE_ERR_FIRST_STEP_BAD_THIS')
                WHERE NEW.prev_line_number = NEW.line_code
                  AND NEW.this_line_number <> (CASE WHEN NEW.msg_type = 1 THEN 0 ELSE 1 END);

                SELECT RAISE(ABORT, 'LINE_ERR_THIS_LINE_BAD_STEP')
                WHERE NEW.prev_line_number <> NEW.line_code
                  AND NOT EXISTS(
                    SELECT 1
                    FROM blocks p
                    WHERE p.bch_name = NEW.bch_name
                      AND p.block_number = NEW.prev_line_number
                      AND p.this_line_number IS NOT NULL
                      AND (
                            (NEW.msg_type = 1 AND
                                (NEW.this_line_number = p.this_line_number OR NEW.this_line_number = p.this_line_number + 1)
                            )
                            OR
                            (NEW.msg_type IN (0,3,4) AND NEW.this_line_number = p.this_line_number + 1)
                          )
                    LIMIT 1
                  );
            END;
            """);
    }

    private static void createConnectionStateTrigger(Statement st) throws SQLException {
        int FRIEND     = (int) DatabaseInitializer.CONNECTION_FRIEND;
        int CONTACT    = (int) DatabaseInitializer.CONNECTION_CONTACT;
        int FOLLOW     = (int) DatabaseInitializer.CONNECTION_FOLLOW;

        int UNFRIEND   = (int) DatabaseInitializer.CONNECTION_UNFRIEND;
        int UNCONTACT  = (int) DatabaseInitializer.CONNECTION_UNCONTACT;
        int UNFOLLOW   = (int) DatabaseInitializer.CONNECTION_UNFOLLOW;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_connection_state_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 3
            BEGIN
                -- FRIEND/CONTACT/FOLLOW:
                -- 1) если записи нет — создаём
                INSERT OR IGNORE INTO connections_state (
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
                  AND NEW.to_bch_name IS NOT NULL;

                -- 2) если запись есть — обновляем актуальные to_*
                UPDATE connections_state
                SET
                    to_bch_name     = NEW.to_bch_name,
                    to_block_number = NEW.to_block_number,
                    to_block_hash   = NEW.to_block_hash
                WHERE login = NEW.login
                  AND rel_type = NEW.msg_sub_type
                  AND to_login = NEW.to_login
                  AND NEW.msg_sub_type IN (%d, %d, %d)
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL;

                -- UNFRIEND/UNCONTACT/UNFOLLOW:
                -- удаляем соответствующее "позитивное" состояние
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
                FRIEND, CONTACT, FOLLOW,
                FRIEND, CONTACT, FOLLOW,

                UNFRIEND,  FRIEND,
                UNCONTACT, CONTACT,
                UNFOLLOW,  FOLLOW,

                UNFRIEND, UNCONTACT, UNFOLLOW
            ));
    }

    private static void createMessageStatsLikeTrigger(Statement st) throws SQLException {
        int LIKE = (int) DatabaseInitializer.REACTION_LIKE;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_like_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 2 AND NEW.msg_sub_type = %d
            BEGIN
                -- создаём строку, если её не было
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                -- +1 like
                UPDATE message_stats
                SET likes_count = likes_count + 1
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(LIKE));
    }

    private static void createMessageStatsReplyTrigger(Statement st) throws SQLException {
        int REPLY = (int) DatabaseInitializer.TEXT_REPLY;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_message_stats_reply_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 1 AND NEW.msg_sub_type = %d
            BEGIN
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                UPDATE message_stats
                SET replies_count = replies_count + 1
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(REPLY));
    }

    private static void createEditApplyTrigger(Statement st) throws SQLException {
        int EDIT_POST = (int) DatabaseInitializer.TEXT_EDIT_POST;
        int EDIT_REPLY = (int) DatabaseInitializer.TEXT_EDIT_REPLY;

        st.executeUpdate("""
            CREATE TRIGGER IF NOT EXISTS trg_blocks_edit_apply_ai
            AFTER INSERT ON blocks
            WHEN NEW.msg_type = 1 AND NEW.msg_sub_type IN (%d, %d)
            BEGIN
                -- 1) помечаем исходный блок, что его "перекрыл" этот edit
                UPDATE blocks
                SET edited_by_block_number = NEW.block_number
                WHERE login = NEW.login
                  AND bch_name = NEW.bch_name
                  AND block_number = NEW.to_block_number
                  AND NEW.to_block_number IS NOT NULL;

                -- 2) создаём stats-строку если её не было
                INSERT OR IGNORE INTO message_stats (
                    to_login, to_bch_name, to_block_number, to_block_hash,
                    likes_count, replies_count, edits_count
                )
                SELECT
                    NEW.to_login, NEW.to_bch_name, NEW.to_block_number, NEW.to_block_hash,
                    0, 0, 0
                WHERE NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;

                -- 3) +1 edit
                UPDATE message_stats
                SET edits_count = edits_count + 1
                WHERE to_login = NEW.to_login
                  AND to_bch_name = NEW.to_bch_name
                  AND to_block_number = NEW.to_block_number
                  AND to_block_hash = NEW.to_block_hash
                  AND NEW.to_login IS NOT NULL
                  AND NEW.to_bch_name IS NOT NULL
                  AND NEW.to_block_number IS NOT NULL
                  AND NEW.to_block_hash IS NOT NULL;
            END;
            """.formatted(EDIT_POST, EDIT_REPLY));
    }
}
