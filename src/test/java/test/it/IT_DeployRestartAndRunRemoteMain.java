package test.it;

import test.it.runner.IT_RunAllMain;

import java.util.Objects;

public class IT_DeployRestartAndRunRemoteMain {

    // ====== НАСТРОЙКИ (можно переопределять systemProperty) ======
    private static final String REMOTE_HOST = System.getProperty("it.remoteHost", "10.147.20.7");
    private static final String REMOTE_USER = System.getProperty("it.remoteUser", "user");

    private static final String REMOTE_DIR  = System.getProperty("it.remoteDir", "/home/user/docker/shine-server");
    private static final String REMOTE_JAR  = REMOTE_DIR + "/shine-server.jar";
    private static final String REMOTE_DATA = System.getProperty("it.remoteDataDir", REMOTE_DIR + "/data");

    private static final String SERVICE_NAME = System.getProperty("it.service", "shine-server");

    private static final String LOCAL_JAR = System.getProperty("it.localJar", "build/libs/shine-server.jar");

    // URI для IT-тестов (переключаем на сервер)
    private static final String WS_URI_SERVER = System.getProperty("it.wsUri", "wss://shineup.me/ws");

    public static void main(String[] args) {

        // 0) Build shadowJar локально
//        shStrict("./gradlew -q shadowJar");

        // 1) stop service на сервере
        sshStrict("sudo systemctl stop " + SERVICE_NAME + " || true");

        // 2) upload jar -> .new
        scpStrict(LOCAL_JAR, REMOTE_JAR + ".new");

        // 3) заменить jar атомарно
        sshStrict("mv -f " + q(REMOTE_JAR + ".new") + " " + q(REMOTE_JAR));

        // 4) удалить data/*
        // (на всякий случай: если папки нет — создать)
        sshStrict("mkdir -p " + q(REMOTE_DATA) + " && rm -rf " + q(REMOTE_DATA) + "/*");

        // 5) start service
        sshStrict("sudo systemctl start " + SERVICE_NAME);

        // 6) дождаться поднятия (простая проверка: порт слушается)
        waitRemotePort7070();

        // 7) переключаем IT на серверный WS URI (без правок исходников)
        System.setProperty("it.wsUri", WS_URI_SERVER);

        // 8) прогон тестов
        int failed = IT_RunAllMain.runAll();
        System.exit(failed);
    }

    private static void waitRemotePort7070() {
        for (int i = 0; i < 50; i++) {
            int code = ssh("ss -ltnp | grep -q ':7070'"); // 0 если найдено
            if (code == 0) return;
            sleepMs(200);
        }
        throw new RuntimeException("Remote port 7070 did not start in time on " + REMOTE_HOST);
    }

    // ---------- helpers ----------
    private static void shStrict(String cmd) {
        int code = sh(cmd);
        if (code != 0) throw new RuntimeException("Command failed (" + code + "): " + cmd);
    }

    private static void sshStrict(String remoteCmd) {
        int code = ssh(remoteCmd);
        if (code != 0) throw new RuntimeException("SSH command failed (" + code + "): " + remoteCmd);
    }

    private static int ssh(String remoteCmd) {
        String cmd = "ssh " + REMOTE_USER + "@" + REMOTE_HOST + " " + q("bash -lc " + q(remoteCmd));
        return sh(cmd);
    }

    private static void scpStrict(String local, String remote) {
        Objects.requireNonNull(local);
        Objects.requireNonNull(remote);
        int code = sh("scp -p " + q(local) + " " + REMOTE_USER + "@" + REMOTE_HOST + ":" + q(remote));
        if (code != 0) throw new RuntimeException("SCP failed (" + code + ")");
    }

    private static int sh(String cmd) {
        try {
            Process p = new ProcessBuilder("bash", "-lc", cmd).inheritIO().start();
            return p.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Command error: " + cmd, e);
        }
    }

    private static String q(String s) {
        // простая одинарная кавычка для bash
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}