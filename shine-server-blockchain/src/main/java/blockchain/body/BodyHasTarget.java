package blockchain.body;

import utils.blockchain.BlockchainNameUtil;

/**
 * BodyHasTarget — контракт для body, которые "ссылаются" на цель (to-поля).
 *
 * ВАЖНО (новое правило):
 *  - toLogin НЕ храним в байтах блока.
 *  - toLogin всегда вычисляем из toBchName по стандарту:
 *      toBchName = login + "-NNN"  =>  toLogin = login
 *
 * Все методы могут возвращать null (если target отсутствует).
 */
public interface BodyHasTarget {

    /** login цели (nullable). */
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
    byte[] toBlockHasheBytes();
}