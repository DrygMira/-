package test.it.utils;

/**
 * TestLog — единое место для:
 *  - ANSI цветов
 *  - стандартных красивых сообщений (title/ok/boom/line/step/send/recv)
 *
 * Включение/выключение подробных логов:
 *   -Dit.verbose=false
 *
 * По умолчанию verbose=true (удобно для ручного прогона).
 */
public final class TestLog {
    private TestLog() {}

    // ============================
    // VERBOSE
    // ============================

    // включается так: ./gradlew test -Dit.verbose=true
    public static final boolean VERBOSE = Boolean.parseBoolean(System.getProperty("it.verbose", "true"));

    // ============================
    // ANSI COLORS
    // ============================

    public static final String R   = "\u001B[0m";
    public static final String G   = "\u001B[32m";
    public static final String Y   = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String C   = "\u001B[36m";

    // ============================
    // BASIC OUTPUT
    // ============================

    public static void info(String s) {
        if (VERBOSE) System.out.println(s);
    }

    public static void line() {
        if (!VERBOSE) return;
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    /** Короткое заглавие. */
    public static void title(String s) {
        if (!VERBOSE) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    /**
     * Длинное заглавие (под многострочный текст).
     *
     * Пример:
     * TestLog.titleBlock("""
     *   ТЕСТ: ...
     *   Ожидание: ...
     * """);
     */
    public static void titleBlock(String multiLineText) {
        if (!VERBOSE) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + multiLineText + R);
        System.out.println(C + "============================================================\n" + R);
    }

    public static void stepTitle(String s) {
        if (!VERBOSE) return;
        System.out.println(C + "\n-------------------- " + s + " --------------------" + R);
    }

    public static void ok(String s) {
        if (!VERBOSE) return;
        System.out.println(G + "✅ " + s + R);
    }

    public static void warn(String s) {
        if (!VERBOSE) return;
        System.out.println(Y + "⚠️ " + s + R);
    }

    public static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    public static void send(String op, String json) {
        if (!VERBOSE) return;
        System.out.println("📤 [" + op + "] Request JSON:");
        System.out.println(json);
        line();
    }

    public static void recv(String op, String json) {
        if (!VERBOSE) return;
        System.out.println("📥 [" + op + "] Response JSON:");
        System.out.println(json);
        line();
    }

    // ============================
    // RUN HELPERS
    // ============================

    /**
     * Запуск тестового тела (без JUnit).
     * Возвращает 0 если ок, 1 если упал.
     *
     * Важно:
     *  - здесь мы НЕ глотаем ошибку: печатаем и возвращаем код
     *  - раннер суммирует количество упавших тестов
     */
    public static int runOne(String testName, Runnable body) {
        try {
            body.run();
            ok(testName + ": OK");
            return 0;
        } catch (Throwable t) {
            boom(testName + ": FAIL. Причина: " + t.getMessage());
            if (VERBOSE) t.printStackTrace(System.out);
            return 1;
        }
    }
}