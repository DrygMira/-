package test.it.utils;

import utils.crypto.Ed25519Util;

/**
 * Глобальный контекст IT прогона (одна JVM).
 *
 * БЫЛО:
 *  - один пользователь (login/device)
 *
 * СТАЛО:
 *  - два пользователя (login1/device1 и login2/device2)
 *  - ключи детерминированы из логинов
 */
public final class ItRunContext {

    private static final Object LOCK = new Object();
    private static volatile boolean inited = false;

    private static String login1;
    private static String bchName1;

    private static String login2;
    private static String bchName2;

    private static byte[] login1PrivKey;
    private static byte[] login1PubKey;
    private static byte[] device1PrivKey;
    private static byte[] device1PubKey;

    private static byte[] login2PrivKey;
    private static byte[] login2PubKey;
    private static byte[] device2PrivKey;
    private static byte[] device2PubKey;

    private ItRunContext() {}

    public static void initIfNeeded() {
        if (inited) return;
        synchronized (LOCK) {
            if (inited) return;

            // USER1
            login1 = TestConfig.LOGIN();
            bchName1 = TestConfig.BCH_NAME();

            login1PrivKey = Ed25519Util.generatePrivateKeyFromString(login1);
            login1PubKey  = Ed25519Util.derivePublicKey(login1PrivKey);

            String deviceSeed1 = login1 + "#device";
            device1PrivKey = Ed25519Util.generatePrivateKeyFromString(deviceSeed1);
            device1PubKey  = Ed25519Util.derivePublicKey(device1PrivKey);

            // USER2
            login2 = TestConfig.LOGIN2();
            bchName2 = TestConfig.BCH_NAME2();

            login2PrivKey = Ed25519Util.generatePrivateKeyFromString(login2);
            login2PubKey  = Ed25519Util.derivePublicKey(login2PrivKey);

            String deviceSeed2 = login2 + "#device";
            device2PrivKey = Ed25519Util.generatePrivateKeyFromString(deviceSeed2);
            device2PubKey  = Ed25519Util.derivePublicKey(device2PrivKey);

            inited = true;

            System.out.println(TestColors.C + "\n============================================================" + TestColors.R);
            System.out.println(TestColors.C + "IT CONTEXT INIT: 2 users" + TestColors.R);
            System.out.println(TestColors.C + "============================================================" + TestColors.R);

            System.out.println("USER1 login          = " + login1);
            System.out.println("USER1 blockchainName = " + bchName1);
            System.out.println("USER1 loginPubKey    = " + bytesToHexShort(login1PubKey));
            System.out.println("USER1 devicePubKey   = " + bytesToHexShort(device1PubKey));
            System.out.println(TestColors.C + "------------------------------------------------------------" + TestColors.R);

            System.out.println("USER2 login          = " + login2);
            System.out.println("USER2 blockchainName = " + bchName2);
            System.out.println("USER2 loginPubKey    = " + bytesToHexShort(login2PubKey));
            System.out.println("USER2 devicePubKey   = " + bytesToHexShort(device2PubKey));
            System.out.println(TestColors.C + "------------------------------------------------------------\n" + TestColors.R);
        }
    }

    // =========================
    // USER1 getters
    // =========================
    public static String login1() { initIfNeeded(); return login1; }
    public static String bchName1(){ initIfNeeded(); return bchName1; }

    public static byte[] login1PrivKey() { initIfNeeded(); return login1PrivKey.clone(); }
    public static byte[] login1PubKey()  { initIfNeeded(); return login1PubKey.clone(); }
    public static byte[] device1PrivKey(){ initIfNeeded(); return device1PrivKey.clone(); }
    public static byte[] device1PubKey() { initIfNeeded(); return device1PubKey.clone(); }

    // =========================
    // USER2 getters
    // =========================
    public static String login2() { initIfNeeded(); return login2; }
    public static String bchName2(){ initIfNeeded(); return bchName2; }

    public static byte[] login2PrivKey() { initIfNeeded(); return login2PrivKey.clone(); }
    public static byte[] login2PubKey()  { initIfNeeded(); return login2PubKey.clone(); }
    public static byte[] device2PrivKey(){ initIfNeeded(); return device2PrivKey.clone(); }
    public static byte[] device2PubKey() { initIfNeeded(); return device2PubKey.clone(); }

    private static String bytesToHexShort(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(b.length, 10);
        for (int i = 0; i < n; i++) sb.append(String.format("%02x", b[i]));
        if (b.length > n) sb.append("...");
        return sb.toString();
    }
}