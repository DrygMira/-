package test.it;

import test.it.utils.TestConfig;
import test.it.utils.TestColors;
import test.it.utils.ItRunContext;
import test.it.ws.IT_03_AddBlock_NoAuth;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/**
 * Ручной запуск всех IT тестов БЕЗ JUnit / Suite.
 *
 * Делает:
 *  1) чистит папку data/
 *  2) запускает 3 теста по очереди (через их main)
 *
 * Запуск из IDE:
 *   Run 'main' этого класса
 *
 * Запуск из консоли:
 *   ./gradlew testClasses
 *   java -cp build/classes/java/test:build/resources/test:build/classes/java/main:build/resources/main <тут_свой_classpath> test.it.IT_RunAllMain
 *
 * (Classpath зависит от твоего Gradle, но в IDE проще всего)
 */
public class IT_RunAllMain {

    public static void main(String[] args) {
        try {
            ItRunContext.initIfNeeded();

            banner("ШАГ 0: очистка data/");
            cleanupDataDir(TestConfig.DATA_DIR);

            banner("ШАГ 1: IT_01_AddUser");
            IT_01_AddUser.main(new String[0]);

            banner("ШАГ 2: IT_02_Sessions");
            IT_02_Sessions.main(new String[0]);

            banner("ШАГ 3: IT_03_AddBlock_NoAuth");
            IT_03_AddBlock_NoAuth.main(new String[0]);

            System.out.println(TestColors.G + "\n✅ ВСЕ 3 IT ТЕСТА УСПЕШНО ЗАВЕРШЕНЫ\n" + TestColors.R);
        } catch (Throwable t) {
            System.out.println(TestColors.RED + "\n❌ IT_RunAllMain: ПРОГОН УПАЛ\n" + TestColors.R);
            t.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void banner(String s) {
        System.out.println(TestColors.C + "\n============================================================" + TestColors.R);
        System.out.println(TestColors.C + s + TestColors.R);
        System.out.println(TestColors.C + "============================================================\n" + TestColors.R);
    }

    private static void cleanupDataDir(String dirName) throws IOException {
        Path dir = Paths.get(dirName);
        if (!Files.exists(dir)) {
            System.out.println("ℹ️ data dir not found: " + dir.toAbsolutePath() + " (создаю)");
            Files.createDirectories(dir);
            return;
        }

        // удаляем ВСЁ внутри папки, но саму папку оставляем
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .filter(p -> !p.equals(dir))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Не смог удалить: " + p.toAbsolutePath(), e);
                    }
                });

        System.out.println("✅ data очищена: " + dir.toAbsolutePath());
    }
}