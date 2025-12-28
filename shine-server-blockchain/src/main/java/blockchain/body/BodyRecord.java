package blockchain.body;

/**
 * BodyRecord_new — общий контракт для всех типов body (тела блока).
 *
 * Идея:
 *  - На каждый тип body (Header, Text, Reaction, ...) — отдельный класс.
 *  - Десериализация из байтов делается КОНСТРУКТОРОМ:
 *      new XxxBody_new(byte[] bodyBytes)
 *    (конструктор обязан распарсить байты или кинуть IllegalArgumentException).
 *
 *  - Валидация делается методом check().
 *    check() должен:
 *      - вернуть this, если всё корректно
 *      - кинуть IllegalArgumentException, если данные некорректны
 *
 *  - Сериализация обратно в байты делается методом toBytes().
 *
 *  - type() и version() — это идентификаторы формата body.
 *    Они должны быть константами для класса (например TYPE=1, VERSION=1).
 *
 * ДОПОЛНЕНИЕ (ЛИНИИ):
 *  - Каждый тип body знает, в какой lineIndex он ДОЛЖЕН находиться.
 *    Это проверяется в валидаторе блока (уровень B).
 */
public interface BodyRecord {

    /** Код типа записи (совпадает с type в bodyBytes). */
    short type();

    /** Версия формата записи (совпадает с version в bodyBytes). */
    short version();

    /** Ожидаемый индекс линии для этого body. */
    short expectedLineIndex();

    /** Проверить корректность содержимого и вернуть этот объект (или кинуть исключение). */
    BodyRecord check();

    /**
     * Сериализовать тело записи в байты (ровно то, что кладётся в block.body).
     * Важно: включает type/version.
     */
    byte[] toBytes();
}