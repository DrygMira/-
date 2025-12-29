package test.it;

import server.ws.WsServer;

public class IT_RunAllCleanStartWsMain {

    public static void main(String[] args) {
        // 1) Гасим всё на 7070 (если ничего нет — не падаем)
        runBash("kill -9 $(lsof -t -i:7070) 2>/dev/null || true");

        // 2) Чистим data/
        IT_CleanAllDate.main(new String[0]);

        // 3) Стартуем WS сервер в отдельном потоке (daemon, чтобы JVM могла завершиться)
        Thread wsThread = new Thread(() -> {
            try {
                WsServer.main(new String[0]); // внутри join() -> поток будет висеть
            } catch (Throwable t) {
                t.printStackTrace(System.out);
            }
        }, "wsServer-thread");
        wsThread.setDaemon(true);
        wsThread.start();

        // 4) Ждём, чтобы успел стартануть
        sleepMs(1000);

        // 5) Запускаем все IT тесты (без System.exit внутри)
        int failed = IT_RunAllMain.runAll();

        // 6) Завершаем процесс с кодом ошибок
        System.exit(failed);
    }

    private static void runBash(String cmd) {
        try {
            Process p = new ProcessBuilder("bash", "-lc", cmd)
                    .inheritIO()
                    .start();
            int code = p.waitFor();
            // тут не ругаемся: команда может быть "пустой" (ничего не слушает порт)
            // а мы уже добавили "|| true"
        } catch (Exception e) {
            System.out.println("WARN: bash command failed: " + e);
        }
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}