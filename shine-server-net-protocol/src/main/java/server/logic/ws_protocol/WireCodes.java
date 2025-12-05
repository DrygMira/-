package server.logic.ws_protocol;

/**
 * WireCodes — константы бинарного протокола поверх WebSocket.
 *.
 * Формат входящего сообщения:
 *   [4] int opCode (big-endian)
 *   [*] payload
 *.
 * Ответ сервера:
 *   ровно [4] int statusCode (big-endian)
 */
public final class WireCodes {
    private WireCodes() {}

    public static final class Op {
        public static final int PING      = 0;
        public static final int ADD_BLOCK = 1;
        public static final int GET_BLOCKCHAIN = 2;
        public static final int SEARCH_USERS = 30;
        public static final int GET_LAST_BLOCK_INFO = 31;
        private Op() {}
    }

    public static final class Status {
        public static final int PONG           = 100; // ответ на PING
//        public static final int OK             = 200; // успех

        public static final int ALREADY_EXISTS = 409; // пришёл блок < N+1
        public static final int NON_SEQUENTIAL = 412; // пришёл блок > N+1


        private Status() {}




        // ============================================================
        // 🟢 УСПЕШНЫЕ ОПЕРАЦИИ
        // ============================================================

        /** ✅ Блок успешно добавлен в цепочку. */
        public static final int OK = 200;

        /** 🌱 Создана новая цепочка (первый блок-заголовок принят). */
        public static final int CHAIN_CREATED = 201;

        /**
         * 🔁 Такой блок уже существует.
         * Клиент может считать это успешным ответом:
         *  - сервер возвращает 8 байт: [4] код (202) + [4] номер последнего блока (int)
         *  - клиент обновляет свой lastBlockNumber и не пересылает этот блок снова.         */
        public static final int BLOCK_ALREADY_EXISTS = 202;  // плюс к кодуследом возвращается номер последнего блока на сервере


        // ============================================================
        // 🟡 ЛОГИЧЕСКИЕ / ПРОТОКОЛЬНЫЕ ОШИБКИ
        // ============================================================

        /** ⚠️ Нарушена последовательность — пришёл блок с номером > ожидаемого.
         *  Сервер вернёт 8 байт: [4] код (409) + [4] последний номер блока.
         *  Клиент должен дослать недостающие блоки.         */
        public static final int OUT_OF_SEQUENCE = 409; // плюс к кодуследом возвращается номер последнего блока на сервере

        /** ❌ Некорректные или неполные данные в запросе. */
        public static final int BAD_REQUEST = 400;

        /** 🚫 Цепочка с указанным blockchainId не найдена. */
        public static final int CHAIN_NOT_FOUND = 404;

        /** 🧩 Несовпадение blockchainId между заголовком блока и телом. */
        public static final int INVALID_BLOCKCHAIN_ID = 421;

        /** ❌ Ошибка верификации блока — хэш или подпись не совпали.
        * 🔐 Ошибка хэша: SHA-256(preimage) не совпал с переданным hash32.
        * 🔏 Ошибка подписи Ed25519 — блок не прошёл криптографическую проверку. */
        public static final int UNVERIFIED = 422;


        /** 🙅 Некорректный логин (пустой, неверный формат, недопустимые символы). По сути вообще не может быть, тк логин проверяют при создании в другом блокчейне*/
        public static final int BAD_LOGIN = 462;


        // ============================================================
        // 🔴 СИСТЕМНЫЕ ОШИБКИ / ОГРАНИЧЕНИЯ
        // ============================================================

        // ============================================================
        // 🔴 СИСТЕМНЫЕ ОШИБКИ / ОГРАНИЧЕНИЯ
        // ============================================================

        /** 💾 Достигнут лимит размера блокчейна. */
        public static final int BLOCKCHAIN_FULL = 507;

        /** 🧱 Ошибка при сохранении или обновлении данных на сервере (файлы, JSON и т.п.). */
        public static final int SERVER_DATA_ERROR = 501;

        /** 💥 Общая внутренняя ошибка сервера (необработанное исключение). */
        public static final int INTERNAL_ERROR = 500;
    }

}
