package test.it.utils;

import utils.crypto.Ed25519Util;

/**
 * Глобальный контекст IT прогона (одна JVM).
 *
 * ТЕПЕРЬ:
 *  - login берётся из TestConfig.LOGIN()
 *  - blockchainName = TestConfig.BCH_NAME()
 *  - ключи генерятся ДЕТЕРМИНИРОВАННО из login (как ты хотела)
 *
 * ПЛЮС:
 *  - тесты можно запускать по одному — initIfNeeded() вызовется автоматически.
 */
public final class ItRunContext {

    private static final Object LOCK = new Object();
    private static volatile boolean inited = false;

    private static String login;
    private static String blockchainName;

    private static byte[] loginPrivKey;
    private static byte[] loginPubKey;

    private static byte[] devicePrivKey;
    private static byte[] devicePubKey;

    private ItRunContext() {}

    /** Инициализировать, если ещё не инициализировано. */
    public static void initIfNeeded() {
        if (inited) return;
        synchronized (LOCK) {
            if (inited) return;

            login = TestConfig.LOGIN();
            blockchainName = TestConfig.BCH_NAME();

            // 1) Генерация ключей ИЗ login
            //    loginKey: приватный ключ = SHA-256(login)
            loginPrivKey = Ed25519Util.generatePrivateKeyFromString(login);
            loginPubKey  = Ed25519Util.derivePublicKey(loginPrivKey);

            //    deviceKey: приватный ключ = SHA-256(login + "#device")
            String deviceSeedStr = login + "#device";
            devicePrivKey = Ed25519Util.generatePrivateKeyFromString(deviceSeedStr);
            devicePubKey  = Ed25519Util.derivePublicKey(devicePrivKey);

            inited = true;

            System.out.println(TestColors.C + "\n============================================================" + TestColors.R);
            System.out.println(TestColors.C + "IT CONTEXT INIT: фиксированные данные из TestConfig" + TestColors.R);
            System.out.println(TestColors.C + "============================================================" + TestColors.R);
            System.out.println("login           = " + login);
            System.out.println("blockchainName  = " + blockchainName);
            System.out.println("loginPubKey     = " + bytesToHexShort(loginPubKey));
            System.out.println("devicePubKey    = " + bytesToHexShort(devicePubKey));
            System.out.println(TestColors.C + "------------------------------------------------------------\n" + TestColors.R);
        }
    }

    public static String login() {
        initIfNeeded();
        return login;
    }

    public static String blockchainName() {
        initIfNeeded();
        return blockchainName;
    }

    public static byte[] loginPrivKey() {
        initIfNeeded();
        return loginPrivKey.clone();
    }

    public static byte[] loginPubKey() {
        initIfNeeded();
        return loginPubKey.clone();
    }

    public static byte[] devicePrivKey() {
        initIfNeeded();
        return devicePrivKey.clone();
    }

    public static byte[] devicePubKey() {
        initIfNeeded();
        return devicePubKey.clone();
    }

    private static String bytesToHexShort(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(b.length, 10);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", b[i]));
        if (b.length > n) sb.append("...");
        return sb.toString();
    }
}