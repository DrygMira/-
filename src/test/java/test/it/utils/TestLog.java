package test.it.utils;

/**
 * TestLog — единое место для:
 *  - ANSI цветов
 *  - стандартных красивых сообщений (title/line/step/send/recv)
 *
 * РЕЖИМЫ:
 *  - it.debug=false (по умолчанию):
 *      печатаем ТОЛЬКО итог: PASS/FAIL по каждому тесту
 *  - it.debug=true:
 *      печатаем всё: ожидания, отправка/ответ (JSON), промежуточные проверки
 */
public final class TestLog {
    private TestLog() {}

    // ============================
    // DEBUG SWITCH
    // ============================

    public static final boolean DEBUG = TestConfig.DEBUG();

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

    /** Дебаг-инфо (печатается только при DEBUG=true). */
    public static void info(String s) {
        if (DEBUG) System.out.println(s);
    }

    public static void line() {
        if (!DEBUG) return;
        System.out.println(C + "------------------------------------------------------------" + R);
    }

    /** Короткое заглавие (только DEBUG). */
    public static void title(String s) {
        if (!DEBUG) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + s + R);
        System.out.println(C + "============================================================\n" + R);
    }

    /** Длинное заглавие (только DEBUG). */
    public static void titleBlock(String multiLineText) {
        if (!DEBUG) return;
        System.out.println(C + "\n============================================================" + R);
        System.out.println(C + multiLineText + R);
        System.out.println(C + "============================================================\n" + R);
    }

    /** Заголовок шага (только DEBUG). */
    public static void stepTitle(String s) {
        if (!DEBUG) return;
        System.out.println(C + "\n-------------------- " + s + " --------------------" + R);
    }

    /** Промежуточное ОК (только DEBUG). */
    public static void ok(String s) {
        if (!DEBUG) return;
        System.out.println(G + "✅ " + s + R);
    }

    /** Итоговый PASS (печатается ВСЕГДА). */
    public static void pass(String s) {
        System.out.println(G + "✅ " + s + R);
    }

    public static void warn(String s) {
        if (!DEBUG) return;
        System.out.println(Y + "⚠️ " + s + R);
    }

    /** FAIL (печатается ВСЕГДА). */
    public static void boom(String s) {
        System.out.println(RED + "****************************************************************" + R);
        System.out.println(RED + "❌ " + s + R);
        System.out.println(RED + "****************************************************************" + R);
    }

    public static void send(String op, String json) {
        if (!DEBUG) return;
        System.out.println("📤 [" + op + "] Request JSON:");
        System.out.println(json);
        line();
    }

    public static void recv(String op, String json) {
        if (!DEBUG) return;
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
            pass(testName + ": OK");
            return 0;
        } catch (Throwable t) {
            boom(testName + ": FAIL. Причина: " + t.getMessage());
            if (DEBUG) t.printStackTrace(System.out);
            return 1;
        }
    }
}