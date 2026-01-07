package blockchain.body;

/**
 * BodyToFields — дополнительный интерфейс для body, которые "ссылаются" на цель (to-поля).
 *
 * Идея:
 *  - Не все body имеют "to".
 *  - Но для индексации и удобства запросов в БД мы хотим единообразно доставать:
 *      toLogin, toBchName, toBlockGlobalNumber, toBlockHashe
 *
 * Важно:
 *  - Все методы могут возвращать null.
 *  - toLogin может отсутствовать в самом формате body (например, ReactionBody, TextBody reply/repost),
 *    но в БД мы пишем toLogin "про запас".
 *    Поэтому writer может:
 *      - взять toLogin из body (если есть),
 *      - либо попытаться вычислить из toBchName.
 */
public interface BodyHasTarget {

    /** login цели (nullable). */
    String toLogin();

    /** blockchainName цели (nullable). */
    String toBchName();

    /** globalNumber цели (nullable). */
    Integer toBlockGlobalNumber();

    /** hash целевого блока (обычно 32 байта). Может быть null, если ссылки нет. */
    byte[] toBlockHasheBytes();
}