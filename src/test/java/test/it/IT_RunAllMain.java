package test.it;

import test.it.utils.ItRunContext;
import test.it.utils.TestLog;
import test.it.ws.IT_03_AddBlock_NoAuth;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit / Suite.
 *
 * Делает:
 *  1) запускает тесты по очереди
 *  2) печатает итоговый короткий отчёт
 *
 * Запуск из IDE:
 *   Run 'main' этого класса
 *
 * Запуск из консоли:
 *   ./gradlew testClasses
 *   java -cp ... test.it.IT_RunAllMain
 *
 * (Classpath зависит от твоего Gradle, но в IDE проще всего)
 */
public class IT_RunAllMain {

    public static void main(String[] args) {
        ItRunContext.initIfNeeded();

        int failed = runAll();

        // Удобно для CI: код выхода = число упавших тестов
        System.exit(failed);
    }

    /**
     * Основной метод, который возвращает число не пройденных тестов (0 если всё хорошо).
     * Его можно вызывать из других раннеров (например, из варианта с очисткой data/).
     */
    public static int runAll() {

        final int total = 3;
        int failed = 0;
        int passed = 0;

        TestLog.title("IT RUN: запуск всех тестов подряд (без очистки data/)");

        // 1) IT_01_AddUser
        TestLog.stepTitle("RUN: IT_01_AddUser");
        int f1 = IT_01_AddUser.run();
        failed += f1; passed += (f1 == 0 ? 1 : 0);

        // 2) IT_02_Sessions
        TestLog.stepTitle("RUN: IT_02_Sessions");
        int f2 = IT_02_Sessions.run();
        failed += f2; passed += (f2 == 0 ? 1 : 0);

        // 3) IT_03_AddBlock_NoAuth (оставлен как есть, поэтому запускаем через его main)
        //    Если он упадёт — он кинет исключение. Мы перехватим и посчитаем как fail=1.
        TestLog.stepTitle("RUN: IT_03_AddBlock_NoAuth (main)");
        int f3 = IT_03_AddBlock_NoAuth.run();
        failed += f3; passed += (f3 == 0 ? 1 : 0);

        // Итоговый короткий отчёт
        TestLog.titleBlock("""
                IT RUN RESULT
                ----------------------------
                total  = %d
                passed = %d
                failed = %d
                """.formatted(total, passed, failed));

        if (failed == 0) {
            TestLog.ok("✅ ВСЕ IT ТЕСТЫ УСПЕШНО ЗАВЕРШЕНЫ");
        } else {
            TestLog.boom("❌ IT ПРОГОН УПАЛ: failed=" + failed + " из " + total);
        }

        return failed;
    }
}