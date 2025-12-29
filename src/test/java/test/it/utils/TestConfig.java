package test.it.utils;

import java.util.Base64;

/**
 * Конфиг для IT тестов.
 *
 * ЛОГИКА:
 *  - login по умолчанию берём из DEFAULT_LOGIN
 *  - можно переопределить запуском:
 *      -Dit.login=anya24
 *      -Dit.bchSuffix=001
 *
 * ВАЖНО:
 *  - ключи/имя блокчейна вычисляются из login (через ItRunContext).
 *  - тесты можно запускать по отдельности, ItRunContext сам инициализируется при первом обращении.
 *
 * ЛОГИ:
 *  - детальный вывод включается флагом:
 *      -Dit.debug=true
 */
public final class TestConfig {

    private TestConfig() {}

    // Твой WS URI
    public static final String WS_URI = "ws://localhost:7070/ws";

    // ======= По умолчанию (можно поменять под свою среду) =======
    public static final String DEFAULT_LOGIN = "anya24";

    // Суффикс блокчейна по твоему правилу: login + 3 цифры
    public static final String DEFAULT_BCH_SUFFIX_3 = "001";

    // Лимит блокчейна для AddUser
    public static final long TEST_BCH_LIMIT = 50_000_000L;

    // Любая строка клиента (для логов)
    public static final String TEST_CLIENT_INFO = "it-tests";

    /** DEBUG-режим: подробные логи отправки/получения/ожиданий (по умолчанию false). */
    public static boolean DEBUG() {
        return Boolean.parseBoolean(System.getProperty("it.debug", "true"));
    }

    /** login для прогона (по умолчанию DEFAULT_LOGIN, можно переопределить -Dit.login=...). */
    public static String LOGIN() {
        return System.getProperty("it.login", DEFAULT_LOGIN);
    }

    /** Суффикс для имени блокчейна (по умолчанию DEFAULT_BCH_SUFFIX_3, можно переопределить -Dit.bchSuffix=...). */
    public static String BCH_SUFFIX_3() {
        return System.getProperty("it.bchSuffix", DEFAULT_BCH_SUFFIX_3);
    }

    /** blockchainName по правилу: login + суффикс. */
    public static String BCH_NAME() {
        return LOGIN() + BCH_SUFFIX_3();
    }

    // ======= Ключи (берём из ItRunContext) =======

    public static byte[] LOGIN_PRIV_KEY() {
        return ItRunContext.loginPrivKey();
    }

    public static byte[] LOGIN_PUB_KEY() {
        return ItRunContext.loginPubKey();
    }

    public static byte[] DEVICE_PRIV_KEY() {
        return ItRunContext.devicePrivKey();
    }

    public static byte[] DEVICE_PUB_KEY() {
        return ItRunContext.devicePubKey();
    }

    public static String LOGIN_PUBKEY_B64() {
        return Base64.getEncoder().encodeToString(LOGIN_PUB_KEY());
    }

    public static String DEVICE_PUBKEY_B64() {
        return Base64.getEncoder().encodeToString(DEVICE_PUB_KEY());
    }

    /** Псевдо-пароль хранилища — достаточно для тестов. */
    public static String fakeStoragePwd() {
        return "pwd-" + System.nanoTime();
    }
}