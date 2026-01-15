package blockchain.body;

import utils.blockchain.BlockchainNameUtil;

/**
 * BodyHasTarget — дополнительный интерфейс для body, которые "ссылаются" на цель (to-поля).
 *
 * Новое правило:
 *  - toLogin НЕ храним в байтах блока.
 *  - toLogin всегда вычисляется из toBchName по стандарту login+"-NNN".
 *
 * Все методы могут возвращать null.
 */
public interface BodyHasTarget {

    /** login цели (nullable). Вычисляется из toBchName(). */
    default String toLogin() {
        String bch = toBchName();
        if (bch == null) return null;
        return BlockchainNameUtil.loginFromBlockchainName(bch);
    }

    /** blockchainName цели (nullable). */
    String toBchName();

    /** globalNumber цели (nullable). */
    Integer toBlockGlobalNumber();

    /** hash целевого блока (обычно 32 байта). Может быть null, если ссылки нет. */
    byte[] toBlockHashBytes();
}