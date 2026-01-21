package blockchain.body;

/**
 * BodyHasLine — для типов, которые имеют линейные поля в body.
 *
 * Новый префикс для line-сообщений (BigEndian) в НАЧАЛЕ bodyBytes:
 *   [4]  lineCode         код линии:
 *                         - 0 для нулевой линии
 *                         - для каналов: blockNumber "заглавия линии" (CREATE_CHANNEL или HEADER/0)
 *   [4]  prevLineNumber
 *   [32] prevLineHash32
 *   [4]  thisLineNumber
 *
 * Важно:
 *  - Правильность prevLineNumber/hash и согласование thisLineNumber
 *    проверяется на сервере/в БД при вставке (а не в body.check()).
 */
public interface BodyHasLine {

    int lineCode();

    int prevLineNumber();

    byte[] prevLineHash32();

    int thisLineNumber();
}