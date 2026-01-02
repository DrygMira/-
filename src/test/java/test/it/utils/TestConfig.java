package test.it.utils;

import java.util.Base64;

/**
 * Конфиг для IT тестов.
 *
 * ДОБАВЛЕНО:
 *  - Второй пользователь (LOGIN2) + его blockchainName и ключи.
 *
 * Важно:
 *  - Имена/ключи вычисляются детерминированно из логина (см. ItRunContext).
 */
public final class TestConfig {

    private TestConfig() {}

    // Твой WS URI
    public static final String WS_URI = "ws://localhost:7070/ws";

    // ======= Пользователь #1 (по умолчанию) =======
    public static final String DEFAULT_LOGIN = "Anya";

    // ======= Пользователь #2 (новый) =======
    public static final String DEFAULT_LOGIN2 = "Anya2";

    // Суффикс блокчейна по твоему правилу: login + 3 цифры
    public static final String DEFAULT_BCH_SUFFIX_3 = "001";

    // Лимит блокчейна для AddUser
    public static final long TEST_BCH_LIMIT = 50_000_000L;

    // Любая строка клиента (для логов)
    public static final String TEST_CLIENT_INFO = "it-tests";

    /** DEBUG-режим: подробные логи (по умолчанию true, как у тебя). */
    public static boolean DEBUG() {
        return Boolean.parseBoolean(System.getProperty("it.debug", "true"));
    }

    // =========================
    // USER #1
    // =========================

    /** login для прогона (user1). */
    public static String LOGIN() {
        return System.getProperty("it.login", DEFAULT_LOGIN);
    }

    /** Суффикс для имени блокчейна (user1). */
    public static String BCH_SUFFIX_3() {
        return System.getProperty("it.bchSuffix", DEFAULT_BCH_SUFFIX_3);
    }

    /** blockchainName по правилу: login + суффикс (user1). */
    public static String BCH_NAME() {
        return LOGIN() + BCH_SUFFIX_3();
    }

    public static byte[] LOGIN_PRIV_KEY() { return ItRunContext.login1PrivKey(); }
    public static byte[] LOGIN_PUB_KEY()  { return ItRunContext.login1PubKey(); }
    public static byte[] DEVICE_PRIV_KEY(){ return ItRunContext.device1PrivKey(); }
    public static byte[] DEVICE_PUB_KEY() { return ItRunContext.device1PubKey(); }

    public static String LOGIN_PUBKEY_B64()  { return Base64.getEncoder().encodeToString(LOGIN_PUB_KEY()); }
    public static String DEVICE_PUBKEY_B64() { return Base64.getEncoder().encodeToString(DEVICE_PUB_KEY()); }

    // =========================
    // USER #2
    // =========================

    /** login второго пользователя. Можно переопределить -Dit.login2=... */
    public static String LOGIN2() {
        return System.getProperty("it.login2", DEFAULT_LOGIN2);
    }

    /** blockchainName второго: login2 + тот же суффикс. */
    public static String BCH_NAME2() {
        return LOGIN2() + BCH_SUFFIX_3();
    }

    public static byte[] LOGIN2_PRIV_KEY()  { return ItRunContext.login2PrivKey(); }
    public static byte[] LOGIN2_PUB_KEY()   { return ItRunContext.login2PubKey(); }
    public static byte[] DEVICE2_PRIV_KEY() { return ItRunContext.device2PrivKey(); }
    public static byte[] DEVICE2_PUB_KEY()  { return ItRunContext.device2PubKey(); }

    public static String LOGIN2_PUBKEY_B64()  { return Base64.getEncoder().encodeToString(LOGIN2_PUB_KEY()); }
    public static String DEVICE2_PUBKEY_B64() { return Base64.getEncoder().encodeToString(DEVICE2_PUB_KEY()); }

    /** Псевдо-пароль хранилища — достаточно для тестов. */
    public static String fakeStoragePwd() {
        return "pwd-" + System.nanoTime();
    }
}