package test.it.runner;

import test.it.cases.IT_01_AddUser;
import test.it.cases.IT_02_Sessions;
import test.it.cases.IT_03_AddBlock_NoAuth;
import test.it.cases.IT_04_UserParams_NoAuth;
import test.it.cases.IT_05_UserConnections;
import test.it.utils.log.TestLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit.
 * Печатает итоги по каждому тесту отдельной строкой.
 */
public class IT_RunAllMain {

    /**
     * Настройка поведения прогона:
     *  - true  : остановить запуск сразу после первого упавшего теста
     *  - false : прогнать все тесты до конца, даже если некоторые упали
     */
    private static final boolean STOP_ON_FIRST_FAIL = true;

    public static void main(String[] args) {
        int failed = runAll();
        // при желании можно вернуть код выхода ОС:
        // System.exit(failed == 0 ? 0 : 1);
    }

    public static int runAll() {

        List<String> summaries = new ArrayList<>();
        int failed = 0;

        TestLog.title("IT RUN: запуск всех тестов подряд"
                + (STOP_ON_FIRST_FAIL ? " (STOP_ON_FIRST_FAIL=ON)" : " (STOP_ON_FIRST_FAIL=OFF)"));

        String s1 = IT_01_AddUser.run(); summaries.add(s1);
        if (s1.contains("FAIL:")) { failed++; if (STOP_ON_FIRST_FAIL) return finishEarly(summaries, failed); }

        String s2 = IT_02_Sessions.run(); summaries.add(s2);
        if (s2.contains("FAIL:")) { failed++; if (STOP_ON_FIRST_FAIL) return finishEarly(summaries, failed); }

        String s3 = IT_03_AddBlock_NoAuth.run(); summaries.add(s3);
        if (s3.contains("FAIL:")) { failed++; if (STOP_ON_FIRST_FAIL) return finishEarly(summaries, failed); }

        String s4 = IT_04_UserParams_NoAuth.run(); summaries.add(s4);
        if (s4.contains("FAIL:")) { failed++; if (STOP_ON_FIRST_FAIL) return finishEarly(summaries, failed); }

        String s5 = IT_05_UserConnections.run(); summaries.add(s5);
        if (s5.contains("FAIL:")) { failed++; if (STOP_ON_FIRST_FAIL) return finishEarly(summaries, failed); }

        return finish(summaries, failed);
    }

    private static int finishEarly(List<String> summaries, int failed) {
        TestLog.boom("⛔ Остановка прогона: найден FAIL, STOP_ON_FIRST_FAIL=ON");
        return finish(summaries, failed);
    }

    private static int finish(List<String> summaries, int failed) {
        TestLog.title("IT RUN RESULT (per test)");
        for (String s : summaries) System.out.println(s);

        if (failed == 0) TestLog.ok("\n  ВСЕ IT ТЕСТЫ УСПЕШНО ЗАВЕРШЕНЫ");
        else TestLog.boom("❌ IT ПРОГОН УПАЛ: failed=" + failed + " из " + summaries.size());

        return failed;
    }
}